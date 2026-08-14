package online.davisfamily.warehouse.sim.dsp.thirdparty;

import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;

@FunctionalInterface
public interface ThirdPartyPackPlanFactory {
    PackPlan createPackPlan(ThirdPartyVisit visit, ThirdPartyLineWork lineWork, int packOrdinal);
}
