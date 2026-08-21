package online.davisfamily.warehouse.sim.dsp.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.tote.Tote.ToteMotionState;

class OsrOutboundTransportQueueTest {

    @Test
    void shouldPreserveExactRoutedTotesInGlobalFifoAcrossDestinations() {
        OsrOutboundTransportQueue queue = queue(3);
        RoutedPhysicalTote first = routed(
                "tote-1", StationType.P2P, "p2p-1");
        RoutedPhysicalTote second = routed(
                "tote-2", StationType.THIRD_PARTY, "third-party-1");
        RoutedPhysicalTote third = routed(
                "tote-3", StationType.ADAPTING, "adapting-1");

        queue.enqueue(first);
        queue.enqueue(second);
        queue.enqueue(third);

        assertSame(first, queue.peek().orElseThrow());
        assertSame(first, queue.dequeue().orElseThrow());
        assertSame(second, queue.dequeue().orElseThrow());
        assertSame(third, queue.dequeue().orElseThrow());
        assertTrue(queue.peek().isEmpty());
        assertTrue(queue.dequeue().isEmpty());
    }

    @Test
    void shouldRejectDuplicateBeforeCheckingFullCapacity() {
        OsrOutboundTransportQueue queue = queue(1);
        RoutedPhysicalTote first = routed(
                "tote-1", StationType.P2P, "p2p-1");
        queue.enqueue(first);

        assertTrue(queue.contains(first.physicalToteId()));
        assertFalse(queue.canAccept());
        assertThrows(IllegalArgumentException.class, () -> queue.enqueue(first));
        assertThrows(IllegalStateException.class, () -> queue.enqueue(
                routed("tote-2", StationType.P2P, "p2p-1")));
    }

    @Test
    void shouldRecoverCapacityAndDuplicateIndexAfterDequeue() {
        OsrOutboundTransportQueue queue = queue(1);
        RoutedPhysicalTote routedTote = routed(
                "tote-1", StationType.P2P, "p2p-1");
        queue.enqueue(routedTote);

        assertSame(routedTote, queue.dequeue().orElseThrow());
        assertFalse(queue.contains(routedTote.physicalToteId()));
        assertTrue(queue.canAccept());

        queue.enqueue(routedTote);
        assertSame(routedTote, queue.peek().orElseThrow());
    }

    @Test
    void shouldExposeDestinationBearingSnapshotWithoutMutatingPayload() {
        OsrOutboundTransportQueue queue = queue(2);
        RoutedPhysicalTote routedTote = routed(
                "tote-1", StationType.ADAPTING, "adapting-1");
        ToteMotionState initialMotionState = routedTote.tote().getInteractionMode();
        queue.enqueue(routedTote);

        OsrOutboundTransportQueueSnapshot snapshot = queue.snapshot();

        assertEquals("osr-transport", snapshot.queueId());
        assertEquals(1, snapshot.occupancy());
        assertEquals(routedTote.physicalToteId(), snapshot.entries().get(0).physicalToteId());
        assertSame(routedTote.destination(), snapshot.entries().get(0).destination());
        assertEquals(initialMotionState, routedTote.tote().getInteractionMode());
        assertSame(routedTote, queue.peek().orElseThrow());
    }

    @Test
    void shouldKeepZeroCapacityQueueEmptyAndRejectInvalidInput() {
        assertThrows(IllegalArgumentException.class, () ->
                new OsrOutboundTransportQueue(null, 1));
        assertThrows(IllegalArgumentException.class, () ->
                new OsrOutboundTransportQueue(" ", 1));
        assertThrows(IllegalArgumentException.class, () ->
                new OsrOutboundTransportQueue("transport", -1));

        OsrOutboundTransportQueue queue = queue(0);

        assertFalse(queue.canAccept());
        assertTrue(queue.snapshot().entries().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> queue.contains(null));
        assertThrows(IllegalArgumentException.class, () -> queue.enqueue(null));
        assertThrows(IllegalStateException.class, () -> queue.enqueue(
                routed("tote-1", StationType.P2P, "p2p-1")));
    }

    private static OsrOutboundTransportQueue queue(int capacity) {
        return new OsrOutboundTransportQueue("  osr-transport  ", capacity);
    }

    private static RoutedPhysicalTote routed(
            String physicalToteId,
            StationType stationType,
            String targetId) {
        return RoutedPhysicalToteTestFixtures.routedTote(
                physicalToteId, stationType, targetId);
    }
}
