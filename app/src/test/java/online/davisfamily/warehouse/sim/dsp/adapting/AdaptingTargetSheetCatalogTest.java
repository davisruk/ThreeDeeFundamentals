package online.davisfamily.warehouse.sim.dsp.adapting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;

class AdaptingTargetSheetCatalogTest {

    @Test
    void shouldKeepExactSheetPerLineAndDefensivelyCopyCallerMap() {
        PreparedLineKey first = new PreparedLineKey("target", "line-a");
        PreparedLineKey second = new PreparedLineKey("target", "line-b");
        Map<PreparedLineKey, OrderSheetKey> source = new LinkedHashMap<>();
        source.put(first, new OrderSheetKey("target", 1));
        source.put(second, new OrderSheetKey("target", 2));

        AdaptingTargetSheetCatalog catalog = new AdaptingTargetSheetCatalog(source);
        source.put(first, new OrderSheetKey("target", 2));
        source.clear();

        assertEquals(new OrderSheetKey("target", 1), catalog.requireTargetSheet(first));
        assertEquals(new OrderSheetKey("target", 2), catalog.requireTargetSheet(second));
    }

    @Test
    void shouldRejectMissingKey() {
        PreparedLineKey missing = new PreparedLineKey("target", "missing");
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> new AdaptingTargetSheetCatalog(Map.of()).requireTargetSheet(missing));
        assertTrue(exception.getMessage().contains(missing.toString()));
    }

    @Test
    void shouldRejectWrongTargetOrderAndNullEntries() {
        PreparedLineKey key = new PreparedLineKey("target", "line");
        assertThrows(IllegalArgumentException.class,
                () -> new AdaptingTargetSheetCatalog(Map.of(key, new OrderSheetKey("other", 1))));
        assertThrows(IllegalArgumentException.class, () -> new AdaptingTargetSheetCatalog(null));

        Map<PreparedLineKey, OrderSheetKey> nullKey = new LinkedHashMap<>();
        nullKey.put(null, new OrderSheetKey("target", 1));
        assertThrows(IllegalArgumentException.class, () -> new AdaptingTargetSheetCatalog(nullKey));

        Map<PreparedLineKey, OrderSheetKey> nullValue = new LinkedHashMap<>();
        nullValue.put(key, null);
        assertThrows(IllegalArgumentException.class, () -> new AdaptingTargetSheetCatalog(nullValue));
    }
}
