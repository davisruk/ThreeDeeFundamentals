package online.davisfamily.threedee.behaviour.routing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import online.davisfamily.threedee.path.PathSegment3;

public class RouteBuilder {
    private final List<RouteSegment> segments = new ArrayList<>();

    public RouteSegment segment(String label, PathSegment3 geometry) {
        RouteSegment segment = new RouteSegment(label, geometry);
        segments.add(segment);
        return segment;
    }

    public RouteBuilder connect(RouteSegment from, RouteSegment to) {
        from.connectTo(to);
        return this;
    }

    public RouteBuilder connect(RouteSegment from, RouteSegment to, float targetEntryDistance) {
        from.connectTo(to, targetEntryDistance);
        return this;
    }

    public List<RouteSegment> getSegments() {
        return Collections.unmodifiableList(segments);
    }
}
