package online.davisfamily.warehouse.sim.dsp.analysis.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

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

class DspFullDayProgressFormatterTest {

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
