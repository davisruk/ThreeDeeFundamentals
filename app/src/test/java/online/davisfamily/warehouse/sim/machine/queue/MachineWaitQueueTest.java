package online.davisfamily.warehouse.sim.machine.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class MachineWaitQueueTest {

    @Test
    void shouldAcceptTotesUntilCapacityIsReached() {
        MachineWaitQueue queue = new MachineWaitQueue("tipper-input", 2);

        assertTrue(queue.canAccept());
        queue.enqueue("tote-a");
        assertTrue(queue.canAccept());
        queue.enqueue("tote-b");

        assertFalse(queue.canAccept());
        assertEquals(2, queue.size());
        assertEquals(2, queue.capacity());
        assertEquals(List.of("tote-a", "tote-b"), queue.toteIds());
    }

    @Test
    void shouldPreserveFifoOrder() {
        MachineWaitQueue queue = new MachineWaitQueue("tipper-input", 3);
        queue.enqueue("tote-a");
        queue.enqueue("tote-b");
        queue.enqueue("tote-c");

        assertEquals("tote-a", queue.peek());
        assertEquals("tote-a", queue.dequeue());
        assertEquals("tote-b", queue.peek());
        assertEquals("tote-b", queue.dequeue());
        assertEquals("tote-c", queue.dequeue());
        assertNull(queue.peek());
        assertNull(queue.dequeue());
    }

    @Test
    void shouldAllowZeroCapacityQueue() {
        MachineWaitQueue queue = new MachineWaitQueue("tipper-input", 0);

        assertFalse(queue.canAccept());
        assertEquals(0, queue.capacity());
        assertEquals(0, queue.size());
    }

    @Test
    void shouldRejectBlankOrNullIds() {
        assertThrows(IllegalArgumentException.class, () -> new MachineWaitQueue(null, 1));
        assertThrows(IllegalArgumentException.class, () -> new MachineWaitQueue("", 1));
        assertThrows(IllegalArgumentException.class, () -> new MachineWaitQueue("   ", 1));

        MachineWaitQueue queue = new MachineWaitQueue("tipper-input", 1);
        assertThrows(IllegalArgumentException.class, () -> queue.enqueue(null));
        assertThrows(IllegalArgumentException.class, () -> queue.enqueue(""));
        assertThrows(IllegalArgumentException.class, () -> queue.enqueue("   "));
    }

    @Test
    void shouldRejectNegativeCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new MachineWaitQueue("tipper-input", -1));
    }

    @Test
    void shouldRejectEnqueueWhenFull() {
        MachineWaitQueue queue = new MachineWaitQueue("tipper-input", 1);
        queue.enqueue("tote-a");

        assertThrows(IllegalStateException.class, () -> queue.enqueue("tote-b"));
    }

    @Test
    void shouldCreateImmutableSnapshot() {
        MachineWaitQueue queue = new MachineWaitQueue("tipper-input", 2);
        queue.enqueue("tote-a");

        MachineWaitQueueSnapshot snapshot = queue.snapshot();

        assertEquals("tipper-input", snapshot.id());
        assertEquals(2, snapshot.capacity());
        assertEquals(List.of("tote-a"), snapshot.toteIds());
        assertTrue(snapshot.canAccept());

        queue.enqueue("tote-b");

        assertEquals(List.of("tote-a"), snapshot.toteIds());
        assertTrue(snapshot.canAccept());
    }
}
