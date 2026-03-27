package online.davisfamily.threedee.model.tracks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.path.PathSegment3;

public final class RouteTrackLayout {
    private final RouteSegment routeSegment;
    private final List<TrackInterval> intervals;
    private final float renderStartDistance;
    private final float renderEndDistance;

    public RouteTrackLayout(
            RouteSegment routeSegment,
            List<TrackInterval> intervals,
            float renderStartDistance,
            float renderEndDistance) {

        if (routeSegment == null) {
            throw new IllegalArgumentException("routeSegment must not be null");
        }
        if (intervals == null) {
            throw new IllegalArgumentException("intervals must not be null");
        }
        if (renderStartDistance < 0f) {
            throw new IllegalArgumentException("renderStartDistance must be >= 0");
        }
        if (renderEndDistance < renderStartDistance) {
            throw new IllegalArgumentException("renderEndDistance must be >= renderStartDistance");
        }

        this.routeSegment = routeSegment;
        this.intervals = Collections.unmodifiableList(new ArrayList<>(intervals));
        this.renderStartDistance = renderStartDistance;
        this.renderEndDistance = renderEndDistance;
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

    public float getRenderStartDistance() {
        return renderStartDistance;
    }

    public float getRenderEndDistance() {
        return renderEndDistance;
    }
}