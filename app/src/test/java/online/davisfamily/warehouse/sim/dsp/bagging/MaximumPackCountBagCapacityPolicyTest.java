package online.davisfamily.warehouse.sim.dsp.bagging;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

class MaximumPackCountBagCapacityPolicyTest {
    private static final PackDimensions DIMENSIONS = new PackDimensions(0.20f, 0.10f, 0.08f);

    @Test
    void shouldAcceptCandidateWhilePackCountRemainsWithinLimit() {
        MaximumPackCountBagCapacityPolicy policy = new MaximumPackCountBagCapacityPolicy(2);

        assertTrue(policy.canAdd(0, demand("pack-1", 1)));
        assertTrue(policy.canAdd(1, demand("pack-2", 1)));
        assertFalse(policy.canAdd(2, demand("pack-3", 1)));
    }

    @Test
    void shouldRejectInvalidMaximumPackCount() {
        assertThrows(IllegalArgumentException.class, () -> new MaximumPackCountBagCapacityPolicy(0));
        assertThrows(IllegalArgumentException.class, () -> new MaximumPackCountBagCapacityPolicy(-1));
    }

    private static BagPackDemand demand(String packId, int ordinal) {
        OrderSheetKey sourceSheet = new OrderSheetKey("source-order", 1);
        String lineReference = "line-" + packId;
        PackSourceProvenance provenance = new PackSourceProvenance(
                sourceSheet,
                lineReference,
                "product-1",
                "SC-1",
                "pharmacy-1",
                "patient-1",
                "prescription-1");
        return new BagPackDemand(
                new PlannedPackSlotKey(sourceSheet, lineReference, ordinal),
                packId,
                DIMENSIONS,
                provenance,
                sourceSheet,
                Optional.of(new PhysicalToteId("tote-1")));
    }
}
