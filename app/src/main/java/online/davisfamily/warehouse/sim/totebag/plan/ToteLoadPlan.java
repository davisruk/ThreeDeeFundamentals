package online.davisfamily.warehouse.sim.totebag.plan;

import online.davisfamily.warehouse.sim.totebag.plan.*;
import online.davisfamily.warehouse.sim.totebag.pack.*;
import online.davisfamily.warehouse.sim.totebag.machine.*;
import online.davisfamily.warehouse.sim.totebag.conveyor.*;
import online.davisfamily.warehouse.sim.totebag.transfer.*;
import online.davisfamily.warehouse.sim.totebag.device.*;
import online.davisfamily.warehouse.sim.totebag.assignment.*;
import online.davisfamily.warehouse.sim.totebag.control.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ToteLoadPlan {
    private final String toteId;
    private final List<PackPlan> packPlans;

    public ToteLoadPlan(String toteId, List<PackPlan> packPlans) {
        if (toteId == null || toteId.isBlank()) {
            throw new IllegalArgumentException("toteId must not be blank");
        }
        if (packPlans == null) {
            throw new IllegalArgumentException("packPlans must not be null");
        }
        this.toteId = toteId;
        this.packPlans = validatedPackPlans(packPlans);
    }

    public String getToteId() {
        return toteId;
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
        return new ToteLoadPlan(toteId, combinedPackPlans);
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
