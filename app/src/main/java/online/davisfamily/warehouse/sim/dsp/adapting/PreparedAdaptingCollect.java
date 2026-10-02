package online.davisfamily.warehouse.sim.dsp.adapting;

import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

/** Read-only, exact-identity decision for one pending strict bench completion. */
public record PreparedAdaptingCollect(
        AdaptingBenchId benchId,
        AdaptingBenchCompletion completion,
        ToteLoadPlan currentLoadPlan,
        ToteLoadPlan replacementLoadPlan,
        PreparedCollectedPackPlans preparedPacks,
        Runnable observerCommit) {
    public PreparedAdaptingCollect {
        if (benchId == null || completion == null || currentLoadPlan == null
                || replacementLoadPlan == null || preparedPacks == null || observerCommit == null) {
            throw new IllegalArgumentException("prepared COLLECT components must not be null");
        }
        if (completion.preparedOrderGroup().isEmpty()) {
            throw new IllegalArgumentException("prepared COLLECT requires strict order group");
        }
    }
}
