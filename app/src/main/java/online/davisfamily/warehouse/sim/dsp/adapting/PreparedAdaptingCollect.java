package online.davisfamily.warehouse.sim.dsp.adapting;

import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

/** Read-only, exact-identity decision for one pending strict bench completion. */
public record PreparedAdaptingCollect(
        AdaptingBenchId benchId,
        AdaptingBenchCompletion completion,
        ToteLoadPlan currentLoadPlan,
        ToteLoadPlan replacementLoadPlan,
        PreparedCollectedPackPlans preparedPacks,
        Runnable observerCommit,
        int positionOrdinal,
        AdaptingPreparedOrderGroup currentOrderGroup) {
    public PreparedAdaptingCollect(AdaptingBenchId benchId, AdaptingBenchCompletion completion,
            ToteLoadPlan currentLoadPlan, ToteLoadPlan replacementLoadPlan,
            PreparedCollectedPackPlans preparedPacks, Runnable observerCommit) {
        this(benchId, completion, currentLoadPlan, replacementLoadPlan, preparedPacks,
                observerCommit, 1, requiredGroup(completion));
    }

    public PreparedAdaptingCollect {
        if (benchId == null || completion == null || currentLoadPlan == null
                || replacementLoadPlan == null || preparedPacks == null || observerCommit == null
                || currentOrderGroup == null) {
            throw new IllegalArgumentException("prepared COLLECT components must not be null");
        }
        if (completion.preparedOrderGroup().isEmpty()) {
            throw new IllegalArgumentException("prepared COLLECT requires strict order group");
        }
        if (positionOrdinal < 1 || completion.visit().visitType() != AdaptingVisitType.COLLECT) {
            throw new IllegalArgumentException("prepared COLLECT requires an exact positive position");
        }
        var physicalToteId = completion.visit().physicalToteId();
        if (!physicalToteId.equals(currentLoadPlan.physicalToteId())
                || !physicalToteId.equals(replacementLoadPlan.physicalToteId())) {
            throw new IllegalStateException("prepared COLLECT load plan physical identity changed");
        }
        AdaptingPreparedOrderGroup retained = completion.preparedOrderGroup().orElseThrow();
        if (!retained.storeId().equals(currentOrderGroup.storeId())
                || !retained.referenceOrderId().equals(currentOrderGroup.referenceOrderId())
                || retained.firstCollection() != currentOrderGroup.firstCollection()
                || !retained.records().equals(currentOrderGroup.records())
                || !completion.collectedLines().equals(currentOrderGroup.records())) {
            throw new IllegalStateException("prepared COLLECT group facts changed");
        }
    }

    private static AdaptingPreparedOrderGroup requiredGroup(AdaptingBenchCompletion completion) {
        if (completion == null || completion.preparedOrderGroup().isEmpty()) {
            throw new IllegalArgumentException("prepared COLLECT requires strict order group");
        }
        return completion.preparedOrderGroup().orElseThrow();
    }
}
