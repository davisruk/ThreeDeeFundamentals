package online.davisfamily.warehouse.sim.totebag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.totebag.bag.Bag;
import online.davisfamily.warehouse.sim.totebag.bag.Bag.BagContainmentState;
import online.davisfamily.warehouse.sim.totebag.bag.Bag.BagMotionState;
import online.davisfamily.warehouse.sim.totebag.pack.Pack;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.BagSpec;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;
import online.davisfamily.warehouse.sim.totebag.transfer.ReleasedPackGroup;

class BagTest {
    @Test
    void shouldExposeBagIdentityAndDefaultStates() {
        BagSpec bagSpec = new BagSpec(0.34f, 0.28f, 0.22f);
        List<PackPlan> packContents = List.of(
                packPlan("pack-1", "correlation-1"),
                packPlan("pack-2", "correlation-1"));
        Bag bag = new Bag("bag-1", "correlation-1", packContents, bagSpec);

        assertEquals("bag-1", bag.getId());
        assertEquals("correlation-1", bag.getCorrelationId());
        assertEquals(2, bag.getPackCount());
        assertEquals(packContents, bag.getPackContents());
        assertEquals(bagSpec, bag.getBagSpec());
        assertEquals(BagMotionState.STAGED, bag.getMotionState());
        assertEquals(BagContainmentState.FREE, bag.getContainmentState());
    }

    @Test
    void shouldCreateBagFromReleasedPackGroup() {
        BagSpec bagSpec = new BagSpec(0.34f, 0.28f, 0.22f);
        ReleasedPackGroup group = new ReleasedPackGroup(
                "correlation-1",
                "prl-1",
                List.of(
                        new Pack("pack-1", "correlation-1", new PackDimensions(0.20f, 0.10f, 0.08f)),
                        new Pack("pack-2", "correlation-1", new PackDimensions(0.18f, 0.10f, 0.08f))),
                0.45f);

        Bag bag = Bag.fromReleasedPackGroup("bag-1", group, bagSpec);

        assertEquals("bag-1", bag.getId());
        assertEquals("correlation-1", bag.getCorrelationId());
        assertEquals(2, bag.getPackCount());
        assertEquals(group.toPackPlans(), bag.getPackContents());
        assertEquals(bagSpec, bag.getBagSpec());
        assertEquals(BagMotionState.STAGED, bag.getMotionState());
        assertEquals(BagContainmentState.FREE, bag.getContainmentState());
    }

    @Test
    void shouldAllowStateTransitions() {
        Bag bag = new Bag("bag-1", "correlation-1", List.of(packPlan("pack-1", "correlation-1")), new BagSpec(0.34f, 0.28f, 0.22f));

        bag.setMotionState(BagMotionState.MOVING);
        bag.setContainmentState(BagContainmentState.IN_RECEIVER);

        assertEquals(BagMotionState.MOVING, bag.getMotionState());
        assertEquals(BagContainmentState.IN_RECEIVER, bag.getContainmentState());
    }

    @Test
    void shouldCopyPackContents() {
        List<PackPlan> packContents = new ArrayList<>();
        packContents.add(packPlan("pack-1", "correlation-1"));

        Bag bag = new Bag("bag-1", "correlation-1", packContents, new BagSpec(0.34f, 0.28f, 0.22f));
        packContents.add(packPlan("pack-2", "correlation-1"));

        assertEquals(1, bag.getPackCount());
        assertThrows(UnsupportedOperationException.class, () -> bag.getPackContents().add(packPlan("pack-3", "correlation-1")));
    }

    @Test
    void shouldRejectInvalidInputs() {
        BagSpec bagSpec = new BagSpec(0.34f, 0.28f, 0.22f);
        List<PackPlan> packContents = List.of(packPlan("pack-1", "correlation-1"));

        assertThrows(IllegalArgumentException.class, () -> new Bag("", "correlation-1", packContents, bagSpec));
        assertThrows(IllegalArgumentException.class, () -> new Bag("bag-1", "", packContents, bagSpec));
        assertThrows(IllegalArgumentException.class, () -> new Bag("bag-1", "correlation-1", null, bagSpec));
        assertThrows(IllegalArgumentException.class, () -> new Bag("bag-1", "correlation-1", List.of(), bagSpec));
        assertThrows(IllegalArgumentException.class, () -> new Bag("bag-1", "correlation-1", listWithNullPackPlan(), bagSpec));
        assertThrows(IllegalArgumentException.class, () -> new Bag("bag-1", "correlation-1", packContents, null));
        assertThrows(IllegalArgumentException.class, () -> Bag.fromReleasedPackGroup("bag-1", null, bagSpec));
        assertThrows(IllegalArgumentException.class, () -> new Bag("bag-1", "correlation-1", packContents, bagSpec)
                .setMotionState(null));
        assertThrows(IllegalArgumentException.class, () -> new Bag("bag-1", "correlation-1", packContents, bagSpec)
                .setContainmentState(null));
    }

    private static PackPlan packPlan(String packId, String correlationId) {
        return new PackPlan(packId, correlationId, new PackDimensions(0.20f, 0.10f, 0.08f));
    }

    private static List<PackPlan> listWithNullPackPlan() {
        List<PackPlan> packContents = new ArrayList<>();
        packContents.add(null);
        return packContents;
    }
}
