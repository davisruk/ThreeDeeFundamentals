package online.davisfamily.warehouse.sim.transfer.strategy;

import java.util.Optional;

import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.transfer.TransferZoneMachine;
import online.davisfamily.warehouse.sim.transfer.TransferZoneMachine.TransferDirection;

public class AlwaysTransferStrategy implements TransferDecisionStrategy {

	@Override
	public Optional<TransferDirection> decide(Tote tote, TransferZoneMachine machine) {
		// TODO Auto-generated method stub
		return Optional.of(TransferDirection.RIGHT);
	}

}
