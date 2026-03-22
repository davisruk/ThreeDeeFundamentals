package online.davisfamily.threedee.behaviour.routing;

import java.util.List;

import online.davisfamily.threedee.rendering.RenderableObject;

public interface RouteDecisionProvider {
	RouteSegment chooseNext(
		RenderableObject object,
		RouteSegment current, 
		List<RouteSegment>options,
		GraphFollowerBehaviour.TravelDirection travelDirection
	);
}
