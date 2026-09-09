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
        assertTrue(first.stream().anyMatch(value -> value.startsWith("P2P[dsp-p2p-line-1]: ")));
        assertTrue(first.stream().anyMatch(value -> value.startsWith("Release: ")));
        assertTrue(first.stream().anyMatch(value -> value.startsWith("Transport: ")));
        assertTrue(first.stream().anyMatch(value -> value.startsWith("Station: ")));
        assertTrue(first.stream().anyMatch(value -> value.startsWith("Load: ")));
        assertTrue(first.stream().anyMatch(value -> value.startsWith("Unsupported: ")));
        assertTrue(first.stream().anyMatch(value -> value.startsWith("Unfinished: ")));
        String text = String.join("\n", first).toLowerCase(java.util.Locale.ROOT);
        assertFalse(text.contains("trunk-loaded"));
        assertFalse(text.contains("dispatch complete"));
    }
}
