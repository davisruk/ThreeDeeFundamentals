package online.davisfamily.threedee.behaviour.routing;

import java.util.List;

import online.davisfamily.threedee.behaviour.routing.GraphFollowerBehaviour.TravelDirection;
import online.davisfamily.threedee.rendering.RenderableObject;

public class PreferredRouteIdDecisionProvider implements RouteDecisionProvider {

	private final int preferredForwardId;
	private final int preferredReverseId;
	

	public PreferredRouteIdDecisionProvider(int preferredForwardId, int preferredReverseId) {
		super();
		this.preferredForwardId = preferredForwardId;
		this.preferredReverseId = preferredReverseId;
	}


	@Override
	public RouteSegment chooseNext(RenderableObject object, RouteSegment current, List<RouteSegment> options, TravelDirection travelDirection) {
		if (options == null || options.isEmpty())
			return null;
		
        int preferred = (travelDirection == TravelDirection.FORWARD)
                ? preferredForwardId
                : preferredReverseId;

        for (RouteSegment option : options) {
            if (preferred == option.getId()) {
                return option;
            }
        }
        return options.get(0);		
	}

}
