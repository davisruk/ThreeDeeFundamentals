package online.davisfamily.threedee.behaviour.routing;

import java.util.ArrayList;
import java.util.List;

import online.davisfamily.threedee.path.PathSegment3;

public final class RouteSegment {
    private final String label;
    private final PathSegment3 geometry;

    private final List<RouteConnection> nextConnections = new ArrayList<>();
    private final List<RouteConnection> previousConnections = new ArrayList<>();

    public RouteSegment(String label, PathSegment3 geometry) {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("label must not be blank");
        }
        if (geometry == null) {
            throw new IllegalArgumentException("geometry must not be null");
        }

        this.label = label;
        this.geometry = geometry;
    }

    public String getLabel() {
        return label;
    }

    public PathSegment3 getGeometry() {
        return geometry;
    }

    public float length() {
    	return geometry.getTotalLength();
    }
    
    public List<RouteConnection> getNextConnections() {
        return nextConnections;
    }

    public List<RouteConnection> getPreviousConnections() {
        return previousConnections;
    }

    public void connectTo(RouteSegment next) {
        RouteConnection nextConnection = new RouteConnection(next, 0f);
        this.nextConnections.add(nextConnection);

        RouteConnection previousConnection = new RouteConnection(this, geometry.getTotalLength());
        next.previousConnections.add(previousConnection);
    }

    public void connectTo(RouteSegment next, float nextEntryDistance) {
        RouteConnection nextConnection = new RouteConnection(next, nextEntryDistance);
        this.nextConnections.add(nextConnection);

        RouteConnection previousConnection = new RouteConnection(this, geometry.getTotalLength());
        next.previousConnections.add(previousConnection);
    }
}
