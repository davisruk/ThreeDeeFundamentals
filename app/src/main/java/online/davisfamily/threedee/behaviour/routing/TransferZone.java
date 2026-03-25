package online.davisfamily.threedee.behaviour.routing;

public final class TransferZone {
    private final float startDistance;
    private final float length;
    private final RouteSegment targetSegment;
    private final float targetStartDistance;

    public TransferZone(
            float startDistance,
            float length,
            RouteSegment targetSegment,
            float targetStartDistance) {
        this.startDistance = startDistance;
        this.length = length;
        this.targetSegment = targetSegment;
        this.targetStartDistance = targetStartDistance;
    }

    public float getStartDistance() {
        return startDistance;
    }

    public float getLength() {
        return length;
    }

    public float getEndDistance() {
        return startDistance + length;
    }

    public RouteSegment getTargetSegment() {
        return targetSegment;
    }

    public float getTargetStartDistance() {
        return targetStartDistance;
    }

    public boolean contains(float distanceOnSegment) {
        return distanceOnSegment >= startDistance && distanceOnSegment <= getEndDistance();
    }
}