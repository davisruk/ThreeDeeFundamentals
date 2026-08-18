package online.davisfamily.warehouse.sim.totebag.plan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

public class ToteLoadPlan {
    private final PhysicalToteId physicalToteId;
    private final List<PackPlan> packPlans;

    public ToteLoadPlan(String toteId, List<PackPlan> packPlans) {
        this(new PhysicalToteId(toteId), packPlans);
    }

    public ToteLoadPlan(PhysicalToteId physicalToteId, List<PackPlan> packPlans) {
        if (physicalToteId == null) {
            throw new IllegalArgumentException("physicalToteId must not be null");
        }
        if (packPlans == null) {
            throw new IllegalArgumentException("packPlans must not be null");
        }
        this.physicalToteId = physicalToteId;
        this.packPlans = validatedPackPlans(packPlans);
    }

    public PhysicalToteId physicalToteId() {
        return physicalToteId;
    }

    @Deprecated(forRemoval = false)
    public String getToteId() {
        return physicalToteId.value();
    }

    public List<PackPlan> getPackPlans() {
        return Collections.unmodifiableList(packPlans);
    }

    public ToteLoadPlan withAdditionalPackPlans(List<PackPlan> additionalPackPlans) {
        if (additionalPackPlans == null) {
            throw new IllegalArgumentException("additionalPackPlans must not be null");
        }

        List<PackPlan> combinedPackPlans = new ArrayList<>(packPlans);
        combinedPackPlans.addAll(additionalPackPlans);
        return new ToteLoadPlan(physicalToteId, combinedPackPlans);
    }

    public Map<String, List<PackPlan>> packPlansByCorrelationId() {
        Map<String, List<PackPlan>> result = new LinkedHashMap<>();
        for (PackPlan packPlan : packPlans) {
            result.computeIfAbsent(packPlan.correlationId(), ignored -> new ArrayList<>()).add(packPlan);
        }
        return result;
    }

    public int packCountForCorrelationId(String correlationId) {
        return packPlansByCorrelationId().getOrDefault(correlationId, List.of()).size();
    }

    public List<String> orderedCorrelationIds() {
        return new ArrayList<>(packPlansByCorrelationId().keySet());
    }

    private static List<PackPlan> validatedPackPlans(List<PackPlan> packPlans) {
        List<PackPlan> copy = List.copyOf(packPlans);
        Set<String> packIds = new LinkedHashSet<>();
        for (PackPlan packPlan : copy) {
            if (!packIds.add(packPlan.packId())) {
                throw new IllegalArgumentException("Duplicate packId: " + packPlan.packId());
            }
        }
        return copy;
    }
}
