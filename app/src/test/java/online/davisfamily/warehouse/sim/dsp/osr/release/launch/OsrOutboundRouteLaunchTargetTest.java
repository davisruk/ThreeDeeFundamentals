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
import online.davisfamily.warehouse.sim.dsp.runtime.SchedulerCommandApplicationResult;

class OsrOutboundRouteLaunchTargetTest {

    @Test
    void shouldStoreExactRequestWithConfiguredDestination() {
        OsrOutboundRouteLaunchQueue queue = new OsrOutboundRouteLaunchQueue("outbound", 2);
        OperationalRouteDestination destination =
                new OperationalRouteDestination(StationType.THIRD_PARTY, "third-party-1");
        OsrOutboundRouteLaunchTarget target =
                new OsrOutboundRouteLaunchTarget(destination, queue);
        OsrProcessingReleaseRequest request = request("tote-1", 1);

        SchedulerCommandApplicationResult result = target.accept(request);

        assertTrue(result.applied());
        assertFalse(result.deferred());
        assertEquals("", result.reason());
        assertEquals("third-party-1", target.targetId());
        assertSame(destination, target.destination());
        OperationalRouteLaunchRequest queued = queue.peek().orElseThrow();
        assertEquals(request.manifest().physicalToteId(), queued.physicalToteId());
        assertEquals(request.manifest().orderSheetKey(), queued.orderSheetKey());
        assertEquals(List.of("UNKNOWN"), queued.pharmacyIds());
        assertEquals(request.releaseTime(), queued.releaseTime());
        assertSame(destination, queued.destination());
    }

    @Test
    void shouldDeferDistinctRequestWhenSharedQueueIsFull() {
        OsrOutboundRouteLaunchQueue queue = new OsrOutboundRouteLaunchQueue("outbound", 1);
        OsrOutboundRouteLaunchTarget firstTarget = target(
                StationType.P2P, "p2p-1", queue);
        OsrOutboundRouteLaunchTarget secondTarget = target(
                StationType.ADAPTING, "adapting-1", queue);
        OsrProcessingReleaseRequest first = request("tote-1", 1);
        OsrProcessingReleaseRequest second = request("tote-2", 2);
        assertTrue(firstTarget.accept(first).applied());

        SchedulerCommandApplicationResult result = secondTarget.accept(second);

        assertFalse(result.applied());
        assertTrue(result.deferred());
        assertTrue(result.reason().contains("adapting-1"));
        assertEquals(first.manifest().physicalToteId(), queue.peek().orElseThrow().physicalToteId());
        assertEquals(1, queue.snapshot().occupancy());
    }

    @Test
    void shouldRejectDuplicateBeforeSharedCapacityCheck() {
        OsrOutboundRouteLaunchQueue queue = new OsrOutboundRouteLaunchQueue("outbound", 1);
        OsrOutboundRouteLaunchTarget firstTarget = target(
                StationType.P2P, "p2p-1", queue);
        OsrOutboundRouteLaunchTarget secondTarget = target(
                StationType.THIRD_PARTY, "third-party-1", queue);
        OsrProcessingReleaseRequest request = request("tote-1", 1);
        assertTrue(firstTarget.accept(request).applied());

        SchedulerCommandApplicationResult result = secondTarget.accept(request);

        assertFalse(result.applied());
        assertFalse(result.deferred());
        assertTrue(result.reason().contains("tote-1"));
        assertEquals(1, queue.snapshot().occupancy());
    }

    @Test
    void shouldValidateConstructionAndRequest() {
        OsrOutboundRouteLaunchQueue queue = new OsrOutboundRouteLaunchQueue("outbound", 1);
        OperationalRouteDestination destination =
                new OperationalRouteDestination(StationType.P2P, "p2p-1");

        assertThrows(IllegalArgumentException.class, () ->
                new OsrOutboundRouteLaunchTarget(null, queue));
        assertThrows(IllegalArgumentException.class, () ->
                new OsrOutboundRouteLaunchTarget(destination, null));

        OsrOutboundRouteLaunchTarget target =
                new OsrOutboundRouteLaunchTarget(destination, queue);
        assertThrows(IllegalArgumentException.class, () -> target.accept(null));
    }

    private static OsrOutboundRouteLaunchTarget target(
            StationType stationType,
            String targetId,
            OsrOutboundRouteLaunchQueue queue) {
        return new OsrOutboundRouteLaunchTarget(
                new OperationalRouteDestination(stationType, targetId),
                queue);
    }

    private static OsrProcessingReleaseRequest request(
            String physicalToteId,
            long sourceSequence) {
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
                Duration.ofSeconds(sourceSequence));
    }
}
