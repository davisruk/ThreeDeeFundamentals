package online.davisfamily.warehouse.sim.dsp.analysis.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayInputLoader;
import online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayInputPaths;
import online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayLoadedInput;
import online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayRuntimeState;
import online.davisfamily.warehouse.sim.dsp.analysis.DspUncalibratedFullDayProfile;
import online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayAnalysisRuntime;
import online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayAnalysisRuntimeFactory;
import online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspHeadlessP2pLineRuntime;
import online.davisfamily.warehouse.sim.dsp.av02.Av02InventorySnapshot;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleSnapshot;
import online.davisfamily.warehouse.sim.dsp.osr.OsrInventorySnapshot;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundAllocationSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.allocation.DspP2pElasticAllocationRuntimeSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.allocation.P2pElasticAllocationSnapshot;
import online.davisfamily.warehouse.sim.dsp.runtime.operational.DspOperationalReleaseControllerSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalReleaseEvaluation;
import online.davisfamily.warehouse.sim.dsp.supply.DspSupplySnapshot;
import online.davisfamily.threedee.sim.framework.SimulationContext;

class DspFullDayMetricsCollectorTest {
    private static final LocalDate OPERATING_DATE = LocalDate.of(2026, 9, 2);

    @Test
    void shouldExposeZeroStateMetadataAndInitialOccupancySample(@TempDir Path directory)
            throws IOException {
        DspUncalibratedFullDayProfile profile = profile();
        DspFullDayLoadedInput input = loadInput(directory, profile);

        try (DspFullDayAnalysisRuntime runtime = new DspFullDayAnalysisRuntimeFactory()
                .create(input, profile)) {
            DspFullDayMetricsSnapshot metrics = runtime.metricsSnapshot();

            assertEquals(profile.profileId(), metrics.profileId());
            assertEquals(profile.calibrationStatus(), metrics.calibrationStatus());
            assertEquals(profile.completionMilestone(), metrics.completionMilestone());
            assertEquals(DspFullDayRuntimeState.RUNNING, metrics.state());
            assertEquals(Duration.ZERO, metrics.observedSimulationDuration());
            assertEquals(1, metrics.occupancySamples().size());
            assertEquals(Duration.ZERO,
                    metrics.occupancySamples().getFirst().elapsedSimulationTime());
            assertEquals(2, metrics.occupancySamples().getFirst().osrOccupancy());
            assertEquals(5, metrics.p2pLines().size());
            assertEquals(0L, metrics.admittedInboundToteCount());
            assertEquals(0L, metrics.allocatedBagCount());
            assertEquals(0d, metrics.actualInboundTotesPerSecond());
        }
    }

    @Test
    void shouldIntegrateFixedStepTimeAndSampleAtConfiguredInterval(@TempDir Path directory)
            throws IOException {
        DspUncalibratedFullDayProfile profile = profile();
        DspFullDayLoadedInput input = loadInput(directory, profile, 100);

        try (DspFullDayAnalysisRuntime runtime = new DspFullDayAnalysisRuntimeFactory()
                .create(input, profile)) {
            DspFullDayMetricsSnapshot before = runtime.metricsSnapshot();
            runtime.metricsCollector().recordExecutionSpeed(2d, 1.5d);
            for (int step = 0; step < 61; step++) {
                runtime.update(1d);
            }

            DspFullDayMetricsSnapshot after = runtime.metricsSnapshot();
            assertEquals(Duration.ZERO, before.observedSimulationDuration());
            assertEquals(Duration.ofSeconds(61), after.observedSimulationDuration());
            assertEquals(2d, after.requestedExecutionSpeed());
            assertEquals(1.5d, after.achievedExecutionSpeed());
            assertTrue(after.occupancySamples().stream()
                    .anyMatch(sample -> sample.elapsedSimulationTime().equals(Duration.ofSeconds(60))));
            assertTrue(after.occupancySamples().size() >= 2);
            assertTrue(after.p2pLines().stream()
                    .allMatch(line -> line.utilization() >= 0d && line.utilization() <= 1d));
            assertEquals(Duration.ZERO, before.occupancySamples().getFirst().elapsedSimulationTime());
            assertThrows(UnsupportedOperationException.class,
                    () -> after.occupancySamples().add(after.occupancySamples().getFirst()));
        }
    }

    @Test
    void shouldCaptureEarlyCompletionAndRetainImmutablePriorMetrics(@TempDir Path directory)
            throws IOException {
        DspUncalibratedFullDayProfile profile = profile();
        DspFullDayLoadedInput input = loadInput(directory, profile);

        try (DspFullDayAnalysisRuntime runtime = new DspFullDayAnalysisRuntimeFactory()
                .create(input, profile)) {
            DspFullDayMetricsSnapshot initial = runtime.metricsSnapshot();
            for (int step = 0; step < 300
                    && runtime.state() == DspFullDayRuntimeState.RUNNING; step++) {
                runtime.update(1d);
            }

            DspFullDayMetricsSnapshot terminal = runtime.metricsSnapshot();
            assertEquals(DspFullDayRuntimeState.ALL_SUPPORTED_WORK_COMPLETE, terminal.state());
            assertTrue(terminal.serviceCentres().stream()
                    .allMatch(centre -> centre.complete()));
            assertTrue(terminal.occupancySamples().size() > initial.occupancySamples().size());
            assertEquals(Duration.ZERO, initial.observedSimulationDuration());
            assertEquals(DspFullDayRuntimeState.RUNNING, initial.state());
        }
    }

    @Test
    void shouldReadEverySupplierOncePerStepAndAccumulateDurationForRepeatedIdentities(
            @TempDir Path directory) throws IOException {
        DspUncalibratedFullDayProfile profile = profile();
        DspFullDayLoadedInput input = loadInput(directory, profile);

        try (DspFullDayAnalysisRuntime runtime = new DspFullDayAnalysisRuntimeFactory()
                .create(input, profile)) {
            List<AtomicInteger> supplierReads = new ArrayList<>();
            DspFullDayMetricsCollector collector = collector(
                    profile,
                    input,
                    countingSuppliers(fixedSuppliers(runtime), supplierReads));

            assertSupplierReads(supplierReads, 1);
            SimulationContext context = new SimulationContext();
            collector.update(context, 0.25d);
            collector.update(context, 0.75d);
            collector.update(context, 2d);

            assertSupplierReads(supplierReads, 4);
            DspFullDayMetricsSnapshot metrics = collector.snapshot();
            assertSupplierReads(supplierReads, 5);
            assertEquals(Duration.ofSeconds(3), metrics.observedSimulationDuration());
            assertEquals(1, metrics.occupancySamples().size());
        }
    }

    @Test
    void shouldRederiveEqualDistinctInputsIndependentlyAndRecoverAfterSupplierFailure(
            @TempDir Path directory) throws IOException {
        DspUncalibratedFullDayProfile profile = profile();
        DspFullDayLoadedInput input = loadInput(directory, profile);

        try (DspFullDayAnalysisRuntime runtime = new DspFullDayAnalysisRuntimeFactory()
                .create(input, profile)) {
            DspFullDayMetricsCollector.SnapshotSuppliers fixed = fixedSuppliers(runtime);
            AtomicReference<DspSupplySnapshot> supply = new AtomicReference<>(
                    fixed.supplySnapshotSupplier().get());
            AtomicReference<OsrInventorySnapshot> osr = new AtomicReference<>(
                    fixed.osrSnapshotSupplier().get());
            AtomicReference<Av02InventorySnapshot> av02 = new AtomicReference<>(
                    fixed.av02SnapshotSupplier().get());
            AtomicReference<PhysicalToteLifecycleSnapshot> lifecycle = new AtomicReference<>(
                    fixed.lifecycleSnapshotSupplier().get());
            AtomicReference<OutboundAllocationSnapshot> outbound = new AtomicReference<>(
                    fixed.outboundSnapshotSupplier().get());
            AtomicReference<WarehouseSchedulerSnapshot> scheduler = new AtomicReference<>(
                    fixed.schedulerSnapshotSupplier().get());
            DspOperationalReleaseEvaluation initialEvaluation =
                    new DspOperationalReleaseEvaluation(Optional.empty(), List.of());
            AtomicReference<DspOperationalReleaseControllerSnapshot> operational =
                    new AtomicReference<>(operationalSnapshot(
                            fixed.operationalReleaseSnapshotSupplier().get(),
                            Optional.of(initialEvaluation)));
            AtomicReference<DspP2pElasticAllocationRuntimeSnapshot> elastic =
                    new AtomicReference<>(fixed.elasticSnapshotSupplier().get());
            DspFullDayMetricsCollector.SnapshotSuppliers mutable = new DspFullDayMetricsCollector.SnapshotSuppliers(
                    fixed.clockSnapshotSupplier(),
                    supply::get,
                    osr::get,
                    av02::get,
                    lifecycle::get,
                    elastic::get,
                    fixed.p2pLineSnapshotsSupplier(),
                    operational::get,
                    fixed.transportInFlightSnapshotSupplier(),
                    fixed.transportIngressSnapshotSupplier(),
                    fixed.transportArrivalSnapshotSupplier(),
                    fixed.outboundTransportSnapshotSupplier(),
                    fixed.stationArrivalSnapshotsSupplier(),
                    fixed.stationProcessingSnapshotSupplier(),
                    fixed.stationClaimSnapshotsSupplier(),
                    outbound::get,
                    scheduler::get,
                    fixed.completionSnapshotsSupplier(),
                    fixed.runtimeStateSupplier());
            DspFullDayMetricsCollector collector = collector(profile, input, mutable);
            SimulationContext context = new SimulationContext();
            collector.update(context, 0d);
            DspFullDayMetricsSnapshot expected = collector.snapshot();

            DspSupplySnapshot originalSupply = supply.get();
            supply.set(copy(originalSupply));
            assertMetricsUnchanged(expected, collector, context);
            OsrInventorySnapshot originalOsr = osr.get();
            osr.set(new OsrInventorySnapshot(
                    originalOsr.capacity(), originalOsr.storedTotes(), originalOsr.departedTotes()));
            assertMetricsUnchanged(expected, collector, context);
            Av02InventorySnapshot originalAv02 = av02.get();
            av02.set(new Av02InventorySnapshot(
                    originalAv02.capacity(), originalAv02.waitingTotes(), originalAv02.departedTotes()));
            assertMetricsUnchanged(expected, collector, context);
            PhysicalToteLifecycleSnapshot originalLifecycle = lifecycle.get();
            lifecycle.set(new PhysicalToteLifecycleSnapshot(
                    originalLifecycle.totes(), originalLifecycle.assignments()));
            assertMetricsUnchanged(expected, collector, context);
            OutboundAllocationSnapshot originalOutbound = outbound.get();
            outbound.set(new OutboundAllocationSnapshot(
                    originalOutbound.openTotesByLine(),
                    originalOutbound.closedTotes(),
                    originalOutbound.allocatedBags()));
            assertMetricsUnchanged(expected, collector, context);
            WarehouseSchedulerSnapshot originalScheduler = scheduler.get();
            scheduler.set(new WarehouseSchedulerSnapshot(
                    originalScheduler.orderStates(),
                    originalScheduler.stationAdmissions(),
                    originalScheduler.preparedLineKeys(),
                    originalScheduler.activeServiceCentreId()));
            assertMetricsUnchanged(expected, collector, context);

            operational.set(operationalSnapshot(
                    operational.get(), Optional.of(initialEvaluation)));
            assertMetricsUnchanged(expected, collector, context);
            DspOperationalReleaseEvaluation equalEvaluation =
                    new DspOperationalReleaseEvaluation(Optional.empty(), List.of());
            operational.set(operationalSnapshot(operational.get(), Optional.of(equalEvaluation)));
            assertMetricsUnchanged(expected, collector, context);
            operational.set(operationalSnapshot(operational.get(), Optional.empty()));
            assertMetricsUnchanged(expected, collector, context);
            operational.set(operationalSnapshot(operational.get(), Optional.empty()));
            assertMetricsUnchanged(expected, collector, context);

            DspP2pElasticAllocationRuntimeSnapshot originalElastic = elastic.get();
            elastic.set(new DspP2pElasticAllocationRuntimeSnapshot(
                    originalElastic.leases(), originalElastic.allocation()));
            assertMetricsUnchanged(expected, collector, context);
            elastic.set(new DspP2pElasticAllocationRuntimeSnapshot(
                    originalElastic.leases(), copy(originalElastic.allocation())));
            assertMetricsUnchanged(expected, collector, context);

            supply.set(null);
            assertThrows(IllegalStateException.class, () -> collector.update(context, 0d));
            supply.set(originalSupply);
            assertMetricsUnchanged(expected, collector, context);
        }
    }

    private static DspFullDayMetricsCollector collector(
            DspUncalibratedFullDayProfile profile,
            DspFullDayLoadedInput input,
            DspFullDayMetricsCollector.SnapshotSuppliers suppliers) {
        return new DspFullDayMetricsCollector(
                profile.profileId(),
                profile.serviceCentreSupplyPolicyId(),
                profile.orderEligibilityPolicyId(),
                profile.candidateRankingPolicyId(),
                profile.p2pLineAllocationPolicyId(),
                profile.outboundAllocationPolicyId(),
                profile.calibrationStatus(),
                profile.completionMilestone(),
                profile.inboundToteArrivalPolicy().interval(),
                profile.metricSampleInterval(),
                profile.serviceCentreSupplyConfig().lowWaterMark(),
                input.report(),
                suppliers);
    }

    private static DspFullDayMetricsCollector.SnapshotSuppliers fixedSuppliers(
            DspFullDayAnalysisRuntime runtime) {
        var clock = runtime.clockController().snapshot();
        var supply = runtime.supplyController().snapshot();
        var osr = runtime.osrInventory().snapshot();
        var av02 = runtime.av02Inventory().snapshot();
        var lifecycle = runtime.lifecycleLedger().snapshot();
        var elastic = runtime.elasticRuntime().operationalSnapshot();
        var p2pLines = runtime.lineRuntimes().stream()
                .map(DspHeadlessP2pLineRuntime::snapshot)
                .toList();
        var operational = runtime.operationalReleaseRuntime().controller().snapshot();
        var transportInFlight = runtime.transportRuntime().inFlightSnapshot();
        var transportIngress = runtime.transportRuntime().ingressController().snapshot();
        var transportArrival = runtime.transportRuntime().arrivalController().snapshot();
        var outboundTransport = runtime.transportRuntime().outboundTransportSnapshot();
        var stationArrivals = runtime.transportRuntime().stationArrivalSnapshots();
        var stationProcessing = runtime.stationProcessingRuntime().coordinatorSnapshot();
        var stationClaims = runtime.stationProcessingRuntime().claimantSnapshots();
        var outbound = runtime.outboundToteAllocator().snapshot();
        var scheduler = runtime.schedulerRuntimeState().snapshot();
        var completions = runtime.completionSnapshots();
        var state = runtime.state();
        return new DspFullDayMetricsCollector.SnapshotSuppliers(
                () -> clock,
                () -> supply,
                () -> osr,
                () -> av02,
                () -> lifecycle,
                () -> elastic,
                () -> p2pLines,
                () -> operational,
                () -> transportInFlight,
                () -> transportIngress,
                () -> transportArrival,
                () -> outboundTransport,
                () -> stationArrivals,
                () -> stationProcessing,
                () -> stationClaims,
                () -> outbound,
                () -> scheduler,
                () -> completions,
                () -> state);
    }

    private static DspFullDayMetricsCollector.SnapshotSuppliers countingSuppliers(
            DspFullDayMetricsCollector.SnapshotSuppliers delegate,
            List<AtomicInteger> reads) {
        return new DspFullDayMetricsCollector.SnapshotSuppliers(
                counted(delegate.clockSnapshotSupplier(), reads),
                counted(delegate.supplySnapshotSupplier(), reads),
                counted(delegate.osrSnapshotSupplier(), reads),
                counted(delegate.av02SnapshotSupplier(), reads),
                counted(delegate.lifecycleSnapshotSupplier(), reads),
                counted(delegate.elasticSnapshotSupplier(), reads),
                counted(delegate.p2pLineSnapshotsSupplier(), reads),
                counted(delegate.operationalReleaseSnapshotSupplier(), reads),
                counted(delegate.transportInFlightSnapshotSupplier(), reads),
                counted(delegate.transportIngressSnapshotSupplier(), reads),
                counted(delegate.transportArrivalSnapshotSupplier(), reads),
                counted(delegate.outboundTransportSnapshotSupplier(), reads),
                counted(delegate.stationArrivalSnapshotsSupplier(), reads),
                counted(delegate.stationProcessingSnapshotSupplier(), reads),
                counted(delegate.stationClaimSnapshotsSupplier(), reads),
                counted(delegate.outboundSnapshotSupplier(), reads),
                counted(delegate.schedulerSnapshotSupplier(), reads),
                counted(delegate.completionSnapshotsSupplier(), reads),
                counted(delegate.runtimeStateSupplier(), reads));
    }

    private static <T> Supplier<T> counted(Supplier<T> delegate, List<AtomicInteger> reads) {
        AtomicInteger count = new AtomicInteger();
        reads.add(count);
        return () -> {
            count.incrementAndGet();
            return delegate.get();
        };
    }

    private static void assertSupplierReads(List<AtomicInteger> reads, int expected) {
        assertEquals(19, reads.size());
        reads.forEach(count -> assertEquals(expected, count.get()));
    }

    private static void assertMetricsUnchanged(
            DspFullDayMetricsSnapshot expected,
            DspFullDayMetricsCollector collector,
            SimulationContext context) {
        collector.update(context, 0d);
        DspFullDayMetricsSnapshot actual = collector.snapshot();
        assertEquals(expected.state(), actual.state());
        assertEquals(expected.clock(), actual.clock());
        assertEquals(expected.observedSimulationDuration(), actual.observedSimulationDuration());
        assertEquals(expected.occupancySamples(), actual.occupancySamples());
        assertEquals(expected.blockDurations(), actual.blockDurations());
        assertEquals(expected.p2pLines(), actual.p2pLines());
        assertEquals(expected.elasticInfeasibilityHistory(), actual.elasticInfeasibilityHistory());
        assertEquals(
                expected.serviceCentres().stream()
                        .map(metrics -> List.of(
                                metrics.serviceCentreId(),
                                metrics.unfinishedSheetCount(),
                                metrics.elasticIssues()))
                        .toList(),
                actual.serviceCentres().stream()
                        .map(metrics -> List.of(
                                metrics.serviceCentreId(),
                                metrics.unfinishedSheetCount(),
                                metrics.elasticIssues()))
                        .toList());
    }

    private static DspSupplySnapshot copy(DspSupplySnapshot source) {
        return new DspSupplySnapshot(
                source.policyId(),
                source.lowWaterMark(),
                source.osrCapacity(),
                source.osrOccupancy(),
                source.activeInboundServiceCentreId(),
                source.nextPhysicalAdmissionElapsedTime(),
                source.authorizedEmptyOrderSheetKeys(),
                source.serviceCentres(),
                source.admittedAfterStartupCount());
    }

    private static P2pElasticAllocationSnapshot copy(P2pElasticAllocationSnapshot source) {
        return new P2pElasticAllocationSnapshot(
                source.profileId(),
                source.calibrationStatus(),
                source.evaluatedAt(),
                source.configuredLineIds(),
                source.maximumConcurrentServiceCentres(),
                source.serviceCentres(),
                source.issues());
    }

    private static DspOperationalReleaseControllerSnapshot operationalSnapshot(
            DspOperationalReleaseControllerSnapshot source,
            Optional<DspOperationalReleaseEvaluation> evaluation) {
        return new DspOperationalReleaseControllerSnapshot(
                source.evaluationMode(),
                source.evaluationInFlight(),
                source.lastCompletedEvaluationSequence(),
                evaluation,
                source.lastCommandApplicationResult(),
                source.lastPhysicalToteId());
    }

    private static DspUncalibratedFullDayProfile profile() {
        return DspUncalibratedFullDayProfile.productionBaseline(
                OPERATING_DATE, 10, Duration.ofSeconds(1), 2, 4, 4);
    }

    private static DspFullDayLoadedInput loadInput(
            Path directory,
            DspUncalibratedFullDayProfile profile) throws IOException {
        return loadInput(directory, profile, 2);
    }

    private static DspFullDayLoadedInput loadInput(
            Path directory,
            DspUncalibratedFullDayProfile profile,
            int orderCount) throws IOException {
        Path productMaster = Files.writeString(directory.resolve("products.csv"), """
                dispensingProductPackColumbusCode,name,thirdPartyLocation,length,width,height
                product-a,Product A,,200,100,80
                """);
        List<Path> orderFiles = new ArrayList<>();
        for (int index = 0; index < orderCount; index++) {
            String orderId = "order-" + (104 + index);
            String toteId = "tote-" + (104 + index);
            String serviceCentreId = index % 2 == 0 ? "104" : "108";
            String priority = serviceCentreId.equals("104") ? "999" : "998";
            String sheetNumber = String.format("%03d", index + 1);
            orderFiles.add(Files.writeString(directory.resolve(orderId + ".json"),
                    message(orderId, toteId, serviceCentreId, priority, sheetNumber)));
        }
        return new DspFullDayInputLoader().load(
                new DspFullDayInputPaths(productMaster, orderFiles), profile);
    }

    private static String message(
            String orderId,
            String physicalToteId,
            String serviceCentreId,
            String priority,
            String sheetNumber) {
        return """
        {
          "header": {"orderId":"%s","sheetNumber":"%s"},
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
                        "referenceSheetNumber":"%s",
                        "numberOfPacksPicked":"1",
                        "referenceOrderId":"%s"
                      }
                    ]
                  }
                }
                """.formatted(
                        orderId,
                        sheetNumber,
                        physicalToteId,
                        priority,
                        serviceCentreId,
                        orderId,
                        sheetNumber,
                        orderId);
    }
}
