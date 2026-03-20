package online.davisfamily.threedee.path;

import online.davisfamily.threedee.matrices.Vec3;

public class BezierPath3 implements Path3 {

	private final BezierSegment3[] segments;
	private final float[] segmentLengths;
	private final float[] cumulativeLengths;
	private final float totalLength;
	
	public BezierPath3 (BezierSegment3... segments) {
        if (segments == null || segments.length == 0) {
            throw new IllegalArgumentException("BezierPath3 requires at least 1 segment");
        }
		
		this.segments = segments;
		this.segmentLengths = new float[segments.length];
		this.cumulativeLengths = new float[segmentLengths.length]; 
		float runningTotal = 0f;
		for (int i=0; i<segmentLengths.length; i++) {
	        float length = segments[i].getTotalLength();
	        segmentLengths[i] = length;
	        runningTotal += length;
	        cumulativeLengths[i] = runningTotal;
		}
		this.totalLength = runningTotal;
	}	
	@Override
	public Vec3 samplePosition(float u) {
	    if (u <= 0f) return sampleByDistance(0f);
	    if (u >= 1f) return sampleByDistance(totalLength);

	    return sampleByDistance(u * totalLength);	}

	@Override
	public Vec3 sampleByDistance(float distance) {
	    if (distance <= 0f) {
	        return segments[0].sampleByDistance(0f);
	    }

	    if (distance >= totalLength) {
	        return segments[segments.length - 1].sampleByDistance(
	            segments[segments.length - 1].getTotalLength()
	        );
	    }

	    int index = findSegmentIndex(distance);

	    float segmentStartDistance = (index == 0) ? 0f : cumulativeLengths[index - 1];
	    float localDistance = distance - segmentStartDistance;

	    return segments[index].sampleByDistance(localDistance);
	}

	@Override
	public float getTotalLength() {
		return totalLength;
	}

	@Override
	public Vec3 sampleTangentByDistance(float distance) {
	    if (distance <= 0f) {
	        return segments[0].sampleTangentByDistance(0f);
	    }

	    if (distance >= totalLength) {
	        BezierSegment3 last = segments[segments.length - 1];
	        return last.sampleTangentByDistance(last.getTotalLength());
	    }

	    int index = findSegmentIndex(distance);

	    float segmentStartDistance = (index == 0) ? 0f : cumulativeLengths[index - 1];
	    float localDistance = distance - segmentStartDistance;

	    return segments[index].sampleTangentByDistance(localDistance);
	}
	
	private int findSegmentIndex(float distance) {
	    for (int i = 0; i < cumulativeLengths.length; i++) {
	        if (distance <= cumulativeLengths[i]) {
	            return i;
	        }
	    }
	    return cumulativeLengths.length - 1;
	}
	
	public static BezierPath3 createCircularPath(float radius, float centreX, float centreZ) {
		float k = 0.55228475f * radius;

		BezierSegment3 s1 = new BezierSegment3(
			    new Vec3(centreX + radius, 0f, centreZ),
			    new Vec3(centreX + radius, 0f, centreZ + k),
			    new Vec3(centreX + k, 0f, centreZ + radius),
			    new Vec3(centreX,     0f, centreZ + radius)
			);
	
		BezierSegment3 s2 = new BezierSegment3(
			    new Vec3(centreX,     0f, centreZ + radius),
			    new Vec3(centreX - k, 0f, centreZ + radius),
			    new Vec3(centreX - radius, 0f, centreZ + k),
			    new Vec3(centreX - radius, 0f, centreZ)
			);

		BezierSegment3 s3 = new BezierSegment3(
			    new Vec3(centreX - radius, 0f, centreZ),
			    new Vec3(centreX - radius, 0f, centreZ - k),
			    new Vec3(centreX - k, 0f, centreZ - radius),
			    new Vec3(centreX,     0f, centreZ - radius)
			);

		BezierSegment3 s4 = new BezierSegment3(
			    new Vec3(centreX,     0f, centreZ - radius),
			    new Vec3(centreX + k, 0f, centreZ - radius),
			    new Vec3(centreX + radius, 0f, centreZ - k),
			    new Vec3(centreX + radius, 0f, centreZ)
			);

		return new BezierPath3(s1, s2, s3, s4);
}

}
