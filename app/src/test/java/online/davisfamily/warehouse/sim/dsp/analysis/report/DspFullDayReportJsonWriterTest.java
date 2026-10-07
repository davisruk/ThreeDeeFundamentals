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
    void shouldRoundTripConfiguredBenchDurationsAndPositionCounts(@TempDir Path directory) throws Exception {
        var profile = DspFullDayReportTestSupport.configuredStations(DspFullDayReportTestSupport.profile());
        var report = DspFullDayReportTestSupport.earlyCompletionReport(directory, profile);
        JsonNode root = new ObjectMapper().readTree(new DspFullDayReportJsonWriter().serialize(report));
        JsonNode benches = root.at("/configuration/adapting/benches");
        assertEquals(6, benches.size());
        int positions = 0;
        for (int index = 0; index < benches.size(); index++) {
            JsonNode bench = benches.get(index);
            assertEquals("bench-" + (index + 1), bench.get("id").textValue());
            assertEquals(60d, bench.get("processingDurationSeconds").doubleValue());
            assertEquals(60d, bench.get("storeDurationSeconds").doubleValue());
            assertEquals(10d, bench.get("collectDurationSeconds").doubleValue());
            assertEquals(3, bench.get("processingPositions").intValue());
            positions += bench.get("processingPositions").intValue();
        }
        assertEquals(18, positions);
        assertEquals(20d, root.at("/configuration/thirdParty/processingDurationSeconds").doubleValue());
        assertEquals(3, root.at("/configuration/queues/adaptingQueueCapacityPerBench").intValue());
        assertEquals("UNCALIBRATED", root.at("/profile/calibrationStatus").textValue());
    }

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
        assertEquals(1, root.at("/configuration/adapting/benches").size());
        assertEquals("adapting-bench-1", root.at("/configuration/adapting/benches/0/id").textValue());
        assertEquals(60d, root.at("/configuration/adapting/benches/0/processingDurationSeconds").doubleValue());
        assertEquals(60d, root.at("/configuration/adapting/benches/0/collectDurationSeconds").doubleValue());
        assertEquals(1, root.at("/configuration/adapting/benches/0/processingPositions").intValue());
        assertEquals(60d, root.at("/configuration/thirdParty/processingDurationSeconds").doubleValue());
        assertNotNull(root.get("load"));
        assertTrue(root.at("/load/inboundToteIdSubstitutions").isArray());
        assertEquals(0, root.at("/load/inboundToteIdSubstitutions").size());
        assertNotNull(root.get("metrics"));
        assertNotNull(root.get("current"));
        assertTrue(root.get("serviceCentres").isArray());
        assertTrue(root.get("p2pLines").isArray());
        assertTrue(root.get("occupancySamples").isArray());
        assertTrue(root.at("/clock/businessDateTime").isTextual());
        assertTrue(root.at("/metrics/observedSimulationDuration/nanos").isIntegralNumber());
        assertTrue(root.at("/metrics/observedSimulationDuration/iso").isTextual());
        assertEquals("104", root.at("/serviceCentres/0/serviceCentreId").textValue());
        assertEquals("P2P_OUTPUT_CLOSED",
                root.at("/serviceCentres/0/p2pOutputClosureState").textValue());
        assertEquals(0, root.at("/serviceCentres/0/missingPackCount").intValue());
        assertEquals(0, root.at("/serviceCentres/0/pdcCollectedPackCount").intValue());
        assertEquals(0, root.at("/serviceCentres/0/affectedAllocatedBagCount").intValue());
        assertEquals(0, root.at("/serviceCentres/0/markedOutboundToteCount").intValue());
        assertEquals(0, root.at("/serviceCentres/0/pendingEmptyBagCount").intValue());
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
    void shouldEmitOrderedInboundToteIdSubstitutions(@TempDir Path directory) throws Exception {
        DspFullDayAnalysisReport report =
                DspFullDayReportTestSupport.reusedCarrierReport(directory);
        JsonNode root = new ObjectMapper().readTree(new DspFullDayReportJsonWriter().serialize(report));

        JsonNode substitutions = root.at("/load/inboundToteIdSubstitutions");
        assertEquals(1, substitutions.size());
        assertEquals("shared-carrier", substitutions.get(0).get("sourcePhysicalToteId").textValue());
        assertEquals("dsp-reused-shared-carrier-2",
                substitutions.get(0).get("substitutedPhysicalToteId").textValue());
        assertEquals(2, substitutions.get(0).get("occurrenceNumber").intValue());
        assertEquals(1L, substitutions.get(0).get("sourceSequenceNumber").longValue());
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
