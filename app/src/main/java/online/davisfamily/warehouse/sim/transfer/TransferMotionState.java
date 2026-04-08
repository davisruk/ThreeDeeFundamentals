package online.davisfamily.warehouse.sim.transfer;

import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.matrices.Vec3;

public class TransferMotionState {
	
	public enum TransferMotionPhase {
		WAITING_FOR_ALIGNMENT,
		LATERAL_TRANSFER
	}
	private final String machineId;
	private final RouteSegment sourceSegment;
	private final RouteSegment targetSegment;
	private final float sourceTransferCentreDistance;
	private final float targetDistanceAlongSegment;
	private Vec3 startPosition;
	private Vec3 endPosition;
	private final float preservedYawRadians;
	private final double durationSeconds;
	private double elapsedSeconds;
	private TransferMotionPhase phase = TransferMotionPhase.WAITING_FOR_ALIGNMENT;
	
	public TransferMotionState(String machineId, RouteSegment sourceSegment, RouteSegment targetSegment,
			float sourceTransferCentreDistance, float targetDistanceAlongSegment, Vec3 startPosition, Vec3 endPosition,
			float preservedYawRadians, double durationSeconds) {
		super();
		this.machineId = machineId;
		this.sourceSegment = sourceSegment;
		this.targetSegment = targetSegment;
		this.sourceTransferCentreDistance = sourceTransferCentreDistance;
		this.targetDistanceAlongSegment = targetDistanceAlongSegment;
		this.startPosition = startPosition;
		this.endPosition = endPosition;
		this.preservedYawRadians = preservedYawRadians;
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

	public float getPreservedYawRadians() {
		return preservedYawRadians;
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

	public float getTargetDistanceAlongSegment() {
		return targetDistanceAlongSegment;
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
	
}
