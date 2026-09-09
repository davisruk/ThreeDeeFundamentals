package online.davisfamily.warehouse.sim.dsp.analysis.report;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class DspFullDayReportJsonWriterTest {

    @Test
    void shouldEmitCompleteExplicitUtf8Schema(@TempDir Path directory) throws Exception {
        DspFullDayAnalysisReport report =
                DspFullDayReportTestSupport.earlyCompletionReport(directory);
        DspFullDayReportJsonWriter writer = new DspFullDayReportJsonWriter();

        byte[] bytes = writer.serialize(report);
        JsonNode root = new ObjectMapper().readTree(bytes);

        assertEquals(1, root.get("schemaVersion").intValue());
        assertEquals("UNCALIBRATED", root.at("/profile/calibrationStatus").textValue());
        assertEquals("P2P_OUTPUT_CLOSED", root.at("/profile/completionMilestone").textValue());
        assertNotNull(root.get("configuration"));
        assertNotNull(root.get("load"));
        assertNotNull(root.get("metrics"));
        assertNotNull(root.get("current"));
        assertTrue(root.get("serviceCentres").isArray());
        assertTrue(root.get("p2pLines").isArray());
        assertTrue(root.get("occupancySamples").isArray());
        assertTrue(root.at("/clock/businessDateTime").isTextual());
        assertTrue(root.at("/metrics/observedSimulationDuration/nanos").isIntegralNumber());
        assertTrue(root.at("/metrics/observedSimulationDuration/iso").isTextual());
        assertEquals("104", root.at("/serviceCentres/0/serviceCentreId").textValue());
        assertEquals("dsp-p2p-line-1", root.at("/p2pLines/0/lineId").textValue());
        assertEquals(
                new String(bytes, StandardCharsets.UTF_8),
                writer.serializeToString(report));
    }

    @Test
    void shouldCreateParentsAndRefuseThenAllowOverwrite(@TempDir Path directory) throws Exception {
        DspFullDayAnalysisReport report =
                DspFullDayReportTestSupport.earlyCompletionReport(directory);
        DspFullDayReportJsonWriter writer = new DspFullDayReportJsonWriter();
        Path output = directory.resolve("nested").resolve("analysis.json");

        writer.write(report, output);
        byte[] original = Files.readAllBytes(output);
        assertTrue(Files.exists(output.getParent()));
        assertThrows(java.nio.file.FileAlreadyExistsException.class,
                () -> writer.write(report, output));
        assertArrayEquals(original, Files.readAllBytes(output));

        writer.write(report, output, true);
        assertArrayEquals(original, Files.readAllBytes(output));
    }

    @Test
    void shouldLeaveExistingTargetUntouchedWhenTargetCannotBeReplaced(@TempDir Path directory)
            throws Exception {
        DspFullDayAnalysisReport report =
                DspFullDayReportTestSupport.earlyCompletionReport(directory);
        DspFullDayReportJsonWriter writer = new DspFullDayReportJsonWriter();
        Path output = directory.resolve("existing.json");
        byte[] original = "existing-значение".getBytes(StandardCharsets.UTF_8);
        Files.write(output, original, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);

        assertThrows(java.nio.file.FileAlreadyExistsException.class,
                () -> writer.write(report, output));
        assertArrayEquals(original, Files.readAllBytes(output));
    }
}
