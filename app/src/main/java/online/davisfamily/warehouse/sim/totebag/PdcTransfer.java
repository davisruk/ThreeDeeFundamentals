package online.davisfamily.warehouse.sim.totebag;

public class PdcTransfer {
    private final Pack pack;
    private final String targetPrlId;
    private final float sourcePdcFrontDistance;
    private final double totalTravelSeconds;
    private double remainingTravelSeconds;

    public PdcTransfer(Pack pack, String targetPrlId, float sourcePdcFrontDistance, double totalTravelSeconds) {
        if (pack == null) {
            throw new IllegalArgumentException("pack must not be null");
        }
        if (targetPrlId == null || targetPrlId.isBlank()) {
            throw new IllegalArgumentException("targetPrlId must not be blank");
        }
        if (sourcePdcFrontDistance < 0f) {
            throw new IllegalArgumentException("sourcePdcFrontDistance must be >= 0");
        }
        if (totalTravelSeconds < 0d) {
            throw new IllegalArgumentException("totalTravelSeconds must be >= 0");
        }
        this.pack = pack;
        this.targetPrlId = targetPrlId;
        this.sourcePdcFrontDistance = sourcePdcFrontDistance;
        this.totalTravelSeconds = totalTravelSeconds;
        this.remainingTravelSeconds = totalTravelSeconds;
    }

    public Pack getPack() {
        return pack;
    }

    public String getTargetPrlId() {
        return targetPrlId;
    }

    public float getSourcePdcFrontDistance() {
        return sourcePdcFrontDistance;
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
