package online.davisfamily.warehouse.sim.totebag.handoff;

import online.davisfamily.warehouse.sim.totebag.machine.CompletedBag;

public interface CompletedBagReceiver {
    boolean canReserveIncomingBag(CompletedBag bag);
    CompletedBagReservation reserveIncomingBag(CompletedBag bag);
    boolean hasReservationFor(CompletedBag bag);
    void beginReceiving(CompletedBagReservation reservation);
    void completeReceiving(CompletedBagReservation reservation);
}
