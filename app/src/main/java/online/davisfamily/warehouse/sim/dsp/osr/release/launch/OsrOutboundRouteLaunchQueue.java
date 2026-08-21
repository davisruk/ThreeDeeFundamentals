package online.davisfamily.warehouse.sim.dsp.osr.release.launch;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.machine.queue.MachineWaitQueue;

public final class OsrOutboundRouteLaunchQueue {
    private final String queueId;
    private final MachineWaitQueue waitQueue;
    private final Map<String, OsrOutboundRouteLaunchRequest> requestsByPhysicalToteId =
            new LinkedHashMap<>();

    public OsrOutboundRouteLaunchQueue(String queueId, int capacity) {
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
        return requestsByPhysicalToteId.containsKey(physicalToteId.value());
    }

    public void enqueue(OsrOutboundRouteLaunchRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        validateInvariants();

        PhysicalToteId physicalToteId = request.physicalToteId();
        if (requestsByPhysicalToteId.containsKey(physicalToteId.value())) {
            throw new IllegalArgumentException(
                    "Physical tote is already queued for outbound route launch " + queueId
                            + ": " + physicalToteId.value());
        }
        if (!waitQueue.canAccept()) {
            throw new IllegalStateException(
                    "OSR outbound route-launch queue is full: " + queueId);
        }

        waitQueue.enqueue(physicalToteId.value());
        requestsByPhysicalToteId.put(physicalToteId.value(), request);
        validateInvariants();
    }

    public Optional<OsrOutboundRouteLaunchRequest> peek() {
        validateInvariants();
        String physicalToteId = waitQueue.peek();
        if (physicalToteId == null) {
            return Optional.empty();
        }
        return Optional.of(requestForQueuedPhysicalTote(physicalToteId));
    }

    public Optional<OsrOutboundRouteLaunchRequest> dequeue() {
        validateInvariants();
        String physicalToteId = waitQueue.peek();
        if (physicalToteId == null) {
            return Optional.empty();
        }

        OsrOutboundRouteLaunchRequest request = requestForQueuedPhysicalTote(physicalToteId);
        String dequeuedPhysicalToteId = waitQueue.dequeue();
        if (!physicalToteId.equals(dequeuedPhysicalToteId)) {
            throw new IllegalStateException(
                    "Outbound route-launch FIFO changed while dequeuing: " + queueId);
        }
        OsrOutboundRouteLaunchRequest removed =
                requestsByPhysicalToteId.remove(physicalToteId);
        if (removed != request) {
            throw new IllegalStateException(
                    "Outbound route-launch request ownership changed while dequeuing: "
                            + physicalToteId);
        }
        validateInvariants();
        return Optional.of(request);
    }

    public OsrOutboundRouteLaunchQueueSnapshot snapshot() {
        validateInvariants();
        List<OsrOutboundRouteLaunchQueueSnapshot.Entry> entries = new ArrayList<>();
        for (String physicalToteId : waitQueue.toteIds()) {
            OsrOutboundRouteLaunchRequest request =
                    requestForQueuedPhysicalTote(physicalToteId);
            entries.add(new OsrOutboundRouteLaunchQueueSnapshot.Entry(
                    request.physicalToteId(),
                    request.destination()));
        }
        return new OsrOutboundRouteLaunchQueueSnapshot(
                queueId,
                waitQueue.capacity(),
                entries);
    }

    private OsrOutboundRouteLaunchRequest requestForQueuedPhysicalTote(String physicalToteId) {
        OsrOutboundRouteLaunchRequest request = requestsByPhysicalToteId.get(physicalToteId);
        if (request == null) {
            throw new IllegalStateException(
                    "Queued physical tote has no outbound route-launch request: "
                            + physicalToteId);
        }
        return request;
    }

    private void validateInvariants() {
        List<String> queuedPhysicalToteIds = waitQueue.toteIds();
        if (queuedPhysicalToteIds.size() != requestsByPhysicalToteId.size()) {
            throw new IllegalStateException(
                    "Outbound route-launch queue/request size mismatch: " + queueId);
        }
        for (String physicalToteId : queuedPhysicalToteIds) {
            requestForQueuedPhysicalTote(physicalToteId);
        }
        Set<String> queuedPhysicalToteIdSet = new HashSet<>(queuedPhysicalToteIds);
        for (String physicalToteId : requestsByPhysicalToteId.keySet()) {
            if (!queuedPhysicalToteIdSet.contains(physicalToteId)) {
                throw new IllegalStateException(
                        "Outbound route-launch request is not present in FIFO " + queueId
                                + ": " + physicalToteId);
            }
        }
    }
}
