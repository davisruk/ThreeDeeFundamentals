package online.davisfamily.warehouse.sim.dsp.analysis.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayInputLoader;
import online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayInputPaths;
import online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayLoadedInput;
import online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayRuntimeState;
import online.davisfamily.warehouse.sim.dsp.analysis.DspUncalibratedFullDayProfile;
import online.davisfamily.warehouse.sim.dsp.analysis.DspUncalibratedFullDayProfile.QueueCapacities;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.OsrInventoryConfig;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyAreaConfig;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteConfig;

class DspFullDayAnalysisRuntimeTest {
    private static final LocalDate OPERATING_DATE = LocalDate.of(2026, 9, 2);

    @Test
    void shouldExposeEveryConfiguredP2pTargetThroughOnePublicRuntime(
            @TempDir Path directory) throws IOException {
        DspUncalibratedFullDayProfile profile = profile();
        DspFullDayLoadedInput input = loadP2pAndEmptyInput(directory, profile);

        try (DspFullDayAnalysisRuntime runtime =
                new DspFullDayAnalysisRuntimeFactory().create(input, profile)) {
            assertEquals(2, input.data().orders().size());
            assertEquals(1, input.data().orders().stream()
                    .filter(order -> order.orderType() == OrderType.FULL_PACK)
                    .count());
            assertEquals(1, input.data().orders().stream()
                    .filter(order -> order.orderType() == OrderType.EMPTY)
                    .count());
            assertEquals(1, input.data().inboundToteManifests().size());
            List<OperationalRouteDestination> expectedDestinations = profile.p2pLineDefinitions()
                    .stream()
                    .map(definition -> definition.destination())
                    .toList();
            List<OperationalRouteDestination> lineDestinations = runtime.lineRuntimes().stream()
                    .map(line -> line.lineDefinition().destination())
                    .toList();

            assertEquals(expectedDestinations, lineDestinations);
            assertEquals(5, new LinkedHashSet<>(lineDestinations).size());
            assertEquals(5, runtime.lineRuntimes().size());
            assertTrue(runtime.lineRuntimes().stream()
                    .allMatch(line -> line.prlConveyors().size() == 31));
            assertTrue(runtime.lineRuntimes().stream()
                    .allMatch(line -> line.outboundToteAllocator()
                            == runtime.outboundToteAllocator()));

            List<OperationalRouteDestination> stationDestinations = runtime
                    .stationProcessingRuntime()
                    .destinations()
                    .stream()
                    .filter(destination -> destination.stationType() == StationType.P2P)
                    .toList();
            assertEquals(5, stationDestinations.size());
            for (OperationalRouteDestination destination : expectedDestinations) {
                assertEquals(1, stationDestinations.stream()
                        .filter(destination::equals)
                        .count());
            }

            List<OperationalRouteDestination> routeDestinations = runtime.transportRuntime()
                    .routeCatalogSnapshot()
                    .entries()
                    .stream()
                    .map(entry -> entry.destination())
                    .filter(destination -> destination.stationType() == StationType.P2P)
                    .toList();
            assertEquals(5, routeDestinations.size());
            for (OperationalRouteDestination destination : expectedDestinations) {
                assertEquals(1, routeDestinations.stream()
                        .filter(destination::equals)
                        .count());
            }

            var emptyOrder = input.data().orders().stream()
                    .filter(order -> order.orderType() == OrderType.EMPTY)
                    .findFirst()
                    .orElseThrow();
            assertTrue(runtime.manifestCatalog()
                    .manifestsFor(emptyOrder.orderSheetKey())
                    .isEmpty());
            assertTrue(runtime.osrInventory().snapshot()
                    .storedTotesFor(emptyOrder.orderSheetKey())
                    .isEmpty());
            assertTrue(runtime.osrInventory().snapshot().departedTotes().stream()
                    .noneMatch(manifest -> manifest.orderSheetKey()
                            .equals(emptyOrder.orderSheetKey())));

            DspFullDayAnalysisRuntimeSnapshot beforeReads = runtime.snapshot();
            runtime.av02AllocationRuntimeController().snapshot();
            runtime.av02Inventory().snapshot();
            runtime.lifecycleLedger().snapshot();
            runtime.outboundToteAllocator().snapshot();
            runtime.elasticRuntime().operationalSnapshot();
            runtime.elasticRuntime().leaseSnapshot();
            runtime.operationalReleaseRuntime().controller().snapshot();
            runtime.transportRuntime().routeCatalogSnapshot();
            runtime.transportRuntime().outboundTransportSnapshot();
            runtime.transportRuntime().inFlightSnapshot();
            runtime.transportRuntime().ingressController().snapshot();
            runtime.transportRuntime().arrivalController().snapshot();
            runtime.transportRuntime().stationArrivalSnapshots();
            runtime.stationProcessingRuntime().coordinatorSnapshot();
            runtime.stationProcessingRuntime().claimantSnapshots();
            runtime.continuationRuntime().snapshot();
            runtime.cutoffController().snapshot();
            runtime.completionSnapshots();
            runtime.metricsSnapshot();
            runtime.lineRuntimes().forEach(DspHeadlessP2pLineRuntime::snapshot);

            assertEquals(beforeReads, runtime.snapshot());
        }
    }

    @Test
    void shouldStopEveryPublicRuntimeOwnerAfterHardCutoff(@TempDir Path directory)
            throws IOException {
        DspUncalibratedFullDayProfile profile = unfinishedProfile();
        DspFullDayLoadedInput input = loadThirdPartyInput(directory, profile);

        DspFullDayAnalysisRuntime runtime = new DspFullDayAnalysisRuntimeFactory()
                .create(input, profile);
        try {
            Duration hardCutoffElapsed = profile.operationalClockConfig()
                    .operatingDurationUntilHardCutoff();
            runtime.update(hardCutoffElapsed.toNanos() / 1_000_000_000d);

            assertEquals(DspFullDayRuntimeState.HARD_CUTOFF_REACHED, runtime.state());
            DspFullDayAnalysisRuntimeSnapshot terminal = runtime.snapshot();
            OwnerReferences owners = ownerReferences(runtime);
            assertEquals(hardCutoffElapsed, terminal.clock().elapsedSimulationTime());
            assertEquals(DspFullDayRuntimeState.HARD_CUTOFF_REACHED, terminal.state());
            assertFalse(terminal.closed());
            assertTrue(terminal.completions().stream()
                    .allMatch(completion -> completion.unsupportedWork().isEmpty()));
            assertTrue(terminal.completions().stream()
                    .anyMatch(completion -> !completion.complete()));

            runtime.update(1d);
            runtime.update(17d);
            runtime.update(1000d);
            assertEquals(terminal, runtime.snapshot());
            assertSameOwnerReferences(owners, runtime);

            runtime.close();
            runtime.close();

            DspFullDayAnalysisRuntimeSnapshot afterClose = runtime.snapshot();
            assertTrue(afterClose.closed());
            assertTerminalValuesUnchanged(terminal, afterClose);
            assertTrue(afterClose.lineSnapshots().stream()
                    .allMatch(line -> line.closed()));
            assertSameOwnerReferences(owners, runtime);
        } finally {
            runtime.close();
        }
    }

    @Test
    void shouldRejectInvalidUpdatesWithoutMutatingThePublicRuntime(
            @TempDir Path directory) throws IOException {
        DspUncalibratedFullDayProfile profile = profile();
        DspFullDayLoadedInput input = loadP2pAndEmptyInput(directory, profile);

        try (DspFullDayAnalysisRuntime runtime =
                new DspFullDayAnalysisRuntimeFactory().create(input, profile)) {
            DspFullDayAnalysisRuntimeSnapshot before = runtime.snapshot();
            OwnerReferences owners = ownerReferences(runtime);

            assertThrows(IllegalArgumentException.class, () -> runtime.update(-1d));
            assertEquals(before, runtime.snapshot());
            assertSameOwnerReferences(owners, runtime);
            assertEquals(DspFullDayRuntimeState.RUNNING, runtime.state());
            assertFalse(runtime.isClosed());

            assertThrows(IllegalArgumentException.class,
                    () -> runtime.update(Double.NaN));
            assertEquals(before, runtime.snapshot());
            assertSameOwnerReferences(owners, runtime);
            assertEquals(DspFullDayRuntimeState.RUNNING, runtime.state());
            assertFalse(runtime.isClosed());
        }
    }

    @Test
    void shouldReconstructIndependentFreshRuntimeOwners(@TempDir Path directory)
            throws IOException {
        DspUncalibratedFullDayProfile profile = profile();
        DspFullDayLoadedInput input = loadP2pAndEmptyInput(directory, profile);
        DspFullDayAnalysisRuntimeFactory factory = new DspFullDayAnalysisRuntimeFactory();

        DspFullDayAnalysisRuntime first = factory.create(input, profile);
        DspFullDayAnalysisRuntimeSnapshot firstInitial = first.snapshot();
        first.update(1d);
        DspFullDayAnalysisRuntimeSnapshot firstProgressed = first.snapshot();
        assertTrue(firstProgressed.clock().elapsedSimulationTime()
                .compareTo(firstInitial.clock().elapsedSimulationTime()) > 0);
        first.close();
        DspFullDayAnalysisRuntimeSnapshot firstClosed = first.snapshot();

        try (DspFullDayAnalysisRuntime second = factory.create(input, profile);
                DspFullDayAnalysisRuntime fresh = factory.create(input, profile)) {
            assertNotSame(first, second);
            assertNotSame(first, fresh);
            assertDistinctOwners(first, second);
            assertDistinctOwners(second, fresh);

            DspFullDayAnalysisRuntimeSnapshot secondInitial = second.snapshot();
            DspFullDayAnalysisRuntimeSnapshot freshInitial = fresh.snapshot();
            assertEquals(freshInitial, secondInitial);
            assertEquals(DspFullDayRuntimeState.RUNNING, second.state());
            assertEquals(Duration.ZERO, secondInitial.clock().elapsedSimulationTime());
            assertEquals(0, secondInitial.av02Allocation().sequence());
            assertEquals(
                    secondInitial.av02Allocation(),
                    second.av02AllocationRuntimeController().snapshot());
            assertEquals(secondInitial.av02Allocation(), freshInitial.av02Allocation());
            assertFalse(second.isClosed());
            assertFalse(fresh.isClosed());

            second.update(1d);
            assertEquals(freshInitial, fresh.snapshot());
            assertEquals(firstClosed, first.snapshot());
            assertTerminalValuesUnchanged(firstProgressed, first.snapshot());
        }
    }

    private static void assertTerminalValuesUnchanged(
            DspFullDayAnalysisRuntimeSnapshot before,
            DspFullDayAnalysisRuntimeSnapshot after) {
        assertEquals(before.state(), after.state());
        assertEquals(before.clock(), after.clock());
        assertEquals(before.scheduler(), after.scheduler());
        assertEquals(before.supply(), after.supply());
        assertEquals(before.osr(), after.osr());
        assertEquals(before.av02(), after.av02());
        assertEquals(before.lifecycle(), after.lifecycle());
        assertEquals(before.av02Allocation(), after.av02Allocation());
        assertEquals(before.operationalRelease(), after.operationalRelease());
        assertEquals(before.elastic(), after.elastic());
        assertEquals(before.stationProcessing(), after.stationProcessing());
        assertEquals(before.stationClaims(), after.stationClaims());
        assertEquals(before.routes(), after.routes());
        assertEquals(before.outboundTransport(), after.outboundTransport());
        assertEquals(before.transportInFlight(), after.transportInFlight());
        assertEquals(before.transportIngress(), after.transportIngress());
        assertEquals(before.transportArrival(), after.transportArrival());
        assertEquals(before.stationArrivals(), after.stationArrivals());
        assertEquals(before.continuation(), after.continuation());
        assertEquals(before.completions(), after.completions());
        assertEquals(before.cutoff(), after.cutoff());
        assertEquals(before.metrics(), after.metrics());
        assertEquals(before.p2pLines().size(), after.p2pLines().size());
        for (int index = 0; index < before.p2pLines().size(); index++) {
            assertLineValuesUnchanged(
                    before.p2pLines().get(index), after.p2pLines().get(index));
        }
    }

    private static void assertLineValuesUnchanged(
            DspHeadlessP2pLineRuntimeSnapshot before,
            DspHeadlessP2pLineRuntimeSnapshot after) {
        assertEquals(before.lineDefinition(), after.lineDefinition());
        assertEquals(before.activity(), after.activity());
        assertEquals(before.stationProcessing(), after.stationProcessing());
        assertEquals(before.prlStatesById(), after.prlStatesById());
        assertEquals(before.prlReceivedPackCountsById(), after.prlReceivedPackCountsById());
        assertEquals(before.completedBagCorrelationIds(), after.completedBagCorrelationIds());
        assertEquals(before.outboundAllocation(), after.outboundAllocation());
    }

    private static void assertDistinctOwners(
            DspFullDayAnalysisRuntime first,
            DspFullDayAnalysisRuntime second) {
        assertNotSame(first.simulationWorld(), second.simulationWorld());
        assertNotSame(first.clockController(), second.clockController());
        assertNotSame(first.schedulerRuntimeState(), second.schedulerRuntimeState());
        assertNotSame(first.supplyController(), second.supplyController());
        assertNotSame(first.manifestCatalog(), second.manifestCatalog());
        assertNotSame(first.loadPlanRegistry(), second.loadPlanRegistry());
        assertNotSame(first.lifecycleLedger(), second.lifecycleLedger());
        assertNotSame(first.osrInventory(), second.osrInventory());
        assertNotSame(first.av02Inventory(), second.av02Inventory());
        assertNotSame(first.outboundToteAllocator(), second.outboundToteAllocator());
        assertNotSame(first.av02AllocationRuntimeController(),
                second.av02AllocationRuntimeController());
        assertNotSame(first.elasticRuntime(), second.elasticRuntime());
        assertNotSame(first.operationalReleaseRuntime(), second.operationalReleaseRuntime());
        assertNotSame(first.transportRuntime(), second.transportRuntime());
        assertNotSame(first.stationProcessingRuntime(), second.stationProcessingRuntime());
        assertNotSame(first.continuationRuntime(), second.continuationRuntime());
        assertNotSame(first.lineRuntimes(), second.lineRuntimes());
        assertNotSame(first.cutoffController(), second.cutoffController());
        assertNotSame(first.completionEvaluator(), second.completionEvaluator());
        assertNotSame(first.metricsCollector(), second.metricsCollector());
        assertNotSame(first.lineRuntimes().getFirst().stationProcessingCoordinator(),
                second.lineRuntimes().getFirst().stationProcessingCoordinator());
        for (int index = 0; index < first.lineRuntimes().size(); index++) {
            assertNotSame(first.lineRuntimes().get(index), second.lineRuntimes().get(index));
        }
    }

    private static OwnerReferences ownerReferences(DspFullDayAnalysisRuntime runtime) {
        return new OwnerReferences(
                runtime.simulationWorld(),
                runtime.clockController(),
                runtime.schedulerRuntimeState(),
                runtime.supplyController(),
                runtime.manifestCatalog(),
                runtime.loadPlanRegistry(),
                runtime.lifecycleLedger(),
                runtime.osrInventory(),
                runtime.av02Inventory(),
                runtime.outboundToteAllocator(),
                runtime.av02AllocationRuntimeController(),
                runtime.elasticRuntime(),
                runtime.operationalReleaseRuntime(),
                runtime.transportRuntime(),
                runtime.stationProcessingRuntime(),
                runtime.continuationRuntime(),
                runtime.lineRuntimes(),
                runtime.lineRuntimes().getFirst().stationProcessingCoordinator(),
                runtime.cutoffController(),
                runtime.completionEvaluator(),
                runtime.metricsCollector());
    }

    private static void assertSameOwnerReferences(
            OwnerReferences owners,
            DspFullDayAnalysisRuntime runtime) {
        assertSame(owners.simulationWorld(), runtime.simulationWorld());
        assertSame(owners.clockController(), runtime.clockController());
        assertSame(owners.schedulerRuntimeState(), runtime.schedulerRuntimeState());
        assertSame(owners.supplyController(), runtime.supplyController());
        assertSame(owners.manifestCatalog(), runtime.manifestCatalog());
        assertSame(owners.loadPlanRegistry(), runtime.loadPlanRegistry());
        assertSame(owners.lifecycleLedger(), runtime.lifecycleLedger());
        assertSame(owners.osrInventory(), runtime.osrInventory());
        assertSame(owners.av02Inventory(), runtime.av02Inventory());
        assertSame(owners.outboundToteAllocator(), runtime.outboundToteAllocator());
        assertSame(owners.av02AllocationRuntimeController(),
                runtime.av02AllocationRuntimeController());
        assertSame(owners.elasticRuntime(), runtime.elasticRuntime());
        assertSame(owners.operationalReleaseRuntime(), runtime.operationalReleaseRuntime());
        assertSame(owners.transportRuntime(), runtime.transportRuntime());
        assertSame(owners.stationProcessingRuntime(), runtime.stationProcessingRuntime());
        assertSame(owners.continuationRuntime(), runtime.continuationRuntime());
        assertSame(owners.lineRuntimes(), runtime.lineRuntimes());
        assertSame(owners.stationProcessingCoordinator(),
                runtime.lineRuntimes().getFirst().stationProcessingCoordinator());
        assertSame(owners.cutoffController(), runtime.cutoffController());
        assertSame(owners.completionEvaluator(), runtime.completionEvaluator());
        assertSame(owners.metricsCollector(), runtime.metricsCollector());
    }

    private static DspUncalibratedFullDayProfile profile() {
        return DspUncalibratedFullDayProfile.productionBaseline(
                OPERATING_DATE, 10, Duration.ofSeconds(1), 2, 4, 4);
    }

    private static DspUncalibratedFullDayProfile unfinishedProfile() {
        DspUncalibratedFullDayProfile baseline =
                DspUncalibratedFullDayProfile.productionBaseline(
                        OPERATING_DATE, 1, Duration.ofSeconds(1), 1, 1, 2);
        return new DspUncalibratedFullDayProfile(
                baseline.operatingDate(),
                new OsrInventoryConfig(1200, List.of("104")),
                baseline.serviceCentreSupplyConfig(),
                baseline.inboundToteArrivalPolicy(),
                baseline.av02AllocationConfig(),
                baseline.p2pElasticAllocationConfig(),
                new OutboundToteConfig(1),
                baseline.maximumPacksPerBag(),
                Duration.ofSeconds(10),
                60,
                baseline.metricSampleInterval(),
                0.001d,
                new QueueCapacities(1, 1, 1, 1, 1),
                new ThirdPartyAreaConfig(16, 1, 100_000d),
                baseline.adaptingStorageConfig(),
                baseline.adaptingBenchDefinitions(),
                baseline.p2pPlaceholderDurations(),
                baseline.p2pLineDefinitions(),
                baseline.prlCountPerLine(),
                baseline.timetable());
    }

    private static DspFullDayLoadedInput loadP2pAndEmptyInput(
            Path directory,
            DspUncalibratedFullDayProfile profile) throws IOException {
        Files.createDirectories(directory);
        Path products = Files.writeString(directory.resolve("products.csv"), """
                dispensingProductPackColumbusCode,name,thirdPartyLocation,length,width,height
                product-a,Product A,,200,100,80
                product-b,Product B,Y74,200,100,80
                """);
        Path full = Files.writeString(directory.resolve("full-104.json"), message(
                "full-104", "001", "05", "full-104-tote", "104", "999",
                List.of(line("full-104-line", "05", "product-a", "pharmacy-104",
                        "patient-104", "rx-full-104", 1, 1))));
        Path empty = Files.writeString(directory.resolve("empty-108.json"), message(
                "empty-108", "001", "03", null, "108", "998",
                List.of(line("empty-108-line", "05", "product-b", "pharmacy-108",
                        "patient-108", "rx-empty-108", 1, 0))));
        return new DspFullDayInputLoader().load(
                new DspFullDayInputPaths(products, List.of(full, empty)), profile);
    }

    private static DspFullDayLoadedInput loadThirdPartyInput(
            Path directory,
            DspUncalibratedFullDayProfile profile) throws IOException {
        Files.createDirectories(directory);
        Path products = Files.writeString(directory.resolve("products.csv"), """
                dispensingProductPackColumbusCode,name,thirdPartyLocation,length,width,height
                product-b,Product B,Y74,200,100,80
                """);
        Path full = Files.writeString(directory.resolve("full-third-party.json"), message(
                "full-third-party", "001", "05", "full-third-party-tote", "104", "999",
                List.of(line("full-third-party-line", "03", "product-b", "pharmacy-104",
                        "patient-104", "rx-third-party", 1, 0))));
        return new DspFullDayInputLoader().load(
                new DspFullDayInputPaths(products, List.of(full)), profile);
    }

    private static String message(
            String orderId,
            String sheetNumber,
            String toteType,
            String physicalToteId,
            String serviceCentreId,
            String priority,
            List<LineSpec> lines) {
        StringBuilder json = new StringBuilder();
        json.append("{\n")
                .append("  \"header\": {\"orderId\":\"").append(orderId)
                .append("\",\"sheetNumber\":\"").append(sheetNumber).append("\"},\n")
                .append("  \"toteIdentifier\": {\"payload\":\"").append(toteType)
                .append("\"},\n");
        if (physicalToteId != null) {
            json.append("  \"transportContainer\": {\"payload\":\"")
                    .append(physicalToteId).append("\"},\n");
        }
        json.append("  \"orderPriority\": {\"payload\":\"").append(priority)
                .append("\"},\n")
                .append("  \"serviceCentre\": {\"payload\":\"")
                .append(serviceCentreId).append("\"},\n")
                .append("  \"orderDetail\": {\n")
                .append("    \"numberOfOrderLines\": ").append(lines.size()).append(",\n")
                .append("    \"orderLines\": [\n");
        for (int index = 0; index < lines.size(); index++) {
            LineSpec line = lines.get(index);
            json.append("      {\"orderLineNumber\":\"").append(line.lineReference())
                    .append("\",\"orderLineType\":\"").append(line.lineType())
                    .append("\",\"pharmacyId\":\"").append(line.pharmacyId())
                    .append("\",\"patientId\":\"").append(line.patientId())
                    .append("\",\"prescriptionId\":\"").append(line.prescriptionId())
                    .append("\",\"productId\":\"").append(line.productId())
                    .append("\",\"numberOfPacks\":\"")
                    .append(String.format("%04d", line.quantity()))
                    .append("\",\"referenceSheetNumber\":\"001\"")
                    .append(",\"numberOfPacksPicked\":\"")
                    .append(String.format("%04d", line.picked()))
                    .append("\",\"referenceOrderId\":\"").append(orderId).append("\"}")
                    .append(index + 1 == lines.size() ? "\n" : ",\n");
        }
        return json.append("    ]\n  }\n}\n").toString();
    }

    private static LineSpec line(
            String lineReference,
            String lineType,
            String productId,
            String pharmacyId,
            String patientId,
            String prescriptionId,
            int quantity,
            int picked) {
        return new LineSpec(lineReference, lineType, productId, pharmacyId, patientId,
                prescriptionId, quantity, picked);
    }

    private record LineSpec(
            String lineReference,
            String lineType,
            String productId,
            String pharmacyId,
            String patientId,
            String prescriptionId,
            int quantity,
            int picked) {
    }

    private record OwnerReferences(
            Object simulationWorld,
            Object clockController,
            Object schedulerRuntimeState,
            Object supplyController,
            Object manifestCatalog,
            Object loadPlanRegistry,
            Object lifecycleLedger,
            Object osrInventory,
            Object av02Inventory,
            Object outboundToteAllocator,
            Object av02AllocationRuntimeController,
            Object elasticRuntime,
            Object operationalReleaseRuntime,
            Object transportRuntime,
            Object stationProcessingRuntime,
            Object continuationRuntime,
            List<DspHeadlessP2pLineRuntime> lineRuntimes,
            Object stationProcessingCoordinator,
            Object cutoffController,
            Object completionEvaluator,
            Object metricsCollector) {
    }
}
