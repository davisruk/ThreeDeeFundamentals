package online.davisfamily.warehouse.sim.dsp.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayAnalysisReport;
import online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayReportJsonWriter;
import online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayAnalysisRuntimeFactory;
import online.davisfamily.warehouse.sim.dsp.analysis.metrics.DspFullDayBlockCategory;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedBag;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleState;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteConfig;
import online.davisfamily.warehouse.sim.dsp.osr.OsrInventoryConfig;
import online.davisfamily.warehouse.sim.dsp.supply.PhysicalToteSupplyState;
import online.davisfamily.warehouse.sim.dsp.supply.ServiceCentreSupplySnapshot;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyAreaConfig;
import online.davisfamily.warehouse.sim.dsp.model.StationType;

/** Full-day integration proof using only synthetic, test-owned input files. */
class DspFullDayAnalysisScenarioTest {
    private static final LocalDate OPERATING_DATE = LocalDate.of(2026, 9, 2);

    @Test
    void shouldExecuteTheMixedFullDayScenario(@TempDir Path directory) throws Exception {
        ScenarioRun first = runScenario(directory.resolve("mixed-first"));
        ScenarioRun second = runScenario(directory.resolve("mixed-second"));

        assertEquals(first.report(), second.report());
        assertEquals(first.reportJson(), second.reportJson());
        assertEquals(first.inspection(), second.inspection());

        DspFullDayAnalysisReport report = first.report();
        var snapshot = report.runtimeSnapshot();
        Set<OrderType> orderTypes = first.input().data().orders().stream()
                .map(order -> order.orderType())
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(OrderType.class)));
        assertEquals(Set.of("104", "108", "109", "116"), first.input().data().orders().stream()
                .map(order -> order.serviceCentreId())
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        assertEquals(EnumSet.allOf(OrderType.class), orderTypes);
        assertEquals(DspUncalibratedFullDayProfile.PROFILE_ID, report.profileId());
        assertEquals(DspUncalibratedFullDayProfile.TIMING_CALIBRATION_STATUS,
                report.calibrationStatus());
        assertEquals(DspCompletionMilestone.P2P_OUTPUT_CLOSED, report.completionMilestone());
        assertEquals(DspFullDayRuntimeState.HARD_CUTOFF_REACHED, report.state());
        assertEquals(DspFullDayTerminationReason.HARD_CUTOFF_REACHED, report.terminationReason());

        assertEquals(5, report.p2pLines().size());
        assertEquals(5, snapshot.p2pLines().size());
        assertTrue(snapshot.p2pLines().stream()
                .allMatch(line -> line.prlStatesById().size() == 31));
        assertTrue(report.metrics().occupancySamples().size() > 1);
        assertTrue(report.metrics().maximumOsrOccupancy() > 0);
        assertTrue(isPositive(report.metrics().blockDurations()
                .get(DspFullDayBlockCategory.STATION_CAPACITY)));
        assertTrue(isPositive(report.metrics().blockDurations()
                .get(DspFullDayBlockCategory.OSR_STATE)));
        assertTrue(report.metrics().p2pLines().stream()
                .anyMatch(line -> line.utilization() > 0d));
        assertEquals(5, report.metrics().closedOutboundToteCount());
        assertEquals(5, report.metrics().allocatedBagCount());

        Map<String, ?> completions = report.serviceCentres().stream()
                .collect(Collectors.toMap(result -> result.serviceCentreId(), result -> result.outcome()));
        assertEquals(DspServiceCentreCompletionOutcome.ON_TARGET, completions.get("104"));
        assertEquals(DspServiceCentreCompletionOutcome.ON_TARGET, completions.get("108"));
        assertEquals(DspServiceCentreCompletionOutcome.ON_TARGET, completions.get("116"));
        assertEquals(DspServiceCentreCompletionOutcome.UNFINISHED_AT_HARD_CUTOFF,
                completions.get("109"));

        assertTrue(snapshot.av02().waitingTotes().stream()
                .anyMatch(tote -> tote.orderSheetKey().equals(new OrderSheetKey("empty-109", 1)))
                || snapshot.av02().departedTotes().stream()
                        .anyMatch(tote -> tote.orderSheetKey()
                                .equals(new OrderSheetKey("empty-109", 1))));
        assertTrue(snapshot.supply().serviceCentres().stream()
                .filter(centre -> centre.serviceCentreId().equals("116")
                        || centre.serviceCentreId().equals("109"))
                .allMatch(centre -> centre.authorizationElapsedTime().isPresent()
                        && centre.admittedAfterStartupCount() > 0));

        assertTrue(snapshot.stationProcessing().activeClaims().stream()
                .anyMatch(claim -> claim.destination().stationType() == StationType.THIRD_PARTY
                        && claim.physicalToteId().value().equals("full-109-third-party-tote")));
        assertTrue(snapshot.lifecycle().totes().values().stream()
                .anyMatch(tote -> tote.state() == PhysicalToteLifecycleState.CONSUMED_AT_ADAPTING));
        assertTrue(snapshot.lifecycle().assignments().size()
                > first.input().data().inboundToteManifests().size());

        Set<String> inputPhysicalToteIds = first.input().data().inboundToteManifests().stream()
                .map(manifest -> manifest.physicalToteId().value())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        assertTrue(snapshot.lifecycle().totes().keySet().stream()
                .map(id -> id.value())
                .noneMatch(id -> id.startsWith("pack-")));
        assertTrue(snapshot.lifecycle().totes().keySet().stream()
                .map(id -> id.value())
                .collect(Collectors.toSet())
                .containsAll(inputPhysicalToteIds));

        PlannedBag spanningBag = first.input().bagPlan().plannedBags().stream()
                .filter(bag -> bag.prescriptionId().equals("rx-104"))
                .findFirst()
                .orElseThrow();
        assertEquals(2, spanningBag.physicalPackIds().size());
        assertEquals(2, first.input().bagPlan().packTraces().stream()
                .filter(trace -> trace.bagKey().equals(spanningBag.bagKey()))
                .map(trace -> trace.inputPhysicalToteId())
                .distinct()
                .count());
        assertTrue(snapshot.lifecycle()
                .assignmentHistoryFor(new OrderSheetKey("full-104", 1)).stream()
                .map(assignment -> assignment.physicalToteId().value())
                .collect(Collectors.toSet())
                .containsAll(Set.of("full-104-tote-1", "full-104-tote-2")));

        Set<String> completedCorrelations = snapshot.p2pLines().stream()
                .flatMap(line -> line.completedBagCorrelationIds().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        assertFalse(completedCorrelations.isEmpty());
        assertTrue(completedCorrelations.stream()
                .allMatch(correlation -> first.input().bagPlan()
                        .findBagByCorrelationId(correlation).isPresent()));
        assertTrue(snapshot.elastic().leases().lines().stream()
                .flatMap(line -> line.physicalAssignments().stream())
                .findAny()
                .isPresent());

        assertTrue(snapshot.p2pLines().stream()
                .flatMap(line -> line.outboundAllocation().closedTotes().stream())
                .allMatch(tote -> tote.allocatedBags().stream()
                        .allMatch(bag -> bag.plannedBag().serviceCentreId()
                                .equals(tote.serviceCentreId().orElseThrow())
                                && bag.plannedBag().pharmacyId()
                                .equals(tote.pharmacyId().orElseThrow()))));
    }

    @Test
    void shouldMeasureLateOutcomeTimings(@TempDir Path directory) throws Exception {
        ScenarioRun run = runLateOutcomeScenario(directory.resolve("late"));
        assertEquals(DspFullDayRuntimeState.ALL_SUPPORTED_WORK_COMPLETE, run.report().state());
        assertEquals(DspFullDayTerminationReason.ALL_SUPPORTED_WORK_COMPLETE,
                run.report().terminationReason());
        Map<String, DspServiceCentreCompletionOutcome> outcomes = run.report().serviceCentres().stream()
                .collect(Collectors.toMap(result -> result.serviceCentreId(), result -> result.outcome()));
        assertEquals(DspServiceCentreCompletionOutcome.MISSED_TRUNKER, outcomes.get("104"));
        assertEquals(DspServiceCentreCompletionOutcome.OVERTIME_BUT_DISPATCHABLE,
                outcomes.get("109"));
        assertTrue(run.report().serviceCentres().stream()
                .filter(result -> result.serviceCentreId().equals("104"))
                .findFirst().orElseThrow().completionDateTime().orElseThrow()
                .isAfter(OPERATING_DATE.atTime(22, 0)));
        assertTrue(run.report().serviceCentres().stream()
                .filter(result -> result.serviceCentreId().equals("109"))
                .findFirst().orElseThrow().completionDateTime().orElseThrow()
                .isAfter(OPERATING_DATE.atTime(22, 0)));
    }

    @Test
    void shouldRetainUnsupportedWorkAtHardCutoff(@TempDir Path directory) throws Exception {
        ScenarioRun run = runUnsupportedCutoffScenario(directory.resolve("unsupported"));

        assertEquals(DspFullDayRuntimeState.HARD_CUTOFF_REACHED, run.report().state());
        assertEquals(DspFullDayTerminationReason.HARD_CUTOFF_REACHED,
                run.report().terminationReason());
        assertEquals(1, run.report().loadReport().ignoredManualMessageCount());
        assertEquals(1, run.report().loadReport().ignoredManualLineCount());
        assertFalse(run.report().unsupportedWork().isEmpty());
        assertFalse(run.report().unfinishedIdentities().isEmpty());
        assertTrue(isPositive(run.report().metrics().blockDurations()
                .get(DspFullDayBlockCategory.UNSUPPORTED_WORK)));
        assertEquals(Duration.ofHours(18), run.report().runtimeSnapshot().cutoff()
                .terminalElapsedTime().orElseThrow());
    }

    @Test
    void shouldDeferUnresolvedProductByServiceCentreAtHardCutoff(@TempDir Path directory) throws Exception {
        ScenarioRun run = runUnresolvedProductScenario(directory.resolve("unresolved-product"));

        assertEquals(DspFullDayRuntimeState.HARD_CUTOFF_REACHED, run.report().state());
        assertEquals(DspFullDayTerminationReason.HARD_CUTOFF_REACHED,
                run.report().terminationReason());
        assertEquals(1, run.report().loadReport().unresolvedProductLines().size());
        assertEquals("109", run.report().loadReport().unresolvedProductLines().getFirst().serviceCentreId());
        assertTrue(run.input().data().orders().stream()
                .noneMatch(order -> order.orderId().equals("unresolved-order")));
        assertTrue(run.input().bagPlan().packTraces().stream()
                .noneMatch(trace -> trace.sourceProvenance().productId().equals("missing-product")));
        assertTrue(run.report().unsupportedWork().stream()
                .anyMatch(value -> value.contains("missing-product") && value.contains("unresolved-order")));

        Map<String, DspServiceCentreCompletionOutcome> outcomes = run.report().serviceCentres().stream()
                .collect(Collectors.toMap(result -> result.serviceCentreId(), result -> result.outcome()));
        assertNotEquals(DspServiceCentreCompletionOutcome.UNFINISHED_AT_HARD_CUTOFF, outcomes.get("104"));
        assertEquals(DspServiceCentreCompletionOutcome.UNFINISHED_AT_HARD_CUTOFF, outcomes.get("109"));
    }

    @Test
    void shouldExecuteRepeatedCarrierAsDistinctDspJourneys(@TempDir Path directory) throws Exception {
        Files.createDirectories(directory);
        DspUncalibratedFullDayProfile profile = profile();
        Path products = Files.writeString(directory.resolve("products.csv"), """
                dispensingProductPackColumbusCode,name,thirdPartyLocation,length,width,height
                product-a,Product A,,200,100,80
                """);
        Path first = writeMessage(directory, "01-first.json", message(
                "reused-first", "001", "05", "shared-carrier", "104", "999",
                List.of(line("first-line", "05", "product-a", "pharmacy-first",
                        "patient-first", "rx-first", 1, 1))));
        Path second = writeMessage(directory, "02-second.json", message(
                "reused-second", "001", "05", "shared-carrier", "108", "998",
                List.of(line("second-line", "05", "product-a", "pharmacy-second",
                        "patient-second", "rx-second", 1, 1))));
        DspFullDayLoadedInput input = new DspFullDayInputLoader().load(
                new DspFullDayInputPaths(products, List.of(first, second)), profile);

        assertEquals(List.of("shared-carrier", "dsp-reused-shared-carrier-2"),
                input.data().inboundToteManifests().stream()
                        .map(manifest -> manifest.physicalToteId().value())
                        .toList());
        assertEquals(1, input.report().inboundToteIdSubstitutions().size());

        AtomicLong monotonicClock = new AtomicLong();
        DspFullDayAnalysisReport report = new DspFullDayAnalysisRunner(
                () -> monotonicClock.addAndGet(1_000_000L),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8))
                .run(input, profile, directory.resolve("report.json"), Optional.empty(), false);

        assertEquals(DspFullDayRuntimeState.ALL_SUPPORTED_WORK_COMPLETE, report.state());
        assertTrue(report.unsupportedWork().isEmpty());
        assertTrue(report.runtimeSnapshot().lifecycle().totes().keySet().stream()
                .map(id -> id.value())
                .collect(Collectors.toSet())
                .containsAll(Set.of("shared-carrier", "dsp-reused-shared-carrier-2")));
    }

    @Test
    void shouldExecuteCapacityBoundedStartupOverflowThroughPublicRuntime(
            @TempDir Path directory) throws Exception {
        Files.createDirectories(directory);
        DspUncalibratedFullDayProfile profile = boundedStartupOverflowProfile();
        Path products = Files.writeString(directory.resolve("products.csv"), """
                dispensingProductPackColumbusCode,name,thirdPartyLocation,length,width,height
                product-a,Product A,,200,100,80
                """);
        List<Path> messages = List.of(
                writeMessage(directory, "01-first-104.json", message(
                        "startup-first-104", "001", "05", "startup-first-104-tote", "104", "999",
                        List.of(line("first-line", "05", "product-a", "pharmacy-104",
                                "patient-104", "rx-startup-1", 1, 1)))),
                writeMessage(directory, "02-first-108.json", message(
                        "startup-first-108", "001", "05", "startup-first-108-tote", "108", "998",
                        List.of(line("second-line", "05", "product-a", "pharmacy-108",
                                "patient-108", "rx-startup-2", 1, 1)))),
                writeMessage(directory, "03-overflow-104.json", message(
                        "startup-overflow-104", "001", "05", "startup-overflow-104-tote", "104", "999",
                        List.of(line("third-line", "05", "product-a", "pharmacy-104",
                                "patient-104-overflow", "rx-startup-3", 1, 1)))));
        DspFullDayLoadedInput input = new DspFullDayInputLoader().load(
                new DspFullDayInputPaths(products, messages), profile);

        try (var runtime = new DspFullDayAnalysisRuntimeFactory().create(input, profile)) {
            var initial = runtime.snapshot();
            assertEquals(2, initial.osr().occupancy());
            assertEquals(2, initial.osr().capacity());
            assertEquals(0, initial.supply().admittedAfterStartupCount());
            assertEquals(1, initial.supply().serviceCentres().stream()
                    .mapToInt(ServiceCentreSupplySnapshot::upstreamWaitingCount)
                    .sum());
            assertEquals(3, initial.supply().serviceCentres().stream()
                    .mapToInt(ServiceCentreSupplySnapshot::physicalManifestCount)
                    .sum());
            assertEquals(
                    PhysicalToteSupplyState.AUTHORIZED_WAITING,
                    initial.supply().serviceCentres().stream()
                            .flatMap(serviceCentre -> serviceCentre.physicalTotes().stream())
                            .filter(tote -> tote.physicalToteId().value()
                                    .equals("startup-overflow-104-tote"))
                            .findFirst()
                            .orElseThrow()
                            .state());
        }

        DspFullDayAnalysisReport report = new DspFullDayAnalysisRunner(
                () -> 1_000_000L,
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8))
                .run(input, profile, directory.resolve("report.json"), Optional.empty(), false);
        assertEquals(DspFullDayRuntimeState.ALL_SUPPORTED_WORK_COMPLETE, report.state());
        assertEquals(DspFullDayTerminationReason.ALL_SUPPORTED_WORK_COMPLETE,
                report.terminationReason());
        assertEquals(3, report.runtimeSnapshot().supply().serviceCentres().stream()
                .mapToInt(ServiceCentreSupplySnapshot::physicalManifestCount)
                .sum());
        assertTrue(report.runtimeSnapshot().supply().osrOccupancy()
                <= report.runtimeSnapshot().supply().osrCapacity());
    }

    private static ScenarioRun runScenario(Path directory) throws IOException {
        Files.createDirectories(directory);
        DspUncalibratedFullDayProfile profile = profile();
        DspFullDayLoadedInput input = loadMixedInput(directory, profile);
        ByteArrayOutputStream inspectionBytes = new ByteArrayOutputStream();
        AtomicLong monotonicClock = new AtomicLong();
        Path output = directory.resolve("report.json");
        DspFullDayAnalysisReport report = new DspFullDayAnalysisRunner(
                () -> monotonicClock.addAndGet(1_000_000L),
                new PrintStream(inspectionBytes, true, StandardCharsets.UTF_8))
                .run(input, profile, output, Optional.empty(), false);
        return new ScenarioRun(
                input,
                report,
                new DspFullDayReportJsonWriter().serializeToString(report),
                inspectionBytes.toString(StandardCharsets.UTF_8));
    }

    private static ScenarioRun runLateOutcomeScenario(Path directory) throws IOException {
        Files.createDirectories(directory);
        DspUncalibratedFullDayProfile profile = lateOutcomeProfile();
        Path products = Files.writeString(directory.resolve("products.csv"), """
                dispensingProductPackColumbusCode,name,thirdPartyLocation,length,width,height
                product-a,Product A,,200,100,80
                """);
        Path first = writeMessage(directory, "01-full-104.json", message(
                "late-104", "001", "05", "late-104-tote", "104", "999",
                List.of(line("late-104-line", "05", "product-a", "pharmacy-104",
                        "patient-104", "rx-late-104", 1, 1))));
        Path second = writeMessage(directory, "02-full-109.json", message(
                "late-109", "001", "05", "late-109-tote", "109", "990",
                List.of(line("late-109-line", "05", "product-a", "pharmacy-109",
                        "patient-109", "rx-late-109", 1, 1))));
        DspFullDayLoadedInput input = new DspFullDayInputLoader().load(
                new DspFullDayInputPaths(products, List.of(first, second)), profile);
        ByteArrayOutputStream inspectionBytes = new ByteArrayOutputStream();
        AtomicLong monotonicClock = new AtomicLong();
        DspFullDayAnalysisReport report = new DspFullDayAnalysisRunner(
                () -> monotonicClock.addAndGet(1_000_000L),
                new PrintStream(inspectionBytes, true, StandardCharsets.UTF_8))
                .run(input, profile, directory.resolve("report.json"), Optional.empty(), false);
        return new ScenarioRun(
                input,
                report,
                new DspFullDayReportJsonWriter().serializeToString(report),
                inspectionBytes.toString(StandardCharsets.UTF_8));
    }

    private static ScenarioRun runUnsupportedCutoffScenario(Path directory) throws IOException {
        Files.createDirectories(directory);
        DspUncalibratedFullDayProfile profile = unsupportedCutoffProfile();
        Path products = Files.writeString(directory.resolve("products.csv"), """
                dispensingProductPackColumbusCode,name,thirdPartyLocation,length,width,height
                product-a,Product A,,200,100,80
                """);
        Path manual = writeMessage(directory, "01-manual.json", message(
                "manual-order", "001", "01", null, "104", "999",
                List.of(line("manual-line", "01", "product-a", "manual-pharmacy",
                        "manual-patient", "manual-rx", 1, 0))));
        Path supported = writeMessage(directory, "02-supported.json", message(
                "supported-order", "001", "05", "supported-tote", "104", "999",
                List.of(line("supported-line", "05", "product-a", "pharmacy-104",
                        "patient-104", "rx-supported", 1, 1))));
        DspFullDayLoadedInput input = new DspFullDayInputLoader().load(
                new DspFullDayInputPaths(products, List.of(manual, supported)), profile);
        ByteArrayOutputStream inspectionBytes = new ByteArrayOutputStream();
        AtomicLong monotonicClock = new AtomicLong();
        DspFullDayAnalysisReport report = new DspFullDayAnalysisRunner(
                () -> monotonicClock.addAndGet(1_000_000L),
                new PrintStream(inspectionBytes, true, StandardCharsets.UTF_8))
                .run(input, profile, directory.resolve("report.json"), Optional.empty(), false);
        return new ScenarioRun(
                input,
                report,
                new DspFullDayReportJsonWriter().serializeToString(report),
                inspectionBytes.toString(StandardCharsets.UTF_8));
    }

    private static ScenarioRun runUnresolvedProductScenario(Path directory) throws IOException {
        Files.createDirectories(directory);
        DspUncalibratedFullDayProfile profile = unresolvedProductProfile();
        Path products = Files.writeString(directory.resolve("products.csv"), """
                dispensingProductPackColumbusCode,name,thirdPartyLocation,length,width,height
                product-a,Product A,,200,100,80
                """);
        Path supported104 = writeMessage(directory, "01-supported-104.json", message(
                "supported-104-order", "001", "05", "supported-104-tote", "104", "999",
                List.of(line("supported-104-line", "05", "product-a", "pharmacy-104",
                        "patient-104", "rx-supported-104", 1, 1))));
        Path supported109 = writeMessage(directory, "02-supported-109.json", message(
                "supported-109-order", "001", "05", "supported-109-tote", "109", "990",
                List.of(line("supported-109-line", "05", "product-a", "pharmacy-109",
                        "patient-109", "rx-supported-109", 1, 1))));
        Path unresolved = writeMessage(directory, "03-unresolved-109.json", message(
                "unresolved-order", "001", "05", "unresolved-tote", "109", "990",
                List.of(line("unresolved-line", "03", "missing-product", "pharmacy-109",
                        "patient-109", "rx-unresolved", 1, 0))));
        DspFullDayLoadedInput input = new DspFullDayInputLoader().load(
                new DspFullDayInputPaths(products, List.of(supported104, supported109, unresolved)), profile);
        ByteArrayOutputStream inspectionBytes = new ByteArrayOutputStream();
        AtomicLong monotonicClock = new AtomicLong();
        DspFullDayAnalysisReport report = new DspFullDayAnalysisRunner(
                () -> monotonicClock.addAndGet(1_000_000L),
                new PrintStream(inspectionBytes, true, StandardCharsets.UTF_8))
                .run(input, profile, directory.resolve("report.json"), Optional.empty(), false);
        return new ScenarioRun(
                input,
                report,
                new DspFullDayReportJsonWriter().serializeToString(report),
                inspectionBytes.toString(StandardCharsets.UTF_8));
    }

    private static DspUncalibratedFullDayProfile profile() {
        DspUncalibratedFullDayProfile baseline =
                DspUncalibratedFullDayProfile.productionBaseline(
                        OPERATING_DATE, 1, Duration.ofSeconds(1), 1, 1, 2);
        return new DspUncalibratedFullDayProfile(
                baseline.operatingDate(),
                baseline.osrInventoryConfig(),
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
                new DspUncalibratedFullDayProfile.QueueCapacities(1, 1, 1, 1, 1),
                new ThirdPartyAreaConfig(16, 1, 100_000d),
                baseline.adaptingStorageConfig(),
                baseline.adaptingBenchDefinitions(),
                baseline.p2pPlaceholderDurations(),
                baseline.p2pLineDefinitions(),
                baseline.prlCountPerLine(),
                baseline.timetable());
    }

    private static DspUncalibratedFullDayProfile lateOutcomeProfile() {
        DspUncalibratedFullDayProfile baseline =
                DspUncalibratedFullDayProfile.productionBaseline(
                        OPERATING_DATE, 1, Duration.ofSeconds(1), 1, 1, 2);
        return new DspUncalibratedFullDayProfile(
                baseline.operatingDate(),
                new online.davisfamily.warehouse.sim.dsp.osr.OsrInventoryConfig(
                        1200, List.of("104", "109")),
                baseline.serviceCentreSupplyConfig(),
                baseline.inboundToteArrivalPolicy(),
                baseline.av02AllocationConfig(),
                baseline.p2pElasticAllocationConfig(),
                new OutboundToteConfig(1),
                baseline.maximumPacksPerBag(),
                Duration.ofSeconds(10),
                60,
                baseline.metricSampleInterval(),
                0.000045d,
                baseline.queueCapacities(),
                baseline.thirdPartyAreaConfig(),
                baseline.adaptingStorageConfig(),
                baseline.adaptingBenchDefinitions(),
                baseline.p2pPlaceholderDurations(),
                baseline.p2pLineDefinitions(),
                baseline.prlCountPerLine(),
                baseline.timetable());
    }

    private static DspUncalibratedFullDayProfile unsupportedCutoffProfile() {
        DspUncalibratedFullDayProfile baseline =
                DspUncalibratedFullDayProfile.productionBaseline(
                        OPERATING_DATE, 1, Duration.ofSeconds(1), 1, 1, 2);
        return new DspUncalibratedFullDayProfile(
                baseline.operatingDate(),
                new online.davisfamily.warehouse.sim.dsp.osr.OsrInventoryConfig(
                        1200, List.of("104")),
                baseline.serviceCentreSupplyConfig(),
                baseline.inboundToteArrivalPolicy(),
                baseline.av02AllocationConfig(),
                baseline.p2pElasticAllocationConfig(),
                baseline.outboundToteConfig(),
                baseline.maximumPacksPerBag(),
                Duration.ofHours(1),
                3,
                baseline.metricSampleInterval(),
                baseline.routeSpeedUnitsPerSecond(),
                baseline.queueCapacities(),
                baseline.thirdPartyAreaConfig(),
                baseline.adaptingStorageConfig(),
                baseline.adaptingBenchDefinitions(),
                baseline.p2pPlaceholderDurations(),
                baseline.p2pLineDefinitions(),
                baseline.prlCountPerLine(),
                baseline.timetable());
    }

    private static DspUncalibratedFullDayProfile boundedStartupOverflowProfile() {
        DspUncalibratedFullDayProfile baseline = profile();
        return new DspUncalibratedFullDayProfile(
                baseline.operatingDate(),
                new OsrInventoryConfig(2, List.of("104", "108")),
                baseline.serviceCentreSupplyConfig(),
                baseline.inboundToteArrivalPolicy(),
                baseline.av02AllocationConfig(),
                baseline.p2pElasticAllocationConfig(),
                baseline.outboundToteConfig(),
                baseline.maximumPacksPerBag(),
                baseline.fixedStep(),
                baseline.maximumStepsPerAdvance(),
                baseline.metricSampleInterval(),
                baseline.routeSpeedUnitsPerSecond(),
                baseline.queueCapacities(),
                baseline.thirdPartyAreaConfig(),
                baseline.adaptingStorageConfig(),
                baseline.adaptingBenchDefinitions(),
                baseline.p2pPlaceholderDurations(),
                baseline.p2pLineDefinitions(),
                baseline.prlCountPerLine(),
                baseline.timetable());
    }

    private static DspUncalibratedFullDayProfile unresolvedProductProfile() {
        DspUncalibratedFullDayProfile baseline =
                DspUncalibratedFullDayProfile.productionBaseline(
                        OPERATING_DATE, 1, Duration.ofSeconds(1), 1, 1, 2);
        return new DspUncalibratedFullDayProfile(
                baseline.operatingDate(),
                new online.davisfamily.warehouse.sim.dsp.osr.OsrInventoryConfig(
                        1200, List.of("104", "109")),
                baseline.serviceCentreSupplyConfig(),
                baseline.inboundToteArrivalPolicy(),
                baseline.av02AllocationConfig(),
                baseline.p2pElasticAllocationConfig(),
                baseline.outboundToteConfig(),
                baseline.maximumPacksPerBag(),
                Duration.ofHours(1),
                3,
                baseline.metricSampleInterval(),
                baseline.routeSpeedUnitsPerSecond(),
                baseline.queueCapacities(),
                baseline.thirdPartyAreaConfig(),
                baseline.adaptingStorageConfig(),
                baseline.adaptingBenchDefinitions(),
                baseline.p2pPlaceholderDurations(),
                baseline.p2pLineDefinitions(),
                baseline.prlCountPerLine(),
                baseline.timetable());
    }

    private static DspFullDayLoadedInput loadMixedInput(
            Path directory,
            DspUncalibratedFullDayProfile profile) throws IOException {
        Path products = Files.writeString(directory.resolve("products.csv"), """
                dispensingProductPackColumbusCode,name,thirdPartyLocation,length,width,height
                product-a,Product A,,200,100,80
                product-b,Product B,Y74,200,100,80
                """);
        List<Path> messages = new ArrayList<>();
        messages.add(writeMessage(directory, "01-adapted-104.json", message(
                "adapted-104", "001", "02", "adapted-104-tote", "104", "999",
                List.of(line("adapted-104-line", "02", "product-a", "pharmacy-adapted",
                        "patient-adapted", "rx-adapted", 1, 0)))));
        for (int index = 1; index <= 2; index++) {
            messages.add(writeMessage(directory, "full-104-" + index + ".json", message(
                    "full-104", "001", "05", "full-104-tote-" + index, "104", "999",
                    List.of(line("full-104-line-" + index, "05", "product-a", "pharmacy-104",
                            "patient-104", "rx-104", 1, 1)))));
        }
        for (int index = 1; index <= 2; index++) {
            messages.add(writeMessage(directory, "full-104-overflow-" + index + ".json", message(
                    "full-104-overflow-" + index, "001", "05",
                    "full-104-overflow-tote-" + index, "104", "999",
                    List.of(line("full-104-overflow-line-" + index, "05", "product-a",
                            "pharmacy-104", "patient-104", "rx-104-overflow-" + index, 1, 1)))));
        }
        messages.add(writeMessage(directory, "06-full-108.json", message(
                "full-108", "001", "05", "full-108-tote", "108", "998",
                List.of(line("full-108-line", "05", "product-a", "pharmacy-108",
                        "patient-108", "rx-108", 1, 1)))));
        messages.add(writeMessage(directory, "07-associated-116.json", message(
                "associated-116", "001", "04", "associated-116-tote", "116", "997",
                List.of(line("associated-116-line", "05", "product-a", "pharmacy-116",
                        "patient-116", "rx-116", 1, 1)))));
        messages.add(writeMessage(directory, "08-empty-109.json", message(
                "empty-109", "001", "03", null, "109", "990",
                List.of(line("empty-109-line", "05", "product-a", "pharmacy-109",
                        "patient-109", "rx-empty-109", 1, 0)))));
        messages.add(writeMessage(directory, "09-full-109-third-party.json", message(
                "full-109-third-party", "001", "05", "full-109-third-party-tote", "109", "990",
                List.of(line("full-109-third-party-line", "03", "product-b", "pharmacy-109",
                        "patient-109", "rx-109-third-party", 2, 1)))));
        for (int index = 1; index <= 8; index++) {
            messages.add(writeMessage(directory, "10-full-109-" + index + ".json", message(
                    "full-109-" + index, "001", "05", "full-109-tote-" + index, "109", "990",
                    List.of(line("full-109-line-" + index, "05", "product-a", "pharmacy-109",
                            "patient-109", "rx-109-" + index, 1, 1)))));
        }
        return new DspFullDayInputLoader().load(
                new DspFullDayInputPaths(products, messages), profile);
    }

    private static Path writeMessage(Path directory, String name, String content) throws IOException {
        return Files.writeString(directory.resolve(name), content);
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
                .append("  \"toteIdentifier\": {\"payload\":\"").append(toteType).append("\"},\n");
        if (physicalToteId != null) {
            json.append("  \"transportContainer\": {\"payload\":\"")
                    .append(physicalToteId).append("\"},\n");
        }
        json.append("  \"orderPriority\": {\"payload\":\"").append(priority).append("\"},\n")
                .append("  \"serviceCentre\": {\"payload\":\"").append(serviceCentreId)
                .append("\"},\n")
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
                    .append("\",\"numberOfPacks\":\"").append(String.format("%04d", line.quantity()))
                    .append("\",\"referenceSheetNumber\":\"001\"")
                    .append(",\"numberOfPacksPicked\":\"")
                    .append(String.format("%04d", line.picked()))
                    .append("\",\"referenceOrderId\":\"").append(orderId).append("\"}")
                    .append(index + 1 == lines.size() ? "\n" : ",\n");
        }
        return json.append("    ]\n  }\n}\n").toString();
    }

    private static boolean isPositive(Duration duration) {
        return !duration.isZero() && !duration.isNegative();
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

    private record ScenarioRun(
            DspFullDayLoadedInput input,
            DspFullDayAnalysisReport report,
            String reportJson,
            String inspection) {
    }
}
