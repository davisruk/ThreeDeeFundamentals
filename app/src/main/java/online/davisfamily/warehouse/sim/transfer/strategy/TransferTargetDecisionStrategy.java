package online.davisfamily.warehouse.sim.transfer.strategy;

import java.util.Optional;

import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.transfer.TransferRoutingDecision;
import online.davisfamily.warehouse.sim.transfer.TransferZoneMachine;

public interface TransferTargetDecisionStrategy {
    Optional<TransferRoutingDecision> decide(Tote tote, TransferZoneMachine machine);
}
