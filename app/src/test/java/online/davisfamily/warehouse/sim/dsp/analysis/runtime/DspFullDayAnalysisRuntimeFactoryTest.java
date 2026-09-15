package online.davisfamily.warehouse.sim.dsp.analysis.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import online.davisfamily.threedee.sim.framework.SimulationController;
import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayInputLoader;
import online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayInputPaths;
import online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayLoadedInput;
import online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayRuntimeState;
import online.davisfamily.warehouse.sim.dsp.analysis.DspUncalibratedFullDayProfile;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedBag;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteSource;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundAllocationSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pPhysicalToteAssignment;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pReleaseAssignmentRequest;
import online.davisfamily.warehouse.sim.totebag.handoff.BagReservation;
import online.davisfamily.warehouse.sim.totebag.bag.Bag;
import online.davisfamily.warehouse.sim.totebag.plan.BagSpec;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;

class DspFullDayAnalysisRuntimeFactoryTest {
    private static final LocalDate OPERATING_DATE = LocalDate.of(2026, 9, 2);

    @Test
    void shouldComposeFiveLinesWithSharedAuthoritativeOwners(@TempDir Path directory)
            throws IOException {
        DspUncalibratedFullDayProfile profile = profile();
        DspFullDayLoadedInput input = loadSingleFullPack(directory, profile);

        try (DspFullDayAnalysisRuntime runtime =
                new DspFullDayAnalysisRuntimeFactory().create(input, profile)) {
            assertEquals(5, runtime.lineRuntimes().size());
            assertEquals(31, runtime.lineRuntimes().getFirst().prlConveyors().size());
            assertSame(runtime.outboundToteAllocator(),
                    runtime.lineRuntimes().getFirst().outboundToteAllocator());
            assertEquals(DspFullDayRuntimeState.RUNNING, runtime.state());
            assertEquals(5, runtime.snapshot().lineSnapshots().size());

            runtime.update(1d);
            runtime.update(120d);
            assertEquals(DspFullDayRuntimeState.RUNNING, runtime.state());
            assertTrue(runtime.snapshot().completions().stream()
                    .anyMatch(snapshot -> snapshot.serviceCentreId().equals("104")));
        }
    }

    @Test
    void shouldKeepCompletionCountsIndexedByServiceCentreAcrossFixedSteps(
            @TempDir Path directory) throws IOException {
        DspUncalibratedFullDayProfile profile = profile();
        DspFullDayLoadedInput input = loadSingleFullPack(directory, profile);

        try (DspFullDayAnalysisRuntime runtime =
                new DspFullDayAnalysisRuntimeFactory().create(input, profile)) {
            var initial = runtime.snapshot().completionSnapshots();
            assertEquals(List.of("104", "108"), initial.stream()
                    .map(snapshot -> snapshot.serviceCentreId())
                    .toList());
            assertEquals(1, initial.get(0).osrWaitingCount());
            assertEquals(1, initial.get(0).remainingPhysicalPackCount());
            assertEquals(1, initial.get(0).remainingPlannedBagCount());
            assertEquals(1, initial.get(1).osrWaitingCount());
            assertEquals(1, initial.get(1).remainingPhysicalPackCount());
            assertEquals(1, initial.get(1).remainingPlannedBagCount());

            runtime.update(1d);

            var afterOneFixedStep = runtime.snapshot().completionSnapshots();
            assertEquals(List.of("104", "108"), afterOneFixedStep.stream()
                    .map(snapshot -> snapshot.serviceCentreId())
                    .toList());
            assertTrue(afterOneFixedStep.stream()
                    .allMatch(snapshot -> snapshot.remainingPlannedBagCount() >= 0
                            && snapshot.remainingPhysicalPackCount() >= 0));
        }
    }

    @Test
    void shouldRefreshComposedP2pAdmissionAcrossOperationalEvaluations(
            @TempDir Path directory) throws IOException {
        DspUncalibratedFullDayProfile profile = profile();
        DspFullDayLoadedInput input = loadSingleFullPack(directory, profile);

        try (DspFullDayAnalysisRuntime runtime =
                new DspFullDayAnalysisRuntimeFactory().create(input, profile)) {
            runtime.update(1d);
            DspFullDayAnalysisRuntimeSnapshot first = runtime.snapshot();
            long firstSequence = first.operationalRelease()
                    .lastCompletedEvaluationSequence()
                    .orElseThrow();

            runtime.update(1d);
            DspFullDayAnalysisRuntimeSnapshot second = runtime.snapshot();
            long secondSequence = second.operationalRelease()
                    .lastCompletedEvaluationSequence()
                    .orElseThrow();

            assertTrue(secondSequence > firstSequence);
            assertTrue(first.operationalRelease().lastEvaluation().isPresent());
            assertTrue(second.operationalRelease().lastEvaluation().isPresent());
            assertEquals(second.p2pLines().size(), runtime.lineRuntimes().size());
            for (int index = 0; index < runtime.lineRuntimes().size(); index++) {
                DspHeadlessP2pLineRuntime lineRuntime = runtime.lineRuntimes().get(index);
                DspHeadlessP2pLineRuntimeSnapshot lineSnapshot = second.p2pLines().get(index);
                assertEquals(
                        lineSnapshot.activity().packPath().nonIdlePrlCount(),
                        lineRuntime.nonIdlePrlCount());
                assertEquals(
                        lineSnapshot.activity().input().stationArrivalCount(),
                        lineRuntime.stationArrivalCount());
            }
        }
    }

    @Test
    void shouldCloseOnlyTheAllocatedCentreAndRecheckLiveProcessingState(
            @TempDir Path directory) throws IOException {
        DspUncalibratedFullDayProfile profile = profile();
        DspFullDayLoadedInput input = loadSingleFullPack(directory, profile);

        try (DspFullDayAnalysisRuntime runtime =
                new DspFullDayAnalysisRuntimeFactory().create(input, profile)) {
            PlannedBag centre104Bag = input.bagPlan().plannedBags().stream()
                    .filter(bag -> bag.serviceCentreId().equals("104"))
                    .findFirst()
                    .orElseThrow();
            assertTrue(input.bagPlan().plannedBags().stream()
                    .anyMatch(bag -> bag.serviceCentreId().equals("108")));

            var line = runtime.lineRuntimes().getFirst();
            OrderSheetKey sourceSheet = centre104Bag.owningOrderSheetKeys().getFirst();
            var manifest = input.data().inboundToteManifests().stream()
                    .filter(candidate -> candidate.orderSheetKey().equals(sourceSheet))
                    .findFirst()
                    .orElseThrow();
            P2pPhysicalToteAssignment assignment = new P2pPhysicalToteAssignment(
                    manifest.physicalToteId(),
                    centre104Bag.serviceCentreId(),
                    line.lineDefinition().lineId(),
                    line.lineDefinition().destination());
            runtime.elasticRuntime().operationalReleaseAssignmentCommitter()
                    .prepare(new P2pReleaseAssignmentRequest(
                            manifest.physicalToteId(),
                            sourceSheet,
                            centre104Bag.serviceCentreId(),
                            line.lineDefinition().destination().targetId(),
                            OperationalPhysicalToteSource.OSR,
                            java.util.Optional.of(assignment)))
                    .commit();
            runtime.outboundToteAllocator().allocate(
                    line.lineDefinition().lineId(), centre104Bag, Duration.ZERO);
            Bag probeBag = probeBag();
            BagReservation reservation = line.bagReceiver().reserveIncomingBag(probeBag);
            line.bagReceiver().beginReceiving(reservation);

            OutboundAllocationSnapshot beforeProcessingCheck =
                    runtime.outboundToteAllocator().snapshot();
            runtime.cutoffController().update(new SimulationContext(), 0d);

            assertSame(beforeProcessingCheck, runtime.outboundToteAllocator().snapshot());
            assertEquals(1, runtime.outboundToteAllocator().snapshot().openTotesByLine().size());

            line.bagReceiver().completeReceiving(reservation);
            assertTrue(line.bagReceiver().removeReceivedBag(probeBag));
            runtime.cutoffController().update(new SimulationContext(), 0d);

            OutboundAllocationSnapshot afterClosure = runtime.outboundToteAllocator().snapshot();
            assertNotSame(beforeProcessingCheck, afterClosure);
            assertEquals(1, afterClosure.closedTotes().size());
            assertEquals("104", afterClosure.closedTotes().getFirst().serviceCentreId().orElseThrow());
            assertTrue(afterClosure.openTotesByLine().isEmpty());
            assertSame(afterClosure, runtime.outboundToteAllocator().snapshot());

            runtime.cutoffController().update(new SimulationContext(), 0d);
            assertSame(afterClosure, runtime.outboundToteAllocator().snapshot());
        }
    }

    @Test
    void shouldValidateBeforeRegisteringAnyController(@TempDir Path directory)
            throws IOException {
        DspUncalibratedFullDayProfile valid = profile();
        DspFullDayLoadedInput input = loadSingleFullPack(directory, valid);
        DspUncalibratedFullDayProfile invalid = new DspUncalibratedFullDayProfile(
                valid.operatingDate(),
                valid.osrInventoryConfig(),
                valid.serviceCentreSupplyConfig(),
                valid.inboundToteArrivalPolicy(),
                valid.av02AllocationConfig(),
                valid.p2pElasticAllocationConfig(),
                valid.outboundToteConfig(),
                valid.maximumPacksPerBag(),
                valid.fixedStep(),
                valid.maximumStepsPerAdvance(),
                valid.metricSampleInterval(),
                valid.routeSpeedUnitsPerSecond(),
                valid.queueCapacities(),
                valid.thirdPartyAreaConfig(),
                valid.adaptingStorageConfig(),
                List.of(new DspUncalibratedFullDayProfile.AdaptingBenchDefinition(
                        valid.p2pLineDefinitions().getFirst().destination().targetId(), 0d)),
                valid.p2pPlaceholderDurations(),
                valid.p2pLineDefinitions(),
                valid.prlCountPerLine(),
                valid.timetable());
        RecordingWorld world = new RecordingWorld();

        assertThrows(IllegalArgumentException.class,
                () -> new DspFullDayAnalysisRuntimeFactory().create(world, input, invalid));
        assertEquals(0, world.controllerCount);
    }

    @Test
    void shouldStopAtHardCutoffAndMakeTerminalActionsIdempotent(@TempDir Path directory)
            throws IOException {
        DspUncalibratedFullDayProfile profile = profile();
        DspFullDayLoadedInput input = loadSingleFullPack(directory, profile);

        DspFullDayAnalysisRuntime runtime = new DspFullDayAnalysisRuntimeFactory()
                .create(input, profile);
        try {
            runtime.update(24 * 60 * 60d);
            assertEquals(DspFullDayRuntimeState.HARD_CUTOFF_REACHED, runtime.state());
            DspFullDayAnalysisRuntimeSnapshot cutoff = runtime.snapshot();

            runtime.update(1d);
            assertEquals(DspFullDayRuntimeState.HARD_CUTOFF_REACHED, runtime.state());
            assertEquals(cutoff.cutoffSnapshot(), runtime.snapshot().cutoffSnapshot());
        } finally {
            runtime.close();
            runtime.close();
        }
        assertTrue(runtime.isClosed());
    }

    @Test
    void shouldReachEarlyCompletionAfterPhysicalWorkAndOutputClosure(@TempDir Path directory)
            throws IOException {
        DspUncalibratedFullDayProfile profile = profile();
        DspFullDayLoadedInput input = loadSingleFullPack(directory, profile);

        try (DspFullDayAnalysisRuntime runtime =
                new DspFullDayAnalysisRuntimeFactory().create(input, profile)) {
            for (int step = 0; step < 300
                    && runtime.state() == DspFullDayRuntimeState.RUNNING; step++) {
                runtime.update(1d);
            }

            assertEquals(
                    DspFullDayRuntimeState.ALL_SUPPORTED_WORK_COMPLETE,
                    runtime.state(),
                    () -> "completion=" + runtime.snapshot().completionSnapshots()
                            + ", operational=" + runtime.snapshot().operationalRelease()
                            + ", elastic=" + runtime.snapshot().elastic()
                            + ", scheduler=" + runtime.snapshot().scheduler());
            assertTrue(runtime.snapshot().completions().stream()
                    .allMatch(snapshot -> snapshot.complete()));
        }
    }

    private static DspUncalibratedFullDayProfile profile() {
        return DspUncalibratedFullDayProfile.productionBaseline(
                OPERATING_DATE, 10, Duration.ofSeconds(1), 2, 4, 4);
    }

    private static DspFullDayLoadedInput loadSingleFullPack(
            Path directory,
            DspUncalibratedFullDayProfile profile) throws IOException {
        Path productMaster = Files.writeString(directory.resolve("products.csv"), """
                dispensingProductPackColumbusCode,name,thirdPartyLocation,length,width,height
                product-a,Product A,,200,100,80
                """);
        Path firstOrder = Files.writeString(directory.resolve("order-104.json"),
                message("order-104", "tote-104", "104", "999"));
        Path secondOrder = Files.writeString(directory.resolve("order-108.json"),
                message("order-108", "tote-108", "108", "998"));
        return new DspFullDayInputLoader().load(
                new DspFullDayInputPaths(productMaster, List.of(firstOrder, secondOrder)), profile);
    }

    private static String message(
            String orderId,
            String physicalToteId,
            String serviceCentreId,
            String priority) {
        return """
                {
                  "header": {"orderId":"%s","sheetNumber":"001"},
                  "toteIdentifier": {"payload":"05"},
                  "transportContainer": {"payload":"%s"},
                  "orderPriority": {"payload":"%s"},
                  "serviceCentre": {"payload":"%s"},
                  "orderDetail": {
                    "numberOfOrderLines": 1,
                    "orderLines": [
                      {
                        "orderLineNumber":"line-1",
                        "orderLineType":"05",
                        "pharmacyId":"pharmacy-1",
                        "patientId":"patient-1",
                        "prescriptionId":"%s-prescription",
                        "productId":"product-a",
                        "numberOfPacks":"1",
                        "referenceSheetNumber":"001",
                        "numberOfPacksPicked":"1",
                        "referenceOrderId":"%s"
                      }
                    ]
                  }
                }
                """.formatted(orderId, physicalToteId, priority, serviceCentreId, orderId, orderId);
    }

    private static Bag probeBag() {
        String correlationId = "probe-correlation";
        return new Bag(
                "probe-bag",
                correlationId,
                List.of(new PackPlan(
                        "probe-pack",
                        correlationId,
                        new PackDimensions(0.20f, 0.10f, 0.08f))),
                new BagSpec(0.34f, 0.28f, 0.22f));
    }

    private static final class RecordingWorld extends SimulationWorld {
        private int controllerCount;

        @Override
        public void addController(SimulationController controller) {
            controllerCount++;
            super.addController(controller);
        }
    }
}
