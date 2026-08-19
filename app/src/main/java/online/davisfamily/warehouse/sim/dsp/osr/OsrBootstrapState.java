package online.davisfamily.warehouse.sim.dsp.osr;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;

public record OsrBootstrapState(
        OsrPhysicalInventory inventory,
        Set<OrderSheetKey> authorizedEmptyOrderSheetKeys) {

    public OsrBootstrapState {
        if (inventory == null) {
            throw new IllegalArgumentException("inventory must not be null");
        }
        if (authorizedEmptyOrderSheetKeys == null) {
            throw new IllegalArgumentException("authorizedEmptyOrderSheetKeys must not be null");
        }

        Set<OrderSheetKey> copiedKeys = new LinkedHashSet<>();
        for (OrderSheetKey orderSheetKey : authorizedEmptyOrderSheetKeys) {
            if (orderSheetKey == null) {
                throw new IllegalArgumentException(
                        "authorizedEmptyOrderSheetKeys must not contain null");
            }
            copiedKeys.add(orderSheetKey);
        }
        authorizedEmptyOrderSheetKeys = Collections.unmodifiableSet(copiedKeys);
    }

    public OsrInventorySnapshot inventorySnapshot() {
        return inventory.snapshot();
    }
}
