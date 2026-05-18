package online.davisfamily.warehouse.sim.dsp.adapting;

import java.util.LinkedHashMap;
import java.util.Map;

import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

public class MapBackedToteLoadPlanRegistry implements MutableToteLoadPlanRegistry {
    private final Map<String, ToteLoadPlan> loadPlansByToteId = new LinkedHashMap<>();

    @Override
    public ToteLoadPlan getLoadPlanFor(String toteId) {
        if (toteId == null || toteId.isBlank()) {
            throw new IllegalArgumentException("toteId must not be blank");
        }
        return loadPlansByToteId.get(toteId);
    }

    @Override
    public void putLoadPlan(ToteLoadPlan toteLoadPlan) {
        if (toteLoadPlan == null) {
            throw new IllegalArgumentException("toteLoadPlan must not be null");
        }
        loadPlansByToteId.put(toteLoadPlan.getToteId(), toteLoadPlan);
    }
}
