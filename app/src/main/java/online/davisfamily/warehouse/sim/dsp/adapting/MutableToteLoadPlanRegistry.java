package online.davisfamily.warehouse.sim.dsp.adapting;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlanProvider;

public interface MutableToteLoadPlanRegistry extends ToteLoadPlanProvider {
    ToteLoadPlan getLoadPlanFor(PhysicalToteId physicalToteId);

    @Override
    default ToteLoadPlan getLoadPlanFor(String toteId) {
        return getLoadPlanFor(new PhysicalToteId(toteId));
    }

    void putLoadPlan(ToteLoadPlan toteLoadPlan);
}
