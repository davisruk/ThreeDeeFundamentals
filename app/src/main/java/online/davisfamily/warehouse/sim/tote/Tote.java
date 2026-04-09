package online.davisfamily.warehouse.sim.tote;

import java.util.EnumSet;

import online.davisfamily.threedee.behaviour.routing.RouteFollower;
import online.davisfamily.threedee.behaviour.routing.RouteFollower.TravelDirection;
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

	public enum FacingDirection {
		WITH_TRAVEL,
		AGAINST_TRAVEL
	}
	
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
	private FacingDirection facingDirection = FacingDirection.WITH_TRAVEL;
	private boolean fixedFacingYawActive = false;
	private float fixedFacingYawRadians;
	private String reservedByMachineId;
	private TransferMotionState transferMotionState;
	private final float yawOffsetRadians;
	
	public Tote(String id, RouteFollower routeFollower, ObjectTransformation transformation, Vec3 offsets, float yawOffsetRadians) {
		super();
		this.id = id;
		this.routeFollower = routeFollower;
		this.transformation = transformation;
		this.offsets = offsets;
		this.cachedPos = new Vec3();
		this.yawOffsetRadians = yawOffsetRadians;
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
		RouteFollowerSnapshot prev = lastRouteSnapshot;
		lastRouteSnapshot = routeFollower.advance(dtSeconds, isMotionBlocked());
		if (fixedFacingYawActive
				&& prev != null
				&& prev.currentSegment().getGeometry().isLinkSegment()
				&& !lastRouteSnapshot.currentSegment().getGeometry().isLinkSegment()) {
			facingDirection = determineFacingDirectionForWorldYaw(lastRouteSnapshot, fixedFacingYawRadians);
			fixedFacingYawActive = false;
		}
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
			cachedPos = transferMotionState.getTargetSegment().getGeometry().sampleByDistance(transferMotionState.getCurrentTargetDistanceAlongSegment());
	        transferMotionState.setEndPosition(cachedPos);
	        transferMotionState.setPhase(TransferMotionPhase.LATERAL_TRANSFER);
		}
	}
	
	public void updateLateralTransferPhase(SimulationContext context, double dtSeconds) {
		double remainingTransferTime =
		        transferMotionState.getDurationSeconds()
		        - transferMotionState.getElapsedSeconds();

		double timeUsed = Math.min(dtSeconds, remainingTransferTime);

		transferMotionState.advanceTargetDistance(routeFollower.getSpeedUnitsPerSecond(), timeUsed);
		transferMotionState.setEndPosition(
				transferMotionState.getTargetSegment().getGeometry()
						.sampleByDistance(transferMotionState.getCurrentTargetDistanceAlongSegment()));
		transferMotionState.updateElapsed(timeUsed);

		float t = smoothstep(transferMotionState.getProgress());
		Vec3 start = transferMotionState.getStartPosition();
		Vec3 end = transferMotionState.getEndPosition();
		cachedPos.lerp(start, end, t);
		transformation.setTranslation(cachedPos);

		if (transferMotionState.isComplete()) {
		    completeTransfer(context);

		    double leftoverDt = dtSeconds - timeUsed;

		    if (leftoverDt > 0) {
		        RouteFollowerSnapshot snapshot =
		                routeFollower.advance(leftoverDt, false);

		        lastRouteSnapshot = snapshot;
		        applySnapshot(snapshot);
		    }
		}		
	}
	
	private void completeTransfer(SimulationContext context) {
		RouteSegment targetSegment = transferMotionState.getTargetSegment();
		float targetDistance = transferMotionState.getCurrentTargetDistanceAlongSegment();
		String machineId = transferMotionState.getMachineId();
		routeFollower.setCurrentSegment(targetSegment);
		routeFollower.setDistanceAlongSegment(targetDistance);
		routeFollower.setTravelDirection(transferMotionState.getTargetTravelDirection());
		facingDirection = transferMotionState.getTargetFacingDirection();
		fixedFacingYawActive = transferMotionState.isTargetFixedFacingYawActive();
		fixedFacingYawRadians = transferMotionState.getTargetFixedFacingYawRadians();
		lastRouteSnapshot = routeFollower.buildSnapshot();
		applySnapshot(lastRouteSnapshot);
		reservedByMachineId = null;
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
		transformation.setAxisRotation(Axis.Y, computeRenderedYaw(snapshot));		
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

		Vec3 startPosition = new Vec3(transformation.xTranslation, transformation.yTranslation, transformation.zTranslation);
		Vec3 targetPosition = targetSegment.getGeometry().sampleByDistance(targetDistanceAlongSegment);
		TravelDirection targetTravelDirection = routeFollower.getTravelDirection();
		FacingDirection targetFacingDirection = determineTargetFacingDirection(
				snap,
				targetSegment,
				targetDistanceAlongSegment,
				targetTravelDirection);
		this.interactionMode = ToteMotionState.TRANSFERRING;
	    this.transferMotionState = new TransferMotionState(
	            machineId,
	            sourceSegment,
	            targetSegment,
	            sourceTransferCenterDistance,
	            targetDistanceAlongSegment,
	            targetTravelDirection,
	            targetFacingDirection,
	            targetSegment.getGeometry().isLinkSegment(),
	            transformation.angleY,
	            startPosition,
	            targetPosition,
	            durationSeconds
	    );
	}

	private float computeRenderedYaw(RouteFollowerSnapshot snapshot) {
		if (fixedFacingYawActive) {
			return fixedFacingYawRadians;
		}
		Vec3 facing = getFacingVector(snapshot, facingDirection);
		return Vec3.yawFromDirection(facing) + yawOffsetRadians;
	}

	private FacingDirection determineTargetFacingDirection(
			RouteFollowerSnapshot currentSnapshot,
			RouteSegment targetSegment,
			float targetDistanceAlongSegment,
			TravelDirection targetTravelDirection) {

		Vec3 preservedFacing = getFacingVector(currentSnapshot, facingDirection);
		Vec3 targetTravelVector = sampleTravelVector(targetSegment, targetDistanceAlongSegment, targetTravelDirection);
		return preservedFacing.dot(targetTravelVector) >= 0f
				? FacingDirection.WITH_TRAVEL
				: FacingDirection.AGAINST_TRAVEL;
	}

	private Vec3 getFacingVector(RouteFollowerSnapshot snapshot, FacingDirection direction) {
		Vec3 travelVector = snapshot.forward();
		if (direction == FacingDirection.AGAINST_TRAVEL) {
			return travelVector.scale(-1f);
		}
		return travelVector;
	}

	private Vec3 sampleTravelVector(RouteSegment segment, float distanceAlongSegment, TravelDirection direction) {
		Vec3 travelVector = segment.getGeometry().sampleOrientationDirectionByDistance(distanceAlongSegment);
		if (direction == TravelDirection.REVERSE) {
			travelVector = travelVector.scale(-1f);
		}
		return travelVector;
	}

	private FacingDirection determineFacingDirectionForWorldYaw(RouteFollowerSnapshot snapshot, float worldYawRadians) {
		Vec3 preservedFacing = directionFromYaw(worldYawRadians - yawOffsetRadians);
		return preservedFacing.dot(snapshot.forward()) >= 0f
				? FacingDirection.WITH_TRAVEL
				: FacingDirection.AGAINST_TRAVEL;
	}

	private Vec3 directionFromYaw(float yawRadians) {
		return new Vec3(
				(float) Math.sin(yawRadians),
				0f,
				(float) Math.cos(yawRadians)
		);
	}
}
