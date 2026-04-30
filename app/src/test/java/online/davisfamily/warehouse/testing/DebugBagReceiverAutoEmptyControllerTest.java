package online.davisfamily.warehouse.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.totebag.bag.Bag;
import online.davisfamily.warehouse.sim.totebag.handoff.BagReservation;
import online.davisfamily.warehouse.sim.totebag.handoff.StoredBagReceiver;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.BagSpec;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;

class DebugBagReceiverAutoEmptyControllerTest {
    @Test
    void shouldRemoveBagsAfterReceiverHasBeenFullForConfiguredDuration() {
        StoredBagReceiver receiver = new StoredBagReceiver("receiver", 2);
        Bag firstBag = bag("bag-a");
        Bag secondBag = bag("bag-b");
        receive(receiver, firstBag);
        receive(receiver, secondBag);
        DebugBagReceiverAutoEmptyController controller = new DebugBagReceiverAutoEmptyController(receiver, 3.0d);

        assertEquals(List.of(), controller.update(1.0d));
        assertEquals(2, receiver.getReceivedBags().size());
        assertEquals(1.0d, controller.getFullElapsedSeconds());

        List<Bag> removedBags = controller.update(2.0d);

        assertEquals(List.of(firstBag, secondBag), removedBags);
        assertTrue(receiver.getReceivedBags().isEmpty());
        assertEquals(0.0d, controller.getFullElapsedSeconds());
    }

    @Test
    void shouldResetTimerWhenReceiverIsNotFull() {
        StoredBagReceiver receiver = new StoredBagReceiver("receiver", 2);
        Bag firstBag = bag("bag-a");
        Bag secondBag = bag("bag-b");
        receive(receiver, firstBag);
        receive(receiver, secondBag);
        DebugBagReceiverAutoEmptyController controller = new DebugBagReceiverAutoEmptyController(receiver, 3.0d);

        controller.update(1.0d);
        receiver.removeReceivedBag(firstBag);
        controller.update(1.0d);

        assertEquals(0.0d, controller.getFullElapsedSeconds());
        assertEquals(List.of(secondBag), receiver.getReceivedBags());
    }

    @Test
    void shouldRejectInvalidInputs() {
        StoredBagReceiver receiver = new StoredBagReceiver("receiver", 1);

        assertThrows(IllegalArgumentException.class, () -> new DebugBagReceiverAutoEmptyController(null, 1.0d));
        assertThrows(IllegalArgumentException.class, () -> new DebugBagReceiverAutoEmptyController(receiver, 0.0d));
        assertThrows(IllegalArgumentException.class,
                () -> new DebugBagReceiverAutoEmptyController(receiver, 1.0d).update(-0.1d));
    }

    private static void receive(StoredBagReceiver receiver, Bag bag) {
        BagReservation reservation = receiver.reserveIncomingBag(bag);
        receiver.beginReceiving(reservation);
        receiver.completeReceiving(reservation);
    }

    private static Bag bag(String correlationId) {
        return new Bag(
                "bag_" + correlationId,
                correlationId,
                List.of(new PackPlan("pack_" + correlationId, correlationId, new PackDimensions(0.20f, 0.10f, 0.08f))),
                new BagSpec(0.34f, 0.28f, 0.22f));
    }
}
