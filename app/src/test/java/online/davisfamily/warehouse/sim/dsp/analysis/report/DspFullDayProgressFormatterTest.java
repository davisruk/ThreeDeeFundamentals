package online.davisfamily.warehouse.sim.dsp.analysis.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayLoadedInput;
import online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayRuntimeState;
import online.davisfamily.warehouse.sim.dsp.analysis.DspUncalibratedFullDayProfile;
import online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayAnalysisRuntime;
import online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayAnalysisRuntimeFactory;
import online.davisfamily.warehouse.sim.dsp.io.DspDatasetLoadReport;
import online.davisfamily.warehouse.sim.dsp.io.LoadedDspData;
import online.davisfamily.warehouse.sim.dsp.io.UnresolvedProductLine;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.scheduler.policy.WholeServiceCentrePolicySnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.policy.WholeServiceCentreReleaseSnapshot;

class DspFullDayProgressFormatterTest {

    @Test
    void shouldProjectBoundedWholeCentreMetadataFromTheCapturedSnapshot(@TempDir Path directory) throws Exception {
        var profile = online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayWholeServiceCentreScenarioTest.withPolicy(
                DspFullDayReportTestSupport.profile(),
                online.davisfamily.warehouse.sim.dsp.scheduler.policy.DspSchedulerPolicy.WHOLE_SERVICE_CENTRE_DRAINED_HANDOVER);
        var input = DspFullDayReportTestSupport.input(directory, profile);
        try (var runtime = new DspFullDayAnalysisRuntimeFactory().create(input, profile)) {
            var captured = runtime.snapshot();
            var progress = DspFullDayProgressSnapshot.from(captured, input, profile);
            var formatter = new DspFullDayProgressFormatter();
            var before = formatter.describe(progress);
            String wholeLine = before.get(indexOfPrefix(before, "WholeServiceCentre:"));
            assertTrue(wholeLine.contains("releaseCentre=104 eligibleCentre=104 unreleasedOsr=1 unreleasedEmpty=0"));
            assertTrue(wholeLine.contains("availableUnleasedLines=[dsp-p2p-line-1, dsp-p2p-line-2, dsp-p2p-line-3, dsp-p2p-line-4, dsp-p2p-line-5]"));
            assertTrue(wholeLine.endsWith("p2pOutstandingToteWatermark=8 outstandingP2pTotes="
                    + "{dsp-p2p-line-1=0, dsp-p2p-line-2=0, dsp-p2p-line-3=0, "
                    + "dsp-p2p-line-4=0, dsp-p2p-line-5=0} deadlinesAndWorkloadCosts=diagnosticOnly"));
            var inspection = new DspFullDayInspectionSnapshot(captured, input, List.of(), List.of());
            var inspectionFormatter = new DspFullDayInspectionFormatter();
            assertTrue(inspectionFormatter.describe(inspection).contains(wholeLine));
            runtime.update(1d);
            var updated = runtime.snapshot();
            var updatedReleases = updated.elastic().allocation().wholeServiceCentrePolicy()
                    .orElseThrow().releases();
            assertEquals(1, updatedReleases.outstandingVersion());
            assertEquals("108", updatedReleases.releaseServiceCentreId().orElseThrow());
            assertEquals("104", updated.elastic().leases().lines().getFirst()
                    .serviceCentreId().orElseThrow());
            var updatedProgress = formatter.describe(DspFullDayProgressSnapshot.from(updated, input, profile));
            String updatedWholeLine = updatedProgress.get(indexOfPrefix(updatedProgress, "WholeServiceCentre:"));
            assertTrue(updatedWholeLine.endsWith("p2pOutstandingToteWatermark=8 outstandingP2pTotes="
                    + "{dsp-p2p-line-1=1, dsp-p2p-line-2=0, dsp-p2p-line-3=0, "
                    + "dsp-p2p-line-4=0, dsp-p2p-line-5=0} deadlinesAndWorkloadCosts=diagnosticOnly"));
            assertTrue(inspectionFormatter.describe(new DspFullDayInspectionSnapshot(
                    updated, input, List.of(), List.of())).contains(updatedWholeLine));
            assertEquals(before, formatter.describe(progress));
            assertTrue(inspectionFormatter.describe(inspection).contains(wholeLine));
            assertFalse(wholeLine.contains("order-104"));
            assertFalse(wholeLine.contains("tote-104"));
        }
    }

    @Test
    void shouldRetainConfiguredCountOrderAndIncludeOldOwnersOutsideAvailableLines() {
        var line5 = new P2pLineId("dsp-p2p-line-5");
        var line2 = new P2pLineId("dsp-p2p-line-2");
        var line4 = new P2pLineId("dsp-p2p-line-4");
        var line1 = new P2pLineId("dsp-p2p-line-1");
        var line3 = new P2pLineId("dsp-p2p-line-3");
        Map<P2pLineId, Integer> counts = new LinkedHashMap<>();
        counts.put(line5, 3);
        counts.put(line2, 0);
        counts.put(line4, 8);
        counts.put(line1, 1);
        counts.put(line3, 0);
        Map<P2pLineId, Integer> zeros = new LinkedHashMap<>();
        counts.keySet().forEach(line -> zeros.put(line, 0));
        var releases = new WholeServiceCentreReleaseSnapshot(12, List.of("104", "108"),
                Optional.of("108"), Map.of("104", 0, "108", 1), Map.of("104", 0, "108", 0),
                Map.of("104", counts, "108", zeros), 12, 9, counts);
        var policy = new WholeServiceCentrePolicySnapshot(releases, Optional.of("108"),
                List.of(line2, line3));
        String captured = DspFullDayProgressFormatter.wholeServiceCentreLine(policy);
        assertEquals("WholeServiceCentre: releaseCentre=108 eligibleCentre=108 unreleasedOsr=1 "
                + "unreleasedEmpty=0 availableUnleasedLines=[dsp-p2p-line-2, dsp-p2p-line-3] "
                + "p2pOutstandingToteWatermark=9 outstandingP2pTotes={dsp-p2p-line-5=3, "
                + "dsp-p2p-line-2=0, dsp-p2p-line-4=8, dsp-p2p-line-1=1, dsp-p2p-line-3=0} "
                + "deadlinesAndWorkloadCosts=diagnosticOnly", captured);
        counts.replaceAll((line, count) -> 0);
        assertEquals(captured, DspFullDayProgressFormatter.wholeServiceCentreLine(policy));
    }

    @Test
    void shouldFormatBoundedDeterministicSectionsAndAggregateRemainingWork(@TempDir Path directory)
            throws Exception {
        DspUncalibratedFullDayProfile profile = DspFullDayReportTestSupport.profile();
        DspFullDayLoadedInput input = DspFullDayReportTestSupport.input(directory, profile);

        try (DspFullDayAnalysisRuntime runtime = new DspFullDayAnalysisRuntimeFactory()
                .create(input, profile)) {
            DspFullDayProgressSnapshot snapshot = DspFullDayProgressSnapshot.from(
                    runtime.snapshot(), input, profile);
            DspFullDayProgressFormatter formatter = new DspFullDayProgressFormatter();

            List<String> first = formatter.describe(snapshot);
            List<String> second = formatter.describe(snapshot);

            assertEquals(first, second);
            assertTrue(first.get(0).startsWith("Run: state="));
            assertTrue(first.get(0).contains("profile=" + profile.profileId()));
            assertTrue(first.get(0).contains("calibration=UNCALIBRATED"));
            assertTrue(first.get(0).contains("milestone=P2P_OUTPUT_CLOSED"));
            assertTrue(first.get(1).startsWith("Clock: "));
            assertTrue(first.get(2).startsWith("Speed: "));
            assertTrue(first.get(3).startsWith("OSR: "));

            int centre104 = indexOfPrefix(first, "ServiceCentre[104]: ");
            int centre108 = indexOfPrefix(first, "ServiceCentre[108]: ");
            assertTrue(centre104 < centre108);
            assertTrue(first.get(centre104).contains("priority=999"));
            assertTrue(first.get(centre104).contains("blocks=DEPENDENCY["));
            assertTrue(first.get(centre104).contains("STATION_CAPACITY["));
            assertTrue(first.get(centre104).contains("OSR_STATE["));
            assertTrue(first.get(centre104).contains("P2P_ASSIGNMENT["));
            assertTrue(first.get(centre104).contains("UNSUPPORTED_WORK["));
            assertTrue(first.get(centre104).contains(
                    "p2pOutputClosure=NOT_CLOSED exceptions=missingPacks:0"
                            + ",pdcCollectedPacks:0,affectedAllocatedBags:0"
                            + ",markedOutboundTotes:0,pendingEmptyBags:0"));

            int firstP2p = indexOfPrefix(first, "P2P[dsp-p2p-line-1]: ");
            assertTrue(centre108 < firstP2p);
            int release = indexOfPrefix(first, "Release: ");
            int transport = indexOfPrefix(first, "Transport: ");
            int station = indexOfPrefix(first, "Station: ");
            int load = indexOfPrefix(first, "Load: ");
            int unsupported = indexOfPrefix(first, "Unsupported: ");
            int remaining = indexOfPrefix(first, "Remaining: ");
            assertTrue(firstP2p < release);
            assertTrue(release < transport);
            assertTrue(transport < station);
            assertTrue(station < load);
            assertTrue(load < unsupported);
            assertTrue(unsupported < remaining);

            int sheets = snapshot.runtime().metrics().serviceCentres().stream()
                    .mapToInt(value -> value.unfinishedSheetCount()).sum();
            int totes = snapshot.runtime().metrics().serviceCentres().stream()
                    .mapToInt(value -> value.unfinishedToteCount()).sum();
            int packs = snapshot.runtime().metrics().serviceCentres().stream()
                    .mapToInt(value -> value.unfinishedPackCount()).sum();
            int bags = snapshot.runtime().metrics().serviceCentres().stream()
                    .mapToInt(value -> value.unfinishedBagCount()).sum();
            assertEquals(
                    "Remaining: sheets=" + sheets + " totes=" + totes
                            + " packs=" + packs + " bags=" + bags,
                    first.get(remaining));

            String text = String.join("\n", first);
            assertFalse(text.contains("WholeServiceCentre:"));
            assertFalse(text.contains("p2pOutstandingToteWatermark="));
            assertFalse(text.contains("outstandingP2pTotes="));
            assertFalse(text.contains("order-104"));
            assertFalse(text.contains("tote-104"));
            assertFalse(text.contains("unfinishedIdentities"));
        }
    }

    @Test
    void shouldReportNsPendingAtProjectionBoundariesWithoutInventingCentreRows(@TempDir Path directory)
            throws Exception {
        var profile = DspFullDayReportTestSupport.profile();
        var input = withNsCandidates(DspFullDayReportTestSupport.input(directory, profile));
        try (var runtime = new DspFullDayAnalysisRuntimeFactory().create(input, profile)) {
            var initial = DspFullDayProgressSnapshot.from(runtime.snapshot(), input, profile);
            assertFalse(initial.completedWithNsCandidates());
            assertFalse(initial.completedWithInputExclusions());
            assertEquals(java.util.Map.of("104", 1, "109", 2),
                    initial.nsCandidateInputLineCountByServiceCentreId());
            assertThrowsUnsupportedMap(initial);
            for (int step = 0; step < 300 && runtime.state() == DspFullDayRuntimeState.RUNNING; step++) {
                runtime.update(1d);
            }
            var completed = DspFullDayProgressSnapshot.from(runtime.snapshot(), input, profile);
            assertTrue(completed.completedWithNsCandidates());
            assertFalse(completed.completedWithInputExclusions());
            var lines = new DspFullDayProgressFormatter().describe(completed);
            assertTrue(lines.getFirst().contains("completedWithNsCandidates=true"));
            assertEquals(1, lines.stream()
                    .filter(line -> line.startsWith("NsCandidatesPendingByServiceCentre:")).count());
            assertTrue(lines.get(indexOfPrefix(lines, "NsCandidatesPendingByServiceCentre:"))
                    .contains("{104=1, 109=2}"));
            assertTrue(lines.get(indexOfPrefix(lines, "ServiceCentre[104]:"))
                    .contains("nsCandidateInputLines:1"));
            assertFalse(lines.stream().anyMatch(line -> line.startsWith("ServiceCentre[109]:")));
            var inspection = new DspFullDayInspectionFormatter().describe(
                    new DspFullDayInspectionSnapshot(runtime.snapshot(), input,
                            List.of(), List.of()));
            assertTrue(inspection.getFirst().contains("completedWithNsCandidates=true"));
            assertTrue(inspection.stream().anyMatch(line ->
                    line.startsWith("NsCandidatesPendingByServiceCentre: {104=1, 109=2}")));
        }
        try (var runtime = new DspFullDayAnalysisRuntimeFactory().create(input, profile)) {
            runtime.update(24 * 60 * 60d);
            var cutoff = DspFullDayProgressSnapshot.from(runtime.snapshot(), input, profile);
            assertEquals(DspFullDayRuntimeState.HARD_CUTOFF_REACHED, runtime.state());
            assertFalse(cutoff.completedWithNsCandidates());
        }
    }

    private static void assertThrowsUnsupportedMap(DspFullDayProgressSnapshot snapshot) {
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> snapshot.nsCandidateInputLineCountByServiceCentreId().clear());
    }

    private static DspFullDayLoadedInput withNsCandidates(DspFullDayLoadedInput input) {
        var report = new DspDatasetLoadReport(0, 0, 0, List.of(
                new UnresolvedProductLine("ns-104", "line", "unknown", "104"),
                new UnresolvedProductLine("source", "line", "unknown", "109"),
                new UnresolvedProductLine("target", "line", "unknown", "109")), List.of());
        var data = input.data();
        return new DspFullDayLoadedInput(new LoadedDspData(
                data.products(), data.orders(), data.preparedLines(), data.loadedPreparedLineKeys(),
                data.startupReadyPreparedLineKeys(), data.inboundToteManifests(), report, data.retainedInputLines()),
                input.reportableOrders(), input.rejectionCatalog(), input.bagPlan(), report, input.timetable());
    }

    private static int indexOfPrefix(List<String> lines, String prefix) {
        for (int index = 0; index < lines.size(); index++) {
            if (lines.get(index).startsWith(prefix)) {
                return index;
            }
        }
        throw new AssertionError("missing line prefix: " + prefix);
    }
}
