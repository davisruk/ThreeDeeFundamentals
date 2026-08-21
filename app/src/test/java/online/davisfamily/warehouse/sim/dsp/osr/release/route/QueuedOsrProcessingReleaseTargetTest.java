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
import online.davisfamily.warehouse.sim.dsp.runtime.SchedulerCommandApplicationResult;

class QueuedOsrProcessingReleaseTargetTest {

    @Test
    void shouldApplyAndStoreRequestExactlyOnce() {
        OperationalRouteEntryQueue queue = queue(2);
        QueuedOsrProcessingReleaseTarget target = new QueuedOsrProcessingReleaseTarget(queue);
        OsrProcessingReleaseRequest request = request("tote-1", Duration.ofSeconds(12));

        SchedulerCommandApplicationResult result = target.accept(request);

        assertTrue(result.applied());
        assertFalse(result.deferred());
        assertEquals("", result.reason());
        assertEquals("third-party-ingress", target.targetId());
        assertSame(request, queue.peek().orElseThrow());
        assertEquals(1, queue.snapshot().occupancy());
    }

    @Test
    void shouldDeferDistinctRequestWhenQueueIsFull() {
        OperationalRouteEntryQueue queue = queue(1);
        QueuedOsrProcessingReleaseTarget target = new QueuedOsrProcessingReleaseTarget(queue);
        OsrProcessingReleaseRequest first = request("tote-1", Duration.ZERO);
        OsrProcessingReleaseRequest second = request("tote-2", Duration.ofSeconds(1));
        assertTrue(target.accept(first).applied());

        SchedulerCommandApplicationResult result = target.accept(second);

        assertFalse(result.applied());
        assertTrue(result.deferred());
        assertTrue(result.reason().contains("third-party-ingress"));
        assertSame(first, queue.peek().orElseThrow());
        assertEquals(1, queue.snapshot().occupancy());
    }

    @Test
    void shouldRejectDuplicateBeforeConsideringFullCapacity() {
        OperationalRouteEntryQueue queue = queue(1);
        QueuedOsrProcessingReleaseTarget target = new QueuedOsrProcessingReleaseTarget(queue);
        OsrProcessingReleaseRequest request = request("tote-1", Duration.ZERO);
        assertTrue(target.accept(request).applied());

        SchedulerCommandApplicationResult result = target.accept(request);

        assertFalse(result.applied());
        assertFalse(result.deferred());
        assertTrue(result.reason().contains("tote-1"));
        assertEquals(1, queue.snapshot().occupancy());
        assertSame(request, queue.peek().orElseThrow());
    }

    @Test
    void shouldValidateConstructionAndRequest() {
        assertThrows(IllegalArgumentException.class, () ->
                new QueuedOsrProcessingReleaseTarget(null));

        QueuedOsrProcessingReleaseTarget target =
                new QueuedOsrProcessingReleaseTarget(queue(1));
        assertThrows(IllegalArgumentException.class, () -> target.accept(null));
    }

    @Test
    void shouldAcceptAfterCapacityIsRecovered() {
        OperationalRouteEntryQueue queue = queue(1);
        QueuedOsrProcessingReleaseTarget target = new QueuedOsrProcessingReleaseTarget(queue);
        OsrProcessingReleaseRequest first = request("tote-1", Duration.ZERO);
        OsrProcessingReleaseRequest second = request("tote-2", Duration.ofSeconds(2));
        assertTrue(target.accept(first).applied());
        assertSame(first, queue.dequeue().orElseThrow());

        SchedulerCommandApplicationResult result = target.accept(second);

        assertTrue(result.applied());
        assertSame(second, queue.peek().orElseThrow());
    }

    private static OperationalRouteEntryQueue queue(int capacity) {
        return new OperationalRouteEntryQueue(new OperationalRouteTargetDefinition(
                StationType.THIRD_PARTY,
                "third-party-ingress",
                capacity));
    }

    private static OsrProcessingReleaseRequest request(
            String physicalToteId,
            Duration releaseTime) {
        return new OsrProcessingReleaseRequest(
                new InboundToteManifest(
                        new PhysicalToteId(physicalToteId),
                        new OrderSheetKey("order-" + physicalToteId, 1),
                        OrderType.FULL_PACK,
                        "104",
                        List.of(new DspOrderItem(
                                "line-" + physicalToteId,
                                "product-" + physicalToteId,
                                1)),
                        1),
                releaseTime);
    }
}
