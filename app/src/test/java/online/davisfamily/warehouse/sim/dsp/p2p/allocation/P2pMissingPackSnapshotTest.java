package online.davisfamily.warehouse.sim.dsp.p2p.allocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.bagging.BagKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;

class P2pMissingPackSnapshotTest {
    @Test
    void pdcUpdatesShareOnlyImmutableClassificationAndPreserveValueSemantics() {
        BagKey bag = new BagKey("rx", 1);
        P2pMissingPackSnapshot original = new P2pMissingPackSnapshot(3,
                Map.of(bag, Set.of("pack")), Set.of(bag), Map.of("104", 1), Map.of(),
                Map.of("order", new OrderSheetKey("order", 1)));
        P2pMissingPackSnapshot first = original.withPdcCollectedPack("104");
        P2pMissingPackSnapshot second = first.withPdcCollectedPack("108");

        assertEquals(4, first.version());
        assertEquals(5, second.version());
        assertEquals(Map.of(), original.pdcCollectedPackCountByServiceCentreId());
        assertEquals(Map.of("104", 1), first.pdcCollectedPackCountByServiceCentreId());
        assertEquals(Map.of("104", 1, "108", 1), second.pdcCollectedPackCountByServiceCentreId());
        assertNotSame(first.pdcCollectedPackCountByServiceCentreId(), second.pdcCollectedPackCountByServiceCentreId());
        assertSame(original.missingPhysicalPackIdsByBagKey(), second.missingPhysicalPackIdsByBagKey());
        assertSame(original.missingPhysicalPackIdsByBagKey().get(bag), second.missingPhysicalPackIdsByBagKey().get(bag));
        assertSame(original.pendingEmptyBagKeys(), second.pendingEmptyBagKeys());
        assertSame(original.missingPackCountByServiceCentreId(), second.missingPackCountByServiceCentreId());
        assertSame(original.firstCollectedSheetByOrderId(), second.firstCollectedSheetByOrderId());
        assertThrows(UnsupportedOperationException.class,
                () -> second.pdcCollectedPackCountByServiceCentreId().put("104", 99));
        Map<String, Integer> expectedPdcCounts = new LinkedHashMap<>();
        expectedPdcCounts.put("104", 1);
        expectedPdcCounts.put("108", 1);
        P2pMissingPackSnapshot equal = new P2pMissingPackSnapshot(5,
                original.missingPhysicalPackIdsByBagKey(), original.pendingEmptyBagKeys(),
                original.missingPackCountByServiceCentreId(), expectedPdcCounts,
                original.firstCollectedSheetByOrderId());
        assertEquals(equal, second);
        assertEquals(equal.hashCode(), second.hashCode());
        assertEquals(equal.toString(), second.toString());
        assertEquals("P2pMissingPackSnapshot[version=0, missingPhysicalPackIdsByBagKey={}, pendingEmptyBagKeys=[], "
                + "missingPackCountByServiceCentreId={}, pdcCollectedPackCountByServiceCentreId={}, "
                + "firstCollectedSheetByOrderId={}]", P2pMissingPackSnapshot.empty().toString());
        assertNotEquals(original, first);
        assertThrows(IllegalArgumentException.class, () -> original.withPdcCollectedPack(" 104"));
        assertThrows(IllegalArgumentException.class, () -> original.withPdcCollectedPack(null));
    }

    @Test
    void emptyIsReusableAndVersionZero() {
        assertSame(P2pMissingPackSnapshot.empty(), P2pMissingPackSnapshot.empty());
        assertEquals(0, P2pMissingPackSnapshot.empty().version());
        assertEquals(Map.of(), P2pMissingPackSnapshot.empty().firstCollectedSheetByOrderId());
    }

    @Test
    void copiesNestedCollectionsAndRejectsMutation() {
        BagKey bag = new BagKey("rx", 1);
        Set<String> ids = new LinkedHashSet<>(Set.of("pack"));
        Map<BagKey, Set<String>> missing = new LinkedHashMap<>();
        missing.put(bag, ids);
        Set<BagKey> pending = new LinkedHashSet<>(Set.of(bag));
        Map<String, Integer> counts = new LinkedHashMap<>(Map.of("104", 1));
        Map<String, OrderSheetKey> first = new LinkedHashMap<>(Map.of("order", new OrderSheetKey("order", 2)));
        P2pMissingPackSnapshot snapshot = new P2pMissingPackSnapshot(
                3, missing, pending, counts, Map.of(), first);
        ids.add("later");
        missing.clear();
        pending.clear();
        counts.clear();
        first.clear();
        assertEquals(Set.of("pack"), snapshot.missingPhysicalPackIdsByBagKey().get(bag));
        assertEquals(Set.of(bag), snapshot.pendingEmptyBagKeys());
        assertEquals(1, snapshot.missingPackCountByServiceCentreId().get("104").intValue());
        assertEquals(new OrderSheetKey("order", 2), snapshot.firstCollectedSheetByOrderId().get("order"));
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.missingPhysicalPackIdsByBagKey().get(bag).add("x"));
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.firstCollectedSheetByOrderId().clear());
    }

    @Test
    void rejectsInvalidVersionCountsPackIdentityAndFirstSheet() {
        BagKey bag = new BagKey("rx", 1);
        assertThrows(IllegalArgumentException.class, () -> new P2pMissingPackSnapshot(
                -1, Map.of(), Set.of(), Map.of(), Map.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new P2pMissingPackSnapshot(
                1, Map.of(), Set.of(), Map.of("104", -1), Map.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new P2pMissingPackSnapshot(
                1, Map.of(bag, Set.of("pack")), Set.of(bag), Map.of(), Map.of(),
                Map.of("other", new OrderSheetKey("order", 1))));
        assertThrows(IllegalArgumentException.class, () -> new P2pMissingPackSnapshot(
                1, Map.of(), Set.of(bag), Map.of(), Map.of(), Map.of()));
    }
}
