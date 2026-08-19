package online.davisfamily.warehouse.sim.dsp.bagging;

import java.util.List;

import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;

public final class MaximumPackCountBagCapacityPolicy implements BagCapacityPolicy {
    private final int maximumPackCount;

    public MaximumPackCountBagCapacityPolicy(int maximumPackCount) {
        if (maximumPackCount <= 0) {
            throw new IllegalArgumentException("maximumPackCount must be positive");
        }
        this.maximumPackCount = maximumPackCount;
    }

    @Override
    public boolean canAdd(List<PackPlan> currentPackPlans, PackPlan candidatePackPlan) {
        if (currentPackPlans == null) {
            throw new IllegalArgumentException("currentPackPlans must not be null");
        }
        if (candidatePackPlan == null) {
            throw new IllegalArgumentException("candidatePackPlan must not be null");
        }
        return currentPackPlans.size() + 1 <= maximumPackCount;
    }
}
