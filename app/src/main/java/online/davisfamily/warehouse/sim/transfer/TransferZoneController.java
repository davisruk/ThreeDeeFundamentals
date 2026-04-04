package online.davisfamily.warehouse.sim.transfer;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.SimulationController;
import online.davisfamily.threedee.sim.framework.events.DetectionEvent;
import online.davisfamily.threedee.sim.framework.events.DetectionEvent.DetectionType;
import online.davisfamily.threedee.sim.framework.events.SimulationEventListener;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.transfer.TransferZoneMachine.TransferZoneState;
import online.davisfamily.warehouse.sim.transfer.strategy.TransferDecisionStrategy;

public class TransferZoneController implements SimulationController, SimulationEventListener<DetectionEvent>{
	private final TransferZoneMachine machine;
	private final TransferDecisionStrategy decisionStrategy;
	
	public TransferZoneController(TransferZoneMachine machine, TransferDecisionStrategy decisionStrategy) {
		super();
		this.machine = machine;
		this.decisionStrategy = decisionStrategy;
	}
	
	@Override
	public void handleEvent(DetectionEvent event, SimulationContext context) {
		if (machine.getApproachSensorId().equals(event.getSensorId())) handleApproachSensor(event, context);
		if (machine.getWindowSensorId().equals(event.getSensorId())) handleWindowSensor(event, context);

	}
	
	private void handleApproachSensor(DetectionEvent event, SimulationContext context) {
		if (event.getType() != DetectionType.ENTER) return;
		if (machine.getState() != TransferZoneState.IDLE) return;
		// could check the detected object is a Tote here but not worth it yet
		Tote t = (Tote) context.getTrackedObjects().stream()
				.filter(to -> to.getId().equals(event.getObjectId()))
				.findAny()
				.orElse(null);
		
		var decision = decisionStrategy.decide(t, machine);
		if (decision.isEmpty()) return;
		
		machine.setReservedToteId(t.getId());
		machine.setActiveDirection(decision.get());
		machine.transitionTo(TransferZoneState.RESERVED);
		t.reserveForTransfer(machine);
		System.out.println(machine);
		System.out.println(t);
	}
	private void handleWindowSensor(DetectionEvent event, SimulationContext context) {
		System.out.println(event);
	}

	@Override
	public void update(SimulationContext context, double dtSeconds) {
		// TODO Auto-generated method stub
		
	}
	
	public TransferZoneMachine getMachine() {
		return machine;
	}

}
