package online.davisfamily.warehouse.sim.dsp.analysis.report;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayLoadedInput;
import online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayRuntimeState;
import online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayAnalysisRuntimeFactory;
import online.davisfamily.warehouse.sim.dsp.io.DspDatasetLoadReport;
import online.davisfamily.warehouse.sim.dsp.io.LoadedDspData;
import online.davisfamily.warehouse.sim.dsp.io.UnresolvedProductLine;

class DspFullDayReportJsonWriterTest {

    @Test
    void shouldSerializeConnectedWholePolicyCountsAndExplicitDiagnosticCosts(@TempDir Path directory) throws Exception {
        var profile = online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayWholeServiceCentreScenarioTest.withPolicy(
                DspFullDayReportTestSupport.profile(),
                online.davisfamily.warehouse.sim.dsp.scheduler.policy.DspSchedulerPolicy.WHOLE_SERVICE_CENTRE_DRAINED_HANDOVER);
        var input = DspFullDayReportTestSupport.input(directory, profile);
        try (var runtime = new DspFullDayAnalysisRuntimeFactory().create(input, profile)) {
            runtime.update(24 * 60 * 60d); // Terminal cutoff captures the single applied release before cutoff.
            var report = new DspFullDayReportFactory().create(runtime.snapshot(), input, profile);
            var writer = new DspFullDayReportJsonWriter();
            byte[] captured = writer.serialize(report);
            JsonNode root = new ObjectMapper().readTree(captured);
            var elastic = root.at("/current/elastic");
            assertEquals(profile.schedulerPolicy().name(), elastic.get("profileId").textValue());
            assertTrue(elastic.get("deadlinesAndWorkloadCostsDiagnosticOnly").booleanValue());
            var whole = elastic.get("wholeServiceCentrePolicy");
            assertEquals("108", whole.get("eligibleServiceCentreId").textValue());
            assertEquals(4, whole.get("availableUnleasedLineIds").size());
            var releases = whole.get("releases");
            assertEquals(1, releases.get("version").longValue());
            assertEquals("108", releases.get("releaseServiceCentreId").textValue());
            assertEquals(2, releases.get("orderedServiceCentreIds").size());
            assertEquals(0, releases.at("/unreleasedOsrToteCounts/104").intValue());
            assertEquals(1, releases.at("/unreleasedOsrToteCounts/108").intValue());
            assertEquals(0, releases.at("/unreleasedEmptySheetCounts/104").intValue());
            assertEquals(1, releases.at("/committedP2pToteCounts/104/dsp-p2p-line-1").intValue());
            assertEquals(5, releases.at("/committedP2pToteCounts/104").size());
            assertEquals(1, releases.get("outstandingVersion").longValue());
            assertEquals(8, releases.get("p2pOutstandingToteWatermark").intValue());
            assertOutstandingCounts(releases.get("outstandingP2pToteCounts"), 1);
            runtime.update(1d);
            assertArrayEquals(captured, writer.serialize(report));
        }
    }

    @Test
    void shouldSerializeEveryZeroCountAfterActualWholeCentreCompletion(@TempDir Path directory)
            throws Exception {
        var profile = online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayWholeServiceCentreScenarioTest.withPolicy(
                DspFullDayReportTestSupport.profile(),
                online.davisfamily.warehouse.sim.dsp.scheduler.policy.DspSchedulerPolicy.WHOLE_SERVICE_CENTRE_DRAINED_HANDOVER);
        var report = DspFullDayReportTestSupport.earlyCompletionReport(directory, profile);
        assertEquals(DspFullDayRuntimeState.ALL_SUPPORTED_WORK_COMPLETE, report.state());
        JsonNode root = new ObjectMapper().readTree(new DspFullDayReportJsonWriter().serialize(report));
        var releases = root.at("/current/elastic/wholeServiceCentrePolicy/releases");
        assertEquals(2, releases.get("version").longValue());
        assertEquals(4, releases.get("outstandingVersion").longValue());
        assertEquals(8, releases.get("p2pOutstandingToteWatermark").intValue());
        assertOutstandingCounts(releases.get("outstandingP2pToteCounts"), 0);
    }

    private static void assertOutstandingCounts(JsonNode counts, int firstLineCount) {
        assertTrue(counts.isObject());
        List<String> keys = new ArrayList<>();
        counts.fieldNames().forEachRemaining(keys::add);
        assertEquals(List.of("dsp-p2p-line-1", "dsp-p2p-line-2", "dsp-p2p-line-3",
                "dsp-p2p-line-4", "dsp-p2p-line-5"), keys);
        for (int index = 1; index <= 5; index++) {
            var count = counts.get("dsp-p2p-line-" + index);
            assertTrue(count.isIntegralNumber());
            assertEquals(index == 1 ? firstLineCount : 0, count.intValue());
        }
    }

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
        assertEquals(0, root.at("/serviceCentres/0/nsCandidateInputLineCount").intValue());
        assertEquals(false, root.get("completedWithNsCandidates").booleanValue());
        assertEquals(0, root.get("nsCandidateInputLineCountByServiceCentreId").size());
        assertEquals("dsp-p2p-line-1", root.at("/p2pLines/0/lineId").textValue());
        assertTrue(root.at("/current/elastic/wholeServiceCentrePolicy").isNull());
        assertFalse(root.at("/current/elastic").has("deadlinesAndWorkloadCostsDiagnosticOnly"));
        assertTrue(root.at("/current/elastic/wholeServiceCentrePolicy/releases/outstandingVersion").isMissingNode());
        assertTrue(root.at("/current/elastic/wholeServiceCentrePolicy/releases/p2pOutstandingToteWatermark").isMissingNode());
        assertTrue(root.at("/current/elastic/wholeServiceCentrePolicy/releases/outstandingP2pToteCounts").isMissingNode());
        assertEquals(
                new String(bytes, StandardCharsets.UTF_8),
                writer.serializeToString(report));
    }

    @Test
    void shouldSerializeNsOccurrencesSeparatelyFromPhysicalExceptionsAndExclusions(@TempDir Path directory)
            throws Exception {
        var profile = DspFullDayReportTestSupport.profile();
        var base = DspFullDayReportTestSupport.input(directory, profile);
        var load = new DspDatasetLoadReport(0, 0, 0, List.of(
                new UnresolvedProductLine("ns-104", "line", "unknown", "104"),
                new UnresolvedProductLine("ns-only", "line", "unknown", "109")), List.of());
        var data = base.data();
        var input = new DspFullDayLoadedInput(new LoadedDspData(
                data.products(), data.orders(), data.preparedLines(), data.loadedPreparedLineKeys(),
                data.startupReadyPreparedLineKeys(), data.inboundToteManifests(), load, data.retainedInputLines()),
                base.reportableOrders(), base.rejectionCatalog(), base.bagPlan(), load, base.timetable());
        try (var runtime = new DspFullDayAnalysisRuntimeFactory().create(input, profile)) {
            for (int step = 0; step < 300 && runtime.state() == DspFullDayRuntimeState.RUNNING; step++) {
                runtime.update(1d);
            }
            var report = new DspFullDayReportFactory().create(runtime.snapshot(), input, profile);
            JsonNode root = new ObjectMapper().readTree(new DspFullDayReportJsonWriter().serialize(report));
            assertTrue(root.get("completedWithNsCandidates").booleanValue());
            assertEquals(false, root.get("completedWithInputExclusions").booleanValue());
            assertEquals(1, root.at("/nsCandidateInputLineCountByServiceCentreId/104").intValue());
            assertEquals(1, root.at("/nsCandidateInputLineCountByServiceCentreId/109").intValue());
            assertEquals(2, root.get("serviceCentres").size());
            assertEquals("P2P_OUTPUT_CLOSED_WITH_EXCEPTION",
                    root.at("/serviceCentres/0/p2pOutputClosureState").textValue());
            assertEquals(1, root.at("/serviceCentres/0/nsCandidateInputLineCount").intValue());
            assertEquals(0, root.at("/serviceCentres/0/missingPackCount").intValue());
            assertEquals(0, root.at("/serviceCentres/0/markedOutboundToteCount").intValue());
            assertEquals(2, root.at("/load/unresolvedProductLines").size());
            assertTrue(report.warnings().contains("Unresolved product unknown for ns-only/line"));
            org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                    () -> report.nsCandidateInputLineCountByServiceCentreId().clear());
        }
        try (var runtime = new DspFullDayAnalysisRuntimeFactory().create(input, profile)) {
            runtime.update(24 * 60 * 60d);
            var report = new DspFullDayReportFactory().create(runtime.snapshot(), input, profile);
            assertEquals(DspFullDayRuntimeState.HARD_CUTOFF_REACHED, report.state());
            assertEquals(false, report.completedWithNsCandidates());
        }
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
