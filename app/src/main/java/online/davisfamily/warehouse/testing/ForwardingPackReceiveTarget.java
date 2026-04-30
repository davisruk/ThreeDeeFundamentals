package online.davisfamily.warehouse.testing;

import online.davisfamily.warehouse.sim.totebag.handoff.PackHandoffPoint;
import online.davisfamily.warehouse.sim.totebag.handoff.PackReceiveTarget;
import online.davisfamily.warehouse.sim.totebag.pack.Pack;

public class ForwardingPackReceiveTarget implements PackReceiveTarget {
    private PackReceiveTarget delegate;

    public void setDelegate(PackReceiveTarget delegate) {
        this.delegate = delegate;
    }

    @Override
    public PackHandoffPoint handoffPoint() {
        if (delegate == null) {
            throw new IllegalStateException("No delegate configured");
        }
        return delegate.handoffPoint();
    }

    @Override
    public boolean canAccept(Pack pack) {
        return delegate != null && delegate.canAccept(pack);
    }

    @Override
    public void accept(Pack pack) {
        if (delegate == null) {
            throw new IllegalStateException("No delegate configured");
        }
        delegate.accept(pack);
    }
}
