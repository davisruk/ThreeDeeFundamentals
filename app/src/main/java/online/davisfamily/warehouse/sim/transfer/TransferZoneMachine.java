package online.davisfamily.warehouse.sim.transfer;

import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.threedee.sim.framework.events.DetectionEvent;
import online.davisfamily.threedee.sim.framework.objects.StatefulSimObject;
import online.davisfamily.threedee.sim.framework.objects.sensors.Sensor;
import online.davisfamily.threedee.sim.framework.objects.sensors.WindowSensor;
import online.davisfamily.threedee.sim.framework.objects.sensors.WindowSensorAreaImpl;
import online.davisfamily.warehouse.sim.events.TransferCompletedEvent;
import online.davisfamily.warehouse.sim.sensor.MembershipSensor;
import online.davisfamily.warehouse.sim.transfer.TransferZoneMachine.TransferZoneState;
import online.davisfamily.warehouse.sim.transfer.strategy.LegacyTransferDecisionStrategyAdapter;
import online.davisfamily.warehouse.sim.transfer.strategy.TransferDecisionStrategy;
import online.davisfamily.warehouse.sim.transfer.strategy.TransferTargetDecisionStrategy;

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
	
	public enum TransferDecision {
		BRANCH,
		CONTINUE
	}
	
	private final String id;
	private String approachSensorId;
	private String windowSensorId;
	private TransferZoneState state = TransferZoneState.IDLE;
	private String reservedToteId;
	private String toteInWindowId;
	private TransferDecision activeDirection;
	private TransferRoutingDecision activeRoutingDecision;
	private double timeInStateSeconds;
	private final TransferZone transferZone;
	
	// factory method to create a transfer zone machine with approach and window sensors
	// sensors, controller and machine are registered with simulation 
	// approach window is based on initial distance and the start of the transfer zone
	public static TransferZoneMachine createTransferZoneMachine(SimulationWorld sim, RouteSegment approachSegment, float approachStartDistance, TransferZone tz, TransferDecisionStrategy transferStrategy) {
		return createTransferZoneMachine(
				sim,
				approachSegment,
				approachStartDistance,
				tz,
				new LegacyTransferDecisionStrategyAdapter(transferStrategy));
	}

	public static TransferZoneMachine createTransferZoneMachine(
			SimulationWorld sim,
			RouteSegment approachSegment,
			float approachStartDistance,
			TransferZone tz,
			TransferTargetDecisionStrategy transferStrategy) {
		return createTransferZoneMachine(
				sim,
				approachSegment,
				approachStartDistance,
				tz.getRouteSegment(),
				tz,
				transferStrategy);
	}

	public static TransferZoneMachine createTransferZoneMachine(
			SimulationWorld sim,
			RouteSegment approachSegment,
			float approachStartDistance,
			RouteSegment transferWindowSegment,
			TransferZone tz,
			TransferTargetDecisionStrategy transferStrategy) {
		String id = tz.getId();
	    Sensor tzms = null;
	    String approachSensorId = null;
	    if (approachSegment != null) {
	        float approachEndDistance = approachSegment == transferWindowSegment
	                ? tz.getStartDistance()
	                : approachSegment.length();
	    	WindowSensorAreaImpl m_wsai = new WindowSensorAreaImpl(
	    			id + "_member_sensor_area",
	    			approachSegment,
	    			approachStartDistance,
	                approachEndDistance);
	    	tzms = new MembershipSensor(id + "_member_sensor", m_wsai);
	    	approachSensorId = tzms.getId();
	    }
	    WindowSensorAreaImpl w_wsai = new WindowSensorAreaImpl(id + "_window_sensor_area", transferWindowSegment, tz.getStartDistance(), tz.getEndDistance());
	    Sensor tzws = new WindowSensor(tz.getId() + "_sensor", w_wsai);
	    TransferZoneMachine tzm = new TransferZoneMachine("Transfer_Machine_" + tz.getId(), approachSensorId, tzws.getId(), tz);
	    TransferZoneController tzc = new TransferZoneController(tzm, transferStrategy);
    	sim.addController(tzc);
	    sim.registerListener(DetectionEvent.class, tzc.detectionHandler());
	    sim.registerListener(TransferCompletedEvent.class, tzc.completionHandler());
	    if (tzms != null) {
	    	sim.addSensor(tzms);
	    }
	    sim.addSensor(tzws);
		return tzm;
	}
	
	public TransferZoneMachine(String id, String approachSensorId, String windowSensorId, TransferZone tz) {
		super();
		this.id = id;
		this.approachSensorId = approachSensorId;
		this.windowSensorId = windowSensorId;
		this.transferZone = tz;
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

	public TransferDecision getActiveDirection() {
		return activeDirection;
	}

	public void setActiveDirection(TransferDecision activeDirection) {
		this.activeDirection = activeDirection;
	}

	public TransferRoutingDecision getActiveRoutingDecision() {
		return activeRoutingDecision;
	}

	public void setActiveRoutingDecision(TransferRoutingDecision activeRoutingDecision) {
		this.activeRoutingDecision = activeRoutingDecision;
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

	public boolean hasApproachSensor() {
		return approachSensorId != null;
	}

	public String getWindowSensorId() {
		return windowSensorId;
	}

	public void setReservedToteId(String reservedToteId) {
		this.reservedToteId = reservedToteId;
	}

	public void clearActiveTransfer() {
		reservedToteId = null;
		toteInWindowId = null;
		activeDirection = null;
		activeRoutingDecision = null;
		state = TransferZoneState.IDLE; 
	}

	public TransferZone getTransferZone() {
		return transferZone;
	}

	public String getToteInWindowId() {
		return toteInWindowId;
	}

	public void setToteInWindowId(String toteInWindowId) {
		this.toteInWindowId = toteInWindowId;
	}


}
