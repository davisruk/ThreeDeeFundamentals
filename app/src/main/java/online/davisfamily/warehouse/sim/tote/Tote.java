package online.davisfamily.warehouse.sim.tote;

import java.util.ArrayList;
import java.util.List;

import online.davisfamily.threedee.behaviour.routing.RouteFollower;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.behaviour.routing.transfer.RouteFollowerSnapshot;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation.Axis;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.TrackableObject;
import online.davisfamily.warehouse.sim.transfer.TransferMotionState;
import online.davisfamily.warehouse.sim.transfer.TransferZoneController;
import online.davisfamily.warehouse.sim.transfer.TransferZoneMachine;

public class Tote implements TrackableObject {
	
	public enum ToteInteractionMode {
		FREE, RESERVED_FOR_TRANSFER, RESERVED,STOPPED,TRANSFERRING,STATION_HELD
	}
	
	private final String id;
	private final RouteFollower routeFollower;
	private RouteFollowerSnapshot lastRouteSnapshot;
	private final ObjectTransformation transformation;
	private ToteInteractionMode interactionMode = ToteInteractionMode.FREE;
	
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
		return interactionMode != ToteInteractionMode.FREE;
	}
	
	private void applySnapshot(RouteFollowerSnapshot snapshot) {
		transformation.setTranslation(snapshot.position());
	    transformation.setAxisRotation(Axis.Y, Vec3.yawFromDirection(snapshot.forward())); // yaw
		//transformation.setAxisRotation(Axis.X, snapshot.up().x); //pitch
	}
	
	public ToteInteractionMode getInteractionMode() {
		return interactionMode;
	}

	public void setInteractionMode(ToteInteractionMode interactionMode) {
		this.interactionMode = interactionMode;
	}

	public RouteFollower getRouteFollower() {
		return routeFollower;
	}

	@Override
	public RouteFollowerSnapshot getLastSnapshot() {
		return lastRouteSnapshot;
	}
}
