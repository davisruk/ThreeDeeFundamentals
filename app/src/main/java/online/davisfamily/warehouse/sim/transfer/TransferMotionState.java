package online.davisfamily.warehouse.sim.transfer;

import online.davisfamily.threedee.behaviour.routing.RouteFollower.TravelDirection;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.warehouse.sim.tote.Tote.FacingDirection;

public class TransferMotionState {
	
	public enum TransferMotionPhase {
		WAITING_FOR_ALIGNMENT,
		LATERAL_TRANSFER
	}
	private final String machineId;
	private final RouteSegment sourceSegment;
	private final RouteSegment targetSegment;
	private final float sourceTransferCentreDistance;
	private final TravelDirection targetTravelDirection;
	private final FacingDirection targetFacingDirection;
	private final boolean targetFixedFacingYawActive;
	private final float targetFixedFacingYawRadians;
	private Vec3 startPosition;
	private Vec3 endPosition;
	private final float targetMergeDistanceAlongSegment;
	private float transferLengthWorld;
	private float distanceTravelledWorld;
	private TransferMotionPhase phase = TransferMotionPhase.WAITING_FOR_ALIGNMENT;
	
	public TransferMotionState(
			String machineId,
			RouteSegment sourceSegment,
			RouteSegment targetSegment,
			float sourceTransferCentreDistance,
			TravelDirection targetTravelDirection,
			FacingDirection targetFacingDirection,
			boolean targetFixedFacingYawActive,
			float targetFixedFacingYawRadians,
			float targetMergeDistanceAlongSegment,
			Vec3 startPosition,
			Vec3 endPosition,
			float transferLengthWorld) {
		super();
		this.machineId = machineId;
		this.sourceSegment = sourceSegment;
		this.targetSegment = targetSegment;
		this.sourceTransferCentreDistance = sourceTransferCentreDistance;
		this.targetTravelDirection = targetTravelDirection;
		this.targetFacingDirection = targetFacingDirection;
		this.targetFixedFacingYawActive = targetFixedFacingYawActive;
		this.targetFixedFacingYawRadians = targetFixedFacingYawRadians;
		this.targetMergeDistanceAlongSegment = targetMergeDistanceAlongSegment;
		this.startPosition = startPosition;
		this.endPosition = endPosition;
		this.transferLengthWorld = transferLengthWorld;
	}

	public void advanceWorldDistance(double speedUnitsPerSecond, double dtSeconds) {
		float delta = (float) (speedUnitsPerSecond * dtSeconds);
		distanceTravelledWorld = clamp(distanceTravelledWorld + delta, 0f, transferLengthWorld);
	}
	
	public TransferMotionPhase getPhase() {
		return phase;
	}

	public void setPhase(TransferMotionPhase phase) {
		this.phase = phase;
	}

	public RouteSegment getSourceSegment() {
		return sourceSegment;
	}

	public float getSourceTransferCentreDistance() {
		return sourceTransferCentreDistance;
	}

	public boolean isComplete() {
		return distanceTravelledWorld >= transferLengthWorld;
	}

	public String getMachineId() {
		return machineId;
	}

	public Vec3 getStartPosition() {
		return startPosition;
	}

	public Vec3 getEndPosition() {
		return endPosition;
	}

	public RouteSegment getTargetSegment() {
		return targetSegment;
	}

	public float getTargetMergeDistanceAlongSegment() {
		return targetMergeDistanceAlongSegment;
	}

	public TravelDirection getTargetTravelDirection() {
		return targetTravelDirection;
	}

	public FacingDirection getTargetFacingDirection() {
		return targetFacingDirection;
	}

	public boolean isTargetFixedFacingYawActive() {
		return targetFixedFacingYawActive;
	}

	public float getTargetFixedFacingYawRadians() {
		return targetFixedFacingYawRadians;
	}

	public float getTransferLengthWorld() {
		return transferLengthWorld;
	}

	public float getDistanceTravelledWorld() {
		return distanceTravelledWorld;
	}

	public void setStartPosition(Vec3 startPosition) {
		this.startPosition = startPosition;
	}

	public void setEndPosition(Vec3 endPosition) {
		this.endPosition = endPosition;
	}

	public void recalculateTransferLengthWorld() {
		if (startPosition == null || endPosition == null) {
			transferLengthWorld = 0f;
			distanceTravelledWorld = 0f;
			return;
		}
		transferLengthWorld = startPosition.distanceTo(endPosition);
		distanceTravelledWorld = clamp(distanceTravelledWorld, 0f, transferLengthWorld);
	}

	public float getTransferAlpha() {
		if (transferLengthWorld <= 0f) {
			return 1f;
		}
		return distanceTravelledWorld / transferLengthWorld;
	}

	private static float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}
	
}
