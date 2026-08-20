package online.davisfamily.warehouse.sim.dsp.supply;

import java.util.function.Supplier;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.SimulationController;
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClockSnapshot;

/**
 * Bridges the authoritative operational clock into the service-centre supply
 * coordinator. Register the operational clock controller before this controller
 * so the supplied snapshot represents the current simulation time.
 */
public final class DspServiceCentreSupplyController implements SimulationController {

    private final Supplier<DspOperationalClockSnapshot> clockSnapshotSupplier;
    private final DspServiceCentreSupplyCoordinator coordinator;

    public DspServiceCentreSupplyController(
            Supplier<DspOperationalClockSnapshot> clockSnapshotSupplier,
            DspServiceCentreSupplyCoordinator coordinator) {
        if (clockSnapshotSupplier == null) {
            throw new IllegalArgumentException("clockSnapshotSupplier must not be null");
        }
        if (coordinator == null) {
            throw new IllegalArgumentException("coordinator must not be null");
        }
        this.clockSnapshotSupplier = clockSnapshotSupplier;
        this.coordinator = coordinator;
    }

    @Override
    public void update(SimulationContext context, double dtSeconds) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        DspOperationalClockSnapshot clockSnapshot = clockSnapshotSupplier.get();
        if (clockSnapshot == null) {
            throw new IllegalStateException("clockSnapshotSupplier returned null");
        }
        coordinator.advance(clockSnapshot);
    }

    public DspSupplySnapshot snapshot() {
        return coordinator.snapshot();
    }
}
