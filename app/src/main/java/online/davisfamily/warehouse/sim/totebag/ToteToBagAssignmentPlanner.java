package online.davisfamily.warehouse.sim.totebag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ToteToBagAssignmentPlanner {
    public List<PrlAssignmentPlan> createPlans(ToteLoadPlan toteLoadPlan, List<String> prlIdsInPriorityOrder) {
        if (toteLoadPlan == null) {
            throw new IllegalArgumentException("toteLoadPlan must not be null");
        }
        if (prlIdsInPriorityOrder == null || prlIdsInPriorityOrder.isEmpty()) {
            throw new IllegalArgumentException("prlIdsInPriorityOrder must not be empty");
        }

        Map<String, List<PackPlan>> byCorrelationId = toteLoadPlan.packPlansByCorrelationId();
        if (byCorrelationId.size() > prlIdsInPriorityOrder.size()) {
            throw new IllegalArgumentException("Not enough PRLs available for tote load plan");
        }

        List<PrlAssignmentPlan> result = new ArrayList<>();
        int prlIndex = 0;
        for (String correlationId : toteLoadPlan.orderedCorrelationIds()) {
            List<PackPlan> plans = byCorrelationId.get(correlationId);
            result.add(new PrlAssignmentPlan(
                    prlIdsInPriorityOrder.get(prlIndex),
                    correlationId,
                    plans.size()));
            prlIndex++;
        }
        return result;
    }
}
