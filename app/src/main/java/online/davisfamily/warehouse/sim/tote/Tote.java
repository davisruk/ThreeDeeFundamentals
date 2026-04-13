package online.davisfamily.warehouse.sim.tote;

import java.util.EnumSet;

import online.davisfamily.threedee.behaviour.routing.RouteFollower;
import online.davisfamily.threedee.behaviour.routing.RouteFollower.TravelDirection;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.behaviour.routing.transfer.RouteFollowerSnapshot;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation.Axis;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.objects.TrackableObject;
import online.davisfamily.warehouse.sim.events.TransferCompletedEvent;
import online.davisfamily.warehouse.sim.transfer.TransferMotionConfig;
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
	private final RenderableObject renderable;
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
	private boolean visualTiltActive = false;
	private float visualTiltAngleZRadians = 0f;
	private final Vec3 visualOffset = new Vec3();
	private boolean visualOffsetActive = false;
	
	public Tote(String id, RouteFollower routeFollower, RenderableObject renderable, Vec3 offsets, float yawOffsetRadians) {
		super();
		if (renderable == null) {
			throw new IllegalArgumentException("renderable must not be null");
		}
		if (renderable.transformation == null) {
			throw new IllegalArgumentException("renderable transformation must not be null");
		}
		this.id = id;
		this.routeFollower = routeFollower;
		this.renderable = renderable;
		this.transformation = renderable.transformation;
		this.offsets = offsets;
		this.cachedPos = new Vec3();
		this.yawOffsetRadians = yawOffsetRadians;
	}

	public ObjectTransformation getTransformation() {
		return transformation;
	}

	public RenderableObject getRenderable() {
		return renderable;
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
			Vec3 startPosition = new Vec3(
					transformation.xTranslation,
					transformation.yTranslation,
					transformation.zTranslation);
	        transferMotionState.setStartPosition(startPosition);
			Vec3 endPosition = transferMotionState.getTargetSegment().getGeometry()
					.sampleByDistance(transferMotionState.getTargetMergeDistanceAlongSegment());
	        transferMotionState.setEndPosition(endPosition);
	        transferMotionState.recalculateTransferLengthWorld();
	        if (transferMotionState.getTransferLengthWorld() <= 0.0001f) {
	        	completeTransfer(context);
	        	return;
	        }
	        transferMotionState.setPhase(TransferMotionPhase.LATERAL_TRANSFER);
		}
	}
	
	public void updateLateralTransferPhase(SimulationContext context, double dtSeconds) {
		transferMotionState.advanceWorldDistance(routeFollower.getSpeedUnitsPerSecond(), dtSeconds);

		float t = transferMotionState.getTransferAlpha();
		Vec3 start = transferMotionState.getStartPosition();
		Vec3 end = transferMotionState.getEndPosition();
		cachedPos.lerp(start, end, t);
		transformation.setTranslation(cachedPos);

		if (transferMotionState.isComplete()) {
		    completeTransfer(context);
		}		
	}
	
	private void completeTransfer(SimulationContext context) {
		RouteSegment targetSegment = transferMotionState.getTargetSegment();
		float targetDistance = transferMotionState.getTargetMergeDistanceAlongSegment();
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
	
	private boolean isMotionBlocked() {
		//return false;
		return motionBlocked.contains(interactionMode);
	}
	
	private void applySnapshot(RouteFollowerSnapshot snapshot) {
		cachedPos.set(snapshot.position()).mutableAdd(offsets);
		if (visualOffsetActive) {
			cachedPos.mutableAdd(visualOffset);
		}
		transformation.setTranslation(cachedPos);
		transformation.setAxisRotation(Axis.Y, computeRenderedYaw(snapshot));
		transformation.angleZ = visualTiltActive ? visualTiltAngleZRadians : 0f;
	}
	
	public void reserveForTransfer(TransferZoneMachine machine) {
		if (machine != null) reservedByMachineId = machine.getId();
	}
	
	public ToteMotionState getInteractionMode() {
		return interactionMode;
	}

	public String getReservedByMachineId() {
		return reservedByMachineId;
	}

	public void setInteractionMode(ToteMotionState interactionMode) {
		this.interactionMode = interactionMode;
	}

	public RouteFollower getRouteFollower() {
		return routeFollower;
	}

	public void setVisualTiltAngleZ(float angleZRadians) {
		visualTiltActive = true;
		visualTiltAngleZRadians = angleZRadians;
		transformation.angleZ = angleZRadians;
	}

	public void clearVisualTiltAngleZ() {
		visualTiltActive = false;
		visualTiltAngleZRadians = 0f;
		transformation.angleZ = 0f;
	}

	public void setVisualOffset(float x, float y, float z) {
		visualOffset.setXYZ(x, y, z);
		visualOffsetActive = true;
	}

	public void clearVisualOffset() {
		visualOffset.setXYZ(0f, 0f, 0f);
		visualOffsetActive = false;
	}

	public void snapToRouteDistance(float distanceAlongSegment) {
		routeFollower.setDistanceAlongSegment(distanceAlongSegment);
		lastRouteSnapshot = routeFollower.buildSnapshot();
		applySnapshot(lastRouteSnapshot);
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
	
	public void beginTransfer(String machineId, RouteSegment targetSegment, RouteSegment sourceSegment, float sourceTransferCenterDistance, float targetDistanceAlongSegment, TransferMotionConfig motionConfig) {
		if (!machineId.equals(reservedByMachineId)) return;
		
		RouteFollowerSnapshot snap = lastRouteSnapshot;
		if (snap == null) return;
		if (motionConfig == null) return;

		Vec3 startPosition = new Vec3(transformation.xTranslation, transformation.yTranslation, transformation.zTranslation);
		TravelDirection targetTravelDirection = routeFollower.getTravelDirection();
		float targetMergeDistanceAlongSegment = calculateMergeDistance(
				startPosition,
				targetSegment,
				targetDistanceAlongSegment,
				routeFollower.getSpeedUnitsPerSecond(),
				motionConfig);
		Vec3 targetPosition = targetSegment.getGeometry().sampleByDistance(targetMergeDistanceAlongSegment);
		FacingDirection targetFacingDirection = determineTargetFacingDirection(
				snap,
				targetSegment,
				targetMergeDistanceAlongSegment,
				targetTravelDirection);
		float transferLengthWorld = startPosition.distanceTo(targetPosition);
		this.interactionMode = ToteMotionState.TRANSFERRING;
	    this.transferMotionState = new TransferMotionState(
	            machineId,
	            sourceSegment,
	            targetSegment,
	            sourceTransferCenterDistance,
	            targetTravelDirection,
	            targetFacingDirection,
	            targetSegment.getGeometry().isLinkSegment(),
	            transformation.angleY,
	            targetMergeDistanceAlongSegment,
	            startPosition,
	            targetPosition,
	            transferLengthWorld
	    );
	}

	private float calculateMergeDistance(
			Vec3 startWorld,
			RouteSegment targetSegment,
			float targetEntryDistanceAlongSegment,
			double speedUnitsPerSecond,
			TransferMotionConfig motionConfig) {
		float preferredTravel = (float) (speedUnitsPerSecond * motionConfig.getPreferredDurationSeconds());
		float minMergeDistance = minMergeDistanceFor(targetSegment, targetEntryDistanceAlongSegment, motionConfig);
		float maxMergeDistance = maxMergeDistanceFor(targetSegment, targetEntryDistanceAlongSegment, motionConfig);
		float bestDistance = minMergeDistance;
		float bestError = Float.MAX_VALUE;

		float step = 0.01f;
		for (float candidateDistance = minMergeDistance; candidateDistance <= maxMergeDistance + 0.0001f; candidateDistance += step) {
			float clampedCandidate = clamp(candidateDistance, minMergeDistance, maxMergeDistance);
			Vec3 candidatePosition = targetSegment.getGeometry().sampleByDistance(clampedCandidate);
			float candidateTravel = startWorld.distanceTo(candidatePosition);
			float error = Math.abs(candidateTravel - preferredTravel);
			if (error < bestError) {
				bestError = error;
				bestDistance = clampedCandidate;
			}
		}

		return bestDistance;
	}

	private float minMergeDistanceFor(RouteSegment targetSegment, float targetEntryDistanceAlongSegment, TransferMotionConfig motionConfig) {
		return clamp(
				targetEntryDistanceAlongSegment + motionConfig.getMinMergeOffsetFromEntryDistance(),
				0f,
				targetSegment.length());
	}

	private float maxMergeDistanceFor(RouteSegment targetSegment, float targetEntryDistanceAlongSegment, TransferMotionConfig motionConfig) {
		return clamp(
				targetEntryDistanceAlongSegment + motionConfig.getMaxMergeOffsetFromEntryDistance(),
				0f,
				targetSegment.length());
	}

	private float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
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
