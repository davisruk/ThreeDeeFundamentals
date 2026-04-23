package online.davisfamily.warehouse.sim.totebag.bag;

import online.davisfamily.warehouse.sim.totebag.bag.Bag.BagContainmentState;
import online.davisfamily.warehouse.sim.totebag.bag.Bag.BagMotionState;
import online.davisfamily.warehouse.sim.totebag.handoff.BagReservation;

public class BagDischarge {
    private final Bag bag;
    private final BagReservation reservation;
    private final double durationSeconds;
    private double elapsedSeconds;

    public BagDischarge(Bag bag, BagReservation reservation, double durationSeconds) {
        if (bag == null) {
            throw new IllegalArgumentException("bag must not be null");
        }
        if (reservation == null) {
            throw new IllegalArgumentException("reservation must not be null");
        }
        if (durationSeconds <= 0.0) {
            throw new IllegalArgumentException("durationSeconds must be positive");
        }
        if (!bag.getCorrelationId().equals(reservation.correlationId())) {
            throw new IllegalArgumentException("bag and reservation correlation ids must match");
        }
        this.bag = bag;
        this.reservation = reservation;
        this.durationSeconds = durationSeconds;
        this.bag.setMotionState(BagMotionState.MOVING);
    }

    public Bag getBag() {
        return bag;
    }

    public BagReservation getReservation() {
        return reservation;
    }

    public double getDurationSeconds() {
        return durationSeconds;
    }

    public double getElapsedSeconds() {
        return elapsedSeconds;
    }

    public void advance(double deltaSeconds) {
        if (deltaSeconds < 0.0) {
            throw new IllegalArgumentException("deltaSeconds must not be negative");
        }
        if (isComplete()) {
            return;
        }

        elapsedSeconds = Math.min(durationSeconds, elapsedSeconds + deltaSeconds);
        if (isComplete()) {
            bag.setMotionState(BagMotionState.RECEIVED);
            bag.setContainmentState(BagContainmentState.IN_RECEIVER);
        }
    }

    public double getProgress() {
        return elapsedSeconds / durationSeconds;
    }

    public boolean isComplete() {
        return elapsedSeconds >= durationSeconds;
    }
}
