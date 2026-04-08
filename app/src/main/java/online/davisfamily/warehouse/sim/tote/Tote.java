package online.davisfamily.warehouse.sim.tote;

import java.util.EnumSet;

import online.davisfamily.threedee.behaviour.routing.RouteFollower;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.behaviour.routing.transfer.RouteFollowerSnapshot;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation.Axis;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.objects.TrackableObject;
import online.davisfamily.warehouse.sim.events.TransferCompletedEvent;
import online.davisfamily.warehouse.sim.transfer.TransferMotionState;
import online.davisfamily.warehouse.sim.transfer.TransferMotionState.TransferMotionPhase;
import online.davisfamily.warehouse.sim.transfer.TransferZoneMachine;

public class Tote implements TrackableObject {
	
	public enum ToteMotionState {
		MOVING, HELD, BLOCKED, TRANSFERRING
	}
	
	private final EnumSet<ToteMotionState> motionBlocked = EnumSet.of(ToteMotionState.HELD, ToteMotionState.BLOCKED, ToteMotionState.TRANSFERRING);
	
	private final String id;
	private final RouteFollower routeFollower;
	private RouteFollowerSnapshot lastRouteSnapshot;
	private final Vec3 offsets;
	private Vec3 cachedPos;
	private final ObjectTransformation transformation;
	private ToteMotionState interactionMode = ToteMotionState.MOVING;
	private String reservedByMachineId;
	private String controllingMachineId;
	private TransferMotionState transferMotionState;
	private final Vec3 lerpPos = new Vec3();
	
	public Tote(String id, RouteFollower routeFollower, ObjectTransformation transformation, Vec3 offsets) {
		super();
		this.id = id;
		this.routeFollower = routeFollower;
		this.transformation = transformation;
		this.offsets = offsets;
		this.cachedPos = new Vec3();
	}

	public ObjectTransformation getTransformation() {
		return transformation;
	}

	@Override
	public String getId() {
		return id;
	}
	
	public void update(SimulationContext context, double dtSeconds) {
		if (interactionMode == ToteMotionState.TRANSFERRING && transferMotionState != null) {
			updateTransferMotion(context, dtSeconds);
			return;
		}
		lastRouteSnapshot = routeFollower.advance(dtSeconds, isMotionBlocked());
		applySnapshot(lastRouteSnapshot);
	}
	
	private void updateTransferMotion(SimulationContext context, double dtSeconds) {
		if (transferMotionState.getPhase() == TransferMotionPhase.WAITING_FOR_ALIGNMENT) {
			updateAlignmentPhase(context, dtSeconds);
		}
		
		if (transferMotionState.getPhase() == TransferMotionPhase.LATERAL_TRANSFER) {
			updateLateralTransferPhase(context, dtSeconds);
		}
	}
	
	public void updateAlignmentPhase(SimulationContext context, double dtSeconds) {
		
		lastRouteSnapshot = routeFollower.advance(dtSeconds, false);
		applySnapshot(lastRouteSnapshot);
		
		// assume tote is centred in calculations if not implement below 
		//float toteCentreDistance = getToteCentreDistanceAlongSegment(snapshot);
		float toteCentreDistance = lastRouteSnapshot.distanceAlongSegment();
		if (toteCentreDistance >= transferMotionState.getSourceTransferCentreDistance()) {
			cachedPos.setXYZ(transformation.xTranslation, transformation.yTranslation, transformation.zTranslation);
	        transferMotionState.setStartPosition(cachedPos);
			cachedPos = transferMotionState.getTargetSegment().getGeometry().sampleByDistance(transferMotionState.getTargetDistanceAlongSegment());
	        transferMotionState.setEndPosition(cachedPos);
	        transferMotionState.setPhase(TransferMotionPhase.LATERAL_TRANSFER);
		}
	}
	
	public void updateLateralTransferPhase(SimulationContext context, double dtSeconds) {
		transferMotionState.updateElapsed(dtSeconds);
		float t = smoothstep(transferMotionState.getProgress());
		Vec3 start = transferMotionState.getStartPosition();
		Vec3 end = transferMotionState.getEndPosition();
		start.lerp(start, end, t);
		transformation.setTranslation(start);
		// preserve yaw
		transformation.setAxisRotation(Axis.Y, transferMotionState.getPreservedYawRadians());
		if (transferMotionState.isComplete()) {
			completeTransfer(context);
		}
	}
	
	private void completeTransfer(SimulationContext context) {
		RouteSegment targetSegment = transferMotionState.getTargetSegment();
		float targetDistance = transferMotionState.getTargetDistanceAlongSegment();
		String machineId = transferMotionState.getMachineId();
		routeFollower.setCurrentSegment(targetSegment);
		routeFollower.setDistanceAlongSegment(targetDistance);
		reservedByMachineId = null;
		controllingMachineId = null;
		interactionMode = ToteMotionState.MOVING;
		transferMotionState = null;
		context.publish(new TransferCompletedEvent(machineId, context.getSimulationTimeSeconds(),id));
	}
	
	private float smoothstep(float t) {
	    return t * t * (3f - 2f * t);
	}
	
	private boolean isMotionBlocked() {
		//return false;
		return motionBlocked.contains(interactionMode);
	}
	
	private void applySnapshot(RouteFollowerSnapshot snapshot) {
		cachedPos.set(snapshot.position()); 
		transformation.setTranslation(cachedPos.add(offsets));
	    transformation.setAxisRotation(Axis.Y, Vec3.yawFromDirection(snapshot.forward())); // yaw
		//transformation.setAxisRotation(Axis.X, snapshot.up().x); //pitch
	}
	
	public void reserveForTransfer(TransferZoneMachine machine) {
		if (machine != null) reservedByMachineId = machine.getId();
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
	
	public void beginTransfer(String machineId, RouteSegment targetSegment, RouteSegment sourceSegment, float sourceTransferCenterDistance, float targetDistanceAlongSegment, double durationSeconds) {
		if (!machineId.equals(reservedByMachineId)) return;
		
		RouteFollowerSnapshot snap = lastRouteSnapshot;
		if (snap == null) return;

		//cachedPos.setXYZ(transformation.xTranslation, transformation.yTranslation, transformation.zTranslation);
		Vec3 startPosition = new Vec3(transformation.xTranslation, transformation.yTranslation, transformation.zTranslation);
		Vec3 targetPosition = targetSegment.getGeometry().sampleByDistance(targetDistanceAlongSegment);
		this.controllingMachineId = machineId;
		this.interactionMode = ToteMotionState.TRANSFERRING;
	    this.transferMotionState = new TransferMotionState(
	            machineId,
	            sourceSegment,
	            targetSegment,
	            sourceTransferCenterDistance,
	            targetDistanceAlongSegment,
	            startPosition,
	            targetPosition,
	            transformation.angleY,
	            durationSeconds
	    );
	}
}
