package online.davisfamily.warehouse.sim.totebag.handoff;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import online.davisfamily.warehouse.sim.totebag.machine.CompletedBag;

public class RecordingCompletedBagReceiver implements CompletedBagReceiver {
    private final String id;
    private final List<String> completedCorrelationIds = new ArrayList<>();

    private CompletedBagReservation activeReservation;
    private boolean receiving;

    public RecordingCompletedBagReceiver(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Override
    public boolean canReserveIncomingBag(CompletedBag bag) {
        return bag != null && activeReservation == null;
    }

    @Override
    public CompletedBagReservation reserveIncomingBag(CompletedBag bag) {
        if (!canReserveIncomingBag(bag)) {
            throw new IllegalStateException("Completed bag receiver cannot reserve incoming bag");
        }
        activeReservation = new CompletedBagReservation(id, bag.correlationId());
        return activeReservation;
    }

    @Override
    public boolean hasReservationFor(CompletedBag bag) {
        return bag != null
                && activeReservation != null
                && bag.correlationId().equals(activeReservation.correlationId());
    }

    @Override
    public void beginReceiving(CompletedBagReservation reservation) {
        validateActiveReservation(reservation);
        receiving = true;
    }

    @Override
    public void completeReceiving(CompletedBagReservation reservation) {
        validateActiveReservation(reservation);
        completedCorrelationIds.add(activeReservation.correlationId());
        activeReservation = null;
        receiving = false;
    }

    public CompletedBagReservation getActiveReservation() {
        return activeReservation;
    }

    public boolean isReceiving() {
        return receiving;
    }

    public List<String> getCompletedCorrelationIds() {
        return Collections.unmodifiableList(completedCorrelationIds);
    }

    private void validateActiveReservation(CompletedBagReservation reservation) {
        if (reservation == null) {
            throw new IllegalArgumentException("reservation must not be null");
        }
        if (activeReservation == null || !activeReservation.equals(reservation)) {
            throw new IllegalStateException("Reservation does not match the receiver's active reservation");
        }
    }
}
