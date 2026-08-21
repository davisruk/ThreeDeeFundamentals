package online.davisfamily.warehouse.sim.dsp.osr.release.route;

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

class OperationalRouteEntryQueueTest {

    @Test
    void shouldPreserveRequestsAndFifoOrder() {
        OperationalRouteEntryQueue queue = queue(2);
        OsrProcessingReleaseRequest first = request("tote-1", 1, Duration.ofSeconds(10));
        OsrProcessingReleaseRequest second = request("tote-2", 2, Duration.ofSeconds(11));

        queue.enqueue(first);
        queue.enqueue(second);

        assertSame(first, queue.peek().orElseThrow());
        assertSame(first, queue.dequeue().orElseThrow());
        assertSame(second, queue.peek().orElseThrow());
        assertSame(second, queue.dequeue().orElseThrow());
        assertTrue(queue.peek().isEmpty());
        assertTrue(queue.dequeue().isEmpty());
    }

    @Test
    void shouldExposeDefinitionCapacityAndImmutablePhysicalOrder() {
        OperationalRouteTargetDefinition definition = new OperationalRouteTargetDefinition(
                StationType.P2P,
                "p2p-ingress",
                2);
        OperationalRouteEntryQueue queue = new OperationalRouteEntryQueue(definition);
        queue.enqueue(request("tote-1", 1, Duration.ZERO));

        OperationalRouteEntryQueueSnapshot snapshot = queue.snapshot();

        assertSame(definition, queue.definition());
        assertEquals(StationType.P2P, snapshot.stationType());
        assertEquals("p2p-ingress", snapshot.targetId());
        assertEquals(2, snapshot.capacity());
        assertEquals(List.of(new PhysicalToteId("tote-1")), snapshot.physicalToteIds());
        assertTrue(snapshot.canAccept());

        queue.enqueue(request("tote-2", 2, Duration.ofSeconds(1)));

        assertEquals(List.of(new PhysicalToteId("tote-1")), snapshot.physicalToteIds());
        assertTrue(snapshot.canAccept());
    }

    @Test
    void shouldRejectInvalidDuplicateAndOverCapacityEnqueue() {
        assertThrows(IllegalArgumentException.class, () -> new OperationalRouteEntryQueue(null));

        OperationalRouteEntryQueue queue = queue(1);
        OsrProcessingReleaseRequest first = request("tote-1", 1, Duration.ZERO);
        queue.enqueue(first);

        assertTrue(queue.contains(new PhysicalToteId("tote-1")));
        assertFalse(queue.canAccept());
        assertThrows(IllegalArgumentException.class, () -> queue.contains(null));
        assertThrows(IllegalArgumentException.class, () -> queue.enqueue(null));
        assertThrows(IllegalArgumentException.class, () -> queue.enqueue(first));
        assertThrows(
                IllegalStateException.class,
                () -> queue.enqueue(request("tote-2", 2, Duration.ZERO)));
    }

    @Test
    void shouldRecoverCapacityAndDuplicateIndexAfterDequeue() {
        OperationalRouteEntryQueue queue = queue(1);
        OsrProcessingReleaseRequest request = request("tote-1", 1, Duration.ZERO);
        queue.enqueue(request);

        assertSame(request, queue.dequeue().orElseThrow());
        assertFalse(queue.contains(new PhysicalToteId("tote-1")));
        assertTrue(queue.canAccept());

        queue.enqueue(request);

        assertSame(request, queue.peek().orElseThrow());
    }

    @Test
    void shouldKeepZeroCapacityQueueEmpty() {
        OperationalRouteEntryQueue queue = queue(0);

        assertFalse(queue.canAccept());
        assertTrue(queue.snapshot().physicalToteIds().isEmpty());
        assertThrows(
                IllegalStateException.class,
                () -> queue.enqueue(request("tote-1", 1, Duration.ZERO)));
    }

    private static OperationalRouteEntryQueue queue(int capacity) {
        return new OperationalRouteEntryQueue(new OperationalRouteTargetDefinition(
                StationType.P2P,
                "p2p-ingress",
                capacity));
    }

    private static OsrProcessingReleaseRequest request(
            String physicalToteId,
            long sourceSequence,
            Duration releaseTime) {
        return new OsrProcessingReleaseRequest(
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
                releaseTime);
    }
}
