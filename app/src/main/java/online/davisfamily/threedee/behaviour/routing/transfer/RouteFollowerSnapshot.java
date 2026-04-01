package online.davisfamily.threedee.behaviour.routing.transfer;

import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.matrices.Vec3;

public record RouteFollowerSnapshot(
	String followerId,
	RouteSegment currentSegment,
	float distanceAlongSegment,
	Vec3 position,
	Vec3 forward,
	Vec3 up,
	float segmentLength,
	float remainingDistanceOnSegment
) {}
