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
	private float currentTargetDistanceAlongSegment;
	private final double durationSeconds;
	private double elapsedSeconds;
	private TransferMotionPhase phase = TransferMotionPhase.WAITING_FOR_ALIGNMENT;
	
	public TransferMotionState(
			String machineId,
			RouteSegment sourceSegment,
			RouteSegment targetSegment,
			float sourceTransferCentreDistance,
			float targetDistanceAlongSegment,
			TravelDirection targetTravelDirection,
			FacingDirection targetFacingDirection,
			boolean targetFixedFacingYawActive,
			float targetFixedFacingYawRadians,
			Vec3 startPosition,
			Vec3 endPosition,
			double durationSeconds) {
		super();
		this.machineId = machineId;
		this.sourceSegment = sourceSegment;
		this.targetSegment = targetSegment;
		this.sourceTransferCentreDistance = sourceTransferCentreDistance;
		this.currentTargetDistanceAlongSegment = targetDistanceAlongSegment;
		this.targetTravelDirection = targetTravelDirection;
		this.targetFacingDirection = targetFacingDirection;
		this.targetFixedFacingYawActive = targetFixedFacingYawActive;
		this.targetFixedFacingYawRadians = targetFixedFacingYawRadians;
		this.startPosition = startPosition;
		this.endPosition = endPosition;
		this.durationSeconds = durationSeconds;
	}

	public void updateElapsed(double dtSeconds) {
		elapsedSeconds+=dtSeconds;
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

	public float getProgress() {
		return (float) Math.min(1.0, elapsedSeconds / durationSeconds);
	}
	
	public boolean isComplete() {
		return getProgress() >= 1.0f;
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

	public float getCurrentTargetDistanceAlongSegment() {
		return currentTargetDistanceAlongSegment;
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

	public double getElapsedSeconds() {
		return elapsedSeconds;
	}

	public double getDurationSeconds() {
		return durationSeconds;
	}

	public void setStartPosition(Vec3 startPosition) {
		this.startPosition = startPosition;
	}

	public void setEndPosition(Vec3 endPosition) {
		this.endPosition = endPosition;
	}

	public void advanceTargetDistance(double speedUnitsPerSecond, double dtSeconds) {
		float delta = (float) (speedUnitsPerSecond * dtSeconds);
		if (targetTravelDirection == TravelDirection.REVERSE) {
			delta = -delta;
		}

		float maxDistance = targetSegment.length();
		currentTargetDistanceAlongSegment = clamp(currentTargetDistanceAlongSegment + delta, 0f, maxDistance);
	}

	private static float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}
	
}
