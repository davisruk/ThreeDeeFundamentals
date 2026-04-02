package online.davisfamily.warehouse.sim.tote;

import online.davisfamily.threedee.behaviour.routing.RouteFollower;
import online.davisfamily.threedee.behaviour.routing.transfer.RouteFollowerSnapshot;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation.Axis;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.objects.TrackableObject;
import online.davisfamily.warehouse.sim.transfer.TransferZoneMachine;

public class Tote implements TrackableObject {
	
	public enum ToteMotionState {
		MOVING, HELD, BLOCKED, TRANSFERRING
	}
	
	private final String id;
	private final RouteFollower routeFollower;
	private RouteFollowerSnapshot lastRouteSnapshot;
	private final ObjectTransformation transformation;
	private ToteMotionState interactionMode = ToteMotionState.MOVING;
	private String reservedByMachineId;
	
	public Tote(String id, RouteFollower routeFollower, ObjectTransformation transformation) {
		super();
		this.id = id;
		this.routeFollower = routeFollower;
		this.transformation = transformation;
	}

	public ObjectTransformation getTransformation() {
		return transformation;
	}

	@Override
	public String getId() {
		return id;
	}
	
	public void update(SimulationContext context, double dtSeconds) {
		lastRouteSnapshot = routeFollower.advance(dtSeconds, isMotionBlocked());
		applySnapshot(lastRouteSnapshot);
	}
	
	private boolean isMotionBlocked() {
		//return false;
		return interactionMode == ToteMotionState.BLOCKED;
	}
	
	private void applySnapshot(RouteFollowerSnapshot snapshot) {
		transformation.setTranslation(snapshot.position());
	    transformation.setAxisRotation(Axis.Y, Vec3.yawFromDirection(snapshot.forward())); // yaw
		//transformation.setAxisRotation(Axis.X, snapshot.up().x); //pitch
	}
	
	public void reserveForTransfer(TransferZoneMachine machine) {
		if (reservedByMachineId == null) reservedByMachineId = machine.getId();
	}
	
	public ToteMotionState getInteractionMode() {
		return interactionMode;
	}

	public void setInteractionMode(ToteMotionState interactionMode) {
		this.interactionMode = interactionMode;
	}

	public RouteFollower getRouteFollower() {
		return routeFollower;
	}

	@Override
	public RouteFollowerSnapshot getLastSnapshot() {
		return lastRouteSnapshot;
	}

	@Override
	public String toString() {
		return "Tote [id=" + id + ", interactionMode=" + interactionMode + ", reservedByMachineId="
				+ reservedByMachineId + "]";
	}
	
	
}
