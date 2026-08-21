package online.davisfamily.warehouse.sim.dsp.transport;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.machine.queue.MachineWaitQueue;

public final class OsrOutboundTransportQueue {
    private final String queueId;
    private final MachineWaitQueue waitQueue;
    private final Map<String, RoutedPhysicalTote> totesByPhysicalToteId =
            new LinkedHashMap<>();

    public OsrOutboundTransportQueue(String queueId, int capacity) {
        if (queueId == null || queueId.isBlank()) {
            throw new IllegalArgumentException("queueId must not be blank");
        }
        this.queueId = queueId.trim();
        this.waitQueue = new MachineWaitQueue(this.queueId, capacity);
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

        PhysicalToteId physicalToteId = routedTote.physicalToteId();
        if (totesByPhysicalToteId.containsKey(physicalToteId.value())) {
            throw new IllegalArgumentException(
                    "Physical tote is already queued for outbound transport " + queueId
                            + ": " + physicalToteId.value());
        }
        if (!waitQueue.canAccept()) {
            throw new IllegalStateException(
                    "OSR outbound transport queue is full: " + queueId);
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
                    "Outbound transport FIFO changed while dequeuing: " + queueId);
        }
        RoutedPhysicalTote removed = totesByPhysicalToteId.remove(physicalToteId);
        if (removed != routedTote) {
            throw new IllegalStateException(
                    "Outbound transport tote ownership changed while dequeuing: "
                            + physicalToteId);
        }
        validateInvariants();
        return Optional.of(routedTote);
    }

    public OsrOutboundTransportQueueSnapshot snapshot() {
        validateInvariants();
        List<OsrOutboundTransportQueueSnapshot.Entry> entries = new ArrayList<>();
        for (String physicalToteId : waitQueue.toteIds()) {
            RoutedPhysicalTote routedTote = toteForQueuedPhysicalTote(physicalToteId);
            entries.add(new OsrOutboundTransportQueueSnapshot.Entry(
                    routedTote.physicalToteId(),
                    routedTote.destination()));
        }
        return new OsrOutboundTransportQueueSnapshot(
                queueId,
                waitQueue.capacity(),
                entries);
    }

    private RoutedPhysicalTote toteForQueuedPhysicalTote(String physicalToteId) {
        RoutedPhysicalTote routedTote = totesByPhysicalToteId.get(physicalToteId);
        if (routedTote == null) {
            throw new IllegalStateException(
                    "Queued physical tote has no outbound transport payload: "
                            + physicalToteId);
        }
        return routedTote;
    }

    private void validateInvariants() {
        List<String> queuedPhysicalToteIds = waitQueue.toteIds();
        if (queuedPhysicalToteIds.size() != totesByPhysicalToteId.size()) {
            throw new IllegalStateException(
                    "Outbound transport queue/payload size mismatch: " + queueId);
        }
        for (String physicalToteId : queuedPhysicalToteIds) {
            toteForQueuedPhysicalTote(physicalToteId);
        }
        Set<String> queuedPhysicalToteIdSet = new HashSet<>(queuedPhysicalToteIds);
        for (String physicalToteId : totesByPhysicalToteId.keySet()) {
            if (!queuedPhysicalToteIdSet.contains(physicalToteId)) {
                throw new IllegalStateException(
                        "Outbound transport payload is not present in FIFO " + queueId
                                + ": " + physicalToteId);
            }
        }
    }
}
