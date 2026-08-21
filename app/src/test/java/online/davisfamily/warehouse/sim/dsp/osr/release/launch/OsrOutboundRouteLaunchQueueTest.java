package online.davisfamily.warehouse.sim.dsp.osr.release.launch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseRequest;

class OsrOutboundRouteLaunchQueueTest {

    @Test
    void shouldPreserveExactRequestsInGlobalFifoAcrossDestinations() {
        OsrOutboundRouteLaunchQueue queue = queue(3);
        OsrOutboundRouteLaunchRequest first = request(
                "tote-1", 1, StationType.P2P, "p2p-1");
        OsrOutboundRouteLaunchRequest second = request(
                "tote-2", 2, StationType.THIRD_PARTY, "third-party-1");
        OsrOutboundRouteLaunchRequest third = request(
                "tote-3", 3, StationType.ADAPTING, "adapting-1");

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
    void shouldExposeDestinationBearingImmutableSnapshot() {
        OsrOutboundRouteLaunchQueue queue = queue(2);
        OsrOutboundRouteLaunchRequest first = request(
                "tote-1", 1, StationType.P2P, "p2p-1");
        queue.enqueue(first);

        OsrOutboundRouteLaunchQueueSnapshot snapshot = queue.snapshot();

        assertEquals("osr-outbound", snapshot.queueId());
        assertEquals(2, snapshot.capacity());
        assertEquals(1, snapshot.occupancy());
        assertEquals(1, snapshot.remainingCapacity());
        assertTrue(snapshot.canAccept());
        assertEquals(
                List.of(new OsrOutboundRouteLaunchQueueSnapshot.Entry(
                        first.physicalToteId(), first.destination())),
                snapshot.entries());

        queue.enqueue(request("tote-2", 2, StationType.ADAPTING, "adapting-1"));

        assertEquals(1, snapshot.occupancy());
        assertTrue(snapshot.canAccept());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.entries().clear());
    }

    @Test
    void shouldRejectDuplicateBeforeCheckingFullCapacity() {
        OsrOutboundRouteLaunchQueue queue = queue(1);
        OsrOutboundRouteLaunchRequest first = request(
                "tote-1", 1, StationType.P2P, "p2p-1");
        queue.enqueue(first);

        assertTrue(queue.contains(first.physicalToteId()));
        assertFalse(queue.canAccept());
        assertThrows(IllegalArgumentException.class, () -> queue.enqueue(first));
        assertThrows(IllegalStateException.class, () -> queue.enqueue(
                request("tote-2", 2, StationType.P2P, "p2p-1")));
    }

    @Test
    void shouldRecoverCapacityAndDuplicateIndexAfterDequeue() {
        OsrOutboundRouteLaunchQueue queue = queue(1);
        OsrOutboundRouteLaunchRequest request = request(
                "tote-1", 1, StationType.P2P, "p2p-1");
        queue.enqueue(request);

        assertSame(request, queue.dequeue().orElseThrow());
        assertFalse(queue.contains(request.physicalToteId()));
        assertTrue(queue.canAccept());

        queue.enqueue(request);

        assertSame(request, queue.peek().orElseThrow());
    }

    @Test
    void shouldKeepZeroCapacityQueueEmptyAndRejectInvalidInput() {
        assertThrows(IllegalArgumentException.class, () ->
                new OsrOutboundRouteLaunchQueue(null, 1));
        assertThrows(IllegalArgumentException.class, () ->
                new OsrOutboundRouteLaunchQueue(" ", 1));
        assertThrows(IllegalArgumentException.class, () ->
                new OsrOutboundRouteLaunchQueue("queue", -1));

        OsrOutboundRouteLaunchQueue queue = queue(0);

        assertFalse(queue.canAccept());
        assertTrue(queue.snapshot().entries().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> queue.contains(null));
        assertThrows(IllegalArgumentException.class, () -> queue.enqueue(null));
        assertThrows(IllegalStateException.class, () -> queue.enqueue(
                request("tote-1", 1, StationType.P2P, "p2p-1")));
    }

    private static OsrOutboundRouteLaunchQueue queue(int capacity) {
        return new OsrOutboundRouteLaunchQueue("  osr-outbound  ", capacity);
    }

    private static OsrOutboundRouteLaunchRequest request(
            String physicalToteId,
            long sourceSequence,
            StationType stationType,
            String targetId) {
        OsrProcessingReleaseRequest releaseRequest = new OsrProcessingReleaseRequest(
                new InboundToteManifest(
                        new PhysicalToteId(physicalToteId),
                        new OrderSheetKey("order-" + sourceSequence, 1),
                        OrderType.FULL_PACK,
                        "104",
                        List.of(new DspOrderItem(
                                "line-" + sourceSequence,
                                "product-" + sourceSequence,
                                1)),
                        sourceSequence),
                Duration.ofSeconds(sourceSequence));
        return new OsrOutboundRouteLaunchRequest(
                releaseRequest,
                new OperationalRouteDestination(stationType, targetId));
    }
}
