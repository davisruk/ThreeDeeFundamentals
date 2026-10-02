package online.davisfamily.warehouse.sim.dsp.adapting;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import online.davisfamily.warehouse.sim.dsp.bagging.PackSourceProvenance;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;

/** Immutable prospective physical packs and their exact source provenance. */
public record PreparedCollectedPackPlans(
        List<PackPlan> packPlans,
        Map<String, PackSourceProvenance> provenanceByPackId) {
    public PreparedCollectedPackPlans {
        if (packPlans == null || provenanceByPackId == null) {
            throw new IllegalArgumentException("prepared packs and provenance must not be null");
        }
        packPlans = List.copyOf(packPlans);
        provenanceByPackId = Collections.unmodifiableMap(new LinkedHashMap<>(provenanceByPackId));
        if (packPlans.size() != provenanceByPackId.size()
                || !packPlans.stream().map(PackPlan::packId).toList()
                        .equals(List.copyOf(provenanceByPackId.keySet()))) {
            throw new IllegalArgumentException("prepared pack IDs must exactly match provenance IDs");
        }
    }
}
