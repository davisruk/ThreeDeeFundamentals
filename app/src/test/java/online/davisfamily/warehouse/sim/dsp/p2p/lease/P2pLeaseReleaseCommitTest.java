package online.davisfamily.warehouse.sim.dsp.p2p.lease;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StartLocation;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.ReleasePhysicalToteFromOsrCommand;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspOrderStatus;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;

class P2pLeaseReleaseCommitTest {

    @Test
    void shouldPrepareWithoutMutationThenCommitLeaseAndPinnedAssignment() {
        Fixture fixture = fixture(requiresP2pOnly());

        P2pReleaseAssignmentCommit commit = fixture.committer().prepare(
                fixture.command("p2p-target", Optional.of(fixture.assignment())),
                fixture.manifest());

        assertTrue(fixture.registry().ownerFor(fixture.line().lineId()).isEmpty());
        assertTrue(fixture.registry().findAssignment(
                fixture.manifest().physicalToteId()).isEmpty());

        commit.commit();

        assertEquals(Optional.of("104"),
                fixture.registry().ownerFor(fixture.line().lineId()));
        assertEquals(Optional.of(fixture.assignment()), fixture.registry().findAssignment(
                fixture.manifest().physicalToteId()));

        fixture.committer().prepare(
                fixture.command("p2p-target", Optional.of(fixture.assignment())),
                fixture.manifest()).commit();
        assertEquals(1, fixture.registry().snapshot(Map.of(
                        fixture.line().lineId(), P2pLineActivitySnapshot.idle()))
                .findLine(fixture.line().lineId()).orElseThrow()
                .physicalAssignments().size());
    }

    @Test
    void shouldAcceptPinnedDestinationForMultiStationRouteButNotAsFirstTarget() {
        Fixture fixture = fixture(new RouteRequirements(
                true, false, false, true, false, StartLocation.OSR));

        fixture.committer().prepare(
                fixture.command("third-party-target", Optional.of(fixture.assignment())),
                fixture.manifest()).commit();

        assertEquals(Optional.of(fixture.assignment()), fixture.registry().findAssignment(
                fixture.manifest().physicalToteId()));
        assertThrows(IllegalStateException.class, () -> fixture.committer().prepare(
                fixture.command("p2p-target", Optional.of(fixture.assignment())),
                fixture.manifest()));
    }

    @Test
    void shouldRejectMissingUnexpectedOrMisdirectedAssignmentsWithoutMutation() {
        Fixture p2pFixture = fixture(requiresP2pOnly());

        assertThrows(IllegalStateException.class, () -> p2pFixture.committer().prepare(
                p2pFixture.command("p2p-target", Optional.empty()),
                p2pFixture.manifest()));
        assertThrows(IllegalStateException.class, () -> p2pFixture.committer().prepare(
                p2pFixture.command("wrong-target", Optional.of(p2pFixture.assignment())),
                p2pFixture.manifest()));
        assertTrue(p2pFixture.registry().ownerFor(p2pFixture.line().lineId()).isEmpty());
        assertTrue(p2pFixture.registry().findAssignment(
                p2pFixture.manifest().physicalToteId()).isEmpty());

        Fixture nonP2pFixture = fixture(new RouteRequirements(
                true, false, false, false, false, StartLocation.OSR));
        assertThrows(IllegalStateException.class, () -> nonP2pFixture.committer().prepare(
                nonP2pFixture.command(
                        "third-party-target", Optional.of(nonP2pFixture.assignment())),
                nonP2pFixture.manifest()));
        assertTrue(nonP2pFixture.registry().ownerFor(
                nonP2pFixture.line().lineId()).isEmpty());
    }

    @Test
    void shouldRejectASelectionOwnedByAnotherServiceCentreWithoutMutation() {
        Fixture fixture = fixture(requiresP2pOnly());
        fixture.registry().acquireLease(
                fixture.line().lineId(), "108", P2pLineActivitySnapshot.idle());

        assertThrows(IllegalStateException.class, () -> fixture.committer().prepare(
                fixture.command("p2p-target", Optional.of(fixture.assignment())),
                fixture.manifest()));

        assertEquals(Optional.of("108"),
                fixture.registry().ownerFor(fixture.line().lineId()));
        assertTrue(fixture.registry().findAssignment(
                fixture.manifest().physicalToteId()).isEmpty());
    }

    @Test
    void shouldResolveExactlyOneCurrentRouteDefinition() {
        RouteRequirements requirements = requiresP2pOnly();
        DspSchedulerOrderState state = new DspSchedulerOrderState(
                new NotionalToteOrder(
                        "order-1",
                        "notional-1",
                        "104",
                        1,
                        OrderType.FULL_PACK,
                        List.of(new DspOrderItem("line-1", "product-1", 1)),
                        999,
                        1),
                requirements,
                DspOrderStatus.WAITING);
        WarehouseSchedulerSnapshot exact = new WarehouseSchedulerSnapshot(
                List.of(state), Map.of(), Set.of(), Optional.of("104"));
        WarehouseSnapshotP2pReleaseRequirementResolver resolver =
                new WarehouseSnapshotP2pReleaseRequirementResolver(() -> exact);

        assertEquals(requirements, resolver.resolve(state.order().orderSheetKey()));

        WarehouseSnapshotP2pReleaseRequirementResolver missing =
                new WarehouseSnapshotP2pReleaseRequirementResolver(() ->
                        new WarehouseSchedulerSnapshot(
                                List.of(), Map.of(), Set.of(), Optional.empty()));
        assertThrows(IllegalStateException.class, () ->
                missing.resolve(state.order().orderSheetKey()));

        WarehouseSnapshotP2pReleaseRequirementResolver ambiguous =
                new WarehouseSnapshotP2pReleaseRequirementResolver(() ->
                        new WarehouseSchedulerSnapshot(
                                List.of(state, state),
                                Map.of(),
                                Set.of(),
                                Optional.of("104")));
        assertThrows(IllegalStateException.class, () ->
                ambiguous.resolve(state.order().orderSheetKey()));
    }

    private static Fixture fixture(RouteRequirements requirements) {
        P2pLineDefinition line = new P2pLineDefinition(
                new P2pLineId("line-1"),
                new OperationalRouteDestination(StationType.P2P, "p2p-target"));
        P2pLineLeaseRegistry registry = new P2pLineLeaseRegistry(List.of(line));
        InboundToteManifest manifest = new InboundToteManifest(
                new PhysicalToteId("physical-1"),
                new OrderSheetKey("order-1", 1),
                OrderType.FULL_PACK,
                "104",
                List.of(new DspOrderItem("line-1", "product-1", 1)),
                1);
        P2pPhysicalToteAssignment assignment = new P2pPhysicalToteAssignment(
                manifest.physicalToteId(),
                manifest.serviceCentreId(),
                line.lineId(),
                line.destination());
        StrictP2pReleaseAssignmentCommitter committer =
                new StrictP2pReleaseAssignmentCommitter(
                        registry,
                        ignored -> requirements,
                        Map.of(line.lineId(), P2pLineActivitySnapshot::idle));
        return new Fixture(line, registry, manifest, assignment, committer);
    }

    private static RouteRequirements requiresP2pOnly() {
        return new RouteRequirements(
                false, false, false, true, false, StartLocation.OSR);
    }

    private record Fixture(
            P2pLineDefinition line,
            P2pLineLeaseRegistry registry,
            InboundToteManifest manifest,
            P2pPhysicalToteAssignment assignment,
            StrictP2pReleaseAssignmentCommitter committer) {

        private ReleasePhysicalToteFromOsrCommand command(
                String targetId,
                Optional<P2pPhysicalToteAssignment> proposedAssignment) {
            return new ReleasePhysicalToteFromOsrCommand(
                    manifest.physicalToteId(),
                    manifest.orderSheetKey(),
                    manifest.serviceCentreId(),
                    targetId,
                    proposedAssignment);
        }
    }
}
