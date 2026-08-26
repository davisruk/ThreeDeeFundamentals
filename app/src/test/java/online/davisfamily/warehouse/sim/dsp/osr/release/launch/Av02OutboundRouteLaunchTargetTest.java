package online.davisfamily.warehouse.sim.dsp.osr.release.launch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRole;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.OperationalPhysicalToteReleaseRequest;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseRequest;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pPhysicalToteAssignment;
import online.davisfamily.warehouse.sim.dsp.runtime.SchedulerCommandApplicationResult;

class Av02OutboundRouteLaunchTargetTest {

    @Test
    void shouldApplyOsrAndAv02TargetsToTheSameBoundedFifo() {
        OsrOutboundRouteLaunchQueue queue = new OsrOutboundRouteLaunchQueue("shared", 2);
        OperationalRouteDestination osrDestination =
                destination(StationType.P2P, "p2p-1");
        OperationalRouteDestination av02Destination =
                destination(StationType.THIRD_PARTY, "third-party-1");
        OsrOutboundRouteLaunchTarget osrTarget =
                new OsrOutboundRouteLaunchTarget(osrDestination, queue);
        Av02OutboundRouteLaunchTarget av02Target =
                new Av02OutboundRouteLaunchTarget(av02Destination, queue);
        OsrProcessingReleaseRequest osrRequest = osrRequest("osr-1");
        OperationalPhysicalToteReleaseRequest av02Request = av02Request("av02-1");

        assertTrue(osrTarget.accept(osrRequest).applied());
        assertTrue(av02Target.accept(av02Request).applied());

        OperationalRouteLaunchRequest first = queue.dequeue().orElseThrow();
        OperationalRouteLaunchRequest second = queue.dequeue().orElseThrow();
        assertEquals(OperationalPhysicalToteSource.OSR, first.source());
        assertEquals(OperationalPhysicalToteSource.AV02, second.source());
        assertSame(av02Request, second.releaseRequest());
        assertEquals(List.of("av02-pharmacy"), second.pharmacyIds());
    }

    @Test
    void shouldDeferAv02WithoutMutationWhenSharedLaunchQueueIsFull() {
        OsrOutboundRouteLaunchQueue queue = new OsrOutboundRouteLaunchQueue("shared", 1);
        OperationalRouteDestination destination = destination(StationType.ADAPTING, "adapting-1");
        OsrOutboundRouteLaunchTarget osrTarget =
                new OsrOutboundRouteLaunchTarget(destination, queue);
        Av02OutboundRouteLaunchTarget av02Target =
                new Av02OutboundRouteLaunchTarget(destination, queue);
        assertTrue(osrTarget.accept(osrRequest("osr-1")).applied());

        SchedulerCommandApplicationResult result = av02Target.accept(av02Request("av02-1"));

        assertFalse(result.applied());
        assertTrue(result.deferred());
        assertEquals(1, queue.snapshot().occupancy());
        assertEquals(new PhysicalToteId("osr-1"), queue.peek().orElseThrow().physicalToteId());
    }

    @Test
    void shouldRejectDuplicateAv02PhysicalIdentity() {
        OsrOutboundRouteLaunchQueue queue = new OsrOutboundRouteLaunchQueue("shared", 2);
        Av02OutboundRouteLaunchTarget target = new Av02OutboundRouteLaunchTarget(
                destination(StationType.THIRD_PARTY, "third-party-1"), queue);
        OperationalPhysicalToteReleaseRequest request = av02Request("av02-1");

        assertTrue(target.accept(request).applied());
        SchedulerCommandApplicationResult repeated = target.accept(request);

        assertFalse(repeated.applied());
        assertFalse(repeated.deferred());
        assertEquals(1, queue.snapshot().occupancy());
        assertSame(request, queue.peek().orElseThrow().releaseRequest());
    }

    @Test
    void shouldRejectOsrReleaseAtAv02Adapter() {
        Av02OutboundRouteLaunchTarget target = new Av02OutboundRouteLaunchTarget(
                destination(StationType.P2P, "p2p-1"),
                new OsrOutboundRouteLaunchQueue("shared", 1));

        assertThrows(IllegalArgumentException.class, () ->
                target.accept(osrOperationalRequest("osr-1")));
    }

    private static OperationalPhysicalToteReleaseRequest av02Request(String physicalToteId) {
        OperationalPhysicalToteIdentity identity = new OperationalPhysicalToteIdentity(
                OperationalPhysicalToteSource.AV02,
                new PhysicalToteId(physicalToteId),
                new OrderSheetKey("empty-" + physicalToteId, 1),
                OrderType.EMPTY,
                "104",
                PhysicalToteRole.PRE_P2P,
                1);
        return new OperationalPhysicalToteReleaseRequest(
                identity,
                List.of("av02-pharmacy"),
                Duration.ZERO,
                Optional.empty());
    }

    private static OsrProcessingReleaseRequest osrRequest(String physicalToteId) {
        return new OsrProcessingReleaseRequest(
                new InboundToteManifest(
                        new PhysicalToteId(physicalToteId),
                        new OrderSheetKey("order-" + physicalToteId, 1),
                        OrderType.FULL_PACK,
                        "104",
                        List.of(new DspOrderItem("line-" + physicalToteId, "product-1", 1)),
                        1),
                Duration.ZERO);
    }

    private static OperationalPhysicalToteReleaseRequest osrOperationalRequest(
            String physicalToteId) {
        InboundToteManifest manifest = osrRequest(physicalToteId).manifest();
        OperationalPhysicalToteIdentity identity = new OperationalPhysicalToteIdentity(
                OperationalPhysicalToteSource.OSR,
                manifest.physicalToteId(),
                manifest.orderSheetKey(),
                manifest.orderType(),
                manifest.serviceCentreId(),
                PhysicalToteRole.INBOUND_PACK,
                manifest.sourceSequenceNumber());
        return new OperationalPhysicalToteReleaseRequest(
                identity,
                List.of("UNKNOWN"),
                Duration.ZERO,
                Optional.empty());
    }

    private static OperationalRouteDestination destination(
            StationType stationType,
            String targetId) {
        return new OperationalRouteDestination(stationType, targetId);
    }
}
