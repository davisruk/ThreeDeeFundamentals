package online.davisfamily.warehouse.sim.dsp.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayReportTestSupport;

class DspFullDayInspectionFormatterTest {

    @Test
    void shouldPreserveCapturedOutstandingCountsThroughBothInspectionFacades(@TempDir Path directory)
            throws Exception {
        var profile = online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayWholeServiceCentreScenarioTest.withPolicy(
                DspFullDayReportTestSupport.profile(),
                online.davisfamily.warehouse.sim.dsp.scheduler.policy.DspSchedulerPolicy.WHOLE_SERVICE_CENTRE_DRAINED_HANDOVER);
        var input = DspFullDayReportTestSupport.input(directory, profile);
        try (var runtime = new online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayAnalysisRuntimeFactory()
                .create(input, profile)) {
            runtime.update(1d);
            var captured = runtime.snapshot();
            var reportSnapshot = new online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayInspectionSnapshot(
                    captured, input, List.of(), List.of());
            var facadeSnapshot = new DspFullDayInspectionSnapshot(reportSnapshot);
            var formatter = new DspFullDayInspectionFormatter();
            var before = formatter.describe(facadeSnapshot);
            String wholeLine = before.stream().filter(line -> line.startsWith("WholeServiceCentre:"))
                    .findFirst().orElseThrow();
            assertTrue(wholeLine.endsWith("p2pOutstandingToteWatermark=8 outstandingP2pTotes="
                    + "{dsp-p2p-line-1=1, dsp-p2p-line-2=0, dsp-p2p-line-3=0, "
                    + "dsp-p2p-line-4=0, dsp-p2p-line-5=0} deadlinesAndWorkloadCosts=diagnosticOnly"));
            var progress = online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayProgressSnapshot
                    .from(captured, input, profile);
            assertTrue(new online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayProgressFormatter()
                    .describe(progress).contains(wholeLine));
            assertEquals(before, formatter.describe(reportSnapshot));
            assertEquals(before, new online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayInspectionFormatter()
                    .describe(reportSnapshot));
            assertEquals(String.join(System.lineSeparator(), before), formatter.formatText(facadeSnapshot));

            runtime.update(1d);
            assertTrue(runtime.snapshot().elastic().allocation().wholeServiceCentrePolicy()
                    .orElseThrow().releases().outstandingVersion()
                    > captured.elastic().allocation().wholeServiceCentrePolicy().orElseThrow()
                            .releases().outstandingVersion());
            assertEquals(before, formatter.describe(facadeSnapshot));
            assertEquals(before, formatter.describe(reportSnapshot));
        }
    }

    @Test
    void shouldExposeEveryLockedSectionWithoutLiveOrUnsupportedClaims(@TempDir Path directory)
            throws Exception {
        var report = DspFullDayReportTestSupport.earlyCompletionReport(directory);
        DspFullDayInspectionFormatter formatter = new DspFullDayInspectionFormatter();

        List<String> first = formatter.describe(new DspFullDayInspectionSnapshot(report));
        List<String> second = formatter.describe(new DspFullDayInspectionSnapshot(report));

        assertEquals(first, second);
        assertTrue(first.getFirst().contains("profile="));
        assertTrue(first.getFirst().contains("calibration=UNCALIBRATED"));
        assertTrue(first.getFirst().contains("milestone=P2P_OUTPUT_CLOSED"));
        assertTrue(first.stream().anyMatch(value -> value.startsWith("Clock: ")));
        assertTrue(first.stream().anyMatch(value -> value.startsWith("Speed: ")));
        assertTrue(first.stream().anyMatch(value -> value.startsWith("OSR: ")));
        assertTrue(first.stream().anyMatch(value -> value.startsWith("ServiceCentre[104]: ")));
        assertTrue(first.stream().anyMatch(value -> value.startsWith("ServiceCentre[104]: ")
                && value.contains("p2pOutputClosure=P2P_OUTPUT_CLOSED")
                && value.contains("exceptions=missingPacks:0,pdcCollectedPacks:0"
                        + ",affectedAllocatedBags:0,markedOutboundTotes:0,pendingEmptyBags:0")));
        assertTrue(first.stream().anyMatch(value -> value.startsWith("P2P[dsp-p2p-line-1]: ")));
        assertTrue(first.stream().anyMatch(value -> value.startsWith("Release: ")));
        assertTrue(first.stream().anyMatch(value -> value.startsWith("Transport: ")));
        assertTrue(first.stream().anyMatch(value -> value.startsWith("Station: ")));
        assertTrue(first.stream().anyMatch(value -> value.startsWith("Load: ")));
        assertTrue(first.stream().anyMatch(value -> value.contains("reusedInboundToteIds=0")));
        assertTrue(first.stream().anyMatch(value -> value.startsWith("Unsupported: ")));
        assertTrue(first.stream().anyMatch(value -> value.startsWith("Unfinished: ")));
        String text = String.join("\n", first).toLowerCase(java.util.Locale.ROOT);
        assertFalse(first.stream().anyMatch(value -> value.startsWith("WholeServiceCentre:")));
        assertFalse(text.contains("p2poutstandingtotewatermark="));
        assertFalse(text.contains("outstandingp2ptotes="));
        assertFalse(text.contains("trunk-loaded"));
        assertFalse(text.contains("dispatch complete"));
    }

    @Test
    void shouldExposeReusedInboundToteCount(@TempDir Path directory) throws Exception {
        var report = DspFullDayReportTestSupport.reusedCarrierReport(directory);
        List<String> lines = new DspFullDayInspectionFormatter()
                .describe(new DspFullDayInspectionSnapshot(report));

        assertTrue(lines.stream().anyMatch(value -> value.contains("reusedInboundToteIds=1")));
    }
}
