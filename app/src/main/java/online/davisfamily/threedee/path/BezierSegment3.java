package online.davisfamily.threedee.path;

import online.davisfamily.threedee.matrices.Vec3;

public class BezierSegment3 {
	// control points
	Vec3 p0,p1,p2,p3;
	
	float[] sampleTs;
	float[] sampleDistances;
	float totalLength;
	private static final int ARC_LENGTH_SAMPLES = 100;
	
	Vec3 samplePosition(float t) {
		/*
		 * Formula: 
		 * B(t) =
		 *	(1-t)^3 p0
		 *	+ 3(1-t)^2 t p1
		 *	+ 3(1-t) t^2 p2
		 *	+ t^3 p3
		 */
		float u = 1f-t;
		float b0 = u * u * u;
		float b1 = 3 * u * u * t;
		float b2 = 3 * u * t * t;
		float b3 = t * t * t;
		
		return p0.scale(b0)
				.add(p1.scale(b1))
				.add(p2.scale(b2))
				.add(p3.scale(b3));
	}
	
	Vec3 sampleTangent(float t) {
		/*
		 * Formula:
		 * B'(t) =
		 * 3(1-t)^2 (p1 - p0)
		 * + 6(1-t)t (p2 - p1)
		 * + 3t^2 (p3 - p2)		
		 */
		float u = 1f-t;
		float b0 = 3 * u * u;
		float b1 = 6 * u * t;
		float b2 = 3 * t * t;
	
		return p1.subtract(p0).scale(b0)
			.add(p2.subtract(p1).scale(b1))
			.add(p3.subtract(p2).scale(b2))
			.normalize();
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
