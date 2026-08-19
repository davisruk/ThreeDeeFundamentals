package online.davisfamily.warehouse.sim.dsp.bagging;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

public record BagPlanningRequest(List<BagPlanningTote> planningTotes) {

    public BagPlanningRequest {
        if (planningTotes == null || planningTotes.isEmpty()) {
            throw new IllegalArgumentException("planningTotes must not be empty");
        }
        if (planningTotes.stream().anyMatch(planningTote -> planningTote == null)) {
            throw new IllegalArgumentException("planningTotes must not contain null");
        }
        planningTotes = List.copyOf(planningTotes);

        Set<PhysicalToteId> physicalToteIds = new LinkedHashSet<>();
        Set<String> physicalPackIds = new LinkedHashSet<>();
        for (BagPlanningTote planningTote : planningTotes) {
            if (!physicalToteIds.add(planningTote.toteLoadPlan().physicalToteId())) {
                throw new IllegalArgumentException(
                        "Duplicate physical tote ID: " + planningTote.toteLoadPlan().physicalToteId().value());
            }
            planningTote.toteLoadPlan().getPackPlans().forEach(packPlan -> {
                if (!physicalPackIds.add(packPlan.packId())) {
                    throw new IllegalArgumentException("Duplicate physical pack ID: " + packPlan.packId());
                }
            });
        }
    }
}
