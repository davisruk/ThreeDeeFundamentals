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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ToteLoadPlan {
    private final String toteId;
    private final List<PackPlan> packPlans;

    public ToteLoadPlan(String toteId, List<PackPlan> packPlans) {
        if (toteId == null || toteId.isBlank()) {
            throw new IllegalArgumentException("toteId must not be blank");
        }
        if (packPlans == null || packPlans.isEmpty()) {
            throw new IllegalArgumentException("packPlans must not be empty");
        }
        this.toteId = toteId;
        this.packPlans = List.copyOf(packPlans);
    }

    public String getToteId() {
        return toteId;
    }

    public List<PackPlan> getPackPlans() {
        return Collections.unmodifiableList(packPlans);
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
}
