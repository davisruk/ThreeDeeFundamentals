package online.davisfamily.threedee.path;

import online.davisfamily.threedee.matrices.Vec3;

public class LinearSegment3 implements PathSegment3 {

	private final Vec3 start, end, tangent;
	private final float length;
	private final boolean linkSegment;
	
	public LinearSegment3 (Vec3 start, Vec3 end, boolean linkSegment) {
		this.start = start;
		this.end = end;
		this.length = start.distanceTo(end);
		this.tangent = this.end.subtract(this.start).normalize();
		this.linkSegment = linkSegment;
	}
	
	
	@Override
	public boolean isLinkSegment() {
		return linkSegment;
	}


	@Override
	public float getTotalLength() {
		return length;
	}

	@Override
	public Vec3 sampleByDistance(float distance) {
		if (distance <= 0f) return Vec3.copy(start);
		if (distance >= length) return Vec3.copy(end);

		float t = distance / length; 
		return new Vec3(
				lerp(start.x, end.x, t),
				lerp(start.y, end.y, t),
				lerp(start.z, end.z, t)
		);
	}

	@Override
	public Vec3 sampleTangentByDistance(float distance) {
		return tangent; 
	}

	// interpolate place on axis 
	private static float lerp(float a, float b, float t) {
		return a + ((b - a) * t);
	}

	@Override
	public Vec3 getStartPoint() {
		return start;
	}

	@Override
	public Vec3 getEndPoint() {
		return end;
	}
}
