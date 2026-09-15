package online.davisfamily.warehouse.sim.dsp.av02;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

public final class Av02InventorySnapshot {
    private final int capacity;
    private final List<Av02AllocatedTote> waitingTotes;
    private final List<Av02AllocatedTote> departedTotes;
    private final Map<PhysicalToteId, Av02AllocatedTote> waitingTotesByPhysicalToteId;
    private final Map<OrderSheetKey, Av02AllocatedTote> waitingTotesByOrderSheetKey;
    private final Map<PhysicalToteId, Av02AllocatedTote> totesByPhysicalToteId;
    private final Map<OrderSheetKey, Av02AllocatedTote> totesByOrderSheetKey;

    public Av02InventorySnapshot(
            int capacity,
            List<Av02AllocatedTote> waitingTotes,
            List<Av02AllocatedTote> departedTotes) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1");
        }
        this.capacity = capacity;
        this.waitingTotes = copyAndRejectNull(waitingTotes, "waitingTotes");
        this.departedTotes = copyAndRejectNull(departedTotes, "departedTotes");
        if (this.waitingTotes.size() > capacity) {
            throw new IllegalArgumentException("waitingTotes must not exceed capacity");
        }

        Map<PhysicalToteId, Av02AllocatedTote> waitingByPhysicalToteId = new LinkedHashMap<>();
        Map<OrderSheetKey, Av02AllocatedTote> waitingByOrderSheetKey = new LinkedHashMap<>();
        Map<PhysicalToteId, Av02AllocatedTote> allByPhysicalToteId = new LinkedHashMap<>();
        Map<OrderSheetKey, Av02AllocatedTote> allByOrderSheetKey = new LinkedHashMap<>();
        for (Av02AllocatedTote tote : this.waitingTotes) {
            requireUniqueIdentity(allByPhysicalToteId, allByOrderSheetKey, tote);
            waitingByPhysicalToteId.put(tote.physicalToteId(), tote);
            waitingByOrderSheetKey.put(tote.orderSheetKey(), tote);
        }
        for (Av02AllocatedTote tote : this.departedTotes) {
            requireUniqueIdentity(allByPhysicalToteId, allByOrderSheetKey, tote);
        }

        this.waitingTotesByPhysicalToteId = immutableMap(waitingByPhysicalToteId);
        this.waitingTotesByOrderSheetKey = immutableMap(waitingByOrderSheetKey);
        this.totesByPhysicalToteId = immutableMap(allByPhysicalToteId);
        this.totesByOrderSheetKey = immutableMap(allByOrderSheetKey);
    }

    public int capacity() {
        return capacity;
    }

    public List<Av02AllocatedTote> waitingTotes() {
        return waitingTotes;
    }

    public List<Av02AllocatedTote> departedTotes() {
        return departedTotes;
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
        return Optional.ofNullable(waitingTotesByPhysicalToteId.get(physicalToteId));
    }

    public Optional<Av02AllocatedTote> findWaiting(OrderSheetKey orderSheetKey) {
        if (orderSheetKey == null) {
            throw new IllegalArgumentException("orderSheetKey must not be null");
        }
        return Optional.ofNullable(waitingTotesByOrderSheetKey.get(orderSheetKey));
    }

    public Optional<Av02AllocatedTote> findTote(PhysicalToteId physicalToteId) {
        requirePhysicalToteId(physicalToteId);
        return Optional.ofNullable(totesByPhysicalToteId.get(physicalToteId));
    }

    public Optional<Av02AllocatedTote> findTote(OrderSheetKey orderSheetKey) {
        if (orderSheetKey == null) {
            throw new IllegalArgumentException("orderSheetKey must not be null");
        }
        return Optional.ofNullable(totesByOrderSheetKey.get(orderSheetKey));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Av02InventorySnapshot that)) {
            return false;
        }
        return capacity == that.capacity
                && waitingTotes.equals(that.waitingTotes)
                && departedTotes.equals(that.departedTotes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(capacity, waitingTotes, departedTotes);
    }

    @Override
    public String toString() {
        return "Av02InventorySnapshot[capacity=" + capacity
                + ", waitingTotes=" + waitingTotes
                + ", departedTotes=" + departedTotes + "]";
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
            Map<PhysicalToteId, Av02AllocatedTote> physicalTotesById,
            Map<OrderSheetKey, Av02AllocatedTote> totesByOrderSheet,
            Av02AllocatedTote tote) {
        if (physicalTotesById.putIfAbsent(tote.physicalToteId(), tote) != null) {
            throw new IllegalArgumentException(
                    "Duplicate AV02 physical tote ID: " + tote.physicalToteId().value());
        }
        if (totesByOrderSheet.putIfAbsent(tote.orderSheetKey(), tote) != null) {
            throw new IllegalArgumentException(
                    "Duplicate AV02 order sheet: " + tote.orderSheetKey());
        }
    }

    private static <K> Map<K, Av02AllocatedTote> immutableMap(
            Map<K, Av02AllocatedTote> values) {
        return Collections.unmodifiableMap(values);
    }

    private static void requirePhysicalToteId(PhysicalToteId physicalToteId) {
        if (physicalToteId == null) {
            throw new IllegalArgumentException("physicalToteId must not be null");
        }
    }
}
