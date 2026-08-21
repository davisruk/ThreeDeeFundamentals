package online.davisfamily.warehouse.sim.dsp.transport.routing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;

public final class WarehouseTransportInFlightRegistry {
    private final int capacity;
    private final Map<String, ActiveEntry> entriesByPhysicalToteId = new LinkedHashMap<>();

    public WarehouseTransportInFlightRegistry(int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity must be >= 0");
        }
        this.capacity = capacity;
    }

    public boolean canAccept() {
        return entriesByPhysicalToteId.size() < capacity;
    }

    public boolean contains(PhysicalToteId physicalToteId) {
        return entriesByPhysicalToteId.containsKey(requirePhysicalToteId(physicalToteId));
    }

    public void register(RoutedPhysicalTote routedTote) {
        requireRoutedTote(routedTote);
        String physicalToteId = routedTote.physicalToteId().value();
        if (entriesByPhysicalToteId.containsKey(physicalToteId)) {
            throw new IllegalArgumentException(
                    "Physical tote is already in flight: " + physicalToteId);
        }
        if (!canAccept()) {
            throw new IllegalStateException("Warehouse transport in-flight registry is full");
        }
        entriesByPhysicalToteId.put(physicalToteId, new ActiveEntry(routedTote));
    }

    public Optional<RoutedPhysicalTote> find(PhysicalToteId physicalToteId) {
        ActiveEntry entry = entriesByPhysicalToteId.get(requirePhysicalToteId(physicalToteId));
        return entry == null ? Optional.empty() : Optional.of(entry.routedTote);
    }

    public void markArrivalPending(RoutedPhysicalTote routedTote) {
        activeEntryForExactPayload(routedTote).arrivalPending = true;
    }

    public RoutedPhysicalTote completeArrival(RoutedPhysicalTote routedTote) {
        ActiveEntry entry = activeEntryForExactPayload(routedTote);
        String physicalToteId = routedTote.physicalToteId().value();
        ActiveEntry removed = entriesByPhysicalToteId.remove(physicalToteId);
        if (removed != entry) {
            throw new IllegalStateException(
                    "In-flight tote ownership changed while completing arrival: "
                            + physicalToteId);
        }
        return entry.routedTote;
    }

    public WarehouseTransportInFlightSnapshot snapshot() {
        List<WarehouseTransportInFlightSnapshot.Entry> snapshotEntries = new ArrayList<>();
        for (ActiveEntry entry : entriesByPhysicalToteId.values()) {
            RoutedPhysicalTote routedTote = entry.routedTote;
            RouteSegment currentSegment =
                    routedTote.tote().getRouteFollower().getCurrentSegment();
            if (currentSegment == null) {
                throw new IllegalStateException(
                        "In-flight tote has no current route segment: "
                                + routedTote.physicalToteId().value());
            }
            snapshotEntries.add(new WarehouseTransportInFlightSnapshot.Entry(
                    routedTote.physicalToteId(),
                    routedTote.destination(),
                    currentSegment.getLabel(),
                    routedTote.tote().getInteractionMode(),
                    entry.arrivalPending));
        }
        return new WarehouseTransportInFlightSnapshot(capacity, snapshotEntries);
    }

    private ActiveEntry activeEntryForExactPayload(RoutedPhysicalTote routedTote) {
        requireRoutedTote(routedTote);
        String physicalToteId = routedTote.physicalToteId().value();
        ActiveEntry entry = entriesByPhysicalToteId.get(physicalToteId);
        if (entry == null) {
            throw new IllegalArgumentException(
                    "Physical tote is not in flight: " + physicalToteId);
        }
        if (entry.routedTote != routedTote) {
            throw new IllegalArgumentException(
                    "Operation requires the exact in-flight routed tote: " + physicalToteId);
        }
        return entry;
    }

    private static String requirePhysicalToteId(PhysicalToteId physicalToteId) {
        if (physicalToteId == null) {
            throw new IllegalArgumentException("physicalToteId must not be null");
        }
        return physicalToteId.value();
    }

    private static void requireRoutedTote(RoutedPhysicalTote routedTote) {
        if (routedTote == null) {
            throw new IllegalArgumentException("routedTote must not be null");
        }
    }

    private static final class ActiveEntry {
        private final RoutedPhysicalTote routedTote;
        private boolean arrivalPending;

        private ActiveEntry(RoutedPhysicalTote routedTote) {
            this.routedTote = routedTote;
        }
    }
}
