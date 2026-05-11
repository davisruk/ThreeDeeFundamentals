package online.davisfamily.warehouse.testing.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.StartLocation;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements;
import online.davisfamily.warehouse.sim.dsp.runtime.DspSchedulerRuntimeState;
import online.davisfamily.warehouse.sim.dsp.runtime.SchedulerCommandApplicationResult;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspDependencyEvaluator;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspOrderStatus;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspReleaseScheduler;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;
import online.davisfamily.warehouse.sim.dsp.scheduler.ServiceCentrePriority;
import online.davisfamily.warehouse.sim.dsp.scheduler.ServiceCentreWindowPolicy;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationCapacity;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

class SchedulerDebugInspectableTest {
    private static final PackDimensions TEST_PACK = new PackDimensions(0.1f, 0.05f, 0.04f);

    @Test
    void shouldDescribeReleaseDecision() {
        ScheduledDebugToteInjectorController controller = controller(
                runtimeState(List.of(waitingOrder("order-1", "sc-1"))),
                SchedulerCommandApplicationResult.appliedResult());

        controller.update(new SimulationContext(), 0.1d);

        List<String> lines = new SchedulerDebugInspectable(controller).describe();
        assertEquals("Scheduler: debug", lines.get(0));
        assertTrue(lines.contains("Mode: sync"));
        assertTrue(lines.contains("In flight: false"));
        assertTrue(lines.contains("Last eval seq: 0"));
        assertTrue(lines.contains("Active SC: sc-1"));
        assertTrue(lines.contains("Waiting: order-1"));
        assertTrue(lines.contains("Release: order-1"));
        assertTrue(lines.contains("Blocked SC: none"));
        assertTrue(lines.contains("Blocked candidates: none"));
        assertTrue(lines.contains("Last applied: order-1"));
        assertTrue(lines.contains("Last deferred: none"));
        assertTrue(lines.contains("Last rejected: none"));
    }

    @Test
    void shouldDescribeBlockedDecisionAndReasons() {
        ScheduledDebugToteInjectorController controller = controller(
                runtimeState(List.of(waitingOrder("order-1", "sc-1")), false),
                SchedulerCommandApplicationResult.appliedResult());

        controller.update(new SimulationContext(), 0.1d);

        List<String> lines = new SchedulerDebugInspectable(controller).describe();
        assertTrue(lines.contains("Mode: sync"));
        assertTrue(lines.contains("In flight: false"));
        assertTrue(lines.contains("Last eval seq: 0"));
        assertTrue(lines.contains("Active SC: sc-1"));
        assertTrue(lines.contains("Release: none"));
        assertTrue(lines.contains("Blocked SC: sc-1"));
        assertTrue(lines.contains("Blocked candidates: order-1"));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Block: ")));
    }

    @Test
    void shouldDescribeDeferredAndRejectedResults() {
        ScheduledDebugToteInjectorController deferredController = controller(
                runtimeState(List.of(waitingOrder("order-1", "sc-1"))),
                SchedulerCommandApplicationResult.deferredResult("later"));
        deferredController.update(new SimulationContext(), 0.1d);

        List<String> deferredLines = new SchedulerDebugInspectable(deferredController).describe();
        assertTrue(deferredLines.contains("Mode: sync"));
        assertTrue(deferredLines.contains("In flight: false"));
        assertTrue(deferredLines.contains("Last eval seq: 0"));
        assertTrue(deferredLines.contains("Last applied: none"));
        assertTrue(deferredLines.contains("Last deferred: order-1 - later"));
        assertTrue(deferredLines.contains("Last rejected: none"));

        ScheduledDebugToteInjectorController rejectedController = controller(
                runtimeState(List.of(waitingOrder("order-1", "sc-1"))),
                SchedulerCommandApplicationResult.rejectedResult("bad target state"));
        try {
            rejectedController.update(new SimulationContext(), 0.1d);
        } catch (IllegalStateException ignored) {
        }

        List<String> rejectedLines = new SchedulerDebugInspectable(rejectedController).describe();
        assertTrue(rejectedLines.contains("Mode: sync"));
        assertTrue(rejectedLines.contains("In flight: false"));
        assertTrue(rejectedLines.contains("Last eval seq: 0"));
        assertTrue(rejectedLines.contains("Last applied: none"));
        assertTrue(rejectedLines.contains("Last deferred: none"));
        assertTrue(rejectedLines.contains("Last rejected: order-1 - bad target state"));
    }

    private static ScheduledDebugToteInjectorController controller(
            DspSchedulerRuntimeState runtimeState,
            SchedulerCommandApplicationResult releaseResult) {
        return new ScheduledDebugToteInjectorController(
                new DspReleaseScheduler(
                        new ServiceCentreWindowPolicy(new ServiceCentrePriority(List.of("sc-1"))),
                        new DspDependencyEvaluator()),
                runtimeState,
                new ScheduledTipperToteReleaseCatalog(List.of(release("order-1", "tote-1", new AtomicInteger()))),
                new TestReleaseTarget(releaseResult));
    }

    private static DspSchedulerRuntimeState runtimeState(List<DspSchedulerOrderState> orderStates) {
        return runtimeState(orderStates, true);
    }

    private static DspSchedulerRuntimeState runtimeState(List<DspSchedulerOrderState> orderStates, boolean p2pOpen) {
        return new DspSchedulerRuntimeState(new WarehouseSchedulerSnapshot(
                orderStates,
                Map.of(StationType.P2P, new StationAdmissionSnapshot(
                        StationType.P2P,
                        new StationCapacity(1, 1),
                        new StationSnapshot(StationType.P2P, 0, 0),
                        p2pOpen,
                        p2pOpen ? "" : "blocked")),
                Set.of(),
                Optional.empty()));
    }

    private static DspSchedulerOrderState waitingOrder(String orderId, String serviceCentreId) {
        NotionalToteOrder order = new NotionalToteOrder(
                orderId,
                "notional-" + orderId,
                serviceCentreId,
                1,
                OrderType.FULL_PACK,
                List.of(new DspOrderItem("line-" + orderId, "product-" + orderId, 1)),
                0);
        return new DspSchedulerOrderState(
                order,
                new RouteRequirements(false, false, false, true, false, StartLocation.OSR),
                DspOrderStatus.WAITING);
    }

    private static ScheduledTipperToteRelease release(String orderId, String toteId, AtomicInteger factoryCount) {
        ToteLoadPlan toteLoadPlan = new ToteLoadPlan(
                toteId,
                List.of(new PackPlan("pack-" + toteId, "bag-" + toteId, TEST_PACK)));
        return new ScheduledTipperToteRelease(orderId, toteLoadPlan, () -> {
            factoryCount.incrementAndGet();
            return null;
        });
    }

    private static final class TestReleaseTarget implements ScheduledToteReleaseTarget {
        private final SchedulerCommandApplicationResult releaseResult;

        private TestReleaseTarget(SchedulerCommandApplicationResult releaseResult) {
            this.releaseResult = releaseResult;
        }

        @Override
        public boolean canAcceptRelease() {
            return true;
        }

        @Override
        public SchedulerCommandApplicationResult release(online.davisfamily.warehouse.sim.totebag.assembly.TipperTotePayload payload) {
            return releaseResult;
        }
    }
}
