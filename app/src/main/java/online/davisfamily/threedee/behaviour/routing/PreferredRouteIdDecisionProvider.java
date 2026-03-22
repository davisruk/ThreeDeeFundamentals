package online.davisfamily.threedee.behaviour.routing;

import java.util.List;

import online.davisfamily.threedee.behaviour.routing.GraphFollowerBehaviour.DirectionOfTravel;
import online.davisfamily.threedee.rendering.RenderableObject;

public class PreferredRouteIdDecisionProvider implements RouteDecisionProvider {

	private final int preferredId;
	

	public PreferredRouteIdDecisionProvider(int preferredId) {
		super();
		this.preferredId = preferredId;
	}


	@Override
	public RouteSegment chooseNext(RenderableObject object, RouteSegment current, List<RouteSegment> options, DirectionOfTravel travelDirection) {
		if (options == null || options.isEmpty())
			return null;
		
		for(RouteSegment r: options) {
			int id = r.getId();
			if ( id == preferredId)
				return r;
		}
		
		return options.get(0);
	}

}
