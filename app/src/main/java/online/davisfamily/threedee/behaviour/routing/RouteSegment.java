package online.davisfamily.threedee.behaviour.routing;

import java.util.ArrayList;
import java.util.List;

import online.davisfamily.threedee.path.PathSegment3;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class RouteSegment {
    private final String label;
    private final PathSegment3 geometry;

    private final List<RouteConnection> nextConnections = new ArrayList<>();
    private final List<RouteConnection> previousConnections = new ArrayList<>();
    private final List<TransferZone> transferZones = new ArrayList<>();

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

    public List<RouteConnection> getNextConnections() {
        return nextConnections;
    }

    public List<RouteConnection> getPreviousConnections() {
        return previousConnections;
    }

    public List<TransferZone> getTransferZones() {
        return transferZones;
    }

    /**
     * Connect this segment to the start of the next segment.
     */
    public void connectTo(RouteSegment next) {
        connectTo(next, 0f, geometry.getTotalLength());
    }

    /**
     * Connect this segment to an arbitrary entry distance on the next segment.
     * The reverse connection is recorded on the next segment with this segment's end distance.
     */
    public void connectTo(RouteSegment next, float nextEntryDistance) {
        connectTo(next, nextEntryDistance, geometry.getTotalLength());
    }

    /**
     * Full form:
     * this segment connects from thisExitDistance
     * to next segment at nextEntryDistance.
     *
     * For now, your follower only uses nextEntryDistance for forward traversal,
     * but keeping both values makes the model more honest and future-proof.
     */
    public void connectTo(RouteSegment next, float nextEntryDistance, float thisExitDistance) {
        if (next == null) {
            throw new IllegalArgumentException("next must not be null");
        }

        if (thisExitDistance < 0f || thisExitDistance > geometry.getTotalLength()) {
            throw new IllegalArgumentException("thisExitDistance out of range");
        }

        if (nextEntryDistance < 0f || nextEntryDistance > next.getGeometry().getTotalLength()) {
            throw new IllegalArgumentException("nextEntryDistance out of range");
        }

        nextConnections.add(new RouteConnection(next, nextEntryDistance));
        next.previousConnections.add(new RouteConnection(this, thisExitDistance));
    }
}
