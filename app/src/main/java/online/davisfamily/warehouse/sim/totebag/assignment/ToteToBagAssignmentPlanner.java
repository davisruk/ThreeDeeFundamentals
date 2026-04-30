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

        List<PrlAssignmentPlan> result = new ArrayList<>();
        int assignmentCount = Math.min(correlationIds.size(), prlIdsInPriorityOrder.size());
        for (int prlIndex = 0; prlIndex < assignmentCount; prlIndex++) {
            String correlationId = correlationIds.get(prlIndex);
            result.add(new PrlAssignmentPlan(
                    prlIdsInPriorityOrder.get(prlIndex),
                    correlationId,
                    batchPlan.expectedPackCountFor(correlationId)));
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
