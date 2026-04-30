package online.davisfamily.warehouse.testing;

import java.util.List;

import online.davisfamily.warehouse.sim.totebag.bag.Bag;
import online.davisfamily.warehouse.sim.totebag.handoff.StoredBagReceiver;

public class DebugBagReceiverAutoEmptyController {
    private final StoredBagReceiver receiver;
    private final double fullDurationSeconds;
    private double fullElapsedSeconds;

    public DebugBagReceiverAutoEmptyController(StoredBagReceiver receiver, double fullDurationSeconds) {
        if (receiver == null) {
            throw new IllegalArgumentException("receiver must not be null");
        }
        if (fullDurationSeconds <= 0d) {
            throw new IllegalArgumentException("fullDurationSeconds must be > 0");
        }
        this.receiver = receiver;
        this.fullDurationSeconds = fullDurationSeconds;
    }

    public List<Bag> update(double dtSeconds) {
        if (dtSeconds < 0d) {
            throw new IllegalArgumentException("dtSeconds must not be negative");
        }
        if (!receiver.isFull()) {
            fullElapsedSeconds = 0d;
            return List.of();
        }

        fullElapsedSeconds += dtSeconds;
        if (fullElapsedSeconds < fullDurationSeconds) {
            return List.of();
        }

        List<Bag> removedBags = List.copyOf(receiver.getReceivedBags());
        for (Bag bag : removedBags) {
            receiver.removeReceivedBag(bag);
        }
        fullElapsedSeconds = 0d;
        return removedBags;
    }

    public double getFullElapsedSeconds() {
        return fullElapsedSeconds;
    }

    public double getFullDurationSeconds() {
        return fullDurationSeconds;
    }
}
