package online.davisfamily.warehouse.sim.dsp.bagging;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;

class MaximumPackCountBagCapacityPolicyTest {
    private static final PackDimensions DIMENSIONS = new PackDimensions(0.20f, 0.10f, 0.08f);

    @Test
    void shouldAcceptCandidateWhilePackCountRemainsWithinLimit() {
        MaximumPackCountBagCapacityPolicy policy = new MaximumPackCountBagCapacityPolicy(2);

        assertTrue(policy.canAdd(List.of(), pack("pack-1")));
        assertTrue(policy.canAdd(List.of(pack("pack-1")), pack("pack-2")));
        assertFalse(policy.canAdd(List.of(pack("pack-1"), pack("pack-2")), pack("pack-3")));
    }

    @Test
    void shouldRejectInvalidMaximumPackCount() {
        assertThrows(IllegalArgumentException.class, () -> new MaximumPackCountBagCapacityPolicy(0));
        assertThrows(IllegalArgumentException.class, () -> new MaximumPackCountBagCapacityPolicy(-1));
    }

    private static PackPlan pack(String packId) {
        return new PackPlan(packId, "legacy-correlation", DIMENSIONS);
    }
}
