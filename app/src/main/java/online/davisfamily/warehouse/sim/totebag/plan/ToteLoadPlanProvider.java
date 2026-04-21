package online.davisfamily.warehouse.sim.totebag.plan;

public interface ToteLoadPlanProvider {
    ToteLoadPlan getLoadPlanFor(String toteId);
}
