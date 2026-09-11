package online.davisfamily.warehouse.sim.dsp.analysis.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayInputLoader;
import online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayInputPaths;
import online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayLoadedInput;
import online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayRuntimeState;
import online.davisfamily.warehouse.sim.dsp.analysis.DspUncalibratedFullDayProfile;

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

    private static final class RecordingWorld extends SimulationWorld {
        private int controllerCount;

        @Override
        public void addController(SimulationController controller) {
            controllerCount++;
            super.addController(controller);
        }
    }
}
