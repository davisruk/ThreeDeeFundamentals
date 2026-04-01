package online.davisfamily.warehouse.sim.tote;

import java.util.ArrayList;
import java.util.List;

import online.davisfamily.threedee.behaviour.routing.RouteFollower;
import online.davisfamily.threedee.behaviour.routing.transfer.RouteFollowerSnapshot;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation.Axis;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.sim.framework.SimObject;
import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.warehouse.sim.transfer.TransferZoneController;
import online.davisfamily.warehouse.sim.transfer.TransferZoneMachine;

public class Tote implements SimObject {
	
	public enum ToteInteractionMode {
		FREE, RESERVED_FOR_TRANSFER, RESERVED,STOPPED,TRANSFERRING,STATION_HELD
	}
	
	private final String id;
	private final RouteFollower routeFollower;
	private final ObjectTransformation transformation;
	
	private ToteInteractionMode interactionMode = ToteInteractionMode.FREE;
	private TransferZoneMachine controllingTransferZone;
	private List<TransferZoneController> transferZoneControllers = new ArrayList<>();
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
		for (TransferZoneController tzc:transferZoneControllers) {
			TransferZoneMachine machine = tzc.getMachine();
			// move the following checks into sensors later
			if (!isOnSourceSegment(machine, snapshot)) continue;
			
			float distance = snapshot.distanceAlongSegment();
			if (isInApproachWindow(machine, distance)) {
				tzc.onToteApproaching(this, snapshot);
			}
			if (isInTransferWindow(machine, distance)) {
				tzc.onToteEnteredTransferWindow(this);
			}
		}
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

	public TransferZoneMachine getControllingTransferZone() {
		return controllingTransferZone;
	}

	public void setControllingTransferZone(TransferZoneMachine controllingTransferZone) {
		this.controllingTransferZone = controllingTransferZone;
	}

}
