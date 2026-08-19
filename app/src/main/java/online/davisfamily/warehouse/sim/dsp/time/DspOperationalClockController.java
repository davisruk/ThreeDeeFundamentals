package online.davisfamily.warehouse.sim.dsp.time;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.SimulationController;

/**
 * Publishes the business-time view of the simulation context for later
 * scheduler snapshot consumers. Register it before those consumers.
 */
public final class DspOperationalClockController implements SimulationController {

    private final DspOperationalClock clock;
    private DspOperationalClockSnapshot latestSnapshot;

    public DspOperationalClockController(DspOperationalClock clock) {
        if (clock == null) {
            throw new IllegalArgumentException("clock must not be null");
        }
        this.clock = clock;
        this.latestSnapshot = clock.initialSnapshot();
    }

    @Override
    public void update(SimulationContext context, double dtSeconds) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        latestSnapshot = clock.snapshotAtSimulationSeconds(context.getSimulationTimeSeconds());
    }

    public DspOperationalClockSnapshot snapshot() {
        return latestSnapshot;
    }
}
