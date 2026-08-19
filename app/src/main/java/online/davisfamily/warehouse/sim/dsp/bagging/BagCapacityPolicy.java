package online.davisfamily.warehouse.sim.dsp.bagging;

import java.util.List;

import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;

public interface BagCapacityPolicy {
    boolean canAdd(List<PackPlan> currentPackPlans, PackPlan candidatePackPlan);
}
