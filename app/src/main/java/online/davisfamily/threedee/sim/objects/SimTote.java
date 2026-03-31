package online.davisfamily.threedee.sim.objects;

import online.davisfamily.threedee.behaviour.routing.RouteFollower;
import online.davisfamily.threedee.behaviour.routing.transfer.RouteFollowerSnapshot;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation.Axis;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.sim.framework.SimObject;
import online.davisfamily.threedee.sim.framework.SimulationContext;

public class SimTote implements SimObject {
	
	public enum ToteInteractionMode {
		FREE,RESERVED,STOPPED,TRANSFERRING,STATION_HELD
	}
	
	private final String id;
	private final RouteFollower routeFollower;
	private final ObjectTransformation transformation;
	
	private ToteInteractionMode interactionMode = ToteInteractionMode.FREE;
	private String controllingEquipmentId;
	
	public SimTote(String id, RouteFollower routeFollower, ObjectTransformation transformation) {
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
		//routeFollower.update(context, dtSeconds, this);
		RouteFollowerSnapshot snapshot = routeFollower.advance(dtSeconds, isMotionBlocked());
		applySnapshot(snapshot);
		evaluateWarehouseInteractions(context, snapshot);
	}
	
	private boolean isMotionBlocked() {
		return interactionMode != ToteInteractionMode.FREE;
	}
	
	private void applySnapshot(RouteFollowerSnapshot snapshot) {
		transformation.setTranslation(snapshot.position());
	    transformation.setAxisRotation(Axis.Y, Vec3.yawFromDirection(snapshot.forward())); // yaw
		//transformation.setAxisRotation(Axis.X, snapshot.up().x); //pitch
	}
	
	private void evaluateWarehouseInteractions(SimulationContext context, RouteFollowerSnapshot snapshot) {
		
	}
	
	public ToteInteractionMode getInteractionMode() {
		return interactionMode;
	}

	public void setInteractionMode(ToteInteractionMode interactionMode) {
		this.interactionMode = interactionMode;
	}

	public String getControllingEquipmentId() {
		return controllingEquipmentId;
	}

	public void setControllingEquipmentId(String controllingEquipmentId) {
		this.controllingEquipmentId = controllingEquipmentId;
	}

	public RouteFollower getRouteFollower() {
		return routeFollower;
	}
}
