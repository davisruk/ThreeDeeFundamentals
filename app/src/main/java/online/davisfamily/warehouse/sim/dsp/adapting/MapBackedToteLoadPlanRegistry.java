package online.davisfamily.warehouse.sim.dsp.adapting;

import java.util.LinkedHashMap;
import java.util.Map;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

public class MapBackedToteLoadPlanRegistry implements MutableToteLoadPlanRegistry {
    private final Map<PhysicalToteId, ToteLoadPlan> loadPlansByToteId = new LinkedHashMap<>();

    @Override
    public ToteLoadPlan getLoadPlanFor(PhysicalToteId physicalToteId) {
        if (physicalToteId == null) {
            throw new IllegalArgumentException("physicalToteId must not be null");
        }
        return loadPlansByToteId.get(physicalToteId);
    }

    @Override
    public void putLoadPlan(ToteLoadPlan toteLoadPlan) {
        if (toteLoadPlan == null) {
            throw new IllegalArgumentException("toteLoadPlan must not be null");
        }
        loadPlansByToteId.put(toteLoadPlan.physicalToteId(), toteLoadPlan);
    }
}
