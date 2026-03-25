package online.davisfamily.threedee.behaviour.routing;

import java.util.List;

import online.davisfamily.threedee.behaviour.routing.GraphFollowerBehaviour.TravelDirection;
import online.davisfamily.threedee.rendering.RenderableObject;

public class PreferredRouteIdDecisionProvider implements RouteDecisionProvider {

	private final String preferredForwardLabel;
	private final String preferredReverseLabel;
	

	public PreferredRouteIdDecisionProvider(String preferredForwardLabel, String preferredReverseLabel) {
		super();
		this.preferredForwardLabel = preferredForwardLabel;
		this.preferredReverseLabel = preferredReverseLabel;
	}


	@Override
	public RouteSegment chooseNext(RenderableObject object, RouteSegment current, List<RouteSegment> options, TravelDirection travelDirection) {
		if (options == null || options.isEmpty())
			return null;
		
        String preferred = (travelDirection == TravelDirection.FORWARD)
                ? preferredForwardLabel
                : preferredReverseLabel;

        for (RouteSegment option : options) {
            if (preferred.equals(option.getLabel())) {
                return option;
            }
        }
        return options.get(0);		
	}

}
