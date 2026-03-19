package online.davisfamily.threedee.path;

import online.davisfamily.threedee.matrices.Vec3;

public interface Path3 {
	// determine position without regard to speed
	Vec3 samplePosition(float u);
	// determine position using uniform speed
	Vec3 sampleByDistance(float distance);
	float getTotalLength();
	// find heading angle
	Vec3 sampleTangentByDistance(float distance);
}
