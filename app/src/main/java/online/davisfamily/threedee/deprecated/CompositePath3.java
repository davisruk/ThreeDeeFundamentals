package online.davisfamily.threedee.deprecated;

import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.path.PathSegment3;

public class CompositePath3 implements Path3 {

	private final PathSegment3[] segments;
	private final float[] segmentLengths;
	private final float[] cumulativeLengths;
	private final float totalLength;
	
	public CompositePath3 (PathSegment3... segments) {
        if (segments == null || segments.length == 0) {
            throw new IllegalArgumentException("CompositePath3 requires at least 1 segment");
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

	    return sampleByDistance(u * totalLength);
	}

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
	        PathSegment3 last = segments[segments.length - 1];
	        return last.sampleTangentByDistance(last.getTotalLength());
	    }

	    int index = findSegmentIndex(distance);

	    float segmentStartDistance = (index == 0) ? 0f : cumulativeLengths[index - 1];
	    float localDistance = distance - segmentStartDistance;

	    return segments[index].sampleTangentByDistance(localDistance);
	}

	public String toString() {
		StringBuffer buff = new StringBuffer("Composite Path:\r\n");
		for (PathSegment3 ps:segments) {
			buff.append(startEndData(ps) + "\r\n");
		}
		return buff.toString();
	}
	private int findSegmentIndex(float distance) {
	    for (int i = 0; i < cumulativeLengths.length; i++) {
	        if (distance <= cumulativeLengths[i]) {
	            return i;
	        }
	    }
	    return cumulativeLengths.length - 1;
	}
	
	public String startEndData(PathSegment3 ps) {
		return "Start Point:" + ps.getStartPoint()
				+ " EndPoint:" + ps.getEndPoint()
				+ " Start Tangent:" + Vec3.yawFromDirection(ps.getStartTangent())
				+ " End Tangent:" + Vec3.yawFromDirection(ps.getEndTangent());
	}
}
