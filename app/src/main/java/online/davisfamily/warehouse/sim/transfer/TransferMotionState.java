package online.davisfamily.warehouse.sim.transfer;

import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.matrices.Vec3;

public class TransferMotionState {
	private final String machineId;
	private final Vec3 startPosition;
	private final Vec3 endPosition;
	private final RouteSegment targetSegment;
	private final float targetDistanceAlongSegment;
	
	private double elapsedSeconds;
	private final double durationSeconds;
	
	public TransferMotionState(String machineId, Vec3 startPosition, Vec3 endPosition, RouteSegment routeSegment,
			float targetDistanceAlongSegment, double durationSeconds) {
		super();
		this.machineId = machineId;
		this.startPosition = startPosition;
		this.endPosition = endPosition;
		this.targetSegment = routeSegment;
		this.targetDistanceAlongSegment = targetDistanceAlongSegment;
		this.durationSeconds = durationSeconds;
	}
	
	public void update(double dtSeconds) {
		elapsedSeconds+=dtSeconds;
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
	
}
