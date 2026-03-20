package online.davisfamily.threedee.path;

import online.davisfamily.threedee.matrices.Vec3;

public class BezierSegment3 implements PathSegment3 {
	// control points
	private final Vec3 p0,p1,p2,p3;
	
	private final float[] sampleTs;
	private final float[] sampleDistances;
	private final float totalLength;
	private static final int ARC_LENGTH_SAMPLES = 100;
	
	public BezierSegment3(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3) {
        this.p0 = p0;
        this.p1 = p1;
        this.p2 = p2;
        this.p3 = p3;
        
		ArcLengthData arcLengthData = buildArcLengthTable();
		this.sampleTs = arcLengthData.ts;
		this.sampleDistances = arcLengthData.distances;
		this.totalLength = arcLengthData.totalLength;
	}
	
	public float getLength() {
		return totalLength;
	}
	
	/*
	 * Formula: 
	 * B(t) =
	 *	(1-t)^3 p0
	 *	+ 3(1-t)^2 t p1
	 *	+ 3(1-t) t^2 p2
	 *	+ t^3 p3
	 */
	public Vec3 samplePosition(float t) {
		t = Math.max(0f, Math.min(1f, t));
		float u = 1f-t;
		float b0 = u * u * u;
		float b1 = 3f * u * u * t;
		float b2 = 3f * u * t * t;
		float b3 = t * t * t;
		
		return p0.scale(b0)
				.add(p1.scale(b1))
				.add(p2.scale(b2))
				.add(p3.scale(b3));
	}
	
	/*
	 * Formula:
	 * B'(t) =
	 * 3(1-t)^2 (p1 - p0)
	 * + 6(1-t)t (p2 - p1)
	 * + 3t^2 (p3 - p2)		
	 */
	public Vec3 sampleTangent(float t) {
		t = Math.max(0f, Math.min(1f, t));
		float u = 1f-t;
		float b0 = 3f * u * u;
		float b1 = 6f * u * t;
		float b2 = 3f * t * t;
	
		return p1.subtract(p0).scale(b0)
			.add(p2.subtract(p1).scale(b1))
			.add(p3.subtract(p2).scale(b2))
			.normalize();
	}
	
	@Override
	public Vec3 sampleByDistance(float distance) {
		float t = findTForDistance(distance);
		return samplePosition(t);
	}
	@Override
	public Vec3 sampleTangentByDistance(float distance) {
		float t = findTForDistance(distance);
		return sampleTangent(t);
	}
	
	@Override
	public Vec3 getStartPoint() {
		return p0;
	}

	@Override
	public Vec3 getEndPoint() {
		return p3;
	}

	// creates a curve where the middle control points are calculated based on the the in and out direction
	// this allows yaw translation of objects travelling the path to match in and out of the curve
	public static BezierSegment3 createSmoothConnector(Vec3 start, Vec3 end, PathSegment3 incoming, PathSegment3 outgoing, float handleLength) {
		Vec3 dIn = incoming.getEndPoint().subtract(incoming.getStartPoint()).normalize();
		Vec3 dOut = outgoing.getEndPoint().subtract(outgoing.getStartPoint()).normalize();
		Vec3 p1 = start.add(dIn.scale(handleLength));
		Vec3 p2 = end.subtract(dOut.scale(handleLength));
		return new BezierSegment3 (start, p1, p2, end);
	}
	
	private float findTForDistance(float distance) {
		if (distance <= 0f) return 0f;
		if (distance >= totalLength) return 1f;
		
		for (int i = 1; i < sampleDistances.length; i++) {
			if (distance <= sampleDistances[i]) {
				float d0 = sampleDistances[i-1];
				float d1 = sampleDistances[i];
				float t0 = sampleTs[i-1];
				float t1 = sampleTs[i];
				
				float range = d1 - d0;
				if (range == 0f) return t0;
				
				float local = (distance - d0) / range;
				return lerp(t0,t1,local);
			}
		}
		
		return 1f;
	}
	
	// interpolate place on axis 
	private static float lerp(float a, float b, float t) {
		return a + ((b - a) * t);
	}
	
	private static class ArcLengthData {
		final float[] ts;
		final float[] distances;
		final float totalLength;
		ArcLengthData(float[] ts, float[] distances, float totalLength){
			this.ts = ts;
			this.distances = distances;
			this.totalLength = totalLength;
		}
	}
	
	private ArcLengthData buildArcLengthTable() {
		float[] ts = new float[ARC_LENGTH_SAMPLES + 1];
		float[] distances = new float[ARC_LENGTH_SAMPLES + 1];
		
		Vec3 previousPoint = samplePosition(0f);
		ts[0] = 0f;
		distances[0] = 0f;
		float runningTotal = 0f;
		
		for (int i=1; i<=ARC_LENGTH_SAMPLES;i++) {
			float t = (float) i / ARC_LENGTH_SAMPLES;
			Vec3 currentPoint = samplePosition(t);
			runningTotal += previousPoint.distanceTo(currentPoint);
			ts[i] = t;
			distances[i] = runningTotal;
			previousPoint = currentPoint;
		}
		return new ArcLengthData(ts, distances, runningTotal);
	}

}
