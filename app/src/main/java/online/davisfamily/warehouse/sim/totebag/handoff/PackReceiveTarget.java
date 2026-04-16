package online.davisfamily.warehouse.sim.totebag.handoff;

import online.davisfamily.warehouse.sim.totebag.pack.Pack;

public interface PackReceiveTarget {
    PackHandoffPoint handoffPoint();
    boolean canAccept(Pack pack);
    void accept(Pack pack);
}
