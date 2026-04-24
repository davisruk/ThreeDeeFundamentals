package online.davisfamily.warehouse.sim.totebag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.totebag.bag.Bag;
import online.davisfamily.warehouse.sim.totebag.handoff.BagReservation;
import online.davisfamily.warehouse.sim.totebag.handoff.RecordingBagReceiver;
import online.davisfamily.warehouse.sim.totebag.handoff.StoredBagReceiver;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.BagSpec;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;

class BagReceiverTest {
    @Test
    void shouldReserveReceiveAndRecordBag() {
        RecordingBagReceiver receiver = new RecordingBagReceiver("receiver");
        Bag bag = completedBag("bag-a");

        assertTrue(receiver.canReserveIncomingBag(bag));
        BagReservation reservation = receiver.reserveIncomingBag(bag);

        assertEquals(new BagReservation("receiver", "bag-a"), reservation);
        assertTrue(receiver.hasReservationFor(bag));
        assertFalse(receiver.canReserveIncomingBag(completedBag("bag-b")));

        receiver.beginReceiving(reservation);
        assertTrue(receiver.isReceiving());

        receiver.completeReceiving(reservation);
        assertFalse(receiver.isReceiving());
        assertNull(receiver.getActiveReservation());
        assertEquals(java.util.List.of("bag-a"), receiver.getCompletedCorrelationIds());
    }

    @Test
    void shouldRejectMismatchedReservation() {
        RecordingBagReceiver receiver = new RecordingBagReceiver("receiver");
        receiver.reserveIncomingBag(completedBag("bag-a"));

        BagReservation otherReservation = new BagReservation("receiver", "bag-b");
        assertThrows(IllegalStateException.class, () -> receiver.beginReceiving(otherReservation));
        assertThrows(IllegalStateException.class, () -> receiver.completeReceiving(otherReservation));
    }

    @Test
    void shouldStoreReceivedRuntimeBags() {
        StoredBagReceiver receiver = new StoredBagReceiver("receiver");
        Bag bag = completedBag("bag-a");

        BagReservation reservation = receiver.reserveIncomingBag(bag);
        receiver.beginReceiving(reservation);
        receiver.completeReceiving(reservation);

        assertEquals(java.util.List.of(bag), receiver.getReceivedBags());
        assertEquals(java.util.List.of("bag-a"), receiver.getCompletedCorrelationIds());
        assertFalse(receiver.isReceiving());
        assertNull(receiver.getActiveReservation());
    }

    private static Bag completedBag(String correlationId) {
        return new Bag(
                "bag_" + correlationId,
                correlationId,
                java.util.List.of(new PackPlan("pack-1", correlationId, new PackDimensions(0.20f, 0.10f, 0.08f))),
                new BagSpec(0.34f, 0.28f, 0.22f));
    }
}
