package online.davisfamily.threedee.behaviour.routing;

import online.davisfamily.threedee.behaviour.routing.transfer.TransferDecisionStrategy;

public final class TransferZone {
    private final float startDistance;
    private final float length;
    private final RouteSegment targetSegment;
    private final float targetStartDistance;
    private final TransferDecisionStrategy decisionStrategy;

    public TransferZone(
            float startDistance,
            float length,
            RouteSegment targetSegment,
            float targetStartDistance,
            TransferDecisionStrategy decisionStrategy) {

        if (targetSegment == null) {
            throw new IllegalArgumentException("targetSegment must not be null");
        }
        if (decisionStrategy == null) {
            throw new IllegalArgumentException("decisionStrategy must not be null");
        }
        if (length <= 0f) {
            throw new IllegalArgumentException("length must be > 0");
        }

        this.startDistance = startDistance;
        this.length = length;
        this.targetSegment = targetSegment;
        this.targetStartDistance = targetStartDistance;
        this.decisionStrategy = decisionStrategy;
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

    public TransferDecisionStrategy getDecisionStrategy() {
        return decisionStrategy;
    }

    public boolean contains(float distanceOnSegment) {
        return distanceOnSegment >= startDistance && distanceOnSegment <= getEndDistance();
    }
}