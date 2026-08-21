package online.davisfamily.warehouse.sim.dsp.transport.routing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.transfer.TransferRoutingDecision;
import online.davisfamily.warehouse.sim.transfer.TransferZoneMachine;

class DestinationAwareTransferTargetDecisionStrategyTest {

    @Test
    void shouldResolveExactActiveDestinationDecisionAndClearOldBlock() {
        WarehouseTransferRoutingTableTest.Topology topology =
                WarehouseTransferRoutingTableTest.topology();
        WarehouseTransferRoutingTable table = topology.table(topology.entries());
        WarehouseTransportInFlightRegistry registry =
                new WarehouseTransportInFlightRegistry(2);
        DestinationAwareTransferTargetDecisionStrategy strategy =
                new DestinationAwareTransferTargetDecisionStrategy(
                        topology.firstMachine().getId(), table, registry);
        RoutedPhysicalTote unknown = RoutedToteRoutingTestFixtures.routedTote(
                "unknown", topology.adapting());
        assertTrue(strategy.decide(unknown.tote(), topology.firstMachine()).isEmpty());
        assertTrue(strategy.blocked());

        RoutedPhysicalTote active = RoutedToteRoutingTestFixtures.routedTote(
                "active", topology.adapting());
        registry.register(active);
        TransferRoutingDecision decision = strategy
                .decide(active.tote(), topology.firstMachine())
                .orElseThrow();

        assertSame(topology.sharedBranchTarget(), decision.transferTarget());
        assertFalse(strategy.blocked());
        assertTrue(strategy.blockedPhysicalToteId().isEmpty());
    }

    @Test
    void shouldRejectCopyWithActiveIdentityAndWrongMachine() {
        WarehouseTransferRoutingTableTest.Topology topology =
                WarehouseTransferRoutingTableTest.topology();
        WarehouseTransferRoutingTable table = topology.table(topology.entries());
        WarehouseTransportInFlightRegistry registry =
                new WarehouseTransportInFlightRegistry(1);
        RoutedPhysicalTote active = RoutedToteRoutingTestFixtures.routedTote(
                "active", topology.p2p());
        RoutedPhysicalTote copy = RoutedToteRoutingTestFixtures.routedTote(
                "active", topology.p2p());
        registry.register(active);
        DestinationAwareTransferTargetDecisionStrategy strategy =
                new DestinationAwareTransferTargetDecisionStrategy(
                        topology.firstMachine().getId(), table, registry);

        assertTrue(strategy.decide(copy.tote(), topology.firstMachine()).isEmpty());
        assertTrue(strategy.blockedReason().contains("exact active"));
        assertTrue(strategy.decide(active.tote(), topology.secondMachine()).isEmpty());
        assertTrue(strategy.blockedReason().contains("wrong machine"));
    }

    @Test
    void shouldReturnEmptyWhenConfiguredMachineHasNoTableEntry() {
        WarehouseTransferRoutingTableTest.Topology topology =
                WarehouseTransferRoutingTableTest.topology();
        WarehouseTransferRoutingTable table = topology.table(topology.entries());
        WarehouseTransportInFlightRegistry registry =
                new WarehouseTransportInFlightRegistry(1);
        RoutedPhysicalTote active = RoutedToteRoutingTestFixtures.routedTote(
                "active", topology.p2p());
        registry.register(active);
        TransferZoneMachine unknownMachine = WarehouseTransferRoutingTableTest.machine(
                "unknown-machine",
                WarehouseTransferRoutingTableTest.segment("source"),
                WarehouseTransferRoutingTableTest.segment("target"));
        DestinationAwareTransferTargetDecisionStrategy strategy =
                new DestinationAwareTransferTargetDecisionStrategy(
                        unknownMachine.getId(), table, registry);

        assertTrue(strategy.decide(active.tote(), unknownMachine).isEmpty());
        assertTrue(strategy.blockedReason().contains("No transfer decision"));
    }

    @Test
    void shouldValidateDependenciesAndArguments() {
        WarehouseTransferRoutingTableTest.Topology topology =
                WarehouseTransferRoutingTableTest.topology();
        WarehouseTransferRoutingTable table = topology.table(topology.entries());
        WarehouseTransportInFlightRegistry registry =
                new WarehouseTransportInFlightRegistry(1);
        DestinationAwareTransferTargetDecisionStrategy strategy =
                new DestinationAwareTransferTargetDecisionStrategy(
                        topology.firstMachine().getId(), table, registry);

        assertThrows(IllegalArgumentException.class,
                () -> strategy.decide(null, topology.firstMachine()));
        Tote tote = RoutedToteRoutingTestFixtures.routedTote(
                "tote", topology.p2p()).tote();
        assertThrows(IllegalArgumentException.class, () -> strategy.decide(tote, null));
        assertThrows(IllegalArgumentException.class,
                () -> new DestinationAwareTransferTargetDecisionStrategy(
                        " ", table, registry));
        assertThrows(IllegalArgumentException.class,
                () -> new DestinationAwareTransferTargetDecisionStrategy(
                        "machine", null, registry));
        assertThrows(IllegalArgumentException.class,
                () -> new DestinationAwareTransferTargetDecisionStrategy(
                        "machine", table, null));
    }
}
