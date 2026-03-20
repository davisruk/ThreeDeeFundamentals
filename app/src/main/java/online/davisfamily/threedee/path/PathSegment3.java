package online.davisfamily.threedee.path;

import online.davisfamily.threedee.matrices.Vec3;

public interface PathSegment3 {
	float getLength();
	Vec3 sampleByDistance(float distance);
	Vec3 sampleTangentByDistance(float distance);
	Vec3 getStartPoint();
	Vec3 getEndPoint();
	
	default float getStartTangent() {
		return Vec3.yawFromDirection(sampleTangentByDistance(0f));
	}

	default float getEndTangent() {
		return Vec3.yawFromDirection(sampleTangentByDistance(getLength()));
	}
}
