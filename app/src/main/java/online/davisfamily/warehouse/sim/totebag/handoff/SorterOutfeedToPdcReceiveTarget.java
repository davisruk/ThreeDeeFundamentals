package online.davisfamily.warehouse.sim.totebag.handoff;

import online.davisfamily.warehouse.sim.totebag.conveyor.PdcConveyor;
import online.davisfamily.warehouse.sim.totebag.layout.ToteToBagCoreLayout;
import online.davisfamily.warehouse.sim.totebag.pack.Pack;

public class SorterOutfeedToPdcReceiveTarget implements PackReceiveTarget {
    private final PackHandoffPoint sorterOutfeedPoint;
    private final PdcConveyor pdcConveyor;
    private final ToteToBagCoreLayout layout;

    public SorterOutfeedToPdcReceiveTarget(
            PackHandoffPoint sorterOutfeedPoint,
            PdcConveyor pdcConveyor,
            ToteToBagCoreLayout layout) {
        if (sorterOutfeedPoint == null || pdcConveyor == null || layout == null) {
            throw new IllegalArgumentException("Sorter outfeed to PDC target inputs must not be null");
        }
        this.sorterOutfeedPoint = sorterOutfeedPoint;
        this.pdcConveyor = pdcConveyor;
        this.layout = layout;
    }

    @Override
    public PackHandoffPoint handoffPoint() {
        return sorterOutfeedPoint;
    }

    @Override
    public boolean canAccept(Pack pack) {
        return pdcConveyor.canAcceptIncomingPackAtFrontDistance(pack, frontDistanceFor(pack));
    }

    @Override
    public void accept(Pack pack) {
        pdcConveyor.acceptIncomingPackAtFrontDistance(pack, frontDistanceFor(pack));
    }

    private float frontDistanceFor(Pack pack) {
        float alongPdc = sorterOutfeedPoint.worldPosition().x - layout.pdcStartX();
        return alongPdc + (pack.getDimensions().length() * 0.5f);
    }
}
