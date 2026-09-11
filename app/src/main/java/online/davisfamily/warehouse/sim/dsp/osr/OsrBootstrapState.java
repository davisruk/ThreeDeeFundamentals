package online.davisfamily.warehouse.sim.dsp.osr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

public record OsrBootstrapState(
        OsrPhysicalInventory inventory,
        Set<OrderSheetKey> authorizedEmptyOrderSheetKeys,
        List<PhysicalToteId> startupOverflowPhysicalToteIds) {

    public OsrBootstrapState(
            OsrPhysicalInventory inventory,
            Set<OrderSheetKey> authorizedEmptyOrderSheetKeys) {
        this(inventory, authorizedEmptyOrderSheetKeys, List.of());
    }

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

        if (startupOverflowPhysicalToteIds == null) {
            throw new IllegalArgumentException("startupOverflowPhysicalToteIds must not be null");
        }
        List<PhysicalToteId> copiedOverflowIds = new ArrayList<>();
        Set<PhysicalToteId> seenOverflowIds = new LinkedHashSet<>();
        for (PhysicalToteId physicalToteId : startupOverflowPhysicalToteIds) {
            if (physicalToteId == null) {
                throw new IllegalArgumentException(
                        "startupOverflowPhysicalToteIds must not contain null");
            }
            if (!seenOverflowIds.add(physicalToteId)) {
                throw new IllegalArgumentException(
                        "Duplicate startup overflow physical tote ID: "
                                + physicalToteId.value());
            }
            copiedOverflowIds.add(physicalToteId);
        }
        startupOverflowPhysicalToteIds = List.copyOf(copiedOverflowIds);
    }

    public OsrInventorySnapshot inventorySnapshot() {
        return inventory.snapshot();
    }
}
