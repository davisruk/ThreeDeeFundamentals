package online.davisfamily.warehouse.sim.dsp.adapting;

import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlanProvider;

class MapBackedToteLoadPlanRegistryTest {

    @Test
    void shouldBridgeExistingStringProviderAtGenericBoundary() {
        MapBackedToteLoadPlanRegistry registry = new MapBackedToteLoadPlanRegistry();
        ToteLoadPlan plan = new ToteLoadPlan(new PhysicalToteId("physical-tote-1"), List.of());
        registry.putLoadPlan(plan);

        ToteLoadPlanProvider genericProvider = registry;

        assertSame(plan, registry.getLoadPlanFor(new PhysicalToteId("physical-tote-1")));
        assertSame(plan, genericProvider.getLoadPlanFor("physical-tote-1"));
    }
}
