package online.davisfamily.warehouse.sim.transfer.strategy;

import java.util.Optional;

import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.transfer.TransferZoneMachine;
import online.davisfamily.warehouse.sim.transfer.TransferZoneMachine.TransferDirection;

public class ToggleStrategy implements TransferDecisionStrategy {
	private boolean transfer = true;

	
	public ToggleStrategy(boolean initialState) {
		super();
		this.transfer = initialState;
	}

	@Override
	public Optional<TransferDirection> decide(Tote tote, TransferZoneMachine machine) {
		Optional<TransferDirection> ret = transfer ? Optional.of(TransferDirection.LEFT):Optional.of(TransferDirection.RIGHT);
		transfer = !transfer;
		return ret;
	}

}
