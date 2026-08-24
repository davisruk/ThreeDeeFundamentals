package online.davisfamily.warehouse.sim.dsp.av02;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

public final class Av02PhysicalToteInventory {
    private final Av02AllocationConfig config;
    private final Map<PhysicalToteId, Av02AllocatedTote> waitingTotes = new LinkedHashMap<>();
    private final Set<PhysicalToteId> seenPhysicalToteIds = new LinkedHashSet<>();
    private final Set<OrderSheetKey> seenOrderSheetKeys = new LinkedHashSet<>();
    private final List<Av02AllocatedTote> departedTotes = new ArrayList<>();

    public Av02PhysicalToteInventory(Av02AllocationConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        this.config = config;
    }

    public void store(Av02AllocatedTote tote) {
        if (tote == null) {
            throw new IllegalArgumentException("tote must not be null");
        }
        if (seenPhysicalToteIds.contains(tote.physicalToteId())) {
            throw new IllegalStateException(
                    "Physical tote has already entered AV02 inventory: "
                            + tote.physicalToteId().value());
        }
        if (seenOrderSheetKeys.contains(tote.orderSheetKey())) {
            throw new IllegalStateException(
                    "Order sheet has already entered AV02 inventory: " + tote.orderSheetKey());
        }
        if (full()) {
            throw new IllegalStateException(
                    "AV02 capacity exceeded: capacity=" + config.capacity()
                            + ", occupancy=" + occupancy());
        }

        waitingTotes.put(tote.physicalToteId(), tote);
        seenPhysicalToteIds.add(tote.physicalToteId());
        seenOrderSheetKeys.add(tote.orderSheetKey());
    }

    public Av02AllocatedTote recordDeparture(PhysicalToteId physicalToteId) {
        requirePhysicalToteId(physicalToteId);
        Av02AllocatedTote requested = waitingTotes.get(physicalToteId);
        if (requested == null) {
            throw new IllegalStateException(
                    "Physical tote is not currently waiting at AV02: " + physicalToteId.value());
        }
        Av02AllocatedTote head = head().orElseThrow();
        if (!head.physicalToteId().equals(physicalToteId)) {
            throw new IllegalStateException(
                    "Physical tote is not at the head of AV02 inventory: " + physicalToteId.value());
        }

        waitingTotes.remove(physicalToteId);
        departedTotes.add(requested);
        return requested;
    }

    public int capacity() {
        return config.capacity();
    }

    public int occupancy() {
        return waitingTotes.size();
    }

    public int remainingCapacity() {
        return capacity() - occupancy();
    }

    public boolean full() {
        return occupancy() == capacity();
    }

    public Optional<Av02AllocatedTote> head() {
        return waitingTotes.values().stream().findFirst();
    }

    public Optional<Av02AllocatedTote> findWaiting(PhysicalToteId physicalToteId) {
        requirePhysicalToteId(physicalToteId);
        return Optional.ofNullable(waitingTotes.get(physicalToteId));
    }

    public Optional<Av02AllocatedTote> findWaiting(OrderSheetKey orderSheetKey) {
        if (orderSheetKey == null) {
            throw new IllegalArgumentException("orderSheetKey must not be null");
        }
        return waitingTotes.values().stream()
                .filter(tote -> tote.orderSheetKey().equals(orderSheetKey))
                .findFirst();
    }

    public Av02InventorySnapshot snapshot() {
        return new Av02InventorySnapshot(
                config.capacity(),
                List.copyOf(waitingTotes.values()),
                departedTotes);
    }

    private static void requirePhysicalToteId(PhysicalToteId physicalToteId) {
        if (physicalToteId == null) {
            throw new IllegalArgumentException("physicalToteId must not be null");
        }
    }
}
