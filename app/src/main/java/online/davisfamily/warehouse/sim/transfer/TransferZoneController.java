package online.davisfamily.warehouse.sim.transfer;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.SimulationController;
import online.davisfamily.threedee.sim.framework.events.DetectionEvent;
import online.davisfamily.threedee.sim.framework.events.DetectionEvent.DetectionType;
import online.davisfamily.threedee.sim.framework.events.SimulationEventListener;
import online.davisfamily.warehouse.sim.events.TransferCompletedEvent;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.transfer.TransferZoneMachine.TransferDecision;
import online.davisfamily.warehouse.sim.transfer.TransferZoneMachine.TransferZoneState;
import online.davisfamily.warehouse.sim.transfer.mechanism.TransferZoneMechanism;
import online.davisfamily.warehouse.sim.transfer.strategy.LegacyTransferDecisionStrategyAdapter;
import online.davisfamily.warehouse.sim.transfer.strategy.TransferDecisionStrategy;
import online.davisfamily.warehouse.sim.transfer.strategy.TransferTargetDecisionStrategy;

public class TransferZoneController implements SimulationController{
	private final TransferZoneMachine machine;
	private final TransferTargetDecisionStrategy decisionStrategy;
	
	public TransferZoneController(TransferZoneMachine machine, TransferDecisionStrategy decisionStrategy) {
		this(machine, new LegacyTransferDecisionStrategyAdapter(decisionStrategy));
	}

	public TransferZoneController(TransferZoneMachine machine, TransferTargetDecisionStrategy decisionStrategy) {
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
		if (machine.hasApproachSensor() && machine.getApproachSensorId().equals(event.getSensorId())) handleApproachSensor(event, context);
		if (machine.getWindowSensorId().equals(event.getSensorId())) handleWindowSensor(event, context);
	}
	
	public void handleTransferCompleted(TransferCompletedEvent  event, SimulationContext context) {
		if (!machine.getId().equals(event.sourceId)) return;
		
		if (!event.toteId.equals(machine.getReservedToteId())) return;
		machine.clearActiveTransfer();
	}
	
	private void handleApproachSensor(DetectionEvent event, SimulationContext context) {
		// This controller still resolves the Tote by event object id rather than
		// receiving the object reference directly from the event. That keeps the
		// current event bus simple, but it leaves warehouse transfer startup
		// dependent on id consistency between the tracked sim object and whatever
		// upstream component published the snapshot/event identity.
		Tote t = (Tote) context.getTrackedObjects().stream()
				.filter(to -> to.getId().equals(event.getObjectId()))
				.findAny()
				.orElse(null);

		if (event.getDetectionType() != DetectionType.ENTER) return;
		if (machine.getState() != TransferZoneState.IDLE) return;
		if (t == null) return;
		// could check the detected object is a Tote here but not worth it yet
		
		var routingDecision = decisionStrategy.decide(t, machine);
		if (routingDecision.isEmpty()) return;
		TransferRoutingDecision selectedDecision = routingDecision.get();
		
		commandMechanisms(selectedDecision.isTransfer() ? TransferOutcome.BRANCH : TransferOutcome.CONTINUE);
		machine.setReservedToteId(t.getId());
		machine.setActiveDirection(selectedDecision.isTransfer() ? TransferDecision.BRANCH : TransferDecision.CONTINUE);
		machine.setActiveRoutingDecision(selectedDecision);
		if (machine.getActiveDirection().equals(TransferDecision.BRANCH)) {
			machine.transitionTo(TransferZoneState.RESERVED);
			t.reserveForTransfer(machine);
		} else {
			machine.setReservedToteId(null);
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
		if (t == null) return;

		if (event.getDetectionType() == DetectionType.ENTER
				&& !machine.hasApproachSensor()
				&& machine.getState() == TransferZoneState.IDLE) {
			attemptReservation(t);
		}

		if (event.getDetectionType() == DetectionType.ENTER
				&& machine.getState() == TransferZoneState.RESERVED
				&& t.getId().equals(machine.getReservedToteId())) {
			machine.setToteInWindowId(t.getId());
			attemptBeginTransfer(context, t);
			return;
		}

		if (event.getDetectionType() == DetectionType.EXIT
				&& t.getId().equals(machine.getReservedToteId())
				&& t.getId().equals(machine.getToteInWindowId())) {
			machine.clearActiveTransfer();
		}
	}

	@Override
	public void update(SimulationContext context, double dtSeconds) {
		updateMechanisms(dtSeconds);
		attemptBeginTransfer(context, resolveReservedTote(context));
		
	}
	
	public TransferZoneMachine getMachine() {
		return machine;
	}

	private void updateMechanisms(double dtSeconds) {
		for (TransferZoneMechanism mechanism : machine.getTransferZone().getMechanisms()) {
			mechanism.update(dtSeconds);
		}
	}

	private void commandMechanisms(TransferOutcome outcome) {
		for (TransferZoneMechanism mechanism : machine.getTransferZone().getMechanisms()) {
			mechanism.command(outcome);
		}
	}

	private boolean mechanismsReadyFor(TransferOutcome outcome) {
		for (TransferZoneMechanism mechanism : machine.getTransferZone().getMechanisms()) {
			if (!mechanism.isReadyFor(outcome)) {
				return false;
			}
		}
		return true;
	}

	private Tote resolveReservedTote(SimulationContext context) {
		if (machine.getReservedToteId() == null) {
			return null;
		}
		return (Tote) context.getTrackedObjects().stream()
				.filter(to -> to.getId().equals(machine.getReservedToteId()))
				.findAny()
				.orElse(null);
	}

	private void attemptReservation(Tote tote) {
		var routingDecision = decisionStrategy.decide(tote, machine);
		if (routingDecision.isEmpty()) return;
		TransferRoutingDecision selectedDecision = routingDecision.get();

		commandMechanisms(selectedDecision.isTransfer() ? TransferOutcome.BRANCH : TransferOutcome.CONTINUE);
		machine.setReservedToteId(tote.getId());
		machine.setActiveDirection(selectedDecision.isTransfer() ? TransferDecision.BRANCH : TransferDecision.CONTINUE);
		machine.setActiveRoutingDecision(selectedDecision);
		if (machine.getActiveDirection().equals(TransferDecision.BRANCH)) {
			machine.transitionTo(TransferZoneState.RESERVED);
			tote.reserveForTransfer(machine);
		} else {
			machine.setReservedToteId(null);
		}
	}

	private void attemptBeginTransfer(SimulationContext context, Tote tote) {
		if (tote == null) return;
		if (machine.getState() != TransferZoneState.RESERVED) return;
		if (!tote.getId().equals(machine.getReservedToteId())) return;
		if (!tote.getId().equals(machine.getToteInWindowId())) return;
		if (machine.getActiveDirection() != TransferDecision.BRANCH) return;
		if (machine.getActiveRoutingDecision() == null) return;
		if (!machine.getActiveRoutingDecision().isTransfer()) return;
		if (!mechanismsReadyFor(TransferOutcome.BRANCH)) return;
		if (tote.getLastSnapshot() == null) return;

		TransferZone tz = machine.getTransferZone();
		if (!tz.contains(tote.getLastSnapshot().distanceAlongSegment())) return;
		TransferTarget target = machine.getActiveRoutingDecision().transferTarget();

		machine.transitionTo(TransferZoneState.TRANSFERRING);
		tote.beginTransfer(machine.getId(),
				target.segment(),
				tz.getSourceSegment(),
				tz.getCentrePoint(),
				target.entryDistance(),
				target.travelDirection(),
				machine.getActiveRoutingDecision().orientationPolicy(),
				tz.getMotionConfig());
	}

}
