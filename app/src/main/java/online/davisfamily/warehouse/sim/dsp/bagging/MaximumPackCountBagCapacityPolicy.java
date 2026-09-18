package online.davisfamily.warehouse.sim.dsp.bagging;

public final class MaximumPackCountBagCapacityPolicy implements BagCapacityPolicy {
    private final int maximumPackCount;

    public MaximumPackCountBagCapacityPolicy(int maximumPackCount) {
        if (maximumPackCount <= 0) {
            throw new IllegalArgumentException("maximumPackCount must be positive");
        }
        this.maximumPackCount = maximumPackCount;
    }

    @Override
    public boolean canAdd(int currentPackCount, BagPackDemand candidate) {
        if (currentPackCount < 0) {
            throw new IllegalArgumentException("currentPackCount must not be negative");
        }
        if (candidate == null) {
            throw new IllegalArgumentException("candidate must not be null");
        }
        return currentPackCount < maximumPackCount;
    }
}
