package online.davisfamily.warehouse.sim.totebag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.totebag.bag.Bag;
import online.davisfamily.warehouse.sim.totebag.handoff.BagReceiver;
import online.davisfamily.warehouse.sim.totebag.handoff.BagReservation;
import online.davisfamily.warehouse.sim.totebag.handoff.RecordingBagReceiver;
import online.davisfamily.warehouse.sim.totebag.handoff.StoredBagReceiver;
import online.davisfamily.warehouse.sim.totebag.machine.BaggingMachine;
import online.davisfamily.warehouse.sim.totebag.machine.BaggingMachineState;
import online.davisfamily.warehouse.sim.totebag.pack.Pack;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.BagSpec;
import online.davisfamily.warehouse.sim.totebag.transfer.ReleasedPackGroup;

class BaggingMachineTest {
    @Test
    void shouldDeliverCompletedBagToReceiverAfterDischargeCompletes() {
        RecordingBagReceiver receiver = new RecordingBagReceiver("receiver");
        BaggingMachine baggingMachine = new BaggingMachine(
                "bagger",
                new BagSpec(0.34f, 0.28f, 0.22f),
                0.05d,
                0.05d,
                0.05d,
                0.05d,
                receiver);
        ReleasedPackGroup group = releasedGroup("bag-a");

        baggingMachine.startBagging(group);
        baggingMachine.completeIncomingTransfer(group);
        for (int i = 0; i < 3; i++) {
            baggingMachine.update(null, 0.05d);
        }

        assertNotNull(baggingMachine.getActiveDischarge());
        assertTrue(receiver.isReceiving());
        assertEquals(List.of(), receiver.getCompletedCorrelationIds());
        assertEquals(List.of(), baggingMachine.getCompletedCorrelationIds());

        baggingMachine.update(null, 0.05d);

        assertEquals(List.of("bag-a"), baggingMachine.getCompletedCorrelationIds());
        assertEquals(List.of("bag-a"), receiver.getCompletedCorrelationIds());
        assertEquals(1, baggingMachine.getCompletedRuntimeBags().size());
        Bag runtimeBag = baggingMachine.getCompletedRuntimeBags().getFirst();
        assertEquals("bag_bag-a", runtimeBag.getId());
        assertEquals("bag-a", runtimeBag.getCorrelationId());
        assertEquals(group.toPackPlans(), runtimeBag.getPackContents());
    }

    @Test
    void shouldWaitForReceiverBeforeDischargingBag() {
        ToggleBagReceiver receiver = new ToggleBagReceiver("receiver");
        BaggingMachine baggingMachine = new BaggingMachine(
                "bagger",
                new BagSpec(0.34f, 0.28f, 0.22f),
                0.05d,
                0.05d,
                0.05d,
                0.05d,
                receiver);
        ReleasedPackGroup group = releasedGroup("bag-a");

        baggingMachine.startBagging(group);
        baggingMachine.completeIncomingTransfer(group);
        receiver.setAvailable(false);
        for (int i = 0; i < 3; i++) {
            baggingMachine.update(null, 0.05d);
        }

        assertEquals(BaggingMachineState.WAITING_FOR_RECEIVER, baggingMachine.getState());
        assertFalse(baggingMachine.isAvailable());
        assertNull(baggingMachine.getActiveDischarge());
        assertEquals(List.of(), receiver.getCompletedCorrelationIds());
        assertEquals(List.of(), baggingMachine.getCompletedCorrelationIds());

        receiver.setAvailable(true);
        baggingMachine.update(null, 0.05d);

        assertEquals(BaggingMachineState.DISCHARGING, baggingMachine.getState());
        assertNotNull(baggingMachine.getActiveDischarge());
        assertTrue(receiver.isReceiving());

        baggingMachine.update(null, 0.05d);

        assertEquals(BaggingMachineState.IDLE, baggingMachine.getState());
        assertEquals(List.of("bag-a"), receiver.getCompletedCorrelationIds());
        assertEquals(List.of("bag-a"), baggingMachine.getCompletedCorrelationIds());
    }

    @Test
    void shouldWaitForStoredReceiverCapacityBeforeDischargingBag() {
        StoredBagReceiver receiver = new StoredBagReceiver("receiver", 1);
        BaggingMachine baggingMachine = new BaggingMachine(
                "bagger",
                new BagSpec(0.34f, 0.28f, 0.22f),
                0.05d,
                0.05d,
                0.05d,
                0.05d,
                receiver);
        ReleasedPackGroup group = releasedGroup("bag-a");

        baggingMachine.startBagging(group);
        baggingMachine.completeIncomingTransfer(group);
        Bag storedBag = new Bag(
                "stored-bag",
                "stored-bag",
                List.of(new online.davisfamily.warehouse.sim.totebag.plan.PackPlan(
                        "stored-pack",
                        "stored-bag",
                        new PackDimensions(0.20f, 0.10f, 0.08f))),
                new BagSpec(0.34f, 0.28f, 0.22f));
        BagReservation storedReservation = receiver.reserveIncomingBag(storedBag);
        receiver.beginReceiving(storedReservation);
        receiver.completeReceiving(storedReservation);
        for (int i = 0; i < 4; i++) {
            baggingMachine.update(null, 0.05d);
        }

        assertEquals(BaggingMachineState.WAITING_FOR_RECEIVER, baggingMachine.getState());
        assertFalse(baggingMachine.canReserveIncomingGroup(releasedGroup("bag-b")));
        assertEquals(List.of(storedBag), receiver.getReceivedBags());

        receiver.removeReceivedBag(storedBag);
        baggingMachine.update(null, 0.05d);

        assertEquals(BaggingMachineState.DISCHARGING, baggingMachine.getState());
        assertNotNull(baggingMachine.getActiveDischarge());
    }

    @Test
    void shouldNotAcceptNewGroupWhenStoredReceiverIsAlreadyFull() {
        StoredBagReceiver receiver = new StoredBagReceiver("receiver", 1);
        Bag storedBag = new Bag(
                "stored-bag",
                "stored-bag",
                List.of(new online.davisfamily.warehouse.sim.totebag.plan.PackPlan(
                        "stored-pack",
                        "stored-bag",
                        new PackDimensions(0.20f, 0.10f, 0.08f))),
                new BagSpec(0.34f, 0.28f, 0.22f));
        BagReservation storedReservation = receiver.reserveIncomingBag(storedBag);
        receiver.beginReceiving(storedReservation);
        receiver.completeReceiving(storedReservation);
        BaggingMachine baggingMachine = new BaggingMachine(
                "bagger",
                new BagSpec(0.34f, 0.28f, 0.22f),
                0.05d,
                0.05d,
                0.05d,
                0.05d,
                receiver);
        ReleasedPackGroup group = releasedGroup("bag-a");

        assertFalse(baggingMachine.canReserveIncomingGroup(group));

        receiver.removeReceivedBag(storedBag);

        assertTrue(baggingMachine.canReserveIncomingGroup(group));
    }

    @Test
    void shouldAcceptSecondGroupWhileFirstBagIsDischarging() {
        RecordingBagReceiver receiver = new RecordingBagReceiver("receiver");
        BaggingMachine baggingMachine = new BaggingMachine(
                "bagger",
                new BagSpec(0.34f, 0.28f, 0.22f),
                0.05d,
                0.05d,
                0.05d,
                0.15d,
                receiver);
        ReleasedPackGroup firstGroup = releasedGroup("bag-a");
        ReleasedPackGroup secondGroup = releasedGroup("bag-b");

        baggingMachine.startBagging(firstGroup);
        baggingMachine.completeIncomingTransfer(firstGroup);
        for (int i = 0; i < 3; i++) {
            baggingMachine.update(null, 0.05d);
        }

        assertEquals(BaggingMachineState.DISCHARGING, baggingMachine.getState());
        assertNotNull(baggingMachine.getActiveDischarge());
        assertTrue(baggingMachine.canReserveIncomingGroup(secondGroup));

        baggingMachine.startBagging(secondGroup);
        baggingMachine.completeIncomingTransfer(secondGroup);

        assertEquals(secondGroup, baggingMachine.getCurrentGroup());
        assertNotNull(baggingMachine.getActiveDischarge());
        assertEquals(List.of(), receiver.getCompletedCorrelationIds());
    }

    private static ReleasedPackGroup releasedGroup(String correlationId) {
        Pack pack = new Pack(
                "pack-1",
                correlationId,
                new PackDimensions(0.20f, 0.10f, 0.08f));
        return new ReleasedPackGroup(correlationId, "prl-1", List.of(pack), 0.25f);
    }

    private static class ToggleBagReceiver implements BagReceiver {
        private final String id;
        private final List<String> completedCorrelationIds = new java.util.ArrayList<>();
        private boolean available = true;
        private BagReservation activeReservation;
        private boolean receiving;

        private ToggleBagReceiver(String id) {
            this.id = id;
        }

        private void setAvailable(boolean available) {
            this.available = available;
        }

        private boolean isReceiving() {
            return receiving;
        }

        private List<String> getCompletedCorrelationIds() {
            return completedCorrelationIds;
        }

        @Override
        public boolean canReserveIncomingBag(Bag bag) {
            return available && bag != null && activeReservation == null;
        }

        @Override
        public BagReservation reserveIncomingBag(Bag bag) {
            if (!canReserveIncomingBag(bag)) {
                throw new IllegalStateException("Bag receiver cannot reserve incoming bag");
            }
            activeReservation = new BagReservation(id, bag.getCorrelationId());
            return activeReservation;
        }

        @Override
        public boolean hasReservationFor(Bag bag) {
            return bag != null
                    && activeReservation != null
                    && bag.getCorrelationId().equals(activeReservation.correlationId());
        }

        @Override
        public void beginReceiving(BagReservation reservation) {
            receiving = true;
        }

        @Override
        public void completeReceiving(BagReservation reservation) {
            completedCorrelationIds.add(reservation.correlationId());
            activeReservation = null;
            receiving = false;
        }
    }
}
