package online.davisfamily.warehouse.sim.dsp.analysis.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayRuntimeState;
import online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayTerminationReason;
import online.davisfamily.warehouse.sim.dsp.analysis.DspServiceCentreCompletionOutcome;
import online.davisfamily.warehouse.sim.dsp.analysis.DspUncalibratedFullDayProfile;
import online.davisfamily.warehouse.sim.dsp.scheduler.policy.DspSchedulerPolicy;

class DspFullDayReportFactoryTest {

    @Test
    void shouldReportSelectedComponentIdsAndDiagnosticOnlyNewPolicyInputs(@TempDir Path directory)
            throws Exception {
        var base = DspFullDayReportTestSupport.configuredStations(DspFullDayReportTestSupport.profile());
        var legacy = selectedProfile(base, DspSchedulerPolicy.DEADLINE_AWARE_ELASTIC_STICKY_LEASES);
        var legacyReport = DspFullDayReportTestSupport.earlyCompletionReport(directory, legacy);
        assertEquals(legacy.schedulerPolicy().name(), legacyReport.configuration().get("schedulerPolicy"));
        assertEquals(8, legacyReport.configuration().get("p2pOutstandingToteWatermark"));
        assertEquals(false, legacyReport.configuration().get("p2pOutstandingToteWatermarkEnabled"));
        assertEquals(Map.of(
                "serviceCentreSupply", "PRIORITY_ORDERED_OSR_LOW_WATERMARK",
                "orderEligibility", "ORDER_WIDE_PREPARATION_READY_OVERLAP",
                "candidateRanking", "ADAPTED_FIRST_PHARMACY_GROUPED_THEN_SOURCE_SEQUENCE",
                "p2pLineAllocation", "DEADLINE_AWARE_ELASTIC_STICKY_LEASES",
                "outboundAllocation", "PHARMACY_PURE_FIXED_BAG_CAPACITY",
                "inboundArrival", base.inboundToteArrivalPolicy().policyId()),
                legacyReport.configuration().get("policies"));
        assertEquals("ACTIVE_DECISION_INPUTS", ((Map<?, ?>) legacyReport.configuration()
                .get("p2pElastic")).get("deadlineAndWorkloadInputRole"));

        var whole = selectedProfile(base, DspSchedulerPolicy.WHOLE_SERVICE_CENTRE_DRAINED_HANDOVER);
        // Only the static reporting projection is exercised: Step 3 intentionally forbids a new-mode run.
        var projection = DspFullDayReportFactory.class.getDeclaredMethod(
                "configuration", DspUncalibratedFullDayProfile.class);
        projection.setAccessible(true);
        var configuration = (Map<?, ?>) projection.invoke(null, whole);
        assertEquals(whole.schedulerPolicy().name(), configuration.get("schedulerPolicy"));
        assertEquals(whole.schedulerPolicy().name(), configuration.get("profileId"));
        assertEquals(8, configuration.get("p2pOutstandingToteWatermark"));
        assertEquals(true, configuration.get("p2pOutstandingToteWatermarkEnabled"));
        assertEquals(Map.of(
                "serviceCentreSupply", "PRIORITY_ORDERED_OSR_LOW_WATERMARK",
                "orderEligibility", "WHOLE_SERVICE_CENTRE_ORDER_WIDE_PREPARATION_READY",
                "candidateRanking", "WHOLE_SERVICE_CENTRE_ADAPTED_FIRST_PHARMACY_SOURCE_SEQUENCE",
                "p2pLineAllocation", "WHOLE_SERVICE_CENTRE_DRAINED_HANDOVER",
                "outboundAllocation", "PHARMACY_PURE_FIXED_BAG_CAPACITY",
                "inboundArrival", base.inboundToteArrivalPolicy().policyId()), configuration.get("policies"));
        var elastic = (Map<?, ?>) configuration.get("p2pElastic");
        assertEquals("DIAGNOSTIC_ONLY", elastic.get("deadlineAndWorkloadInputRole"));
        var legacyElastic = (Map<?, ?>) legacyReport.configuration().get("p2pElastic");
        for (String field : List.of("workloadCosts", "downstreamHandlingDuration",
                "downstreamHandlingDurationNanos", "safetyFactorPermille", "parallelEfficiencyPermille")) {
            assertEquals(legacyElastic.get(field), elastic.get(field), field);
        }
        for (String field : List.of("adapting", "thirdParty", "queues", "timetable", "clock",
                "execution", "osr", "outbound", "p2pPlaceholders", "p2pLines")) {
            assertEquals(legacyReport.configuration().get(field), configuration.get(field), field);
        }
        assertEquals("UNCALIBRATED", configuration.get("calibrationStatus"));
        assertEquals("P2P_OUTPUT_CLOSED", configuration.get("completionMilestone"));
    }

    private static DspUncalibratedFullDayProfile selectedProfile(
            DspUncalibratedFullDayProfile base, DspSchedulerPolicy selected) {
        return new DspUncalibratedFullDayProfile(
                base.operatingDate(),
                base.osrInventoryConfig(),
                base.serviceCentreSupplyConfig(),
                base.inboundToteArrivalPolicy(),
                base.av02AllocationConfig(),
                base.p2pElasticAllocationConfig(),
                base.outboundToteConfig(),
                base.maximumPacksPerBag(),
                base.fixedStep(),
                base.maximumStepsPerAdvance(),
                base.metricSampleInterval(),
                base.routeSpeedUnitsPerSecond(),
                base.queueCapacities(),
                base.thirdPartyAreaConfig(),
                base.adaptingStorageConfig(),
                base.adaptingBenchDefinitions(),
                base.p2pPlaceholderDurations(),
                base.p2pLineDefinitions(),
                base.prlCountPerLine(),
                base.timetable(),
                selected);
    }

    @Test
    void shouldReportEffectiveStationSettingsAndRetainTheLegacyStoreAlias(@TempDir Path directory)
            throws Exception {
        var profile = DspFullDayReportTestSupport.configuredStations(DspFullDayReportTestSupport.profile());
        var report = DspFullDayReportTestSupport.earlyCompletionReport(directory, profile);
        var adapting = (java.util.Map<?, ?>) report.configuration().get("adapting");
        var benches = (List<?>) adapting.get("benches");
        assertEquals(6, benches.size());
        int positions = 0;
        for (int index = 0; index < benches.size(); index++) {
            var bench = (java.util.Map<?, ?>) benches.get(index);
            assertEquals("bench-" + (index + 1), bench.get("id"));
            assertEquals(60d, bench.get("processingDurationSeconds"));
            assertEquals(60d, bench.get("storeDurationSeconds"));
            assertEquals(10d, bench.get("collectDurationSeconds"));
            assertEquals(3, bench.get("processingPositions"));
            positions += (Integer) bench.get("processingPositions");
            assertThrows(UnsupportedOperationException.class, bench::clear);
        }
        assertEquals(18, positions);
        var thirdParty = (java.util.Map<?, ?>) report.configuration().get("thirdParty");
        assertEquals(20d, thirdParty.get("processingDurationSeconds"));
        assertEquals(1, thirdParty.get("maxConcurrentVisits"));
        assertEquals(16, thirdParty.get("waitingCapacity"));
        var queues = (java.util.Map<?, ?>) report.configuration().get("queues");
        assertEquals(3, queues.get("adaptingQueueCapacityPerBench"));
        assertEquals("UNCALIBRATED", report.calibrationStatus());
        assertEquals(DspFullDayRuntimeState.ALL_SUPPORTED_WORK_COMPLETE, report.state());
    }

    @Test
    void shouldProduceStableTerminalReportValues(@TempDir Path directory) throws Exception {
        DspFullDayAnalysisReport report =
                DspFullDayReportTestSupport.earlyCompletionReport(directory);

        assertEquals(DspFullDayRuntimeState.ALL_SUPPORTED_WORK_COMPLETE, report.state());
        assertEquals(
                DspFullDayTerminationReason.ALL_SUPPORTED_WORK_COMPLETE,
                report.terminationReason());
        assertEquals("UNCALIBRATED", report.calibrationStatus());
        assertEquals("P2P_OUTPUT_CLOSED", report.completionMilestone().name());
        assertTrue(report.loadReport().inboundToteIdSubstitutions().isEmpty());
        assertTrue(report.warnings().stream()
                .noneMatch(value -> value.contains("Reused inbound carrier barcodes")));
        assertEquals(List.of("104", "108"), report.serviceCentres().stream()
                .map(DspServiceCentreAnalysisResult::serviceCentreId).toList());
        assertEquals(
                List.of("dsp-p2p-line-1", "dsp-p2p-line-2", "dsp-p2p-line-3",
                        "dsp-p2p-line-4", "dsp-p2p-line-5"),
                report.p2pLines().stream().map(value -> value.lineId().value()).toList());
        assertTrue(report.occupancySamples().stream()
                .map(value -> value.elapsedSimulationTime())
                .sorted()
                .toList()
                .equals(report.occupancySamples().stream()
                        .map(value -> value.elapsedSimulationTime()).toList()));
        assertTrue(report.serviceCentres().stream()
                .allMatch(value -> value.outcome() != DspServiceCentreCompletionOutcome.UNFINISHED_AT_HARD_CUTOFF));
        assertTrue(report.serviceCentres().stream().allMatch(value ->
                value.completion().p2pOutputClosureState()
                        == online.davisfamily.warehouse.sim.dsp.analysis.DspP2pOutputClosureState.P2P_OUTPUT_CLOSED
                        && value.completion().missingPackCount() == 0
                        && value.completion().pdcCollectedPackCount() == 0
                        && value.completion().affectedAllocatedBagCount() == 0
                        && value.completion().markedOutboundToteCount() == 0
                        && value.completion().pendingEmptyBagCount() == 0));
        assertTrue(report.serviceCentres().stream().allMatch(value -> report.runtimeSnapshot()
                .completions().stream()
                .anyMatch(completion -> completion == value.completion())));

        assertThrows(UnsupportedOperationException.class,
                () -> report.serviceCentres().add(report.serviceCentres().getFirst()));
        assertThrows(UnsupportedOperationException.class,
                () -> ((List<Object>) report.configuration().get("p2pLines"))
                        .add("must remain immutable"));
    }

    @Test
    void shouldReportHardCutoffAndPreserveUnfinishedIdentities(@TempDir Path directory)
            throws Exception {
        DspFullDayAnalysisReport report =
                DspFullDayReportTestSupport.hardCutoffReport(directory);

        assertEquals(DspFullDayRuntimeState.HARD_CUTOFF_REACHED, report.state());
        assertEquals(
                DspFullDayTerminationReason.HARD_CUTOFF_REACHED,
                report.terminationReason());
        assertTrue(report.serviceCentres().stream().anyMatch(value ->
                value.outcome() == DspServiceCentreCompletionOutcome.UNFINISHED_AT_HARD_CUTOFF));
        assertTrue(report.unfinishedIdentities().stream()
                .anyMatch(value -> value.contains("order-104") || value.contains("order-108")
                        || value.contains("tote-104") || value.contains("tote-108")));
    }

    @Test
    void shouldReportReusedCarrierNormalizationOnce(@TempDir Path directory) throws Exception {
        DspFullDayAnalysisReport report =
                DspFullDayReportTestSupport.reusedCarrierReport(directory);

        assertEquals(1, report.loadReport().inboundToteIdSubstitutions().size());
        assertEquals(1, report.warnings().stream()
                .filter(value -> value.contains("Reused inbound carrier barcodes"))
                .count());
    }
}
