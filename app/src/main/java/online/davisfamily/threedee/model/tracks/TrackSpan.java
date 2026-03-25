package online.davisfamily.threedee.model.tracks;

public class TrackSpan {
    private final float startDistance;
    private final float endDistance;

    public TrackSpan(float startDistance, float endDistance) {
        if (endDistance < startDistance) {
            throw new IllegalArgumentException("endDistance must be >= startDistance");
        }
        this.startDistance = startDistance;
        this.endDistance = endDistance;
    }

    public float getStartDistance() {
        return startDistance;
    }

    public float getEndDistance() {
        return endDistance;
    }

    public float getLength() {
        return endDistance - startDistance;
    }
}
