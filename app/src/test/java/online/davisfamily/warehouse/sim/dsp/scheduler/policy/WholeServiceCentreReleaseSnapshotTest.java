package online.davisfamily.warehouse.sim.dsp.scheduler.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;

class WholeServiceCentreReleaseSnapshotTest {
    private static final P2pLineId FIRST_LINE = new P2pLineId("line-first");
    private static final P2pLineId SECOND_LINE = new P2pLineId("line-second");

    @Test
    void shouldPublishOrderedImmutableOutstandingCountsAndPreserveSixArgumentCompatibility() {
        Map<P2pLineId, Integer> committedByLine = new LinkedHashMap<>();
        committedByLine.put(SECOND_LINE, 0);
        committedByLine.put(FIRST_LINE, 0);
        Map<String, Map<P2pLineId, Integer>> committed = new LinkedHashMap<>();
        committed.put("Z", committedByLine);
        Map<P2pLineId, Integer> outstanding = new LinkedHashMap<>();
        outstanding.put(SECOND_LINE, 1);
        outstanding.put(FIRST_LINE, 0);

        var bounded = snapshot(3, List.of("Z"), Map.of("Z", 0), committed,
                7, 2, outstanding);
        assertEquals(List.of(SECOND_LINE, FIRST_LINE), List.copyOf(bounded.outstandingP2pToteCounts().keySet()));
        assertEquals(1, bounded.outstandingToteCount(SECOND_LINE));
        assertEquals(0, bounded.outstandingToteCount(FIRST_LINE));
        assertEquals(7, bounded.outstandingVersion());
        assertEquals(2, bounded.p2pOutstandingToteWatermark());
        outstanding.put(SECOND_LINE, 2);
        assertEquals(1, bounded.outstandingToteCount(SECOND_LINE));
        assertThrows(UnsupportedOperationException.class, () -> bounded.outstandingP2pToteCounts().clear());

        var compatible = new WholeServiceCentreReleaseSnapshot(0, List.of("Z"), Optional.empty(),
                Map.of("Z", 0), Map.of("Z", 0), committed);
        assertEquals(0, compatible.outstandingVersion());
        assertEquals(Integer.MAX_VALUE, compatible.p2pOutstandingToteWatermark());
        assertEquals(Map.of(SECOND_LINE, 0, FIRST_LINE, 0), compatible.outstandingP2pToteCounts());

        var noCentres = new WholeServiceCentreReleaseSnapshot(0, List.of(), Optional.empty(),
                Map.of(), Map.of(), Map.of(), 0, 8, Map.of(FIRST_LINE, 0, SECOND_LINE, 0));
        assertEquals(0, noCentres.outstandingToteCount(FIRST_LINE));
        assertThrows(IllegalArgumentException.class, () -> noCentres.outstandingToteCount(new P2pLineId("unknown")));
        assertThrows(IllegalArgumentException.class, () -> noCentres.outstandingToteCount(null));

        var legacyNoCentres = new WholeServiceCentreReleaseSnapshot(0, List.of(), Optional.empty(),
                Map.of(), Map.of(), Map.of());
        assertTrue(legacyNoCentres.outstandingP2pToteCounts().isEmpty());
    }

    @Test
    void shouldRejectInvalidVersionsWatermarksCountsAndConfiguredLineSets() {
        var committed = Map.of("Z", Map.of(FIRST_LINE, 0, SECOND_LINE, 0));
        assertThrows(IllegalArgumentException.class, () -> snapshot(-1, List.of("Z"),
                Map.of("Z", 0), committed, 0, 2, Map.of(FIRST_LINE, 0, SECOND_LINE, 0)));
        assertThrows(IllegalArgumentException.class, () -> snapshot(0, List.of("Z"),
                Map.of("Z", 0), committed, -1, 2, Map.of(FIRST_LINE, 0, SECOND_LINE, 0)));
        assertThrows(IllegalArgumentException.class, () -> snapshot(0, List.of("Z"),
                Map.of("Z", 0), committed, 0, 0, Map.of(FIRST_LINE, 0, SECOND_LINE, 0)));
        assertThrows(IllegalArgumentException.class, () -> snapshot(0, List.of("Z"),
                Map.of("Z", 0), committed, 0, 2, null));
        assertThrows(IllegalArgumentException.class, () -> snapshot(0, List.of("Z"),
                Map.of("Z", 0), committed, 0, 2, Map.of(FIRST_LINE, 3, SECOND_LINE, 0)));
        assertThrows(IllegalArgumentException.class, () -> snapshot(0, List.of("Z"),
                Map.of("Z", 0), committed, 0, 2, Map.of(FIRST_LINE, -1, SECOND_LINE, 0)));
        assertThrows(IllegalArgumentException.class, () -> snapshot(0, List.of("Z"),
                Map.of("Z", 0), committed, 0, 2, Map.of(FIRST_LINE, 0)));
        assertThrows(IllegalArgumentException.class, () -> snapshot(0, List.of("Z"),
                Map.of("Z", 0), committed, 0, 2,
                Collections.singletonMap(null, 0)));
        assertThrows(IllegalArgumentException.class, () -> snapshot(0, List.of("Z"),
                Map.of("Z", 0), committed, 0, 2,
                Collections.singletonMap(FIRST_LINE, null)));

        assertThrows(IllegalArgumentException.class, () -> new WholeServiceCentreReleaseSnapshot(
                0, List.of(), Optional.empty(), Map.of(), Map.of(), Map.of(), 0, 8,
                Map.of(FIRST_LINE, 1)));
        assertThrows(IllegalArgumentException.class, () -> new WholeServiceCentreReleaseSnapshot(
                0, List.of(), Optional.empty(), Map.of(), Map.of(), Map.of(), 0, 8,
                Map.of(FIRST_LINE, 9)));
        assertThrows(IllegalArgumentException.class, () -> new WholeServiceCentreReleaseSnapshot(
                0, List.of("Z"), Optional.empty(), Map.of("Z", 0), Map.of("Z", 0), committed,
                0, 8, Map.of(FIRST_LINE, 0)));
    }

    private static WholeServiceCentreReleaseSnapshot snapshot(
            long version,
            List<String> centres,
            Map<String, Integer> unreleased,
            Map<String, Map<P2pLineId, Integer>> committed,
            long outstandingVersion,
            int watermark,
            Map<P2pLineId, Integer> outstanding) {
        return new WholeServiceCentreReleaseSnapshot(version, centres, Optional.empty(),
                unreleased, unreleased, committed, outstandingVersion, watermark, outstanding);
    }
}
