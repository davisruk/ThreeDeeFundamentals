package online.davisfamily.warehouse.sim.totebag.assignment;

import online.davisfamily.warehouse.sim.totebag.plan.*;
import online.davisfamily.warehouse.sim.totebag.pack.*;
import online.davisfamily.warehouse.sim.totebag.machine.*;
import online.davisfamily.warehouse.sim.totebag.conveyor.*;
import online.davisfamily.warehouse.sim.totebag.transfer.*;
import online.davisfamily.warehouse.sim.totebag.device.*;
import online.davisfamily.warehouse.sim.totebag.assignment.*;
import online.davisfamily.warehouse.sim.totebag.control.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ToteToBagAssignmentPlanner {
    public List<PrlAssignmentPlan> createPlans(ToteToBagBatchPlan batchPlan, List<String> prlIdsInPriorityOrder) {
        if (batchPlan == null) {
            throw new IllegalArgumentException("batchPlan must not be null");
        }
        if (prlIdsInPriorityOrder == null || prlIdsInPriorityOrder.isEmpty()) {
            throw new IllegalArgumentException("prlIdsInPriorityOrder must not be empty");
        }

        List<String> correlationIds = batchPlan.orderedCorrelationIds();
        if (correlationIds.size() > prlIdsInPriorityOrder.size()) {
            throw new IllegalArgumentException("Not enough PRLs available for tote load plan");
        }

        List<PrlAssignmentPlan> result = new ArrayList<>();
        int prlIndex = 0;
        for (String correlationId : correlationIds) {
            result.add(new PrlAssignmentPlan(
                    prlIdsInPriorityOrder.get(prlIndex),
                    correlationId,
                    batchPlan.expectedPackCountFor(correlationId)));
            prlIndex++;
        }
        return result;
    }

    public List<PrlAssignmentPlan> createPlans(ToteLoadPlan toteLoadPlan, List<String> prlIdsInPriorityOrder) {
        if (toteLoadPlan == null) {
            throw new IllegalArgumentException("toteLoadPlan must not be null");
        }
        return createPlans(ToteToBagBatchPlan.fromToteLoadPlan(toteLoadPlan), prlIdsInPriorityOrder);
    }
}
