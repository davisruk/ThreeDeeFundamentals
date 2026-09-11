package online.davisfamily.warehouse.sim.dsp.analysis.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayLoadedInput;
import online.davisfamily.warehouse.sim.dsp.analysis.DspUncalibratedFullDayProfile;
import online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayAnalysisRuntime;
import online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayAnalysisRuntimeFactory;

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

    private static int indexOfPrefix(List<String> lines, String prefix) {
        for (int index = 0; index < lines.size(); index++) {
            if (lines.get(index).startsWith(prefix)) {
                return index;
            }
        }
        throw new AssertionError("missing line prefix: " + prefix);
    }
}
