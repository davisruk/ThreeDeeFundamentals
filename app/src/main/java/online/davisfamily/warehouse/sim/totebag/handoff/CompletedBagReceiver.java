package online.davisfamily.warehouse.sim.totebag.handoff;

import online.davisfamily.warehouse.sim.totebag.bag.Bag;

public interface CompletedBagReceiver {
    boolean canReserveIncomingBag(Bag bag);
    CompletedBagReservation reserveIncomingBag(Bag bag);
    boolean hasReservationFor(Bag bag);
    void beginReceiving(CompletedBagReservation reservation);
    void completeReceiving(CompletedBagReservation reservation);
}
