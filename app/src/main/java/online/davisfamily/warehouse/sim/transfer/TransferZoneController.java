package online.davisfamily.warehouse.sim.transfer;

import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.SimulationController;
import online.davisfamily.threedee.sim.framework.events.DetectionEvent;
import online.davisfamily.threedee.sim.framework.events.DetectionEvent.DetectionType;
import online.davisfamily.threedee.sim.framework.events.SimulationEvent;
import online.davisfamily.threedee.sim.framework.events.SimulationEventListener;
import online.davisfamily.warehouse.sim.events.TransferCompletedEvent;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.transfer.TransferZoneMachine.TransferDecision;
import online.davisfamily.warehouse.sim.transfer.TransferZoneMachine.TransferZoneState;
import online.davisfamily.warehouse.sim.transfer.strategy.TransferDecisionStrategy;

public class TransferZoneController implements SimulationController{
	private final TransferZoneMachine machine;
	private final TransferDecisionStrategy decisionStrategy;
	
	public TransferZoneController(TransferZoneMachine machine, TransferDecisionStrategy decisionStrategy) {
		super();
		this.machine = machine;
		this.decisionStrategy = decisionStrategy;
	}
	
    public SimulationEventListener<DetectionEvent> detectionHandler() {
        return (event, context) -> handleDetection(event, context);
    }

    public SimulationEventListener<TransferCompletedEvent> completionHandler() {
        return (event, context) -> handleTransferCompleted(event, context);
    }
	
	public void handleDetection(DetectionEvent  event, SimulationContext context) {
		if (machine.getApproachSensorId().equals(event.getSensorId())) handleApproachSensor(event, context);
		if (machine.getWindowSensorId().equals(event.getSensorId())) handleWindowSensor(event, context);
	}
	
	public void handleTransferCompleted(TransferCompletedEvent  event, SimulationContext context) {
		if (!machine.getId().equals(event.sourceId)) return;
		
		if (!event.toteId.equals(machine.getReservedToteId())) return;
		machine.setReservedToteId(null);
		machine.setActiveDirection(null);
		machine.transitionTo(TransferZoneState.IDLE);
	}
	
	private void handleApproachSensor(DetectionEvent event, SimulationContext context) {
		// This controller still resolves the Tote by event object id rather than
		// receiving the object reference directly from the event. That keeps the
		// current event bus simple, but it leaves warehouse transfer startup
		// dependent on id consistency between the tracked sim object and whatever
		// upstream component published the snapshot/event identity.
		DetectionType d = event.getDetectionType();
		Tote t = (Tote) context.getTrackedObjects().stream()
				.filter(to -> to.getId().equals(event.getObjectId()))
				.findAny()
				.orElse(null);

		if (event.getDetectionType() != DetectionType.ENTER) return;
		if (machine.getState() != TransferZoneState.IDLE) return;
		// could check the detected object is a Tote here but not worth it yet
		
		var decision = decisionStrategy.decide(t, machine);
		if (decision.isEmpty()) return;
		
		machine.setReservedToteId(t.getId());
		machine.setActiveDirection(decision.get());
		if (machine.getActiveDirection().equals(TransferDecision.BRANCH)) {
			machine.transitionTo(TransferZoneState.RESERVED);
			t.reserveForTransfer(machine);
		}
	}
	
	private void handleWindowSensor(DetectionEvent event, SimulationContext context) {

		// Same lookup fragility as handleApproachSensor(...). This remains
		// intentionally unchanged until the wider event identity/threading
		// direction is decided.
		Tote t = (Tote) context.getTrackedObjects().stream()
				.filter(to -> to.getId().equals(event.getObjectId()))
				.findAny()
				.orElse(null);
		if (event.getDetectionType() != DetectionType.ENTER) return;
		if (machine.getState() != TransferZoneState.RESERVED) return;

		if (machine.getState() == TransferZoneState.RESERVED && t.getId().equals(machine.getReservedToteId())) {
			machine.transitionTo(TransferZoneState.TRANSFERRING);
			TransferZone tz = machine.getTransferZone();
			t.beginTransfer(machine.getId(),
					tz.getTargetSegment(),
					tz.getSourceSegment(),
					tz.getCentrePoint(),
					tz.getTargetStartDistance(),
					tz.getMotionConfig());
		}
		
		machine.transitionTo(TransferZoneState.ACTIVE);
	}

	@Override
	public void update(SimulationContext context, double dtSeconds) {
		// TODO Auto-generated method stub
		
	}
	
	public TransferZoneMachine getMachine() {
		return machine;
	}

}
