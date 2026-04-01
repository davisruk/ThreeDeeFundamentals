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
	private List<TransferZoneController> transferZoneControllers = new ArrayList<>();
	private TransferMotionState transfer;
	private String controllingEquipmentId;
	
	public Tote(String id, RouteFollower routeFollower, ObjectTransformation transformation) {
		super();
		this.id = id;
		this.routeFollower = routeFollower;
		this.transformation = transformation;
	}

	public Tote(String id, RouteFollower routeFollower, ObjectTransformation transformation, List<TransferZoneController> transferZoneControllers) {
		this(id, routeFollower,transformation);
		this.transferZoneControllers = transferZoneControllers;
	}
	
	public ObjectTransformation getTransformation() {
		return transformation;
	}

	public void addTransferZoneController(TransferZoneController tzc) {
		transferZoneControllers.add(tzc);
	}
	
	@Override
	public String getId() {
		return id;
	}
	
	public void update(SimulationContext context, double dtSeconds) {
		if (interactionMode == ToteInteractionMode.TRANSFERRING) {
			updateTransferMotion(dtSeconds);
			return;
		}

		lastRouteSnapshot = routeFollower.advance(dtSeconds, isMotionBlocked());
		applySnapshot(lastRouteSnapshot);
//		evaluateWarehouseInteractions(context, snapshot);
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
	
	private void evaluateWarehouseInteractions(SimulationContext context, RouteFollowerSnapshot snapshot) {
		for (TransferZoneController tzc:transferZoneControllers) {
			TransferZoneMachine machine = tzc.getMachine();
			// move the following checks into sensors later
			if (!isOnSourceSegment(machine, snapshot)) continue;
			
			float distance = snapshot.distanceAlongSegment();
			if (isInApproachWindow(machine, distance)) {
				tzc.onToteApproaching(this, snapshot);
			}
			if (isInTransferWindow(machine, distance)) {
				transfer = new TransferMotionState(tzc.getMachine(), snapshot.position());
				tzc.onToteEnteredTransferWindow(this);
			}
		}
	}
	
	private void updateTransferMotion(double dt) {
		transfer.update(dt);
		double t = transfer.t();
		t=t*t*(3-2*t); // smooth
		Vec3 start = transfer.getStartPosition();
		Vec3 end = transfer.getTargetSegment().getGeometry().sampleByDistance(0f);
		transformation.setTranslation(Vec3.immutableLerp(start,end,(float)t));
		if (transfer.done()) {
			completeTransfer();
		}
	}
	
	private void completeTransfer() {
		RouteSegment target = transfer.getTargetSegment();
		routeFollower.setCurrentSegment(target);
		routeFollower.setDistanceAlongSegment(0f);
		transfer = null;
		interactionMode = ToteInteractionMode.FREE;
	}
	
	private boolean isInTransferWindow(TransferZoneMachine machine, float distanceAlongSegment) {
		float start = machine.getDefinition().getStartDistance();
		float end = machine.getDefinition().getEndDistance();
		return distanceAlongSegment >= start && distanceAlongSegment <= end;
	}

	private boolean isInApproachWindow(TransferZoneMachine machine, float distanceAlongSegment) {
		float start = machine.getDefinition().getStartDistance();
		float triggerDistance = 0.5f; // make this configurable
		return distanceAlongSegment < start && distanceAlongSegment <= start - triggerDistance;
	}


	private boolean isOnSourceSegment(TransferZoneMachine machine, RouteFollowerSnapshot snapshot) {
		return snapshot.currentSegment()==machine.getDefinition().getSourceSegment();
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

	@Override
	public RouteFollowerSnapshot getLastSnapshot() {
		return lastRouteSnapshot;
	}
}
