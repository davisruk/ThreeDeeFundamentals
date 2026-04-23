package online.davisfamily.warehouse.sim.totebag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.totebag.handoff.CompletedBagReservation;
import online.davisfamily.warehouse.sim.totebag.handoff.RecordingCompletedBagReceiver;
import online.davisfamily.warehouse.sim.totebag.machine.CompletedBag;
import online.davisfamily.warehouse.sim.totebag.plan.BagSpec;

class CompletedBagReceiverTest {
    @Test
    void shouldReserveReceiveAndRecordCompletedBag() {
        RecordingCompletedBagReceiver receiver = new RecordingCompletedBagReceiver("receiver");
        CompletedBag bag = completedBag("bag-a");

        assertTrue(receiver.canReserveIncomingBag(bag));
        CompletedBagReservation reservation = receiver.reserveIncomingBag(bag);

        assertEquals(new CompletedBagReservation("receiver", "bag-a"), reservation);
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
        RecordingCompletedBagReceiver receiver = new RecordingCompletedBagReceiver("receiver");
        receiver.reserveIncomingBag(completedBag("bag-a"));

        CompletedBagReservation otherReservation = new CompletedBagReservation("receiver", "bag-b");
        assertThrows(IllegalStateException.class, () -> receiver.beginReceiving(otherReservation));
        assertThrows(IllegalStateException.class, () -> receiver.completeReceiving(otherReservation));
    }

    private static CompletedBag completedBag(String correlationId) {
        return new CompletedBag(correlationId, 2, new BagSpec(0.34f, 0.28f, 0.22f));
    }
}
