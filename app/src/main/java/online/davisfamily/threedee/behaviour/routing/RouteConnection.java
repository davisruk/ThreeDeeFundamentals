package online.davisfamily.threedee.behaviour.routing;

public final class RouteConnection {
    private final RouteSegment segment;
    private final float entryDistance;

    public RouteConnection(RouteSegment segment, float entryDistance) {
        if (segment == null) {
            throw new IllegalArgumentException("segment must not be null");
        }
        if (entryDistance < 0f || entryDistance > segment.getGeometry().getTotalLength()) {
            throw new IllegalArgumentException(
                "entryDistance must be within the target segment length"
            );
        }

        this.segment = segment;
        this.entryDistance = entryDistance;
    }

    public RouteSegment getSegment() {
        return segment;
    }

    public float getEntryDistance() {
        return entryDistance;
    }

    @Override
    public String toString() {
        return "RouteConnection{segment=" + segment.getLabel()
                + ", entryDistance=" + entryDistance + "}";
    }
}