package online.davisfamily.warehouse.sim.dsp.bagging;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;

class DspPackPlanFactoryTest {

    @Test
    void shouldCreateGenericPackPlanAndRegisterSourceProvenance() {
        PackProvenanceRegistry registry = new PackProvenanceRegistry();
        DspPackPlanFactory factory = new DspPackPlanFactory(registry);
        PackDimensions dimensions = new PackDimensions(0.20f, 0.10f, 0.08f);
        PackSourceProvenance provenance = new PackSourceProvenance(
                new OrderSheetKey("source-order", 2),
                "line-1",
                "product-1",
                "104",
                "pharmacy-1",
                "patient-1",
                "prescription-1");

        PackPlan packPlan = factory.createPackPlan("pack-1", "initial-correlation", dimensions, provenance);

        assertEquals(new PackPlan("pack-1", "initial-correlation", dimensions), packPlan);
        assertEquals(provenance, registry.find("pack-1").orElseThrow());
    }
}
