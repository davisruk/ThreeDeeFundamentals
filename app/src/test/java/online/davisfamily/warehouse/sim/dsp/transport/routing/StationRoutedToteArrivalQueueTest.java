package online.davisfamily.warehouse.sim.dsp.transport.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;
import online.davisfamily.warehouse.sim.tote.Tote.ToteMotionState;

class StationRoutedToteArrivalQueueTest {

    @Test
    void shouldPreserveExactPayloadsInFifoWithoutChangingToteState() {
        OperationalRouteDestination destination = destination(
                StationType.P2P, "p2p-1");
        StationRoutedToteArrivalQueue queue = queue(destination, 2);
        RoutedPhysicalTote first = routed("tote-1", destination);
        RoutedPhysicalTote second = routed("tote-2", destination);
        first.tote().setInteractionMode(ToteMotionState.BLOCKED);
        second.tote().setInteractionMode(ToteMotionState.MOVING);

        queue.enqueue(first);
        queue.enqueue(second);

        assertSame(destination, queue.destination());
        assertSame(first, queue.peek().orElseThrow());
        assertEquals(ToteMotionState.BLOCKED, first.tote().getInteractionMode());
        assertEquals(ToteMotionState.MOVING, second.tote().getInteractionMode());
        assertSame(first, queue.dequeue().orElseThrow());
        assertSame(second, queue.dequeue().orElseThrow());
        assertTrue(queue.peek().isEmpty());
        assertTrue(queue.dequeue().isEmpty());
        assertEquals(ToteMotionState.BLOCKED, first.tote().getInteractionMode());
        assertEquals(ToteMotionState.MOVING, second.tote().getInteractionMode());
    }

    @Test
    void shouldSupportEveryOperationalDestinationStation() {
        for (StationType stationType : new StationType[] {
                StationType.THIRD_PARTY,
                StationType.ADAPTING,
                StationType.P2P
        }) {
            OperationalRouteDestination destination = destination(
                    stationType, "target-" + stationType.name());
            StationRoutedToteArrivalQueue queue = queue(destination, 1);
            RoutedPhysicalTote routedTote = routed(
                    "tote-" + stationType.name(), destination);

            queue.enqueue(routedTote);

            assertSame(routedTote, queue.peek().orElseThrow());
            assertEquals(stationType, queue.snapshot().destination().stationType());
        }
    }

    @Test
    void shouldRejectWrongDestinationWithoutMutation() {
        OperationalRouteDestination p2p = destination(StationType.P2P, "p2p-1");
        StationRoutedToteArrivalQueue queue = queue(p2p, 1);
        RoutedPhysicalTote wrongStation = routed(
                "tote-1", destination(StationType.ADAPTING, "p2p-1"));
        RoutedPhysicalTote wrongTarget = routed(
                "tote-2", destination(StationType.P2P, "p2p-2"));

        assertThrows(IllegalArgumentException.class, () -> queue.enqueue(wrongStation));
        assertThrows(IllegalArgumentException.class, () -> queue.enqueue(wrongTarget));

        assertTrue(queue.peek().isEmpty());
        assertEquals(0, queue.snapshot().occupancy());
    }

    @Test
    void shouldRejectDuplicateBeforeCapacityAndRecoverAfterDequeue() {
        OperationalRouteDestination destination = destination(
                StationType.THIRD_PARTY, "third-party-1");
        StationRoutedToteArrivalQueue queue = queue(destination, 1);
        RoutedPhysicalTote first = routed("tote-1", destination);
        RoutedPhysicalTote second = routed("tote-2", destination);
        queue.enqueue(first);

        assertTrue(queue.contains(first.physicalToteId()));
        assertFalse(queue.canAccept());
        assertThrows(IllegalArgumentException.class, () -> queue.enqueue(first));
        assertThrows(IllegalStateException.class, () -> queue.enqueue(second));

        assertSame(first, queue.dequeue().orElseThrow());
        assertFalse(queue.contains(first.physicalToteId()));
        assertTrue(queue.canAccept());
        queue.enqueue(second);
        assertSame(second, queue.peek().orElseThrow());
    }

    @Test
    void shouldKeepZeroCapacityQueueEmptyAndRejectInvalidInput() {
        OperationalRouteDestination destination = destination(
                StationType.ADAPTING, "bench-1");

        assertThrows(IllegalArgumentException.class, () -> queue(null, 1));
        assertThrows(IllegalArgumentException.class, () -> queue(destination, -1));

        StationRoutedToteArrivalQueue queue = queue(destination, 0);
        assertFalse(queue.canAccept());
        assertTrue(queue.peek().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> queue.contains(null));
        assertThrows(IllegalArgumentException.class, () -> queue.enqueue(null));
        assertThrows(IllegalStateException.class, () ->
                queue.enqueue(routed("tote-1", destination)));
        assertEquals(0, queue.snapshot().occupancy());
    }

    private static StationRoutedToteArrivalQueue queue(
            OperationalRouteDestination destination,
            int capacity) {
        return new StationRoutedToteArrivalQueue(destination, capacity);
    }

    private static OperationalRouteDestination destination(
            StationType stationType,
            String targetId) {
        return RoutedToteRoutingTestFixtures.destination(stationType, targetId);
    }

    private static RoutedPhysicalTote routed(
            String physicalToteId,
            OperationalRouteDestination destination) {
        return RoutedToteRoutingTestFixtures.routedTote(
                physicalToteId, destination);
    }
}
