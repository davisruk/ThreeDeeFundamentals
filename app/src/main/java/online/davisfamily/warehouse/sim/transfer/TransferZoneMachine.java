package online.davisfamily.warehouse.sim.transfer;

import online.davisfamily.threedee.behaviour.routing.TransferZone;
import online.davisfamily.warehouse.sim.tote.Tote;

public class TransferZoneMachine {
	
	public enum TransferZoneState {
		IDLE,
		READY_LEFT,
		READY_RIGHT,
		TRANSFERRING,
		RESETTING,
		BLOCKED
	}
	
	public enum TransferDirection {
		LEFT,
		RIGHT
	}
	
	private final String id;
	private final TransferZone definition;
	
	private TransferZoneState state = TransferZoneState.IDLE;
	private Tote reservedTote;
	private TransferDirection activeDirection;
	private double timeInStateSeconds;
	
	public TransferZoneMachine(String id, TransferZone definition) {
		super();
		this.id = id;
		this.definition = definition;
	}
	
	public void update(double dtSeconds) {
		timeInStateSeconds += dtSeconds;
	}
	
	public void transitionTo(TransferZoneState newState) {
		state = newState;
		timeInStateSeconds = 0.0;
	}

	public Tote getReservedTote() {
		return reservedTote;
	}

	public void setReservedTote(Tote reservedTote) {
		this.reservedTote = reservedTote;
	}

	public TransferDirection getActiveDirection() {
		return activeDirection;
	}

	public void setActiveDirection(TransferDirection activeDirection) {
		this.activeDirection = activeDirection;
	}

	public String getId() {
		return id;
	}

	public TransferZoneState getState() {
		return state;
	}

	public double getTimeInStateSeconds() {
		return timeInStateSeconds;
	}
	
	

}
