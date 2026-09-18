package online.davisfamily.warehouse.sim.dsp.bagging;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

public record BagPlanningRequest(
        List<BagPackDemand> packDemands,
        List<BagPlanningTote> planningTotes) {

    public BagPlanningRequest {
        if (packDemands == null) {
            throw new IllegalArgumentException("packDemands must not be null");
        }
        if (planningTotes == null) {
            throw new IllegalArgumentException("planningTotes must not be null");
        }
        if (packDemands.isEmpty() && planningTotes.isEmpty()) {
            throw new IllegalArgumentException("packDemands and planningTotes must not both be empty");
        }

        packDemands = copyAndValidateDemands(packDemands);
        planningTotes = copyAndValidatePlanningTotes(planningTotes);
    }

    private static List<BagPackDemand> copyAndValidateDemands(List<BagPackDemand> demands) {
        Set<PlannedPackSlotKey> slotKeys = new LinkedHashSet<>();
        Set<String> reservedPhysicalPackIds = new LinkedHashSet<>();
        for (BagPackDemand demand : demands) {
            if (demand == null) {
                throw new IllegalArgumentException("packDemands must not contain null");
            }
            if (!slotKeys.add(demand.slotKey())) {
                throw new IllegalArgumentException("Duplicate planned pack slot key: " + demand.slotKey());
            }
            if (!reservedPhysicalPackIds.add(demand.reservedPhysicalPackId())) {
                throw new IllegalArgumentException(
                        "Duplicate reserved physical pack ID: " + demand.reservedPhysicalPackId());
            }
        }
        return List.copyOf(demands);
    }

    private static List<BagPlanningTote> copyAndValidatePlanningTotes(
            List<BagPlanningTote> planningTotes) {
        Set<PhysicalToteId> physicalToteIds = new LinkedHashSet<>();
        Set<String> physicalPackIds = new LinkedHashSet<>();
        for (BagPlanningTote planningTote : planningTotes) {
            if (planningTote == null) {
                throw new IllegalArgumentException("planningTotes must not contain null");
            }
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
        return List.copyOf(planningTotes);
    }
}
