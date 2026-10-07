package online.davisfamily.warehouse.sim.dsp.analysis.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayRuntimeState;
import online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayTerminationReason;
import online.davisfamily.warehouse.sim.dsp.analysis.DspServiceCentreCompletionOutcome;

class DspFullDayReportFactoryTest {

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
