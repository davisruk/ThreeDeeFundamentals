package online.davisfamily.warehouse.sim.transfer;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.objects.StatefulSimObject;
import online.davisfamily.warehouse.sim.transfer.TransferZoneMachine.TransferZoneState;

public class TransferZoneMachine implements StatefulSimObject<TransferZoneState>{
	
	public enum TransferZoneState {
		IDLE,
		RESERVED,
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
	private String approachSensorId;
	private String windowSensorId;
	private TransferZoneState state = TransferZoneState.IDLE;
	private String reservedToteId;
	private TransferDirection activeDirection;
	private double timeInStateSeconds;
	
	public TransferZoneMachine(String id, String approachSensorId, String windowSensorId) {
		super();
		this.id = id;
		this.approachSensorId = approachSensorId;
		this.windowSensorId = windowSensorId;
	}
	

	@Override
	public String toString() {
		return "TransferZoneMachine [id=" + id + ", approachSensorId=" + approachSensorId + ", windowSensorId="
				+ windowSensorId + ", state=" + state + ", reservedToteId=" + reservedToteId + ", activeDirection="
				+ activeDirection + ", timeInStateSeconds=" + timeInStateSeconds + "]";
	}

	@Override
	public void update(SimulationContext context, double dtSeconds) {
		timeInStateSeconds += dtSeconds;
	}
	
	public void transitionTo(TransferZoneState newState) {
		state = newState;
		timeInStateSeconds = 0.0;
	}

	public String getReservedToteId() {
		return reservedToteId;
	}

	public void setReservedTote(String reservedToteId) {
		this.reservedToteId = reservedToteId;
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

	public String getApproachSensorId() {
		return approachSensorId;
	}

	public String getWindowSensorId() {
		return windowSensorId;
	}

	public void setReservedToteId(String reservedToteId) {
		this.reservedToteId = reservedToteId;
	}

	public void clearActiveTransfer() {
		reservedToteId = null;
		state = TransferZoneState.IDLE; 
	}


}
