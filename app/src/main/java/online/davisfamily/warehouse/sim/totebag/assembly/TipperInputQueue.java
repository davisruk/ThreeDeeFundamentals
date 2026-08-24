package online.davisfamily.warehouse.sim.totebag.assembly;

import java.util.LinkedHashMap;
import java.util.Map;

import online.davisfamily.warehouse.sim.machine.queue.MachineWaitQueue;
import online.davisfamily.warehouse.sim.machine.queue.MachineWaitQueueSnapshot;
import online.davisfamily.warehouse.sim.tote.Tote.ToteMotionState;

public class TipperInputQueue {
    private final MachineWaitQueue queue;
    private final Map<String, TipperTotePayload> payloadsByToteId = new LinkedHashMap<>();

    public TipperInputQueue(String id, int capacity) {
        this.queue = new MachineWaitQueue(id, capacity);
    }

    public boolean canAccept() {
        return queue.canAccept();
    }

    public boolean contains(String toteId) {
        if (toteId == null || toteId.isBlank()) {
            throw new IllegalArgumentException("toteId must not be null or blank");
        }
        return payloadsByToteId.containsKey(toteId);
    }

    public void enqueue(TipperTotePayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("payload must not be null");
        }
        String toteId = payload.getTote().getId();
        if (contains(toteId)) {
            throw new IllegalArgumentException(
                    "Tote is already queued at tipper input: " + toteId);
        }
        if (!queue.canAccept()) {
            throw new IllegalStateException("Tipper input queue is full");
        }

        payload.getTote().setInteractionMode(ToteMotionState.HELD);
        queue.enqueue(toteId);
        payloadsByToteId.put(toteId, payload);
    }

    public TipperTotePayload peekPayload() {
        String toteId = queue.peek();
        if (toteId == null) {
            return null;
        }
        return payloadsByToteId.get(toteId);
    }

    public TipperTotePayload dequeuePayload() {
        String toteId = queue.dequeue();
        if (toteId == null) {
            return null;
        }
        return payloadsByToteId.remove(toteId);
    }

    public MachineWaitQueueSnapshot snapshot() {
        return queue.snapshot();
    }
}
