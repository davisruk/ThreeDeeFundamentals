package online.davisfamily.warehouse.sim.dsp.adapting;

import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlanProvider;

public interface MutableToteLoadPlanRegistry extends ToteLoadPlanProvider {
    void putLoadPlan(ToteLoadPlan toteLoadPlan);
}
