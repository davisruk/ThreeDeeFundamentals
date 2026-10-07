package online.davisfamily.warehouse.sim.dsp.adapting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.io.LoadedDspData;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;

class AdaptedLineStoreTest {
    private static final OrderSheetKey SOURCE = new OrderSheetKey("adapted-source", 7);
    private static final String CENTRE = "104";
    private static final String STORE = "0000310";

    @Test
    void legacyStoreRetainsKeySpecificCollectionAndCoordinates() {
        AdaptedLineStore store = new AdaptedLineStore(new AdaptingStorageLayout(
                new AdaptingStorageConfig(1, 2, 2), storageMap()));
        DspOrderItem a = line("a", "order-a", STORE);
        DspOrderItem b = line("b", "order-b", STORE);
        DspOrderItem c = line("c", "order-c", STORE);
        store.stageAll(List.of(a, b, c), SOURCE, CENTRE);

        assertFalse(store.strictStorage());
        assertEquals(3, store.snapshot().stagedLineCount());
        assertEquals(List.of(key(c), key(a)), store.takeAll(List.of(key(c), key(a))).stream()
                .map(AdaptedLineRecord::key).toList());
        assertEquals(1, store.snapshot().stagedLineCount());
        AdaptedLineRecord record = store.take(key(b)).orElseThrow();
        assertEquals(new AdaptingStorageLocation(STORE, new AdaptingBenchId("bench-1"), 0, 0, 1),
                record.location().orElseThrow());
        assertEquals(SOURCE, record.sourceOrderSheetKey());
        assertEquals(CENTRE, record.sourceServiceCentreId());
        assertTrue(store.take(key(a)).isEmpty());
        assertThrows(IllegalStateException.class, () -> store.prepareOrderGroup(STORE, "order-a"));
    }

    @Test
    void legacyBinsCountFullStoreLocationWhenCoordinatesCoincide() {
        AdaptingStorageMap map = storageMap();
        map.assignPharmacyToBench("0000388", new AdaptingBenchId("bench-1"));
        AdaptedLineStore store = new AdaptedLineStore(new AdaptingStorageLayout(
                AdaptingStorageConfig.defaults(), map));
        store.stage(line("a", "first", STORE), SOURCE, CENTRE);
        store.stage(line("b", "second", "0000388"), SOURCE, CENTRE);
        assertEquals(2, store.snapshot().activeBinCount());
    }

    @Test
    void legacyTakeAllFailureDoesNotRemoveEarlierKey() {
        AdaptedLineStore store = new AdaptedLineStore(new AdaptingStorageLayout(
                AdaptingStorageConfig.defaults(), storageMap()));
        DspOrderItem present = line("present", "order", STORE);
        store.stage(present, SOURCE, CENTRE);
        assertThrows(IllegalStateException.class, () -> store.takeAll(List.of(key(present), key(present))));
        assertThrows(IllegalStateException.class, () -> store.takeAll(List.of(
                key(present), new PreparedLineKey("missing", "missing"))));
        assertTrue(store.contains(key(present)));
    }

    @Test
    void strictBinsShareOrderAcrossSheetsAndDrainInStagingOrder() {
        DspOrderItem a = line("a", "target", STORE);
        DspOrderItem b = line("b", "target", STORE);
        DspOrderItem c = line("c", "target", STORE);
        OrderSheetKey first = new OrderSheetKey("target", 1);
        OrderSheetKey second = new OrderSheetKey("target", 2);
        AdaptedLineStore store = strictStore(2, Map.of(key(a), first, key(b), second, key(c), first), Map.of());
        store.stageAll(List.of(a, b, c), SOURCE, CENTRE);

        assertTrue(store.strictStorage());
        List<AdaptingBinSnapshot> bins = store.binSnapshots();
        assertEquals(List.of(new AdaptingBinId(STORE, "target", 1),
                new AdaptingBinId(STORE, "target", 2)), bins.stream().map(AdaptingBinSnapshot::id).toList());
        assertEquals(List.of(key(a), key(b)), binKeys(bins.getFirst()));
        assertEquals(List.of(key(c)), binKeys(bins.get(1)));
        assertSame(bins.get(1).id(), bins.getFirst().nextBinId().orElseThrow());
        assertTrue(bins.get(1).nextBinId().isEmpty());
        assertSame(bins, store.binSnapshots());
        assertEquals(2, store.snapshot().activeBinCount());
        assertTrue(bins.stream().flatMap(bin -> bin.stagedRecords().stream())
                .allMatch(record -> record.location().isEmpty()));

        AdaptingPreparedOrderGroup decision = store.prepareOrderGroup(STORE, "target");
        assertTrue(decision.firstCollection());
        assertEquals(List.of(key(a), key(b), key(c)), decision.records().stream().map(AdaptedLineRecord::key).toList());
        assertThrows(UnsupportedOperationException.class, () -> decision.records().clear());
        assertSame(bins, store.binSnapshots());
        assertEquals(decision.records(), store.commitOrderGroup(decision));
        assertEquals(0, store.snapshot().activeBinCount());
        assertEquals(0, store.snapshot().stagedLineCount());
        assertTrue(store.binSnapshots().isEmpty());
        assertNotSame(bins, store.binSnapshots());

        AdaptingPreparedOrderGroup later = store.prepareOrderGroup(STORE, "target");
        assertFalse(later.firstCollection());
        assertEquals(List.of(), later.records());
        assertEquals(List.of(), store.commitOrderGroup(later));
        assertEquals(List.of(), store.takeOrderGroup(STORE, "target"));
        assertThrows(IllegalStateException.class, () -> store.stage(a, SOURCE, CENTRE));
        assertThrows(IllegalStateException.class, () -> store.commitOrderGroup(decision));
    }

    @Test
    void strictOverflowAppendsToTailAndPreservesLinksAcrossStoreVisits() {
        Map<PreparedLineKey, OrderSheetKey> targets = new LinkedHashMap<>();
        List<DspOrderItem> lines = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            DspOrderItem line = line("line-" + i, "target", STORE);
            lines.add(line);
            targets.put(key(line), new OrderSheetKey("target", i % 2 + 1));
        }
        AdaptedLineStore store = strictStore(2, targets, Map.of());
        store.stageAll(lines.subList(0, 5), SOURCE, CENTRE);
        List<AdaptingBinSnapshot> before = store.binSnapshots();
        assertEquals(List.of(2, 2, 1), before.stream().map(bin -> bin.stagedRecords().size()).toList());
        assertEquals(List.of(1, 2, 3), before.stream().map(bin -> bin.id().ordinal()).toList());
        assertSame(before.get(1).id(), before.get(0).nextBinId().orElseThrow());
        assertSame(before.get(2).id(), before.get(1).nextBinId().orElseThrow());
        assertThrows(UnsupportedOperationException.class, () -> before.clear());
        assertThrows(UnsupportedOperationException.class, () -> before.getFirst().stagedRecords().clear());

        store.stageAll(List.of(lines.get(5)), new OrderSheetKey("later-source", 1), CENTRE);
        List<AdaptingBinSnapshot> after = store.binSnapshots();
        assertNotSame(before, after);
        assertSame(before.get(2).id(), after.get(2).id());
        assertEquals(List.of(2, 2, 2), after.stream().map(bin -> bin.stagedRecords().size()).toList());
        assertEquals(new OrderSheetKey("later-source", 1), after.get(2).stagedRecords().get(1).sourceOrderSheetKey());
        assertEquals(3, store.snapshot().activeBinCount());
    }

    @Test
    void oneSourceVisitCanStageMultipleStoresWithoutMixingGroups() {
        DspOrderItem a = line("a", "first", STORE);
        DspOrderItem b = line("b", "second", "0000388");
        Map<PreparedLineKey, OrderSheetKey> targets = Map.of(
                key(a), new OrderSheetKey("first", 1), key(b), new OrderSheetKey("second", 1));
        AdaptedLineStore store = strictStore(2, targets, Map.of("second", "0000388"));
        List<AdaptingBinSnapshot> empty = store.binSnapshots();
        assertThrows(IllegalStateException.class, () -> store.stageAll(List.of(a,
                line("b", "second", STORE)), SOURCE, CENTRE));
        assertSame(empty, store.binSnapshots());
        assertEquals(0, store.snapshot().stagedLineCount());
        store.stageAll(List.of(a, b), SOURCE, CENTRE);

        assertEquals(List.of(new AdaptingBinId(STORE, "first", 1),
                new AdaptingBinId("0000388", "second", 1)),
                store.binSnapshots().stream().map(AdaptingBinSnapshot::id).toList());
        assertEquals(List.of(key(a)), store.takeOrderGroup(STORE, "first").stream().map(AdaptedLineRecord::key).toList());
        assertEquals(List.of(key(b)), store.binSnapshots().stream().flatMap(bin -> bin.stagedRecords().stream())
                .map(AdaptedLineRecord::key).toList());
    }

    @Test
    void rejectedStoreAndIncompleteCollectionLeaveStateAndSnapshotUnchanged() {
        DspOrderItem a = line("a", "target", STORE);
        DspOrderItem b = line("b", "target", STORE);
        DspOrderItem wrongStore = line("b", "target", "0000388");
        AdaptedLineStore store = strictStore(2, Map.of(
                key(a), new OrderSheetKey("target", 1), key(b), new OrderSheetKey("target", 2)), Map.of());
        store.stage(a, SOURCE, CENTRE);
        List<AdaptingBinSnapshot> before = store.binSnapshots();

        assertThrows(IllegalStateException.class, () -> store.prepareOrderGroup(STORE, "target"));
        assertThrows(IllegalStateException.class, () -> store.prepareOrderGroup("0000388", "target"));
        assertThrows(IllegalStateException.class, () -> store.stage(wrongStore, SOURCE, CENTRE));
        assertThrows(IllegalStateException.class, () -> store.stageAll(List.of(b, b), SOURCE, CENTRE));
        assertThrows(IllegalStateException.class, () -> store.stageAll(List.of(b,
                line("unknown", "target", STORE)), SOURCE, CENTRE));
        assertThrows(IllegalStateException.class, () -> store.take(key(a)));
        assertThrows(IllegalStateException.class, () -> store.takeAll(List.of(key(a))));
        assertSame(before, store.binSnapshots());
        assertEquals(1, store.snapshot().stagedLineCount());
        assertEquals(1, store.snapshot().activeBinCount());
    }

    @Test
    void staleDecisionCannotDrainAfterAnotherStoreMutation() {
        DspOrderItem first = line("first", "target", STORE);
        DspOrderItem unrelated = line("other", "unrelated", STORE);
        AdaptedLineStore store = strictStore(2, Map.of(
                key(first), new OrderSheetKey("target", 1),
                key(unrelated), new OrderSheetKey("unrelated", 1)), Map.of());
        store.stage(first, SOURCE, CENTRE);
        AdaptingPreparedOrderGroup preview = store.prepareOrderGroup(STORE, "target");
        store.stage(unrelated, SOURCE, CENTRE);
        assertThrows(IllegalStateException.class, () -> store.commitOrderGroup(preview));
        assertTrue(store.contains(key(first)));
        assertTrue(store.contains(key(unrelated)));
        assertEquals(2, store.snapshot().activeBinCount());
    }

    @Test
    void rejectsNonAdaptedRecord() {
        DspOrderItem manual = new DspOrderItem("line", "product", 1, STORE,
                DspOrderLineType.MANUAL, "target", 1, 0);
        assertThrows(IllegalArgumentException.class,
                () -> AdaptedLineRecord.fromPreparedLine(manual, SOURCE, CENTRE));
    }

    @Test
    void refreshShouldPreserveOrderedFactsAcrossUnrelatedStoreAndCollectMutations() {
        DspOrderItem a = line("a", "first", STORE);
        DspOrderItem b = line("b", "second", STORE);
        DspOrderItem c = line("c", "first", STORE);
        AdaptedLineStore store = strictStore(1, Map.of(
                key(a), new OrderSheetKey("first", 1), key(c), new OrderSheetKey("first", 2),
                key(b), new OrderSheetKey("second", 1)), Map.of());
        store.stageAll(List.of(c, a), SOURCE, CENTRE);
        var first = store.prepareOrderGroup(STORE, "first");
        assertSame(first, store.refreshOrderGroupDecision(first));
        store.stage(b, SOURCE, CENTRE);
        var bins = store.binSnapshots();
        var afterStore = store.refreshOrderGroupDecision(first);
        assertNotSame(first, afterStore);
        assertEquals(first.records(), afterStore.records());
        assertSame(bins, store.binSnapshots());
        var second = store.prepareOrderGroup(STORE, "second");
        store.commitOrderGroup(second);
        bins = store.binSnapshots();
        var afterCollect = store.refreshOrderGroupDecision(first);
        assertEquals(List.of(key(c), key(a)), afterCollect.records().stream()
                .map(AdaptedLineRecord::key).toList());
        assertSame(bins, store.binSnapshots());
        assertThrows(IllegalStateException.class, () -> store.commitOrderGroup(afterStore));
        assertEquals(first.records(), store.commitOrderGroup(afterCollect));
        assertThrows(IllegalStateException.class, () -> store.refreshOrderGroupDecision(first));
        var later = store.prepareOrderGroup(STORE, "first");
        assertFalse(later.firstCollection());
        assertSame(later, store.refreshOrderGroupDecision(later));
    }

    @Test
    void refreshMustNotReplaceChangedOrderedRecords() {
        DspOrderItem a = line("a", "first", STORE);
        DspOrderItem b = line("b", "first", STORE);
        DspOrderItem other = line("other", "other", STORE);
        AdaptedLineStore store = strictStore(2, Map.of(
                key(a), new OrderSheetKey("first", 1), key(b), new OrderSheetKey("first", 1),
                key(other), new OrderSheetKey("other", 1)), Map.of());
        store.stageAll(List.of(a, b), SOURCE, CENTRE);
        var actual = store.prepareOrderGroup(STORE, "first");
        var reversed = new AdaptingPreparedOrderGroup(STORE, "first", actual.mutationVersion(),
                List.of(actual.records().get(1), actual.records().getFirst()), true);
        store.stage(other, SOURCE, CENTRE);
        var bins = store.binSnapshots();
        assertThrows(IllegalStateException.class, () -> store.refreshOrderGroupDecision(reversed));
        assertSame(bins, store.binSnapshots());
        assertThrows(IllegalArgumentException.class, () -> store.refreshOrderGroupDecision(null));
    }

    private static AdaptedLineStore strictStore(int linesPerBin,
            Map<PreparedLineKey, OrderSheetKey> targets, Map<String, String> stores) {
        Map<OrderSheetKey, List<DspOrderItem>> aliases = new LinkedHashMap<>();
        List<NotionalToteOrder> orders = new ArrayList<>();
        int sourceSheet = 1;
        for (Map.Entry<PreparedLineKey, OrderSheetKey> entry : targets.entrySet()) {
            PreparedLineKey key = entry.getKey();
            String store = stores.getOrDefault(key.targetOrderId(), STORE);
            DspOrderItem prepared = line(key.lineReference(), key.targetOrderId(), store);
            orders.add(order("source-" + sourceSheet, sourceSheet++, OrderType.ADAPTED,
                    List.of(prepared)));
            aliases.computeIfAbsent(entry.getValue(), ignored -> new ArrayList<>()).add(
                    new DspOrderItem(key.lineReference(), "product", 1, store,
                            DspOrderLineType.ADAPTED, "source", 1, 0));
        }
        aliases.forEach((sheet, items) -> orders.add(order(sheet.orderId(), sheet.sheetNumber(),
                OrderType.ASSOCIATED, items)));
        AdaptingTargetSheetCatalog targetCatalog = new AdaptingTargetSheetCatalog(targets);
        AdaptingOrderPreparationCatalog orderCatalog = new AdaptingOrderPreparationCatalog(
                new LoadedDspData(List.of(), orders, List.of(), Set.of()), targetCatalog);
        return new AdaptedLineStore(new AdaptingStorageLayout(
                new AdaptingStorageConfig(linesPerBin, 2, 2), storageMap(), targetCatalog, orderCatalog));
    }

    private static NotionalToteOrder order(String id, int sheet, OrderType type, List<DspOrderItem> items) {
        return new NotionalToteOrder(id, id, CENTRE, sheet, type, items, 999, sheet);
    }

    private static AdaptingStorageMap storageMap() {
        AdaptingStorageMap map = new AdaptingStorageMap();
        map.configureAvailableBenches(List.of(new AdaptingBenchId("bench-1")));
        map.assignPharmacyToBench(STORE, new AdaptingBenchId("bench-1"));
        return map;
    }

    private static DspOrderItem line(String reference, String targetOrder, String store) {
        return new DspOrderItem(reference, "product-" + reference, 1, store,
                DspOrderLineType.ADAPTED, targetOrder, 1, 0);
    }

    private static PreparedLineKey key(DspOrderItem line) {
        return PreparedLineKey.forPreparedLine(line);
    }

    private static List<PreparedLineKey> binKeys(AdaptingBinSnapshot bin) {
        return bin.stagedRecords().stream().map(AdaptedLineRecord::key).toList();
    }
}
