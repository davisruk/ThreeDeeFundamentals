package online.davisfamily.warehouse.sim.dsp.p2p.lease;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifestCatalog;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.ReleasePhysicalToteFromOsrCommand;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteSource;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements;
import online.davisfamily.warehouse.sim.dsp.model.StartLocation;

class P2pReleaseAssignmentCommitTest {

    @Test
    void shouldCreateSourceNeutralRequestWithoutManifestForAv02() {
        PhysicalToteId physicalToteId = new PhysicalToteId("av02-1");
        OrderSheetKey orderSheetKey = new OrderSheetKey("empty-1", 1);
        OperationalRouteDestination destination = new OperationalRouteDestination(
                StationType.P2P,
                "p2p-1");
        P2pPhysicalToteAssignment assignment = new P2pPhysicalToteAssignment(
                physicalToteId,
                "104",
                new P2pLineId("line-1"),
                destination);
        online.davisfamily.warehouse.sim.dsp.av02.ReleasePhysicalToteFromAv02Command command =
                new online.davisfamily.warehouse.sim.dsp.av02.ReleasePhysicalToteFromAv02Command(
                        physicalToteId,
                        orderSheetKey,
                        "104",
                        "p2p-1",
                        Optional.of(assignment));

        P2pReleaseAssignmentRequest request = P2pReleaseAssignmentRequest.from(command);

        assertEquals(OperationalPhysicalToteSource.AV02, request.source());
        assertEquals(physicalToteId, request.physicalToteId());
        assertEquals(orderSheetKey, request.orderSheetKey());
        assertEquals(Optional.of(assignment), request.proposedP2pAssignment());
    }

    @Test
    void shouldAdaptLegacyOsrCommitterUsingTheLiveManifest() {
        InboundToteManifest manifest = new InboundToteManifest(
                new PhysicalToteId("osr-1"),
                new OrderSheetKey("full-pack-1", 1),
                OrderType.FULL_PACK,
                "104",
                List.of(new DspOrderItem("line-1", "product-1", 1)),
                1);
        AtomicReference<ReleasePhysicalToteFromOsrCommand> observedCommand =
                new AtomicReference<>();
        AtomicReference<InboundToteManifest> observedManifest = new AtomicReference<>();
        P2pReleaseAssignmentCommit expectedCommit = () -> { };
        P2pReleaseAssignmentCommitter legacy = (command, liveManifest) -> {
            observedCommand.set(command);
            observedManifest.set(liveManifest);
            return expectedCommit;
        };
        OperationalRouteDestination destination = new OperationalRouteDestination(
                StationType.P2P,
                "p2p-1");
        P2pPhysicalToteAssignment assignment = new P2pPhysicalToteAssignment(
                manifest.physicalToteId(),
                manifest.serviceCentreId(),
                new P2pLineId("line-1"),
                destination);
        P2pReleaseAssignmentRequest request = new P2pReleaseAssignmentRequest(
                manifest.physicalToteId(),
                manifest.orderSheetKey(),
                manifest.serviceCentreId(),
                destination.targetId(),
                OperationalPhysicalToteSource.OSR,
                Optional.of(assignment));

        P2pReleaseAssignmentCommit actual =
                new OsrP2pReleaseAssignmentCommitterAdapter(
                        new InboundToteManifestCatalog(List.of(manifest)),
                        legacy)
                .prepare(request);

        assertSame(expectedCommit, actual);
        assertEquals(manifest, observedManifest.get());
        assertEquals(manifest.physicalToteId(), observedCommand.get().physicalToteId());
        assertEquals(manifest.orderSheetKey(), observedCommand.get().orderSheetKey());
    }

    @Test
    void shouldKeepStrictCommitterSourceNeutralInputAvailable() {
        PhysicalToteId physicalToteId = new PhysicalToteId("av02-1");
        OrderSheetKey orderSheetKey = new OrderSheetKey("empty-1", 1);
        OperationalRouteDestination destination = new OperationalRouteDestination(
                StationType.P2P,
                "p2p-1");
        P2pLineId lineId = new P2pLineId("line-1");
        P2pLineDefinition definition = new P2pLineDefinition(lineId, destination);
        P2pPhysicalToteAssignment assignment = new P2pPhysicalToteAssignment(
                physicalToteId, "104", lineId, destination);
        P2pLineLeaseRegistry registry = new P2pLineLeaseRegistry(List.of(definition));
        StrictP2pReleaseAssignmentCommitter committer =
                new StrictP2pReleaseAssignmentCommitter(
                        registry,
                        ignored -> new RouteRequirements(
                                false, false, false, true, false, StartLocation.AV02),
                        java.util.Map.of(lineId, P2pLineActivitySnapshot::idle));

        P2pReleaseAssignmentCommit pending = committer.prepare(
                new P2pReleaseAssignmentRequest(
                        physicalToteId,
                        orderSheetKey,
                        "104",
                        destination.targetId(),
                        OperationalPhysicalToteSource.AV02,
                        Optional.of(assignment)));

        pending.commit();
        assertEquals(Optional.of(assignment), registry.findAssignment(physicalToteId));
    }
}
