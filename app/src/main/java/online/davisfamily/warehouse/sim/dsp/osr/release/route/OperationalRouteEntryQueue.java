package online.davisfamily.warehouse.sim.dsp.osr.release.route;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseRequest;
import online.davisfamily.warehouse.sim.machine.queue.MachineWaitQueue;

public final class OperationalRouteEntryQueue {
    private final OperationalRouteTargetDefinition definition;
    private final MachineWaitQueue waitQueue;
    private final Map<String, OsrProcessingReleaseRequest> requestsByPhysicalToteId =
            new LinkedHashMap<>();

    public OperationalRouteEntryQueue(OperationalRouteTargetDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("definition must not be null");
        }
        this.definition = definition;
        this.waitQueue = new MachineWaitQueue(
                definition.targetId(), definition.waitingCapacity());
    }

    public OperationalRouteTargetDefinition definition() {
        return definition;
    }

    public boolean canAccept() {
        return waitQueue.canAccept();
    }

    public boolean contains(PhysicalToteId physicalToteId) {
        if (physicalToteId == null) {
            throw new IllegalArgumentException("physicalToteId must not be null");
        }
        return requestsByPhysicalToteId.containsKey(physicalToteId.value());
    }

    public void enqueue(OsrProcessingReleaseRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        PhysicalToteId physicalToteId = request.manifest().physicalToteId();
        if (contains(physicalToteId)) {
            throw new IllegalArgumentException(
                    "Physical tote is already queued at target " + definition.targetId()
                            + ": " + physicalToteId.value());
        }
        if (!canAccept()) {
            throw new IllegalStateException(
                    "Operational route-entry queue is full: " + definition.targetId());
        }

        waitQueue.enqueue(physicalToteId.value());
        requestsByPhysicalToteId.put(physicalToteId.value(), request);
    }

    public Optional<OsrProcessingReleaseRequest> peek() {
        String physicalToteId = waitQueue.peek();
        if (physicalToteId == null) {
            return Optional.empty();
        }
        return Optional.of(requestForQueuedPhysicalTote(physicalToteId));
    }

    public Optional<OsrProcessingReleaseRequest> dequeue() {
        String physicalToteId = waitQueue.dequeue();
        if (physicalToteId == null) {
            return Optional.empty();
        }
        OsrProcessingReleaseRequest request = requestsByPhysicalToteId.remove(physicalToteId);
        if (request == null) {
            throw new IllegalStateException(
                    "Queued physical tote has no release request: " + physicalToteId);
        }
        return Optional.of(request);
    }

    public OperationalRouteEntryQueueSnapshot snapshot() {
        List<PhysicalToteId> physicalToteIds = new ArrayList<>();
        for (String physicalToteId : waitQueue.toteIds()) {
            physicalToteIds.add(requestForQueuedPhysicalTote(physicalToteId)
                    .manifest()
                    .physicalToteId());
        }
        return new OperationalRouteEntryQueueSnapshot(
                definition.stationType(),
                definition.targetId(),
                waitQueue.capacity(),
                physicalToteIds);
    }

    private OsrProcessingReleaseRequest requestForQueuedPhysicalTote(String physicalToteId) {
        OsrProcessingReleaseRequest request = requestsByPhysicalToteId.get(physicalToteId);
        if (request == null) {
            throw new IllegalStateException(
                    "Queued physical tote has no release request: " + physicalToteId);
        }
        return request;
    }
}
