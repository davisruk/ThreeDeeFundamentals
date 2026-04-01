package online.davisfamily.warehouse.sim.transfer;

import java.util.Optional;

import online.davisfamily.threedee.behaviour.routing.transfer.RouteFollowerSnapshot;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.tote.Tote.ToteInteractionMode;
import online.davisfamily.warehouse.sim.transfer.TransferZoneMachine.TransferDirection;
import online.davisfamily.warehouse.sim.transfer.TransferZoneMachine.TransferZoneState;
import online.davisfamily.warehouse.sim.transfer.strategy.TransferDecisionStrategy;

public class TransferZoneController {
	private final TransferZoneMachine machine;
	private final TransferDecisionStrategy decisionStrategy;
	
	public TransferZoneController(TransferZoneMachine machine, TransferDecisionStrategy decisionStrategy) {
		super();
		this.machine = machine;
		this.decisionStrategy = decisionStrategy;
	}
	
	public void update(double dtSeconds) {
		machine.update(dtSeconds);
		if (machine.getState() == TransferZoneState.RESETTING && machine.getTimeInStateSeconds() > 0.1) {
			machine.clearActiveTransfer();
		}
	}
	
	public void onToteApproaching(Tote tote, RouteFollowerSnapshot snapshot) {
		if (machine.getState() != TransferZoneState.IDLE) {
			return;
		}
		Optional<TransferDirection> decision = decisionStrategy.decide(tote, machine);
		machine.setReservedTote(tote);
		machine.setActiveDirection(decision.get());
		
		tote.setInteractionMode(ToteInteractionMode.RESERVED_FOR_TRANSFER);
		tote.setControllingTransferZone(machine);
		
		machine.transitionTo(
				decision.get() == TransferDirection.LEFT ? 
				TransferZoneState.READY_LEFT :
				TransferZoneState.READY_RIGHT
		);
	}
	
	public void onToteEnteredTransferWindow(Tote tote) {
		if (machine.getReservedTote() != tote) {
			return;
		}
		tote.setInteractionMode(ToteInteractionMode.TRANSFERRING);
		machine.transitionTo(TransferZoneState.TRANSFERRING);
	}
	
	public void onTransferComplete(Tote tote) {
		if (machine.getReservedTote() != tote) {
			return;
		}
		tote.setInteractionMode(ToteInteractionMode.FREE);
		tote.setControllingTransferZone(null);
		machine.transitionTo(TransferZoneState.RESETTING);
	}
	
	public TransferZoneMachine getMachine() {
		return machine;
	}
}
