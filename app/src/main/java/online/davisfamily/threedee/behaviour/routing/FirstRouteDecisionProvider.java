package online.davisfamily.threedee.behaviour.routing;

import java.util.List;

import online.davisfamily.threedee.behaviour.routing.GraphFollowerBehaviour.DirectionOfTravel;
import online.davisfamily.threedee.rendering.RenderableObject;

public class FirstRouteDecisionProvider implements RouteDecisionProvider {

	@Override
	public RouteSegment chooseNext(RenderableObject object, RouteSegment current, List<RouteSegment> options, DirectionOfTravel travelDirection) {
		
		if (options == null || options.isEmpty())
			return null;
		return options.get(0);
	}
}
