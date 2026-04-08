package online.davisfamily.warehouse.sim.transfer.strategy;

import java.util.Optional;

import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.transfer.TransferZoneMachine;
import online.davisfamily.warehouse.sim.transfer.TransferZoneMachine.TransferDecision;

public class ToggleStrategy implements TransferDecisionStrategy {
	private boolean transfer = true;

	
	public ToggleStrategy(boolean initialState) {
		super();
		this.transfer = initialState;
	}

	@Override
	public Optional<TransferDecision> decide(Tote tote, TransferZoneMachine machine) {
		Optional<TransferDecision> ret = transfer ? Optional.of(TransferDecision.BRANCH):Optional.of(TransferDecision.CONTINUE);
		transfer = !transfer;
		return ret;
	}

}
