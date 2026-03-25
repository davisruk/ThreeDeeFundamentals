package online.davisfamily.threedee.model.tracks;

public class TargetGuideOpening {
    private final float centreDistance;
    private final GuideSide side;

    public TargetGuideOpening(float centreDistance, GuideSide side) {
        if (side == null) {
            throw new IllegalArgumentException("side must not be null");
        }
        this.centreDistance = centreDistance;
        this.side = side;
    }

    public float getCentreDistance() {
        return centreDistance;
    }

    public GuideSide getSide() {
        return side;
    }
}
