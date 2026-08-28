package online.davisfamily.warehouse.sim.dsp.station.processing;

import java.util.ArrayList;
import java.util.List;

import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;

/**
 * One composed production runtime for station-processing arrival and completion boundaries.
 *
 * <p>The runtime owns the ordered controller references registered by its factory, while all
 * mutable station ownership remains in the supplied simulation-thread coordinator and domain
 * targets. Closing this value does not unregister controllers or reset domain state.</p>
 */
public final class DspStationProcessingRuntime implements AutoCloseable {
    private final List<StationArrivalClaimController> claimantControllers;
    private final List<StationProcessingCompletionController> completionControllers;
    private final StationConsumedToteController consumedToteController;
    private final StationProcessingCoordinator coordinator;
    private final List<OperationalRouteDestination> destinations;
    private boolean closed;

    DspStationProcessingRuntime(
            List<StationArrivalClaimController> claimantControllers,
            List<StationProcessingCompletionController> completionControllers,
            StationConsumedToteController consumedToteController,
            StationProcessingCoordinator coordinator,
            List<OperationalRouteDestination> destinations) {
        if (claimantControllers == null
                || completionControllers == null
                || consumedToteController == null
                || coordinator == null
                || destinations == null) {
            throw new IllegalArgumentException("runtime dependencies must not be null");
        }
        if (claimantControllers.stream().anyMatch(controller -> controller == null)
                || completionControllers.stream().anyMatch(controller -> controller == null)
                || destinations.stream().anyMatch(destination -> destination == null)) {
            throw new IllegalArgumentException("runtime dependencies must not contain null");
        }
        if (claimantControllers.size() != destinations.size()) {
            throw new IllegalArgumentException(
                    "claimant controller and destination counts must match");
        }
        this.claimantControllers = List.copyOf(claimantControllers);
        this.completionControllers = List.copyOf(completionControllers);
        this.consumedToteController = consumedToteController;
        this.coordinator = coordinator;
        this.destinations = List.copyOf(destinations);
    }

    /** Returns fresh immutable value snapshots in claimant registration order. */
    public List<StationArrivalClaimControllerSnapshot> claimantSnapshots() {
        List<StationArrivalClaimControllerSnapshot> snapshots = new ArrayList<>();
        for (StationArrivalClaimController controller : claimantControllers) {
            snapshots.add(controller.snapshot());
        }
        return List.copyOf(snapshots);
    }

    /** Returns a fresh immutable value snapshot of shared station-processing ownership. */
    public StationProcessingSnapshot coordinatorSnapshot() {
        return coordinator.snapshot();
    }

    /** Returns immutable destinations in claimant order. */
    public List<OperationalRouteDestination> destinations() {
        return destinations;
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
    }
}
