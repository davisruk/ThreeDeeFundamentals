package online.davisfamily.warehouse.sim.dsp.adapting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;

class AdaptedLineStoreTest {

    @Test
    void shouldStageAndTakeSingleAdaptedLine() {
        AdaptedLineStore store = new AdaptedLineStore();
        DspOrderItem preparedLine = adaptedLine("line-1", "order-1", "0000310");
        PreparedLineKey key = PreparedLineKey.forPreparedLine(preparedLine);

        store.stage(preparedLine);

        assertTrue(store.contains(key));
        assertEquals(1, store.snapshot().stagedLineCount());
        assertTrue(store.snapshot().stagedLineKeys().contains(key));

        AdaptedLineRecord record = store.take(key).orElseThrow();

        assertEquals(key, record.key());
        assertEquals(preparedLine, record.line());
        assertFalse(store.contains(key));
        assertEquals(0, store.snapshot().stagedLineCount());
    }

    @Test
    void shouldTakeAllRequestedAdaptedLinesAndRemoveThemFromStore() {
        AdaptedLineStore store = new AdaptedLineStore();
        DspOrderItem line1 = adaptedLine("line-1", "order-1", "0000310");
        DspOrderItem line2 = adaptedLine("line-2", "order-2", "0000388");
        DspOrderItem line3 = adaptedLine("line-3", "order-3", "0000456");

        store.stage(line1);
        store.stage(line2);
        store.stage(line3);

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
        AdaptedLineStore store = new AdaptedLineStore();
        DspOrderItem presentLine = adaptedLine("line-1", "order-1", "0000310");
        DspOrderItem missingLine = adaptedLine("line-2", "order-2", "0000388");
        PreparedLineKey presentKey = PreparedLineKey.forPreparedLine(presentLine);
        PreparedLineKey missingKey = PreparedLineKey.forPreparedLine(missingLine);
        store.stage(presentLine);

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
                () -> AdaptedLineRecord.fromPreparedLine(manualLine));

        assertEquals("line must be ADAPTED", exception.getMessage());
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
