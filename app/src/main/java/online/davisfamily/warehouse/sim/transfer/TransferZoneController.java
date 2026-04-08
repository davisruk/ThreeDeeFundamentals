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

		Tote t = (Tote) context.getTrackedObjects().stream()
				.filter(to -> to.getId().equals(event.getObjectId()))
				.findAny()
				.orElse(null);

		if (machine.getState() == TransferZoneState.RESERVED && t.getId().equals(machine.getReservedToteId())) {
			machine.transitionTo(TransferZoneState.TRANSFERRING);
			RouteSegment targetSegment = machine.getTransferZone().getTargetSegment();
			float targetDistance = 0f;
			t.beginTransfer(machine.getId(), targetSegment, targetDistance, 0.35);
		}
		
		DetectionType d = event.getDetectionType();

		if (event.getDetectionType() != DetectionType.ENTER) return;
		if (machine.getState() != TransferZoneState.RESERVED) return;
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
