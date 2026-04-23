package online.davisfamily.warehouse.sim.totebag.handoff;

import online.davisfamily.warehouse.sim.totebag.transfer.ReleasedPackGroup;

public interface PackGroupReceiver {
    boolean canReserveIncomingGroup(ReleasedPackGroup group);
    PackGroupReservation reserveIncomingGroup(ReleasedPackGroup group);
    boolean hasReservationFor(ReleasedPackGroup group);
    void beginReceiving(PackGroupReservation reservation);
}
