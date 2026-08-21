package online.davisfamily.warehouse.sim.dsp.transport.routing;

import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.transfer.TransferRoutingDecision;
import online.davisfamily.warehouse.sim.transfer.TransferZoneMachine;
import online.davisfamily.warehouse.sim.transfer.strategy.TransferTargetDecisionStrategy;

public final class DestinationAwareTransferTargetDecisionStrategy
        implements TransferTargetDecisionStrategy {
    private final String transferMachineId;
    private final WarehouseTransferRoutingTable routingTable;
    private final WarehouseTransportInFlightRegistry inFlightRegistry;
    private Optional<PhysicalToteId> blockedPhysicalToteId = Optional.empty();
    private String blockedReason = "";

    public DestinationAwareTransferTargetDecisionStrategy(
            String transferMachineId,
            WarehouseTransferRoutingTable routingTable,
            WarehouseTransportInFlightRegistry inFlightRegistry) {
        if (transferMachineId == null || transferMachineId.isBlank()) {
            throw new IllegalArgumentException("transferMachineId must not be blank");
        }
        if (routingTable == null) {
            throw new IllegalArgumentException("routingTable must not be null");
        }
        if (inFlightRegistry == null) {
            throw new IllegalArgumentException("inFlightRegistry must not be null");
        }
        this.transferMachineId = transferMachineId.trim();
        this.routingTable = routingTable;
        this.inFlightRegistry = inFlightRegistry;
    }

    @Override
    public Optional<TransferRoutingDecision> decide(
            Tote tote,
            TransferZoneMachine machine) {
        if (tote == null) {
            throw new IllegalArgumentException("tote must not be null");
        }
        if (machine == null) {
            throw new IllegalArgumentException("machine must not be null");
        }
        PhysicalToteId physicalToteId = new PhysicalToteId(tote.getId());
        if (!transferMachineId.equals(machine.getId())) {
            recordBlock(physicalToteId, "Transfer strategy received the wrong machine");
            return Optional.empty();
        }

        Optional<RoutedPhysicalTote> optionalRoutedTote =
                inFlightRegistry.find(physicalToteId);
        if (optionalRoutedTote.isEmpty()) {
            recordBlock(physicalToteId, "Tote is not active in warehouse transport");
            return Optional.empty();
        }
        RoutedPhysicalTote routedTote = optionalRoutedTote.orElseThrow();
        if (routedTote.tote() != tote) {
            recordBlock(physicalToteId, "Detected tote is not the exact active tote");
            return Optional.empty();
        }

        Optional<TransferRoutingDecision> decision = routingTable.findDecision(
                transferMachineId, routedTote.destination());
        if (decision.isEmpty()) {
            recordBlock(physicalToteId, "No transfer decision is configured for destination");
            return Optional.empty();
        }
        clearBlock();
        return decision;
    }

    public Optional<PhysicalToteId> blockedPhysicalToteId() {
        return blockedPhysicalToteId;
    }

    public String blockedReason() {
        return blockedReason;
    }

    public boolean blocked() {
        return blockedPhysicalToteId.isPresent();
    }

    private void recordBlock(PhysicalToteId physicalToteId, String reason) {
        blockedPhysicalToteId = Optional.of(physicalToteId);
        blockedReason = reason;
    }

    private void clearBlock() {
        blockedPhysicalToteId = Optional.empty();
        blockedReason = "";
    }
}
