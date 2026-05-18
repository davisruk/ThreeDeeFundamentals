package online.davisfamily.warehouse.sim.dsp.adapting;

import java.util.List;

import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;

public interface CollectedPackPlanFactory {
    List<PackPlan> createPackPlans(List<AdaptedLineRecord> collectedLines);
}
