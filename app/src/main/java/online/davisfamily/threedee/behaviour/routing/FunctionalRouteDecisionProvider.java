package online.davisfamily.threedee.behaviour.routing;

import java.util.List;
import java.util.function.BiFunction;

import online.davisfamily.threedee.behaviour.routing.RouteFollower.TravelDirection;
import online.davisfamily.threedee.rendering.RenderableObject;

public class FunctionalRouteDecisionProvider implements RouteDecisionProvider {

    private final BiFunction<RouteSegment, List<RouteSegment>, RouteSegment> chooser;

    public FunctionalRouteDecisionProvider(
            BiFunction<RouteSegment, List<RouteSegment>, RouteSegment> chooser) {
        this.chooser = chooser;
    }

    @Override
    public RouteSegment chooseNext(
            RenderableObject object,
            RouteSegment current,
            List<RouteSegment> options, TravelDirection direction) {

        if (options == null || options.isEmpty()) {
            return null;
        }

        RouteSegment chosen = chooser.apply(current, options);
        return chosen != null ? chosen : options.get(0);
    }
}