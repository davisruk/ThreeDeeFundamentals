package online.davisfamily.warehouse.sim.totebag.control;

import online.davisfamily.warehouse.sim.totebag.pack.Pack;

public interface TipperDownstreamFlow {
    boolean canAcceptDischargedPack(Pack pack);
    void acceptDischargedPack(Pack pack);
    void update(double dtSeconds);
    boolean keepsTipperOccupied();
}
