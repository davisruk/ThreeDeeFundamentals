package online.davisfamily.threedee.path;

import online.davisfamily.threedee.matrices.Vec3;

public class LinearPath3 implements Path3 {

	private final Vec3[] points;
	
	public LinearPath3 (Vec3... points) {
		if (points == null || points.length < 2) {
			throw new IllegalArgumentException("LinearPath3 requires at least 2 points");
		}
		this.points = points;
	}
	
	@Override
	public Vec3 samplePosition(float u) {
		if (u <= 0f) return Vec3.copy(points[0]);
		if (u >= 1f) return Vec3.copy(points[points.length - 1]);
		
		float scaled = u * (points.length - 1);
		int index = (int)Math.floor(scaled);
		float localT = scaled - index;
		
		Vec3 a = points[index];
		Vec3 b = points[index + 1];
		
		return new Vec3(
				lerp(a.x, b.x, localT),
				lerp(a.y, b.y, localT),
				lerp(a.z, b.z, localT)
		);
	}
	
	// interpolate place on axis 
	private static float lerp(float a, float b, float t) {
		return a + ((b - a) * t);
	}
}
