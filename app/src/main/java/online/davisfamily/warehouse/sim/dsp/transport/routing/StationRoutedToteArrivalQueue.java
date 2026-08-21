package online.davisfamily.warehouse.sim.dsp.transport.routing;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;
import online.davisfamily.warehouse.sim.machine.queue.MachineWaitQueue;

public final class StationRoutedToteArrivalQueue {
    private final OperationalRouteDestination destination;
    private final MachineWaitQueue waitQueue;
    private final Map<String, RoutedPhysicalTote> totesByPhysicalToteId =
            new LinkedHashMap<>();

    public StationRoutedToteArrivalQueue(
            OperationalRouteDestination destination,
            int capacity) {
        if (destination == null) {
            throw new IllegalArgumentException("destination must not be null");
        }
        this.destination = destination;
        this.waitQueue = new MachineWaitQueue(destination.targetId(), capacity);
    }

    public OperationalRouteDestination destination() {
        return destination;
    }

    public boolean canAccept() {
        validateInvariants();
        return waitQueue.canAccept();
    }

    public boolean contains(PhysicalToteId physicalToteId) {
        if (physicalToteId == null) {
            throw new IllegalArgumentException("physicalToteId must not be null");
        }
        validateInvariants();
        return totesByPhysicalToteId.containsKey(physicalToteId.value());
    }

    public void enqueue(RoutedPhysicalTote routedTote) {
        if (routedTote == null) {
            throw new IllegalArgumentException("routedTote must not be null");
        }
        validateInvariants();
        if (!destination.equals(routedTote.destination())) {
            throw new IllegalArgumentException(
                    "Routed tote destination must match station arrival queue "
                            + destination.targetId());
        }

        PhysicalToteId physicalToteId = routedTote.physicalToteId();
        if (totesByPhysicalToteId.containsKey(physicalToteId.value())) {
            throw new IllegalArgumentException(
                    "Physical tote is already queued at station arrival "
                            + destination.targetId() + ": " + physicalToteId.value());
        }
        if (!waitQueue.canAccept()) {
            throw new IllegalStateException(
                    "Station routed-tote arrival queue is full: "
                            + destination.targetId());
        }

        waitQueue.enqueue(physicalToteId.value());
        totesByPhysicalToteId.put(physicalToteId.value(), routedTote);
        validateInvariants();
    }

    public Optional<RoutedPhysicalTote> peek() {
        validateInvariants();
        String physicalToteId = waitQueue.peek();
        if (physicalToteId == null) {
            return Optional.empty();
        }
        return Optional.of(toteForQueuedPhysicalTote(physicalToteId));
    }

    public Optional<RoutedPhysicalTote> dequeue() {
        validateInvariants();
        String physicalToteId = waitQueue.peek();
        if (physicalToteId == null) {
            return Optional.empty();
        }

        RoutedPhysicalTote routedTote = toteForQueuedPhysicalTote(physicalToteId);
        String dequeuedPhysicalToteId = waitQueue.dequeue();
        if (!physicalToteId.equals(dequeuedPhysicalToteId)) {
            throw new IllegalStateException(
                    "Station arrival FIFO changed while dequeuing: "
                            + destination.targetId());
        }
        RoutedPhysicalTote removed = totesByPhysicalToteId.remove(physicalToteId);
        if (removed != routedTote) {
            throw new IllegalStateException(
                    "Station arrival tote ownership changed while dequeuing: "
                            + physicalToteId);
        }
        validateInvariants();
        return Optional.of(routedTote);
    }

    public StationRoutedToteArrivalQueueSnapshot snapshot() {
        validateInvariants();
        List<StationRoutedToteArrivalQueueSnapshot.Entry> entries = new ArrayList<>();
        for (String physicalToteId : waitQueue.toteIds()) {
            RoutedPhysicalTote routedTote = toteForQueuedPhysicalTote(physicalToteId);
            entries.add(new StationRoutedToteArrivalQueueSnapshot.Entry(
                    routedTote.physicalToteId(),
                    routedTote.destination()));
        }
        return new StationRoutedToteArrivalQueueSnapshot(
                destination,
                waitQueue.capacity(),
                entries);
    }

    private RoutedPhysicalTote toteForQueuedPhysicalTote(String physicalToteId) {
        RoutedPhysicalTote routedTote = totesByPhysicalToteId.get(physicalToteId);
        if (routedTote == null) {
            throw new IllegalStateException(
                    "Queued physical tote has no station arrival payload: "
                            + physicalToteId);
        }
        return routedTote;
    }

    private void validateInvariants() {
        List<String> queuedPhysicalToteIds = waitQueue.toteIds();
        if (queuedPhysicalToteIds.size() != totesByPhysicalToteId.size()) {
            throw new IllegalStateException(
                    "Station arrival queue/payload size mismatch: "
                            + destination.targetId());
        }
        for (String physicalToteId : queuedPhysicalToteIds) {
            RoutedPhysicalTote routedTote = toteForQueuedPhysicalTote(physicalToteId);
            if (!destination.equals(routedTote.destination())) {
                throw new IllegalStateException(
                        "Queued tote destination changed at station arrival: "
                                + physicalToteId);
            }
        }
        Set<String> queuedPhysicalToteIdSet = new HashSet<>(queuedPhysicalToteIds);
        for (String physicalToteId : totesByPhysicalToteId.keySet()) {
            if (!queuedPhysicalToteIdSet.contains(physicalToteId)) {
                throw new IllegalStateException(
                        "Station arrival payload is not present in FIFO "
                                + destination.targetId() + ": " + physicalToteId);
            }
        }
    }
}
