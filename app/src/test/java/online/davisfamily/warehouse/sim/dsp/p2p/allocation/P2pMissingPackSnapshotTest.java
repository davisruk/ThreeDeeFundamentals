package online.davisfamily.warehouse.sim.dsp.p2p.allocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
