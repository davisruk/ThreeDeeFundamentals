package online.davisfamily.warehouse.sim.totebag;

public class PrlToPcrTransfer {
    private final Pack pack;
    private final String sourcePrlId;
    private final float targetPcrFrontDistance;
    private final double totalTravelSeconds;
    private double remainingTravelSeconds;

    public PrlToPcrTransfer(Pack pack, String sourcePrlId, float targetPcrFrontDistance, double totalTravelSeconds) {
        if (pack == null) {
            throw new IllegalArgumentException("pack must not be null");
        }
        if (sourcePrlId == null || sourcePrlId.isBlank()) {
            throw new IllegalArgumentException("sourcePrlId must not be blank");
        }
        if (targetPcrFrontDistance < pack.getDimensions().length()) {
            throw new IllegalArgumentException("targetPcrFrontDistance must be >= pack length");
        }
        if (totalTravelSeconds < 0d) {
            throw new IllegalArgumentException("totalTravelSeconds must be >= 0");
        }
        this.pack = pack;
        this.sourcePrlId = sourcePrlId;
        this.targetPcrFrontDistance = targetPcrFrontDistance;
        this.totalTravelSeconds = totalTravelSeconds;
        this.remainingTravelSeconds = totalTravelSeconds;
    }

    public Pack getPack() {
        return pack;
    }

    public String getSourcePrlId() {
        return sourcePrlId;
    }

    public float getTargetPcrFrontDistance() {
        return targetPcrFrontDistance;
    }

    public double getTotalTravelSeconds() {
        return totalTravelSeconds;
    }

    public double getRemainingTravelSeconds() {
        return remainingTravelSeconds;
    }

    public void advance(double dtSeconds) {
        remainingTravelSeconds = Math.max(0d, remainingTravelSeconds - dtSeconds);
    }

    public boolean isComplete() {
        return remainingTravelSeconds <= 0d;
    }

    public double getProgress() {
        if (totalTravelSeconds <= 0d) {
            return 1d;
        }
        return 1d - (remainingTravelSeconds / totalTravelSeconds);
    }
}
