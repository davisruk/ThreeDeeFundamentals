package online.davisfamily.warehouse.sim.dsp.transport.routing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.behaviour.routing.RouteFollower.TravelDirection;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.warehouse.rendering.model.tracks.GuideSide;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.transfer.TransferMotionConfig;
import online.davisfamily.warehouse.sim.transfer.TransferOrientationPolicy;
import online.davisfamily.warehouse.sim.transfer.TransferRoutingDecision;
import online.davisfamily.warehouse.sim.transfer.TransferTarget;
import online.davisfamily.warehouse.sim.transfer.TransferZone;
import online.davisfamily.warehouse.sim.transfer.TransferZoneMachine;
import online.davisfamily.warehouse.sim.transfer.strategy.TransferTargetDecisionStrategy;

class WarehouseTransferRoutingTableTest {

    @Test
    void shouldResolveExplicitDecisionsAcrossCompleteDestinationPaths() {
        Topology topology = topology();
        WarehouseTransferRoutingTable table = topology.table(topology.entries());

        TransferRoutingDecision thirdParty = table.findDecision(
                topology.firstMachine.getId(), topology.thirdParty).orElseThrow();
        TransferRoutingDecision adaptingFirst = table.findDecision(
                topology.firstMachine.getId(), topology.adapting).orElseThrow();
        TransferRoutingDecision adaptingSecond = table.findDecision(
                topology.secondMachine.getId(), topology.adapting).orElseThrow();
        TransferRoutingDecision p2pSecond = table.findDecision(
                topology.secondMachine.getId(), topology.p2p).orElseThrow();

        assertTrue(thirdParty.isContinueOnCurrentRoute());
        assertSame(topology.sharedBranchTarget, adaptingFirst.transferTarget());
        assertSame(topology.adaptingTarget, adaptingSecond.transferTarget());
        assertSame(TravelDirection.REVERSE,
                adaptingSecond.transferTarget().travelDirection());
        assertSame(TransferOrientationPolicy.ALIGN_TO_TARGET_TRAVEL,
                adaptingSecond.orientationPolicy());
        assertTrue(p2pSecond.isContinueOnCurrentRoute());
        assertFalse(table.findDecision("unknown", topology.p2p).isPresent());
        assertFalse(table.findDecision(
                topology.firstMachine.getId(),
                new OperationalRouteDestination(StationType.P2P, "unknown"))
                .isPresent());
    }

    @Test
    void shouldRequireDecisionAtEveryReachableMachine() {
        Topology topology = topology();
        List<WarehouseTransferRoutingTable.Entry> missing =
                new ArrayList<>(topology.entries());
        missing.removeIf(entry -> entry.transferMachineId().equals(
                topology.secondMachine.getId())
                && entry.destinationTargetId().equals(topology.p2p.targetId()));

        assertThrows(
                IllegalArgumentException.class,
                () -> topology.table(missing));
    }

    @Test
    void shouldRejectDuplicateAndUnknownConfiguration() {
        Topology topology = topology();
        List<WarehouseTransferRoutingTable.Entry> duplicate =
                new ArrayList<>(topology.entries());
        duplicate.add(topology.entries().get(0));
        assertThrows(IllegalArgumentException.class, () -> topology.table(duplicate));

        List<WarehouseTransferRoutingTable.Entry> unknownMachine =
                new ArrayList<>(topology.entries());
        unknownMachine.add(new WarehouseTransferRoutingTable.Entry(
                "unknown", topology.p2p.targetId(),
                TransferRoutingDecision.continueOnCurrentRoute()));
        assertThrows(
                IllegalArgumentException.class,
                () -> topology.table(unknownMachine));

        List<WarehouseTransferRoutingTable.Entry> unknownTarget =
                new ArrayList<>(topology.entries());
        unknownTarget.add(new WarehouseTransferRoutingTable.Entry(
                topology.firstMachine.getId(), "unknown",
                TransferRoutingDecision.continueOnCurrentRoute()));
        assertThrows(
                IllegalArgumentException.class,
                () -> topology.table(unknownTarget));

        assertThrows(
                IllegalArgumentException.class,
                () -> new WarehouseTransferRoutingTable(
                        topology.catalog,
                        List.of(topology.firstMachine, topology.firstMachine),
                        topology.entries()));
    }

    @Test
    void shouldRejectTargetOutsideMachineTopologyAndUnreachableTerminal() {
        Topology topology = topology();
        List<WarehouseTransferRoutingTable.Entry> wrongTarget =
                new ArrayList<>(topology.entries());
        wrongTarget.set(1, new WarehouseTransferRoutingTable.Entry(
                topology.firstMachine.getId(),
                topology.adapting.targetId(),
                TransferRoutingDecision.transferTo(topology.adaptingTarget)));
        assertThrows(IllegalArgumentException.class, () -> topology.table(wrongTarget));

        RouteSegment entry = segment("entry");
        RouteSegment continueSegment = segment("continue");
        RouteSegment branch = segment("branch");
        RouteSegment disconnectedTerminal = segment("disconnected");
        entry.connectTo(continueSegment);
        TransferZoneMachine machine = machine("machine", entry, branch);
        OperationalRouteDestination destination =
                destination(StationType.P2P, "p2p");
        WarehouseRouteCatalog catalog = new WarehouseRouteCatalog(List.of(
                definition(destination, entry, disconnectedTerminal)));

        assertThrows(
                IllegalArgumentException.class,
                () -> new WarehouseTransferRoutingTable(
                        catalog,
                        List.of(machine),
                        List.of(new WarehouseTransferRoutingTable.Entry(
                                machine.getId(),
                                destination.targetId(),
                                TransferRoutingDecision.continueOnCurrentRoute()))));
    }

    @Test
    void shouldValidateConstructorAndEntryArguments() {
        Topology topology = topology();
        assertThrows(
                IllegalArgumentException.class,
                () -> new WarehouseTransferRoutingTable(
                        null, List.of(), List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WarehouseTransferRoutingTable(
                        topology.catalog, null, List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WarehouseTransferRoutingTable(
                        topology.catalog, List.of(), null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WarehouseTransferRoutingTable.Entry(
                        " ", "p2p", TransferRoutingDecision.continueOnCurrentRoute()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WarehouseTransferRoutingTable.Entry(
                        "machine", " ", TransferRoutingDecision.continueOnCurrentRoute()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WarehouseTransferRoutingTable.Entry(
                        "machine", "p2p", null));
    }

    static Topology topology() {
        RouteSegment entry = segment("common-entry");
        RouteSegment thirdPartyTerminal = segment("third-party-terminal");
        RouteSegment sharedBranch = segment("shared-branch");
        RouteSegment p2pTerminal = segment("p2p-terminal");
        RouteSegment adaptingTerminal = segment("adapting-terminal");
        entry.connectTo(thirdPartyTerminal);
        sharedBranch.connectTo(p2pTerminal);

        TransferZoneMachine firstMachine = machine("machine-1", entry, sharedBranch);
        TransferZoneMachine secondMachine =
                machine("machine-2", sharedBranch, adaptingTerminal);
        TransferTarget sharedBranchTarget =
                new TransferTarget(sharedBranch, 0f, TravelDirection.FORWARD);
        TransferTarget adaptingTarget =
                new TransferTarget(adaptingTerminal, 0.25f, TravelDirection.REVERSE);
        OperationalRouteDestination thirdParty =
                destination(StationType.THIRD_PARTY, "third-party");
        OperationalRouteDestination adapting =
                destination(StationType.ADAPTING, "adapting");
        OperationalRouteDestination p2p = destination(StationType.P2P, "p2p");
        WarehouseRouteCatalog catalog = new WarehouseRouteCatalog(List.of(
                definition(thirdParty, entry, thirdPartyTerminal),
                definition(adapting, entry, adaptingTerminal),
                definition(p2p, entry, p2pTerminal)));
        return new Topology(
                catalog,
                firstMachine,
                secondMachine,
                sharedBranchTarget,
                adaptingTarget,
                thirdParty,
                adapting,
                p2p);
    }

    static TransferZoneMachine machine(
            String id,
            RouteSegment source,
            RouteSegment target) {
        TransferTargetDecisionStrategy unusedStrategy =
                (tote, machine) -> Optional.empty();
        TransferZone zone = new TransferZone(
                id + "-zone",
                0f,
                0.5f,
                source,
                target,
                0f,
                GuideSide.LEFT,
                GuideSide.RIGHT,
                unusedStrategy,
                new TransferMotionConfig(1d, 0f, 0.25f));
        return new TransferZoneMachine(id, id + "-approach", id + "-window", zone);
    }

    static RouteSegment segment(String label) {
        return RoutedToteRoutingTestFixtures.routeSegment(label, 2f);
    }

    private static WarehouseRouteDefinition definition(
            OperationalRouteDestination destination,
            RouteSegment entry,
            RouteSegment terminal) {
        return new WarehouseRouteDefinition(
                destination,
                entry,
                0f,
                TravelDirection.FORWARD,
                destination.targetId() + "-sensor",
                terminal);
    }

    private static OperationalRouteDestination destination(
            StationType stationType,
            String targetId) {
        return RoutedToteRoutingTestFixtures.destination(stationType, targetId);
    }

    record Topology(
            WarehouseRouteCatalog catalog,
            TransferZoneMachine firstMachine,
            TransferZoneMachine secondMachine,
            TransferTarget sharedBranchTarget,
            TransferTarget adaptingTarget,
            OperationalRouteDestination thirdParty,
            OperationalRouteDestination adapting,
            OperationalRouteDestination p2p) {

        List<WarehouseTransferRoutingTable.Entry> entries() {
            return List.of(
                    new WarehouseTransferRoutingTable.Entry(
                            firstMachine.getId(),
                            thirdParty.targetId(),
                            TransferRoutingDecision.continueOnCurrentRoute()),
                    new WarehouseTransferRoutingTable.Entry(
                            firstMachine.getId(),
                            adapting.targetId(),
                            TransferRoutingDecision.transferTo(sharedBranchTarget)),
                    new WarehouseTransferRoutingTable.Entry(
                            firstMachine.getId(),
                            p2p.targetId(),
                            TransferRoutingDecision.transferTo(sharedBranchTarget)),
                    new WarehouseTransferRoutingTable.Entry(
                            secondMachine.getId(),
                            adapting.targetId(),
                            TransferRoutingDecision.transferTo(
                                    adaptingTarget,
                                    TransferOrientationPolicy.ALIGN_TO_TARGET_TRAVEL)),
                    new WarehouseTransferRoutingTable.Entry(
                            secondMachine.getId(),
                            p2p.targetId(),
                            TransferRoutingDecision.continueOnCurrentRoute()));
        }

        WarehouseTransferRoutingTable table(
                List<WarehouseTransferRoutingTable.Entry> entries) {
            return new WarehouseTransferRoutingTable(
                    catalog, List.of(firstMachine, secondMachine), entries);
        }
    }
}
