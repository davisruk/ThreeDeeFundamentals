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
    void shouldProduceStableTerminalReportValues(@TempDir Path directory) throws Exception {
        DspFullDayAnalysisReport report =
                DspFullDayReportTestSupport.earlyCompletionReport(directory);

        assertEquals(DspFullDayRuntimeState.ALL_SUPPORTED_WORK_COMPLETE, report.state());
        assertEquals(
                DspFullDayTerminationReason.ALL_SUPPORTED_WORK_COMPLETE,
                report.terminationReason());
        assertEquals("UNCALIBRATED", report.calibrationStatus());
        assertEquals("P2P_OUTPUT_CLOSED", report.completionMilestone().name());
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
}
