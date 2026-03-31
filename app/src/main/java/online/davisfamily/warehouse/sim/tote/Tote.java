package online.davisfamily.warehouse.sim.tote;

import online.davisfamily.threedee.behaviour.routing.RouteFollower;
import online.davisfamily.threedee.behaviour.routing.TransferZone;
import online.davisfamily.threedee.behaviour.routing.transfer.RouteFollowerSnapshot;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation.Axis;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.sim.framework.SimObject;
import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.warehouse.sim.transfer.TransferZoneMachine;

public class Tote implements SimObject {
	
	public enum ToteInteractionMode {
		FREE,RESERVED_FOR_TRANSFER, RESERVED,STOPPED,TRANSFERRING,STATION_HELD
	}
	
	private final String id;
	private final RouteFollower routeFollower;
	private final ObjectTransformation transformation;
	
	private ToteInteractionMode interactionMode = ToteInteractionMode.FREE;
	private TransferZoneMachine controllingTransferZone;
	
	private String controllingEquipmentId;
	
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

	public TransferZoneMachine getControllingTransferZone() {
		return controllingTransferZone;
	}

	public void setControllingTransferZone(TransferZoneMachine controllingTransferZone) {
		this.controllingTransferZone = controllingTransferZone;
	}

}
