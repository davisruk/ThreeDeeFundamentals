package online.davisfamily.threedee.sim.framework.objects.sensors;

import online.davisfamily.threedee.behaviour.routing.RouteSegment;

public record WindowSensorAreaImpl (
		String id,
		RouteSegment routeSegment,
		float startDistance,
		float endDistance
)implements WindowSensorArea {}
