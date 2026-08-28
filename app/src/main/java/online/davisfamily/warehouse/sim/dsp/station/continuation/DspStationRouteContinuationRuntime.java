package online.davisfamily.warehouse.sim.dsp.station.continuation;

/**
 * One composed production runtime for station route continuation.
 *
 * <p>The runtime owns the continuation controller registered by its factory.  Mutable station,
 * route, and transport ownership remains in the exact objects supplied to that controller.  A
 * runtime close is deliberately local: it does not unregister the controller or reset any of the
 * supplied simulation state.</p>
 */
public final class DspStationRouteContinuationRuntime implements AutoCloseable {
    private final StationRouteContinuationController controller;
    private boolean closed;

    DspStationRouteContinuationRuntime(StationRouteContinuationController controller) {
        if (controller == null) {
            throw new IllegalArgumentException("controller must not be null");
        }
        this.controller = controller;
    }

    /** Returns a fresh immutable value snapshot of continuation state. */
    public StationRouteContinuationControllerSnapshot snapshot() {
        return controller.snapshot();
    }

    public boolean isClosed() {
        return closed;
    }

    /**
     * Marks this runtime closed without unregistering its simulation controller or mutating
     * coordinator, route, tote, or transport state.
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
    }
}
