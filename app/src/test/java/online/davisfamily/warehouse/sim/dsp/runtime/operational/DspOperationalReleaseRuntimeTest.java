package online.davisfamily.warehouse.sim.dsp.runtime.operational;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.route.OperationalRouteEntryQueue;
import online.davisfamily.warehouse.sim.dsp.osr.release.route.OperationalRouteEntryQueueSnapshot;
import online.davisfamily.warehouse.sim.dsp.osr.release.route.OperationalRouteTargetDefinition;
import online.davisfamily.warehouse.sim.dsp.osr.release.route.OperationalRouteTargetRegistry;
import online.davisfamily.warehouse.sim.dsp.runtime.SchedulerCommandApplicationResult;
import online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalReleaseSnapshot;

class DspOperationalReleaseRuntimeTest {

    @Test
    void shouldExposeExactControllerRegistryAndFreshImmutableQueueSnapshots() {
        CloseTrackingEvaluationSource source = new CloseTrackingEvaluationSource();
        DspOperationalReleaseController controller = new DspOperationalReleaseController(
                source,
                () -> new DspOperationalReleaseSnapshot(
                        List.of(), List.of(), Map.of(), Set.of()),
                command -> SchedulerCommandApplicationResult.appliedResult());
        OperationalRouteEntryQueue queue = new OperationalRouteEntryQueue(
                new OperationalRouteTargetDefinition(StationType.P2P, "p2p-1", 1));
        OperationalRouteTargetRegistry registry = new OperationalRouteTargetRegistry(
                List.of(queue));
        DspOperationalReleaseRuntime runtime = new DspOperationalReleaseRuntime(
                controller, registry);

        List<OperationalRouteEntryQueueSnapshot> before = runtime.routeEntryQueueSnapshots();

        assertSame(controller, runtime.controller());
        assertSame(registry, runtime.routeTargetRegistry());
        assertEquals(0, before.get(0).occupancy());
        assertThrows(UnsupportedOperationException.class, before::clear);
    }

    @Test
    void shouldCloseControllerEvaluationSourceExactlyOnce() {
        CloseTrackingEvaluationSource source = new CloseTrackingEvaluationSource();
        DspOperationalReleaseController controller = new DspOperationalReleaseController(
                source,
                () -> new DspOperationalReleaseSnapshot(
                        List.of(), List.of(), Map.of(), Set.of()),
                command -> SchedulerCommandApplicationResult.appliedResult());
        DspOperationalReleaseRuntime runtime = new DspOperationalReleaseRuntime(
                controller, new OperationalRouteTargetRegistry(List.of()));

        runtime.close();
        runtime.close();

        assertEquals(1, source.closeCount);
    }

    private static final class CloseTrackingEvaluationSource
            implements OperationalReleaseEvaluationSource {
        private int closeCount;

        @Override
        public boolean canSubmit() {
            return false;
        }

        @Override
        public void submit(DspOperationalReleaseSnapshot snapshot) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<OperationalReleaseEvaluationResult> pollResult() {
            return Optional.empty();
        }

        @Override
        public void close() {
            closeCount++;
        }
    }
}
