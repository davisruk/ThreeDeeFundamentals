package online.davisfamily.warehouse.sim.totebag.handoff;

import online.davisfamily.warehouse.sim.totebag.bag.Bag;

public interface BagReceiver {
    boolean canReserveIncomingBag(Bag bag);
    BagReservation reserveIncomingBag(Bag bag);
    boolean hasReservationFor(Bag bag);
    void beginReceiving(BagReservation reservation);
    void completeReceiving(BagReservation reservation);
}
