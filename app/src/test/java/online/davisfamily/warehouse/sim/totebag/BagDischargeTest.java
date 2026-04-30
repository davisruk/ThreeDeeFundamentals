package online.davisfamily.warehouse.sim.totebag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.totebag.bag.Bag;
import online.davisfamily.warehouse.sim.totebag.bag.Bag.BagContainmentState;
import online.davisfamily.warehouse.sim.totebag.bag.Bag.BagMotionState;
import online.davisfamily.warehouse.sim.totebag.bag.BagDischarge;
import online.davisfamily.warehouse.sim.totebag.handoff.BagReservation;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.BagSpec;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;

class BagDischargeTest {
    @Test
    void shouldTrackBagDischargeProgress() {
        Bag bag = bag("bag-1", "correlation-1");
        BagReservation reservation = new BagReservation("receiver-1", "correlation-1");

        BagDischarge discharge = new BagDischarge(bag, reservation, 2.0);

        assertSame(bag, discharge.getBag());
        assertSame(reservation, discharge.getReservation());
        assertEquals(2.0, discharge.getDurationSeconds());
        assertEquals(0.0, discharge.getElapsedSeconds());
        assertEquals(0.0, discharge.getProgress());
        assertFalse(discharge.isComplete());
        assertEquals(BagMotionState.MOVING, bag.getMotionState());
        assertEquals(BagContainmentState.FREE, bag.getContainmentState());

        discharge.advance(0.5);

        assertEquals(0.5, discharge.getElapsedSeconds());
        assertEquals(0.25, discharge.getProgress());
        assertFalse(discharge.isComplete());
        assertEquals(BagMotionState.MOVING, bag.getMotionState());
        assertEquals(BagContainmentState.FREE, bag.getContainmentState());
    }

    @Test
    void shouldCompleteBagWhenDischargeFinishes() {
        Bag bag = bag("bag-1", "correlation-1");
        BagDischarge discharge = new BagDischarge(bag, new BagReservation("receiver-1", "correlation-1"), 2.0);

        discharge.advance(3.0);

        assertEquals(2.0, discharge.getElapsedSeconds());
        assertEquals(1.0, discharge.getProgress());
        assertTrue(discharge.isComplete());
        assertEquals(BagMotionState.RECEIVED, bag.getMotionState());
        assertEquals(BagContainmentState.IN_RECEIVER, bag.getContainmentState());

        discharge.advance(1.0);

        assertEquals(2.0, discharge.getElapsedSeconds());
        assertEquals(1.0, discharge.getProgress());
    }

    @Test
    void shouldRejectInvalidInputs() {
        Bag bag = bag("bag-1", "correlation-1");
        BagReservation reservation = new BagReservation("receiver-1", "correlation-1");

        assertThrows(IllegalArgumentException.class, () -> new BagDischarge(null, reservation, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new BagDischarge(bag, null, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new BagDischarge(bag, reservation, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new BagDischarge(bag, reservation, -1.0));
        assertThrows(IllegalArgumentException.class,
                () -> new BagDischarge(bag, new BagReservation("receiver-1", "correlation-2"), 1.0));
        assertThrows(IllegalArgumentException.class, () -> new BagDischarge(bag, reservation, 1.0).advance(-0.1));
    }

    private static Bag bag(String bagId, String correlationId) {
        return new Bag(
                bagId,
                correlationId,
                List.of(new PackPlan("pack-1", correlationId, new PackDimensions(0.20f, 0.10f, 0.08f))),
                new BagSpec(0.34f, 0.28f, 0.22f));
    }
}
