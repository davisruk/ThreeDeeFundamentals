package online.davisfamily.warehouse.sim.totebag.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;

class ToteLoadPlanTest {

    @Test
    void shouldAllowAnEmptyPlanAndAppendPacksImmutably() {
        ToteLoadPlan emptyPlan = new ToteLoadPlan("tote-1", List.of());
        PackPlan pack = pack("pack-1");

        ToteLoadPlan updatedPlan = emptyPlan.withAdditionalPackPlans(List.of(pack));

        assertNotSame(emptyPlan, updatedPlan);
        assertEquals(List.of(), emptyPlan.getPackPlans());
        assertEquals(List.of(pack), updatedPlan.getPackPlans());
    }

    @Test
    void shouldPreserveExistingOrderWhenAppendingPacks() {
        ToteLoadPlan plan = new ToteLoadPlan("tote-1", List.of(pack("pack-1"), pack("pack-2")));

        ToteLoadPlan updatedPlan = plan.withAdditionalPackPlans(List.of(pack("pack-3"), pack("pack-4")));

        assertEquals(List.of("pack-1", "pack-2", "pack-3", "pack-4"), updatedPlan.getPackPlans().stream()
                .map(PackPlan::packId)
                .toList());
    }

    @Test
    void shouldRejectDuplicatePackIds() {
        ToteLoadPlan plan = new ToteLoadPlan("tote-1", List.of(pack("pack-1")));

        assertThrows(
                IllegalArgumentException.class,
                () -> plan.withAdditionalPackPlans(List.of(pack("pack-1"))));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ToteLoadPlan("tote-1", List.of(pack("pack-1"), pack("pack-1"))));
    }

    private PackPlan pack(String packId) {
        return new PackPlan(packId, "bag-1", new PackDimensions(0.2f, 0.1f, 0.05f));
    }
}
