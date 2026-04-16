package online.davisfamily.warehouse.testing;

import online.davisfamily.warehouse.sim.totebag.control.PackSink;
import online.davisfamily.warehouse.sim.totebag.pack.Pack;

public class ForwardingPackSink implements PackSink {
    private PackSink delegate;

    public void setDelegate(PackSink delegate) {
        this.delegate = delegate;
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
