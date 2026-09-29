package online.davisfamily.warehouse.sim.dsp.adapting;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;

/** Immutable exact fulfilment-sheet lookup for executable prepared lines. */
public final class AdaptingTargetSheetCatalog {
    private final Map<PreparedLineKey, OrderSheetKey> targetSheetsByPreparedLine;

    public AdaptingTargetSheetCatalog(Map<PreparedLineKey, OrderSheetKey> targetSheetsByPreparedLine) {
        if (targetSheetsByPreparedLine == null) {
            throw new IllegalArgumentException("targetSheetsByPreparedLine must not be null");
        }
        Map<PreparedLineKey, OrderSheetKey> copy = new LinkedHashMap<>();
        for (Map.Entry<PreparedLineKey, OrderSheetKey> entry : targetSheetsByPreparedLine.entrySet()) {
            PreparedLineKey key = entry.getKey();
            OrderSheetKey targetSheet = entry.getValue();
            if (key == null || targetSheet == null) {
                throw new IllegalArgumentException("Prepared-line target-sheet entries must not be null");
            }
            if (!key.targetOrderId().equals(targetSheet.orderId())) {
                throw new IllegalArgumentException("Target sheet order ID does not match prepared line " + key);
            }
            copy.put(key, targetSheet);
        }
        this.targetSheetsByPreparedLine = Collections.unmodifiableMap(copy);
    }

    public OrderSheetKey requireTargetSheet(PreparedLineKey key) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        OrderSheetKey targetSheet = targetSheetsByPreparedLine.get(key);
        if (targetSheet == null) {
            throw new IllegalStateException("No target sheet for prepared line " + key);
        }
        return targetSheet;
    }
}
