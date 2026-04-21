package online.davisfamily.warehouse.testing;

import online.davisfamily.warehouse.sim.totebag.control.TipperDownstreamFlow;
import online.davisfamily.warehouse.sim.totebag.handoff.PackReceiveTarget;
import online.davisfamily.warehouse.sim.totebag.pack.Pack;

public class ImmediatePackReceiveDownstreamFlow implements TipperDownstreamFlow {
    private final PackReceiveTarget receiverTarget;

    public ImmediatePackReceiveDownstreamFlow(PackReceiveTarget receiverTarget) {
        if (receiverTarget == null) {
            throw new IllegalArgumentException("receiverTarget must not be null");
        }
        this.receiverTarget = receiverTarget;
    }

    @Override
    public boolean canAcceptDischargedPack(Pack pack) {
        return receiverTarget.canAccept(pack);
    }

    @Override
    public void acceptDischargedPack(Pack pack) {
        receiverTarget.accept(pack);
    }

    @Override
    public void update(double dtSeconds) {
    }

    @Override
    public boolean keepsTipperOccupied() {
        return false;
    }
}
