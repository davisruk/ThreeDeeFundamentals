package online.davisfamily.threedee.deprecated;

import online.davisfamily.threedee.matrices.Vec3;

public class LinearPath3 implements Path3 {

	private final Vec3[] points;
	private final float[] segmentLengths;
	private final float[] cumulativeLengths;
	
	public LinearPath3 (Vec3... points) {
		if (points == null || points.length < 2) {
			throw new IllegalArgumentException("LinearPath3 requires at least 2 points");
		}
		this.points = points;
		this.segmentLengths = calculateSegmentLengths(points);
		this.cumulativeLengths = new float[segmentLengths.length]; 
		float runningTotal = 0f;
		for (int i=0; i<segmentLengths.length; i++) {
			runningTotal+=segmentLengths[i];
			cumulativeLengths[i] = runningTotal;
		}
	}
	
	@Override
	public float getTotalLength() {
		return cumulativeLengths[cumulativeLengths.length-1];
	}
	
	private float[] calculateSegmentLengths(Vec3[] points) {
		float[] ret = new float[points.length-1];
		for (int i=0; i<points.length-1;i++) {
			ret[i] = points[i].distanceTo(points[i+1]);
		}
		return ret;
	}
	
	// sample without regard to speed
	// object spends the same amount of time on each segment
	// object will appear to speed up if segment is longer than others
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
	
	// sample with regard to speed
	// traversal of a segment is based on its length
	// object will appear to have the same speed
	@Override
	public Vec3 sampleByDistance(float distance) {
		if (distance <= 0f) return Vec3.copy(points[0]);
		if (distance >= cumulativeLengths[cumulativeLengths.length - 1]) return Vec3.copy(points[points.length - 1]);

		// find the index of the segment based on cumulative length
		int index = findSegment(distance);
		
		float segmentStartDistance = index == 0 ? 0f: cumulativeLengths[index - 1];
		float localDistance = distance - segmentStartDistance;
		
		Vec3 a = points[index];
		Vec3 b = points[index + 1];
		
		float localT = localDistance / segmentLengths[index]; 
		return new Vec3(
				lerp(a.x, b.x, localT),
				lerp(a.y, b.y, localT),
				lerp(a.z, b.z, localT)
		);
	}
	
	@Override
	public Vec3 sampleTangentByDistance(float distance) {
		int index = findSegment(distance);
		Vec3 a = points[index];
		Vec3 b = points[index+1];
		return b.subtract(a).normalize();

	}
	
	private int findSegment(float distance) {
		int index = 0;
		for (int i = 0; i < cumulativeLengths.length; i++) {
			if (distance <= cumulativeLengths[i]) {index = i;break;}
		}
		return index;
	}

	// interpolate place on axis 
	private static float lerp(float a, float b, float t) {
		return a + ((b - a) * t);
	}
	
}
