package online.davisfamily.warehouse.sim.dsp.station.processing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.transport.routing.StationRoutedToteArrivalQueue;

class StationProcessingBindingTest {

    @Test
    void shouldRetainExactQueueTargetAndDestination() {
        OperationalRouteDestination destination =
                new OperationalRouteDestination(StationType.THIRD_PARTY, "third-party-1");
        StationRoutedToteArrivalQueue sourceQueue =
                new StationRoutedToteArrivalQueue(destination, 2);
        StationProcessingTestTarget target =
                new StationProcessingTestTarget(destination, new StationProcessingCoordinator());

        StationProcessingBinding binding = new StationProcessingBinding(sourceQueue, target);

        assertEquals(sourceQueue, binding.sourceQueue());
        assertEquals(target, binding.target());
        assertEquals(destination, binding.destination());
    }

    @Test
    void shouldRejectNullOrMismatchedBindings() {
        OperationalRouteDestination first =
                new OperationalRouteDestination(StationType.THIRD_PARTY, "third-party-1");
        OperationalRouteDestination second =
                new OperationalRouteDestination(StationType.ADAPTING, "bench-1");
        StationRoutedToteArrivalQueue sourceQueue =
                new StationRoutedToteArrivalQueue(first, 1);
        StationProcessingCoordinator coordinator = new StationProcessingCoordinator();

        assertThrows(IllegalArgumentException.class,
                () -> new StationProcessingBinding(null,
                        new StationProcessingTestTarget(first, coordinator)));
        assertThrows(IllegalArgumentException.class,
                () -> new StationProcessingBinding(sourceQueue, null));
        assertThrows(IllegalArgumentException.class,
                () -> new StationProcessingBinding(sourceQueue,
                        new StationProcessingTestTarget(second, coordinator)));
    }
}
