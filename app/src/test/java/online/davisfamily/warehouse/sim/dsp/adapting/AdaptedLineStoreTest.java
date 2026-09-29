package online.davisfamily.warehouse.sim.dsp.adapting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;

class AdaptedLineStoreTest {
    private static final OrderSheetKey SOURCE_ORDER_SHEET = new OrderSheetKey("adapted-source", 7);
    private static final String SOURCE_SERVICE_CENTRE = "104";

    @Test
    void shouldStageAndTakeSingleAdaptedLine() {
        AdaptedLineStore store = new AdaptedLineStore(new AdaptingStorageLayout(
                AdaptingStorageConfig.defaults(),
                storageMap("0000310", "bench-1")));
        DspOrderItem preparedLine = adaptedLine("line-1", "order-1", "0000310");
        PreparedLineKey key = PreparedLineKey.forPreparedLine(preparedLine);

        stage(store, preparedLine);

        assertTrue(store.contains(key));
        assertEquals(1, store.snapshot().stagedLineCount());
        assertTrue(store.snapshot().stagedLineKeys().contains(key));

        AdaptedLineRecord record = store.take(key).orElseThrow();

        assertEquals(key, record.key());
        assertEquals(SOURCE_ORDER_SHEET, record.sourceOrderSheetKey());
        assertEquals(SOURCE_SERVICE_CENTRE, record.sourceServiceCentreId());
        assertEquals(preparedLine, record.line());
        assertEquals(new AdaptingBenchId("bench-1"), record.location().orElseThrow().benchId());
        assertFalse(store.contains(key));
        assertEquals(0, store.snapshot().stagedLineCount());
    }

    @Test
    void shouldTakeAllRequestedAdaptedLinesAndRemoveThemFromStore() {
        AdaptedLineStore store = new AdaptedLineStore(new AdaptingStorageLayout(
                AdaptingStorageConfig.defaults(),
                storageMap(
                        "0000310", "bench-1",
                        "0000388", "bench-2",
                        "0000456", "bench-2")));
        DspOrderItem line1 = adaptedLine("line-1", "order-1", "0000310");
        DspOrderItem line2 = adaptedLine("line-2", "order-2", "0000388");
        DspOrderItem line3 = adaptedLine("line-3", "order-3", "0000456");

        stage(store, line1);
        stage(store, line2);
        stage(store, line3);

        List<AdaptedLineRecord> records = store.takeAll(List.of(
                PreparedLineKey.forPreparedLine(line2),
                PreparedLineKey.forPreparedLine(line1)));

        assertEquals(List.of(
                PreparedLineKey.forPreparedLine(line2),
                PreparedLineKey.forPreparedLine(line1)),
                records.stream().map(AdaptedLineRecord::key).toList());
        assertFalse(store.contains(PreparedLineKey.forPreparedLine(line1)));
        assertFalse(store.contains(PreparedLineKey.forPreparedLine(line2)));
        assertTrue(store.contains(PreparedLineKey.forPreparedLine(line3)));
        assertEquals(1, store.snapshot().stagedLineCount());
    }

    @Test
    void shouldFailClearlyWhenTakingMissingAdaptedLineBatch() {
        AdaptedLineStore store = new AdaptedLineStore(new AdaptingStorageLayout(
                AdaptingStorageConfig.defaults(),
                storageMap("0000310", "bench-1", "0000388", "bench-2")));
        DspOrderItem presentLine = adaptedLine("line-1", "order-1", "0000310");
        DspOrderItem missingLine = adaptedLine("line-2", "order-2", "0000388");
        PreparedLineKey presentKey = PreparedLineKey.forPreparedLine(presentLine);
        PreparedLineKey missingKey = PreparedLineKey.forPreparedLine(missingLine);
        stage(store, presentLine);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> store.takeAll(List.of(presentKey, missingKey)));

        assertTrue(exception.getMessage().contains("Missing staged adapted lines"));
        assertTrue(store.contains(presentKey));
        assertFalse(store.contains(missingKey));
    }

    @Test
    void shouldRejectNonAdaptedPreparedLine() {
        DspOrderItem manualLine = new DspOrderItem(
                "line-1",
                "product-1",
                1,
                "0000310",
                DspOrderLineType.MANUAL,
                "order-1",
                1,
                0);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> AdaptedLineRecord.fromPreparedLine(
                        manualLine, SOURCE_ORDER_SHEET, SOURCE_SERVICE_CENTRE));

        assertEquals("line must be ADAPTED", exception.getMessage());
    }

    @Test
    void shouldCreateNewBinsShelvesAndRacksWhenCapacitiesAreReached() {
        AdaptedLineStore store = new AdaptedLineStore(new AdaptingStorageLayout(
                new AdaptingStorageConfig(1, 2, 2),
                storageMap("0000310", "bench-2")));

        stage(store, adaptedLine("line-1", "order-1", "0000310"));
        stage(store, adaptedLine("line-2", "order-2", "0000310"));
        stage(store, adaptedLine("line-3", "order-3", "0000310"));
        stage(store, adaptedLine("line-4", "order-4", "0000310"));
        stage(store, adaptedLine("line-5", "order-5", "0000310"));

        AdaptedLineRecord line1 = store.take(PreparedLineKey.forPreparedLine(adaptedLine("line-1", "order-1", "0000310"))).orElseThrow();
        AdaptedLineRecord line3 = store.take(PreparedLineKey.forPreparedLine(adaptedLine("line-3", "order-3", "0000310"))).orElseThrow();
        AdaptedLineRecord line5 = store.take(PreparedLineKey.forPreparedLine(adaptedLine("line-5", "order-5", "0000310"))).orElseThrow();

        assertEquals(new AdaptingStorageLocation("0000310", new AdaptingBenchId("bench-2"), 0, 0, 0), line1.location().orElseThrow());
        assertEquals(new AdaptingStorageLocation("0000310", new AdaptingBenchId("bench-2"), 0, 1, 0), line3.location().orElseThrow());
        assertEquals(new AdaptingStorageLocation("0000310", new AdaptingBenchId("bench-2"), 1, 0, 0), line5.location().orElseThrow());
    }

    @Test
    void shouldRetainAdaptedSourceOrderSheetWhileLineIsStored() {
        AdaptedLineStore store = new AdaptedLineStore();
        DspOrderItem line = adaptedLine("line-1", "associated-target", "0000310");

        stage(store, line);

        AdaptedLineRecord record = store.take(PreparedLineKey.forPreparedLine(line)).orElseThrow();
        assertEquals(SOURCE_ORDER_SHEET, record.sourceOrderSheetKey());
        assertEquals(SOURCE_SERVICE_CENTRE, record.sourceServiceCentreId());
    }

    @Test
    void shouldKeepPreparedTargetKeySeparateFromSourceSheetIdentity() {
        AdaptedLineStore store = new AdaptedLineStore();
        DspOrderItem line = adaptedLine("line-1", "associated-target", "0000310");

        stage(store, line);

        AdaptedLineRecord record = store.take(PreparedLineKey.forPreparedLine(line)).orElseThrow();
        assertEquals(new PreparedLineKey("associated-target", "line-1"), record.key());
        assertEquals(new OrderSheetKey("adapted-source", 7), record.sourceOrderSheetKey());
    }

    @Test
    void shouldKeepInterleavedSheetsInDistinctBinsAndCollectOnlyRequestedSheet() {
        DspOrderItem a1 = adaptedLine("a1", "target", "0000310");
        DspOrderItem b1 = adaptedLine("b1", "target", "0000310");
        DspOrderItem a2 = adaptedLine("a2", "target", "0000310");
        DspOrderItem b2 = adaptedLine("b2", "target", "0000310");
        OrderSheetKey first = new OrderSheetKey("target", 1);
        OrderSheetKey second = new OrderSheetKey("target", 2);
        AdaptedLineStore store = strictStore(new AdaptingStorageConfig(2, 2, 2),
                Map.of(key(a1), first, key(a2), first, key(b1), second, key(b2), second));

        store.stageAll(List.of(a1, b1, a2, b2), SOURCE_ORDER_SHEET, SOURCE_SERVICE_CENTRE);

        List<AdaptingBinSnapshot> bins = store.binSnapshots();
        assertEquals(List.of(first, second), bins.stream().map(bin -> bin.id().targetOrderSheetKey()).toList());
        assertEquals(List.of(new AdaptingBinId("0000310", first, 1),
                new AdaptingBinId("0000310", second, 1)), bins.stream().map(AdaptingBinSnapshot::id).toList());
        assertTrue(bins.stream().flatMap(bin -> bin.stagedRecords().stream())
                .allMatch(record -> record.location().isEmpty()));
        assertEquals(List.of(key(a1), key(a2)), binKeys(bins.get(0)));
        assertEquals(List.of(key(b1), key(b2)), binKeys(bins.get(1)));
        assertEquals(2, store.snapshot().activeBinCount());

        assertEquals(List.of(key(a2), key(a1)), store.takeAll(List.of(key(a2), key(a1)))
                .stream().map(AdaptedLineRecord::key).toList());
        assertEquals(List.of(second), store.binSnapshots().stream()
                .map(bin -> bin.id().targetOrderSheetKey()).toList());
        assertTrue(store.contains(key(b1)));
        assertTrue(store.contains(key(b2)));
    }

    @Test
    void shouldUseStoreSheetBinIdentityAndIgnoreBenchChangesInStrictStorage() {
        DspOrderItem firstLine = adaptedLine("first", "first-target", "0000310");
        DspOrderItem thirdLine = adaptedLine("third", "first-target", "0000310");
        DspOrderItem wrongStoreLine = adaptedLine("wrong-store", "first-target", "0000388");
        DspOrderItem secondLine = adaptedLine("second", "second-target", "0000388");
        AdaptingStorageMap storageMap = storageMap(
                "0000310", "bench-1", "0000388", "bench-1");
        AdaptedLineStore store = new AdaptedLineStore(new AdaptingStorageLayout(
                new AdaptingStorageConfig(2, 2, 2), storageMap,
                new AdaptingTargetSheetCatalog(Map.of(
                        key(firstLine), new OrderSheetKey("first-target", 1),
                        key(thirdLine), new OrderSheetKey("first-target", 1),
                        key(wrongStoreLine), new OrderSheetKey("first-target", 1),
                        key(secondLine), new OrderSheetKey("second-target", 1)))));

        store.stageAll(List.of(firstLine, secondLine), SOURCE_ORDER_SHEET, SOURCE_SERVICE_CENTRE);

        List<AdaptingBinSnapshot> bins = store.binSnapshots();
        assertEquals(2, bins.size());
        AdaptingBinId firstId = bins.getFirst().id();
        assertEquals(List.of(
                new AdaptingBinId("0000310", new OrderSheetKey("first-target", 1), 1),
                new AdaptingBinId("0000388", new OrderSheetKey("second-target", 1), 1)),
                bins.stream().map(AdaptingBinSnapshot::id).toList());
        assertTrue(bins.stream().flatMap(bin -> bin.stagedRecords().stream())
                .allMatch(record -> record.location().isEmpty()));
        assertTrue(store.snapshot().stagedLineCountByBench().isEmpty());
        assertEquals(0, store.snapshot().activeRackCount());
        assertEquals(0, store.snapshot().activeShelfCount());
        assertEquals(2, store.snapshot().activeBinCount());

        storageMap.assignPharmacyToBench("0000310", new AdaptingBenchId("bench-2"));
        store.stageAll(List.of(thirdLine), SOURCE_ORDER_SHEET, SOURCE_SERVICE_CENTRE);
        List<AdaptingBinSnapshot> afterBenchChange = store.binSnapshots();
        assertSame(firstId, afterBenchChange.getFirst().id());
        assertEquals(List.of(key(firstLine), key(thirdLine)), binKeys(afterBenchChange.getFirst()));
        assertEquals(2, store.snapshot().activeBinCount());

        List<AdaptingBinSnapshot> beforeRejectedStage = store.binSnapshots();
        assertThrows(IllegalStateException.class, () -> store.stageAll(List.of(wrongStoreLine),
                SOURCE_ORDER_SHEET, SOURCE_SERVICE_CENTRE));
        assertSame(beforeRejectedStage, store.binSnapshots());
        assertEquals(3, store.snapshot().stagedLineCount());
        assertEquals(2, store.snapshot().activeBinCount());

        store.takeAll(List.of(key(firstLine), key(thirdLine)));
        assertEquals(1, store.snapshot().activeBinCount());
        assertEquals(List.of(key(secondLine)), binKeys(store.binSnapshots().getFirst()));
        store.takeAll(List.of(key(secondLine)));
        assertEquals(0, store.snapshot().activeBinCount());
    }

    @Test
    void shouldCountLegacyBinsByFullLocationWhenStoresShareCoordinateNumbers() {
        AdaptedLineStore store = new AdaptedLineStore(new AdaptingStorageLayout(
                AdaptingStorageConfig.defaults(),
                storageMap("0000310", "bench-1", "0000388", "bench-1")));
        DspOrderItem firstLine = adaptedLine("first", "first-order", "0000310");
        DspOrderItem secondLine = adaptedLine("second", "second-order", "0000388");

        stage(store, firstLine);
        stage(store, secondLine);

        AdaptedLineStoreSnapshot snapshot = store.snapshot();
        assertEquals(Map.of(new AdaptingBenchId("bench-1"), 2), snapshot.stagedLineCountByBench());
        assertEquals(1, snapshot.activeRackCount());
        assertEquals(1, snapshot.activeShelfCount());
        assertEquals(2, snapshot.activeBinCount());
    }

    @Test
    void shouldLinkOverflowBinsAndAppendLaterSourceToNonFullTail() {
        DspOrderItem a1 = adaptedLine("a1", "target", "0000310");
        DspOrderItem a2 = adaptedLine("a2", "target", "0000310");
        DspOrderItem a3 = adaptedLine("a3", "target", "0000310");
        DspOrderItem a4 = adaptedLine("a4", "target", "0000310");
        DspOrderItem a5 = adaptedLine("a5", "target", "0000310");
        DspOrderItem a6 = adaptedLine("a6", "target", "0000310");
        DspOrderItem b1 = adaptedLine("b1", "target", "0000310");
        DspOrderItem b2 = adaptedLine("b2", "target", "0000310");
        DspOrderItem b3 = adaptedLine("b3", "target", "0000310");
        DspOrderItem b4 = adaptedLine("b4", "target", "0000310");
        OrderSheetKey first = new OrderSheetKey("target", 1);
        OrderSheetKey second = new OrderSheetKey("target", 2);
        Map<PreparedLineKey, OrderSheetKey> assignments = new LinkedHashMap<>();
        for (DspOrderItem line : List.of(a1, a2, a3, a4, a5, a6)) {
            assignments.put(key(line), first);
        }
        for (DspOrderItem line : List.of(b1, b2, b3, b4)) {
            assignments.put(key(line), second);
        }
        AdaptedLineStore store = strictStore(new AdaptingStorageConfig(2, 2, 2), assignments);

        store.stageAll(List.of(a1, a2, b1, b2, a3, a4, b3, b4, a5),
                SOURCE_ORDER_SHEET, SOURCE_SERVICE_CENTRE);
        List<AdaptingBinSnapshot> firstBins = store.binSnapshots().stream()
                .filter(bin -> bin.id().targetOrderSheetKey().equals(first)).toList();

        assertEquals(List.of(1, 2, 3), firstBins.stream().map(bin -> bin.id().ordinal()).toList());
        assertEquals(List.of(
                new AdaptingBinId("0000310", first, 1),
                new AdaptingBinId("0000310", first, 2),
                new AdaptingBinId("0000310", first, 3)), firstBins.stream().map(AdaptingBinSnapshot::id).toList());
        assertEquals(List.of(2, 2, 1), firstBins.stream()
                .map(bin -> bin.stagedRecords().size()).toList());
        assertEquals(5, store.snapshot().activeBinCount());
        assertSame(firstBins.get(1).id(), firstBins.get(0).nextBinId().orElseThrow());
        assertSame(firstBins.get(2).id(), firstBins.get(1).nextBinId().orElseThrow());
        assertTrue(firstBins.get(2).nextBinId().isEmpty());
        assertTrue(firstBins.stream().flatMap(bin -> bin.stagedRecords().stream())
                .allMatch(record -> record.location().isEmpty()));

        store.stageAll(List.of(a6), new OrderSheetKey("later-source", 1), SOURCE_SERVICE_CENTRE);
        List<AdaptingBinSnapshot> after = store.binSnapshots().stream()
                .filter(bin -> bin.id().targetOrderSheetKey().equals(first)).toList();
        assertEquals(3, after.size());
        assertSame(firstBins.get(2).id(), after.get(2).id());
        assertEquals(List.of(key(a5), key(a6)), binKeys(after.get(2)));
        assertEquals(new OrderSheetKey("later-source", 1), after.get(2).stagedRecords().get(1).sourceOrderSheetKey());

        store.takeAll(List.of(key(a1), key(a2)));
        List<AdaptingBinSnapshot> withEmptyPredecessor = store.binSnapshots().stream()
                .filter(bin -> bin.id().targetOrderSheetKey().equals(first)).toList();
        assertEquals(3, withEmptyPredecessor.size());
        assertTrue(withEmptyPredecessor.getFirst().stagedRecords().isEmpty());
        assertSame(withEmptyPredecessor.get(1).id(), withEmptyPredecessor.getFirst().nextBinId().orElseThrow());
        assertEquals(8, store.snapshot().stagedLineCount());
        assertEquals(4, store.snapshot().activeBinCount());

        store.takeAll(List.of(key(a3), key(a4), key(a5), key(a6)));
        assertEquals(List.of(second), store.binSnapshots().stream()
                .map(bin -> bin.id().targetOrderSheetKey()).distinct().toList());
    }

    @Test
    void shouldValidateWholeStoreVisitWithoutMutatingBinsOrInvalidatingSnapshots() {
        DspOrderItem first = adaptedLine("first", "target", "0000310");
        DspOrderItem second = adaptedLine("second", "target", "0000310");
        DspOrderItem third = adaptedLine("third", "target", "0000310");
        DspOrderItem wrongPharmacy = adaptedLine("wrong", "target", "0000388");
        DspOrderItem missing = adaptedLine("missing", "target", "0000310");
        OrderSheetKey sheet = new OrderSheetKey("target", 1);
        AdaptedLineStore store = strictStore(new AdaptingStorageConfig(2, 2, 2),
                Map.of(key(first), sheet, key(second), sheet, key(third), sheet,
                        key(wrongPharmacy), sheet));
        store.stageAll(List.of(first), SOURCE_ORDER_SHEET, SOURCE_SERVICE_CENTRE);
        List<AdaptingBinSnapshot> before = store.binSnapshots();

        assertThrows(IllegalStateException.class, () -> store.stageAll(List.of(second, missing),
                SOURCE_ORDER_SHEET, SOURCE_SERVICE_CENTRE));
        assertThrows(IllegalStateException.class, () -> store.stageAll(List.of(second, second),
                SOURCE_ORDER_SHEET, SOURCE_SERVICE_CENTRE));
        assertThrows(IllegalStateException.class, () -> store.stageAll(List.of(second, first),
                SOURCE_ORDER_SHEET, SOURCE_SERVICE_CENTRE));
        assertThrows(IllegalStateException.class, () -> store.stageAll(List.of(second, wrongPharmacy),
                SOURCE_ORDER_SHEET, SOURCE_SERVICE_CENTRE));
        assertSame(before, store.binSnapshots());
        assertEquals(1, store.snapshot().stagedLineCount());
        assertEquals(1, store.snapshot().activeBinCount());

        store.stageAll(List.of(second, third), SOURCE_ORDER_SHEET, SOURCE_SERVICE_CENTRE);
        List<AdaptingBinSnapshot> after = store.binSnapshots();
        assertNotSame(before, after);
        assertEquals(List.of(2, 1), after.stream().map(bin -> bin.stagedRecords().size()).toList());
        assertEquals(new AdaptingBinId("0000310", sheet, 2), after.get(1).id());
        assertTrue(after.stream().flatMap(bin -> bin.stagedRecords().stream())
                .allMatch(record -> record.location().isEmpty()));
        assertThrows(IllegalStateException.class, () -> store.stage(
                AdaptedLineRecord.fromPreparedLine(missing, SOURCE_ORDER_SHEET, SOURCE_SERVICE_CENTRE)));
    }

    @Test
    void shouldPrevalidateCollectAndCacheImmutableInspectionAcrossUnrelatedGroups() {
        Map<PreparedLineKey, OrderSheetKey> assignments = new LinkedHashMap<>();
        List<DspOrderItem> lines = new java.util.ArrayList<>();
        for (int index = 0; index < 30; index++) {
            DspOrderItem line = adaptedLine("line-" + index, "target-" + index, "0000310");
            lines.add(line);
            assignments.put(key(line), new OrderSheetKey("target-" + index, 1));
        }
        AdaptedLineStore store = strictStore(new AdaptingStorageConfig(2, 2, 2), assignments);
        store.stageAll(lines, SOURCE_ORDER_SHEET, SOURCE_SERVICE_CENTRE);
        List<AdaptingBinSnapshot> before = store.binSnapshots();
        assertSame(before, store.binSnapshots());
        PreparedLineKey first = key(lines.getFirst());
        PreparedLineKey second = key(lines.get(1));

        assertThrows(IllegalStateException.class, () -> store.takeAll(List.of(first, first)));
        assertThrows(IllegalStateException.class, () -> store.takeAll(List.of(first,
                new PreparedLineKey("missing", "missing"))));
        assertSame(before, store.binSnapshots());
        assertEquals(30, store.snapshot().stagedLineCount());
        assertEquals(30, store.snapshot().activeBinCount());
        assertThrows(UnsupportedOperationException.class, () -> before.clear());
        assertThrows(UnsupportedOperationException.class, () -> before.getFirst().stagedRecords().clear());

        assertEquals(List.of(second, first), store.takeAll(List.of(second, first))
                .stream().map(AdaptedLineRecord::key).toList());
        List<AdaptingBinSnapshot> after = store.binSnapshots();
        assertNotSame(before, after);
        assertEquals(28, after.size());
        assertEquals(before.subList(2, 30), after);
        assertFalse(store.contains(first));
        assertFalse(store.contains(second));
        assertSame(after, store.binSnapshots());
    }

    private static AdaptedLineStore strictStore(AdaptingStorageConfig config,
            Map<PreparedLineKey, OrderSheetKey> assignments) {
        return new AdaptedLineStore(new AdaptingStorageLayout(config,
                storageMap("0000310", "bench-1", "0000388", "bench-2"),
                new AdaptingTargetSheetCatalog(assignments)));
    }

    private static PreparedLineKey key(DspOrderItem line) {
        return PreparedLineKey.forPreparedLine(line);
    }

    private static List<PreparedLineKey> binKeys(AdaptingBinSnapshot bin) {
        return bin.stagedRecords().stream().map(AdaptedLineRecord::key).toList();
    }

    private static void stage(AdaptedLineStore store, DspOrderItem line) {
        store.stage(line, SOURCE_ORDER_SHEET, " " + SOURCE_SERVICE_CENTRE + " ");
    }

    private static AdaptingStorageMap storageMap(String... values) {
        AdaptingStorageMap storageMap = new AdaptingStorageMap();
        storageMap.configureAvailableBenches(List.of(new AdaptingBenchId("bench-1"), new AdaptingBenchId("bench-2")));
        for (int i = 0; i < values.length; i += 2) {
            storageMap.assignPharmacyToBench(values[i], new AdaptingBenchId(values[i + 1]));
        }
        return storageMap;
    }

    private static DspOrderItem adaptedLine(String lineId, String targetOrderId, String pharmacyId) {
        return new DspOrderItem(
                lineId,
                "product-" + lineId,
                1,
                pharmacyId,
                DspOrderLineType.ADAPTED,
                targetOrderId,
                1,
                0);
    }
}
