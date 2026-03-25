package online.davisfamily.threedee.model.tracks;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.path.PathSegment3;

public final class RouteTrackLayout {
    private final RouteSegment routeSegment;
    private final List<TrackInterval> intervals;

    public RouteTrackLayout(RouteSegment routeSegment, List<TrackInterval> intervals) {
        if (routeSegment == null) {
            throw new IllegalArgumentException("routeSegment must not be null");
        }
        if (intervals == null) {
            throw new IllegalArgumentException("intervals must not be null");
        }

        this.routeSegment = routeSegment;
        this.intervals = Collections.unmodifiableList(new ArrayList<>(intervals));
    }

    public RouteSegment getRouteSegment() {
        return routeSegment;
    }

    public PathSegment3 getGeometry() {
        return routeSegment.getGeometry();
    }

    public List<TrackInterval> getIntervals() {
        return intervals;
    }
}
