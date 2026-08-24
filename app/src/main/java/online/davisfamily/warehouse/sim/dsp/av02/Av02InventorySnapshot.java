package online.davisfamily.warehouse.sim.dsp.av02;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

public record Av02InventorySnapshot(
        int capacity,
        List<Av02AllocatedTote> waitingTotes,
        List<Av02AllocatedTote> departedTotes) {

    public Av02InventorySnapshot {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1");
        }
        waitingTotes = copyAndRejectNull(waitingTotes, "waitingTotes");
        departedTotes = copyAndRejectNull(departedTotes, "departedTotes");
        if (waitingTotes.size() > capacity) {
            throw new IllegalArgumentException("waitingTotes must not exceed capacity");
        }

        Set<PhysicalToteId> physicalToteIds = new LinkedHashSet<>();
        Set<OrderSheetKey> orderSheetKeys = new LinkedHashSet<>();
        for (Av02AllocatedTote tote : waitingTotes) {
            requireUniqueIdentity(physicalToteIds, orderSheetKeys, tote);
        }
        for (Av02AllocatedTote tote : departedTotes) {
            requireUniqueIdentity(physicalToteIds, orderSheetKeys, tote);
        }
    }

    public int occupancy() {
        return waitingTotes.size();
    }

    public int remainingCapacity() {
        return capacity - occupancy();
    }

    public boolean full() {
        return occupancy() == capacity;
    }

    public Optional<Av02AllocatedTote> findWaiting(PhysicalToteId physicalToteId) {
        requirePhysicalToteId(physicalToteId);
        return waitingTotes.stream()
                .filter(tote -> tote.physicalToteId().equals(physicalToteId))
                .findFirst();
    }

    public Optional<Av02AllocatedTote> findWaiting(OrderSheetKey orderSheetKey) {
        if (orderSheetKey == null) {
            throw new IllegalArgumentException("orderSheetKey must not be null");
        }
        return waitingTotes.stream()
                .filter(tote -> tote.orderSheetKey().equals(orderSheetKey))
                .findFirst();
    }

    private static List<Av02AllocatedTote> copyAndRejectNull(
            List<Av02AllocatedTote> totes,
            String fieldName) {
        if (totes == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        for (Av02AllocatedTote tote : totes) {
            if (tote == null) {
                throw new IllegalArgumentException(fieldName + " must not contain null");
            }
        }
        return List.copyOf(totes);
    }

    private static void requireUniqueIdentity(
            Set<PhysicalToteId> physicalToteIds,
            Set<OrderSheetKey> orderSheetKeys,
            Av02AllocatedTote tote) {
        if (!physicalToteIds.add(tote.physicalToteId())) {
            throw new IllegalArgumentException(
                    "Duplicate AV02 physical tote ID: " + tote.physicalToteId().value());
        }
        if (!orderSheetKeys.add(tote.orderSheetKey())) {
            throw new IllegalArgumentException(
                    "Duplicate AV02 order sheet: " + tote.orderSheetKey());
        }
    }

    private static void requirePhysicalToteId(PhysicalToteId physicalToteId) {
        if (physicalToteId == null) {
            throw new IllegalArgumentException("physicalToteId must not be null");
        }
    }
}
