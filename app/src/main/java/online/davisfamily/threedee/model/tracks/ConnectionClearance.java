package online.davisfamily.threedee.model.tracks;

public final class ConnectionClearance {

    public enum ConnectionClearanceType {
        TRANSFER_SOURCE,
        CONNECTION_SOURCE,
        CONNECTION_TARGET
    }

    private final float startDistance;
    private final float endDistance;
    private final GuideSide side;
    private final ConnectionClearanceType type;

    public ConnectionClearance(
            float startDistance,
            float endDistance,
            GuideSide side,
            ConnectionClearanceType type) {

        if (endDistance < startDistance) {
            throw new IllegalArgumentException("endDistance must be >= startDistance");
        }
        if (side == null) {
            throw new IllegalArgumentException("side must not be null");
        }
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }

        this.startDistance = startDistance;
        this.endDistance = endDistance;
        this.side = side;
        this.type = type;
    }

    public float getStartDistance() {
        return startDistance;
    }

    public float getEndDistance() {
        return endDistance;
    }

    public GuideSide getSide() {
        return side;
    }

    public ConnectionClearanceType getType() {
        return type;
    }
}