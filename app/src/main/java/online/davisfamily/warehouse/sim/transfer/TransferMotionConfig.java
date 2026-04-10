package online.davisfamily.warehouse.sim.transfer;

public final class TransferMotionConfig {
    private final double preferredDurationSeconds;
    private final float minMergeOffsetFromEntryDistance;
    private final float maxMergeOffsetFromEntryDistance;

    public TransferMotionConfig(
            double preferredDurationSeconds,
            float minMergeOffsetFromEntryDistance,
            float maxMergeOffsetFromEntryDistance) {

        if (preferredDurationSeconds <= 0.0) {
            throw new IllegalArgumentException("preferredDurationSeconds must be > 0");
        }
        if (minMergeOffsetFromEntryDistance < 0f) {
            throw new IllegalArgumentException("minMergeOffsetFromEntryDistance must be >= 0");
        }
        if (maxMergeOffsetFromEntryDistance < minMergeOffsetFromEntryDistance) {
            throw new IllegalArgumentException("maxMergeOffsetFromEntryDistance must be >= minMergeOffsetFromEntryDistance");
        }

        this.preferredDurationSeconds = preferredDurationSeconds;
        this.minMergeOffsetFromEntryDistance = minMergeOffsetFromEntryDistance;
        this.maxMergeOffsetFromEntryDistance = maxMergeOffsetFromEntryDistance;
    }

    public double getPreferredDurationSeconds() {
        return preferredDurationSeconds;
    }

    public float getMinMergeOffsetFromEntryDistance() {
        return minMergeOffsetFromEntryDistance;
    }

    public float getMaxMergeOffsetFromEntryDistance() {
        return maxMergeOffsetFromEntryDistance;
    }
}
