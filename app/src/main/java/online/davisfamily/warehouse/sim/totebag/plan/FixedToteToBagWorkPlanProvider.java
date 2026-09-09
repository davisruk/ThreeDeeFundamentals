package online.davisfamily.warehouse.sim.totebag.plan;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.OptionalInt;
import java.util.Set;

/** Compatibility provider backed by one immutable batch plan. */
public final class FixedToteToBagWorkPlanProvider implements ToteToBagWorkPlanProvider {
    private final ToteToBagBatchPlan batchPlan;
    private final Set<String> expectedCorrelationIds;

    public FixedToteToBagWorkPlanProvider(ToteToBagBatchPlan batchPlan) {
        if (batchPlan == null) {
            throw new IllegalArgumentException("batchPlan must not be null");
        }
        this.batchPlan = batchPlan;
        this.expectedCorrelationIds = Collections.unmodifiableSet(
                new LinkedHashSet<>(batchPlan.orderedCorrelationIds()));
    }

    public ToteToBagBatchPlan batchPlan() {
        return batchPlan;
    }

    @Override
    public OptionalInt expectedPackCount(String correlationId) {
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId must not be blank");
        }
        int count = batchPlan.expectedPackCountFor(correlationId.trim());
        return count > 0 ? OptionalInt.of(count) : OptionalInt.empty();
    }

    @Override
    public Set<String> expectedCorrelationIds() {
        return expectedCorrelationIds;
    }
}
