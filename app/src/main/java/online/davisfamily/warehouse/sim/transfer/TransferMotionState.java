package online.davisfamily.warehouse.sim.transfer;

import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.matrices.Vec3;

public class TransferMotionState {
	private final TransferZoneMachine machine;
	private final Vec3 startPosition;
	private final RouteSegment targetSegment;
	
	private double elapsedSeconds;

	public TransferMotionState(TransferZoneMachine machine, Vec3 startPosition) {
		super();
		this.machine = machine;
		this.startPosition = startPosition;
		this.targetSegment = machine.getDefinition().getTargetSegment();
	}
	
	public void update(double dtSeconds) {
		elapsedSeconds += dtSeconds;
	}
	
	public double t() {return Math.min(1.0, elapsedSeconds / 0.5);}
	public double getElapsedSeconds() {
		return elapsedSeconds;
	}

	public boolean done() {return t() >= 1.0;}
	public TransferZoneMachine getMachine() {
		return machine;
	}

	public Vec3 getStartPosition() {
		return startPosition;
	}

	public RouteSegment getTargetSegment() {
		return targetSegment;
	}

}
