package online.davisfamily.warehouse.sim.totebag.handoff;

import online.davisfamily.warehouse.sim.totebag.pack.Pack;

public interface PackReleaseSource {
    PackHandoffPoint handoffPoint();
    boolean hasReleasedPack();
    Pack pollReleasedPack();
}
