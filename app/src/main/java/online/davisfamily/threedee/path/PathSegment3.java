package online.davisfamily.threedee.path;

import online.davisfamily.threedee.matrices.Vec3;

public interface PathSegment3 {
	float getTotalLength();
	Vec3 sampleByDistance(float distance);
	Vec3 sampleTangentByDistance(float distance);
	Vec3 getStartPoint();
	Vec3 getEndPoint();
	
	default boolean isLinkSegment() {
		return false;
	}
	
	default Vec3 getStartTangent() {
		return sampleTangentByDistance(0f);
	}

	default Vec3 getEndTangent() {
		return sampleTangentByDistance(getTotalLength());
	}
	
    default Vec3 sampleOrientationDirectionByDistance(float distance) {
        return sampleTangentByDistance(distance);
    }	
}
