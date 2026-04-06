package online.davisfamily.warehouse.sim.transfer;

import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.behaviour.routing.transfer.TransferZone;
import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.threedee.sim.framework.events.DetectionEvent;
import online.davisfamily.threedee.sim.framework.objects.StatefulSimObject;
import online.davisfamily.threedee.sim.framework.objects.sensors.Sensor;
import online.davisfamily.threedee.sim.framework.objects.sensors.WindowSensor;
import online.davisfamily.threedee.sim.framework.objects.sensors.WindowSensorAreaImpl;
import online.davisfamily.warehouse.sim.sensor.MembershipSensor;
import online.davisfamily.warehouse.sim.transfer.TransferZoneMachine.TransferZoneState;
import online.davisfamily.warehouse.sim.transfer.strategy.TransferDecisionStrategy;

public class TransferZoneMachine implements StatefulSimObject<TransferZoneState>{
	
	public enum TransferZoneState {
		IDLE,
		RESERVED,
		ACTIVE,
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
	
	// factory method to create a transfer zone machine with approach and window sensors
	// sensors, controller and machine are registered with simulation 
	// approach window is based on initial distance and the start of the transfer zone
	public static TransferZoneMachine createTransferZoneMachine(SimulationWorld sim, RouteSegment segment, float approachStartDistance, TransferZone tz, TransferDecisionStrategy transferStrategy) {
		String id = tz.getId();
	    WindowSensorAreaImpl m_wsai = new WindowSensorAreaImpl(id + "_member_sensor_area", segment, approachStartDistance, tz.getStartDistance());
	    Sensor tzms = new MembershipSensor(id + "_member_sensor", m_wsai);
	    WindowSensorAreaImpl w_wsai = new WindowSensorAreaImpl(id + "_window_sensor_area", segment, tz.getStartDistance(), tz.getEndDistance());
	    Sensor tzws = new WindowSensor(tz.getId() + "_sensor", w_wsai);
	    TransferZoneMachine tzm = new TransferZoneMachine("Transfer_Machine_" + tz.getId(), tzms.getId(), tzws.getId());
	    TransferZoneController tzc = new TransferZoneController(tzm, transferStrategy);
    	sim.addController(tzc);
	    sim.registerListener(DetectionEvent.class, tzc);
	    sim.addSensor(tzms);
	    sim.addSensor(tzws);
		return tzm;
	}
	
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
