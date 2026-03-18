package online.davisfamily.threedee.path;

import online.davisfamily.threedee.matrices.Vec3;

public interface Path3 {
	Vec3 samplePosition(float u);
	Vec3 sampleByDistance(float distance);
	float getTotalLength();
}
