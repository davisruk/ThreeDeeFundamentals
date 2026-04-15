package online.davisfamily.warehouse.sim.totebag.control;

import online.davisfamily.warehouse.sim.totebag.pack.Pack;

public interface PackSink {
    boolean canAccept(Pack pack);

    void accept(Pack pack);
}
