package online.davisfamily.warehouse.sim.transfer;

import java.util.Optional;

import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.transfer.TransferZoneMachine.TransferDirection;

public interface TransferDecisionStrategy {
	Optional<TransferDirection> decide (Tote tote, TransferZoneMachine machine);
}
