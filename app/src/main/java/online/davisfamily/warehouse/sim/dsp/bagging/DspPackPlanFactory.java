package online.davisfamily.warehouse.sim.dsp.bagging;

import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;

public final class DspPackPlanFactory {
    private final PackProvenanceRegistry provenanceRegistry;

    public DspPackPlanFactory(PackProvenanceRegistry provenanceRegistry) {
        if (provenanceRegistry == null) {
            throw new IllegalArgumentException("provenanceRegistry must not be null");
        }
        this.provenanceRegistry = provenanceRegistry;
    }

    public PackPlan createPackPlan(
            String packId,
            String initialCorrelationId,
            PackDimensions dimensions,
            PackSourceProvenance provenance) {
        provenanceRegistry.register(packId, provenance);
        return new PackPlan(packId, initialCorrelationId, dimensions);
    }
}
