package online.davisfamily.threedee.behaviour.routing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import online.davisfamily.threedee.path.PathSegment3;

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

    public void addTransferZone(TransferZone zone) {
        if (zone == null) {
            throw new IllegalArgumentException("zone must not be null");
        }

        float totalLength = geometry.getTotalLength();
        if (zone.getStartDistance() < 0f || zone.getEndDistance() > totalLength) {
            throw new IllegalArgumentException("transfer zone must lie within the source segment length");
        }

        float targetLength = zone.getTargetSegment().getGeometry().getTotalLength();
        if (zone.getTargetStartDistance() < 0f || zone.getTargetStartDistance() > targetLength) {
            throw new IllegalArgumentException("targetStartDistance must lie within the target segment length");
        }

        for (TransferZone existing : transferZones) {
            boolean overlaps = zone.getStartDistance() < existing.getEndDistance()
                    && zone.getEndDistance() > existing.getStartDistance();
            if (overlaps) {
                throw new IllegalArgumentException("transfer zones must not overlap on the same source segment");
            }
        }

        transferZones.add(zone);
        transferZones.sort(Comparator.comparing(TransferZone::getStartDistance));
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
