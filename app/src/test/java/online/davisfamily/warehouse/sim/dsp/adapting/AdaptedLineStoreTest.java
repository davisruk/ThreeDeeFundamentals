package online.davisfamily.warehouse.sim.dsp.adapting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

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
        assertEquals(new AdaptingBenchId("bench-1"), record.location().benchId());
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

        assertEquals(new AdaptingStorageLocation("0000310", new AdaptingBenchId("bench-2"), 0, 0, 0), line1.location());
        assertEquals(new AdaptingStorageLocation("0000310", new AdaptingBenchId("bench-2"), 0, 1, 0), line3.location());
        assertEquals(new AdaptingStorageLocation("0000310", new AdaptingBenchId("bench-2"), 1, 0, 0), line5.location());
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
