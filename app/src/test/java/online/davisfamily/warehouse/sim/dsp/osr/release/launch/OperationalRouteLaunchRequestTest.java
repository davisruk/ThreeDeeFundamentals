package online.davisfamily.warehouse.sim.dsp.osr.release.launch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRole;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.OperationalPhysicalToteReleaseRequest;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseRequest;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pPhysicalToteAssignment;

class OperationalRouteLaunchRequestTest {

    @Test
    void shouldCreateSourceNeutralOsrLaunchWithoutLosingManifestDerivedIdentity() {
        InboundToteManifest manifest = adaptedManifest();
        OperationalRouteDestination destination = destination(StationType.ADAPTING, "adapting-1");
        OperationalRouteDestination p2pDestination = destination(StationType.P2P, "p2p-1");
        P2pPhysicalToteAssignment assignment = new P2pPhysicalToteAssignment(
                manifest.physicalToteId(),
                manifest.serviceCentreId(),
                new P2pLineId("line-1"),
                p2pDestination);
        Duration releaseTime = Duration.ofSeconds(17);

        OperationalRouteLaunchRequest launchRequest =
                OperationalRouteLaunchRequestFactory.fromOsr(
                        new OsrProcessingReleaseRequest(
                                manifest, releaseTime, Optional.of(assignment)),
                        destination);

        assertEquals(OperationalPhysicalToteSource.OSR, launchRequest.source());
        assertEquals(manifest.physicalToteId(), launchRequest.physicalToteId());
        assertEquals(manifest.orderSheetKey(), launchRequest.orderSheetKey());
        assertEquals(manifest.orderType(), launchRequest.orderType());
        assertEquals(manifest.serviceCentreId(), launchRequest.serviceCentreId());
        assertEquals(PhysicalToteRole.INBOUND_PACK, launchRequest.identity().physicalToteRole());
        assertEquals(manifest.sourceSequenceNumber(), launchRequest.identity().sourceSequenceNumber());
        assertEquals(List.of("pharmacy-a", "pharmacy-b"), launchRequest.pharmacyIds());
        assertEquals(releaseTime, launchRequest.releaseTime());
        assertSame(assignment, launchRequest.p2pAssignment().orElseThrow());
        assertEquals(destination, launchRequest.destination());
    }

    @Test
    void shouldRetainExactOperationalReleaseRequestForAv02() {
        OperationalPhysicalToteReleaseRequest releaseRequest =
                new OperationalPhysicalToteReleaseRequest(
                        av02Identity(),
                        List.of(" pharmacy-a "),
                        Duration.ofSeconds(4),
                        Optional.empty());
        OperationalRouteDestination destination = destination(StationType.THIRD_PARTY, "third-party-1");

        OperationalRouteLaunchRequest launchRequest =
                OperationalRouteLaunchRequestFactory.fromOperational(releaseRequest, destination);

        assertSame(releaseRequest, launchRequest.releaseRequest());
        assertEquals(List.of("pharmacy-a"), launchRequest.pharmacyIds());
        assertEquals(destination, launchRequest.destination());
    }

    @Test
    void shouldAllowLaterP2pAssignmentForNonP2pFirstDestination() {
        OperationalRouteDestination p2pDestination = destination(StationType.P2P, "p2p-1");
        P2pPhysicalToteAssignment assignment = new P2pPhysicalToteAssignment(
                av02Identity().physicalToteId(),
                av02Identity().serviceCentreId(),
                new P2pLineId("line-1"),
                p2pDestination);
        OperationalPhysicalToteReleaseRequest releaseRequest =
                new OperationalPhysicalToteReleaseRequest(
                        av02Identity(),
                        List.of("pharmacy-a"),
                        Duration.ZERO,
                        Optional.of(assignment));

        OperationalRouteLaunchRequest launchRequest =
                OperationalRouteLaunchRequestFactory.fromOperational(
                        releaseRequest,
                        destination(StationType.ADAPTING, "adapting-1"));

        assertSame(releaseRequest, launchRequest.releaseRequest());
        assertSame(assignment, launchRequest.p2pAssignment().orElseThrow());
    }

    @Test
    void shouldContinueToNewDestinationWhileRetainingExactReleaseRequestAndAssignment() {
        OperationalRouteDestination assignedDestination = destination(StationType.P2P, "p2p-1");
        P2pPhysicalToteAssignment assignment = new P2pPhysicalToteAssignment(
                av02Identity().physicalToteId(),
                av02Identity().serviceCentreId(),
                new P2pLineId("line-1"),
                assignedDestination);
        OperationalPhysicalToteReleaseRequest releaseRequest =
                new OperationalPhysicalToteReleaseRequest(
                        av02Identity(),
                        List.of("pharmacy-a"),
                        Duration.ofSeconds(9),
                        Optional.of(assignment));
        OperationalRouteLaunchRequest previous =
                OperationalRouteLaunchRequestFactory.fromOperational(
                        releaseRequest,
                        destination(StationType.ADAPTING, "adapting-1"));

        OperationalRouteDestination next = destination(StationType.P2P, "p2p-1");
        OperationalRouteLaunchRequest continued =
                OperationalRouteLaunchRequestFactory.continueTo(previous, next);

        assertNotSame(previous, continued);
        assertSame(releaseRequest, continued.releaseRequest());
        assertSame(assignment, continued.p2pAssignment().orElseThrow());
        assertEquals(next, continued.destination());
        assertEquals(previous.source(), continued.source());
        assertEquals(previous.physicalToteId(), continued.physicalToteId());
        assertEquals(previous.orderSheetKey(), continued.orderSheetKey());
        assertEquals(previous.orderType(), continued.orderType());
        assertEquals(previous.serviceCentreId(), continued.serviceCentreId());
        assertEquals(previous.pharmacyIds(), continued.pharmacyIds());
        assertEquals(previous.releaseTime(), continued.releaseTime());
    }

    @Test
    void shouldRejectNullContinuationInputs() {
        OperationalRouteDestination destination = destination(StationType.ADAPTING, "adapting-1");
        OperationalRouteLaunchRequest previous =
                OperationalRouteLaunchRequestFactory.fromOperational(
                        new OperationalPhysicalToteReleaseRequest(
                                av02Identity(), List.of("pharmacy-a"), Duration.ZERO, Optional.empty()),
                        destination);

        assertThrows(IllegalArgumentException.class,
                () -> OperationalRouteLaunchRequestFactory.continueTo(null, destination));
        assertThrows(IllegalArgumentException.class,
                () -> OperationalRouteLaunchRequestFactory.continueTo(previous, null));
    }

    @Test
    void shouldRejectMismatchedDirectP2pDestinationAndInvalidPharmacyIdentity() {
        OperationalRouteDestination assignedDestination = destination(StationType.P2P, "p2p-1");
        P2pPhysicalToteAssignment assignment = new P2pPhysicalToteAssignment(
                av02Identity().physicalToteId(),
                av02Identity().serviceCentreId(),
                new P2pLineId("line-1"),
                assignedDestination);
        OperationalPhysicalToteReleaseRequest releaseRequest =
                new OperationalPhysicalToteReleaseRequest(
                        av02Identity(),
                        List.of("pharmacy-a"),
                        Duration.ZERO,
                        Optional.of(assignment));

        assertThrows(IllegalArgumentException.class, () ->
                OperationalRouteLaunchRequestFactory.fromOperational(
                        releaseRequest, destination(StationType.P2P, "p2p-2")));
        assertThrows(IllegalArgumentException.class, () ->
                new OperationalPhysicalToteReleaseRequest(
                        av02Identity(), List.of("pharmacy-a", "pharmacy-b"),
                        Duration.ZERO, Optional.empty()));
        assertThrows(IllegalArgumentException.class, () ->
                new OperationalPhysicalToteReleaseRequest(
                        av02Identity(), List.of(" "), Duration.ZERO, Optional.empty()));
        assertThrows(IllegalArgumentException.class, () ->
                OperationalRouteLaunchRequestFactory.fromOperational(
                        new OperationalPhysicalToteReleaseRequest(
                                osrIdentity(), List.of("pharmacy-a"), Duration.ZERO, Optional.empty()),
                        destination(StationType.ADAPTING, "adapting-1")));
    }

    private static InboundToteManifest adaptedManifest() {
        return new InboundToteManifest(
                new PhysicalToteId("osr-000001"),
                new OrderSheetKey("adapted-1", 1),
                OrderType.ADAPTED,
                "104",
                List.of(
                        item("line-1", "pharmacy-a"),
                        item("line-2", "pharmacy-b")),
                37);
    }

    private static DspOrderItem item(String lineReference, String pharmacyId) {
        return new DspOrderItem(
                lineReference,
                "product-" + lineReference,
                1,
                pharmacyId,
                "patient-" + lineReference,
                "prescription-" + lineReference,
                DspOrderLineType.ADAPTED,
                "associated-1",
                1,
                1);
    }

    private static OperationalPhysicalToteIdentity av02Identity() {
        return new OperationalPhysicalToteIdentity(
                OperationalPhysicalToteSource.AV02,
                new PhysicalToteId("av02-000001"),
                new OrderSheetKey("empty-1", 1),
                OrderType.EMPTY,
                "104",
                PhysicalToteRole.PRE_P2P,
                11);
    }

    private static OperationalPhysicalToteIdentity osrIdentity() {
        return new OperationalPhysicalToteIdentity(
                OperationalPhysicalToteSource.OSR,
                new PhysicalToteId("osr-000001"),
                new OrderSheetKey("adapted-1", 1),
                OrderType.ADAPTED,
                "104",
                PhysicalToteRole.INBOUND_PACK,
                37);
    }

    private static OperationalRouteDestination destination(
            StationType stationType,
            String targetId) {
        return new OperationalRouteDestination(stationType, targetId);
    }
}
