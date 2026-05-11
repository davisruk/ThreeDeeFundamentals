package online.davisfamily.warehouse.sim.machine.queue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class MachineWaitQueue {
    private final String id;
    private final int capacity;
    private final Deque<String> toteIds = new ArrayDeque<>();

    public MachineWaitQueue(String id, int capacity) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be null or blank");
        }
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity must be >= 0");
        }
        this.id = id;
        this.capacity = capacity;
    }

    public boolean canAccept() {
        return toteIds.size() < capacity;
    }

    public void enqueue(String toteId) {
        if (toteId == null || toteId.isBlank()) {
            throw new IllegalArgumentException("toteId must not be null or blank");
        }
        if (!canAccept()) {
            throw new IllegalStateException("Machine wait queue is full: " + id);
        }
        toteIds.addLast(toteId);
    }

    public String peek() {
        return toteIds.peekFirst();
    }

    public String dequeue() {
        return toteIds.pollFirst();
    }

    public int size() {
        return toteIds.size();
    }

    public int capacity() {
        return capacity;
    }

    public List<String> toteIds() {
        return List.copyOf(toteIds);
    }

    public MachineWaitQueueSnapshot snapshot() {
        return new MachineWaitQueueSnapshot(id, capacity, toteIds());
    }
}
