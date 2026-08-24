package online.davisfamily.warehouse.sim.dsp.osr.release.launch;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseRequest;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pPhysicalToteAssignment;

class OsrOutboundRouteLaunchRequestTest {

    @Test
    void shouldRetainExactReleaseIdentityDestinationAndTime() {
        InboundToteManifest manifest = manifest();
        Duration releaseTime = Duration.ofSeconds(17);
        OsrProcessingReleaseRequest releaseRequest =
                new OsrProcessingReleaseRequest(manifest, releaseTime);
        OperationalRouteDestination destination =
                new OperationalRouteDestination(StationType.P2P, "p2p-1");

        OsrOutboundRouteLaunchRequest launchRequest =
                new OsrOutboundRouteLaunchRequest(releaseRequest, destination);

        assertSame(releaseRequest, launchRequest.releaseRequest());
        assertSame(manifest, launchRequest.releaseRequest().manifest());
        assertSame(manifest.physicalToteId(), launchRequest.physicalToteId());
        assertSame(releaseTime, launchRequest.releaseRequest().releaseTime());
        assertSame(destination, launchRequest.destination());
    }

    @Test
    void shouldRetainPinnedP2pAssignmentWithoutCopyingIt() {
        InboundToteManifest manifest = manifest();
        OperationalRouteDestination p2pDestination =
                new OperationalRouteDestination(StationType.P2P, "p2p-1");
        P2pPhysicalToteAssignment assignment = new P2pPhysicalToteAssignment(
                manifest.physicalToteId(),
                manifest.serviceCentreId(),
                new P2pLineId("line-1"),
                p2pDestination);
        OsrProcessingReleaseRequest releaseRequest = new OsrProcessingReleaseRequest(
                manifest,
                Duration.ofSeconds(17),
                Optional.of(assignment));

        OsrOutboundRouteLaunchRequest launchRequest =
                new OsrOutboundRouteLaunchRequest(releaseRequest, p2pDestination);

        assertSame(assignment, launchRequest.p2pAssignment().orElseThrow());
    }

    @Test
    void shouldRejectNullReleaseRequestOrDestination() {
        OsrProcessingReleaseRequest releaseRequest =
                new OsrProcessingReleaseRequest(manifest(), Duration.ZERO);
        OperationalRouteDestination destination =
                new OperationalRouteDestination(StationType.ADAPTING, "adapting-1");

        assertThrows(IllegalArgumentException.class, () ->
                new OsrOutboundRouteLaunchRequest(null, destination));
        assertThrows(IllegalArgumentException.class, () ->
                new OsrOutboundRouteLaunchRequest(releaseRequest, null));
    }

    private static InboundToteManifest manifest() {
        return new InboundToteManifest(
                new PhysicalToteId("tote-1"),
                new OrderSheetKey("order-1", 1),
                OrderType.FULL_PACK,
                "104",
                List.of(new DspOrderItem("line-1", "product-1", 1)),
                0);
    }
}
