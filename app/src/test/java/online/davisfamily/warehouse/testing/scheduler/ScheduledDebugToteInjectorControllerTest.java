package online.davisfamily.warehouse.testing.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
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
import online.davisfamily.warehouse.sim.totebag.assembly.TipperTotePayload;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;

class ScheduledDebugToteInjectorControllerTest {
    private static final PackDimensions TEST_PACK = new PackDimensions(0.1f, 0.05f, 0.04f);

    @Test
    void shouldReleaseSchedulerSelectedToteAndMarkOrderReleased() {
        DspSchedulerRuntimeState runtimeState = runtimeState(List.of(waitingOrder("order-1", "sc-1")));
        TestReleaseTarget releaseTarget = new TestReleaseTarget(true, SchedulerCommandApplicationResult.appliedResult());
        AtomicInteger factoryCount = new AtomicInteger();
        ScheduledDebugToteInjectorController controller = new ScheduledDebugToteInjectorController(
                scheduler("sc-1"),
                runtimeState,
                new ScheduledTipperToteReleaseCatalog(List.of(release("order-1", "tote-1", factoryCount))),
                releaseTarget);

        controller.update(new SimulationContext(), 0.1d);

        assertEquals(1, factoryCount.get());
        assertEquals(1, releaseTarget.releaseCalls);
        assertEquals(DspOrderStatus.RELEASED, runtimeState.snapshot().orderStates().getFirst().status());
        assertEquals(Optional.of("sc-1"), runtimeState.snapshot().activeServiceCentreId());
    }

    @Test
    void shouldDoNothingWhenTargetCannotAcceptRelease() {
        DspSchedulerRuntimeState runtimeState = runtimeState(List.of(waitingOrder("order-1", "sc-1")));
        TestReleaseTarget releaseTarget = new TestReleaseTarget(false, SchedulerCommandApplicationResult.appliedResult());
        AtomicInteger factoryCount = new AtomicInteger();
        ScheduledDebugToteInjectorController controller = new ScheduledDebugToteInjectorController(
                scheduler("sc-1"),
                runtimeState,
                new ScheduledTipperToteReleaseCatalog(List.of(release("order-1", "tote-1", factoryCount))),
                releaseTarget);

        controller.update(new SimulationContext(), 0.1d);

        assertEquals(0, factoryCount.get());
        assertEquals(0, releaseTarget.releaseCalls);
        assertEquals(DspOrderStatus.WAITING, runtimeState.snapshot().orderStates().getFirst().status());
    }

    @Test
    void shouldDoNothingWhenSchedulerHasNoReleaseDecision() {
        DspSchedulerRuntimeState runtimeState = runtimeState(List.of(completedOrder("order-1", "sc-1")));
        TestReleaseTarget releaseTarget = new TestReleaseTarget(true, SchedulerCommandApplicationResult.appliedResult());
        AtomicInteger factoryCount = new AtomicInteger();
        ScheduledDebugToteInjectorController controller = new ScheduledDebugToteInjectorController(
                scheduler("sc-1"),
                runtimeState,
                new ScheduledTipperToteReleaseCatalog(List.of(release("order-1", "tote-1", factoryCount))),
                releaseTarget);

        controller.update(new SimulationContext(), 0.1d);

        assertEquals(0, factoryCount.get());
        assertEquals(0, releaseTarget.releaseCalls);
        assertEquals(DspOrderStatus.COMPLETED, runtimeState.snapshot().orderStates().getFirst().status());
    }

    @Test
    void shouldDoNothingWhenSchedulerReturnsBlockedDecision() {
        DspSchedulerRuntimeState runtimeState = runtimeState(List.of(waitingOrder("order-1", "sc-1")), false);
        TestReleaseTarget releaseTarget = new TestReleaseTarget(true, SchedulerCommandApplicationResult.appliedResult());
        AtomicInteger factoryCount = new AtomicInteger();
        ScheduledDebugToteInjectorController controller = new ScheduledDebugToteInjectorController(
                scheduler("sc-1"),
                runtimeState,
                new ScheduledTipperToteReleaseCatalog(List.of(release("order-1", "tote-1", factoryCount))),
                releaseTarget);

        controller.update(new SimulationContext(), 0.1d);

        assertEquals(0, factoryCount.get());
        assertEquals(0, releaseTarget.releaseCalls);
        assertEquals(DspOrderStatus.WAITING, runtimeState.snapshot().orderStates().getFirst().status());
    }

    @Test
    void shouldNotMarkReleasedWhenTargetDefersCommand() {
        DspSchedulerRuntimeState runtimeState = runtimeState(List.of(waitingOrder("order-1", "sc-1")));
        TestReleaseTarget releaseTarget = new TestReleaseTarget(true, SchedulerCommandApplicationResult.deferredResult("later"));
        AtomicInteger factoryCount = new AtomicInteger();
        ScheduledDebugToteInjectorController controller = new ScheduledDebugToteInjectorController(
                scheduler("sc-1"),
                runtimeState,
                new ScheduledTipperToteReleaseCatalog(List.of(release("order-1", "tote-1", factoryCount))),
                releaseTarget);

        controller.update(new SimulationContext(), 0.1d);

        assertEquals(1, factoryCount.get());
        assertEquals(1, releaseTarget.releaseCalls);
        assertEquals(DspOrderStatus.WAITING, runtimeState.snapshot().orderStates().getFirst().status());
    }

    @Test
    void shouldRejectMissingReleaseForSchedulerCommand() {
        DspSchedulerRuntimeState runtimeState = runtimeState(List.of(waitingOrder("order-1", "sc-1")));
        ScheduledDebugToteInjectorController controller = new ScheduledDebugToteInjectorController(
                scheduler("sc-1"),
                runtimeState,
                new ScheduledTipperToteReleaseCatalog(List.of(release("other-order", "tote-1", new AtomicInteger()))),
                new TestReleaseTarget(true, SchedulerCommandApplicationResult.appliedResult()));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> controller.update(new SimulationContext(), 0.1d));

        assertTrue(exception.getMessage().contains("order-1"));
    }

    @Test
    void shouldRejectTargetRejectionResult() {
        DspSchedulerRuntimeState runtimeState = runtimeState(List.of(waitingOrder("order-1", "sc-1")));
        ScheduledDebugToteInjectorController controller = new ScheduledDebugToteInjectorController(
                scheduler("sc-1"),
                runtimeState,
                new ScheduledTipperToteReleaseCatalog(List.of(release("order-1", "tote-1", new AtomicInteger()))),
                new TestReleaseTarget(true, SchedulerCommandApplicationResult.rejectedResult("bad target state")));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> controller.update(new SimulationContext(), 0.1d));

        assertTrue(exception.getMessage().contains("bad target state"));
    }

    private static DspReleaseScheduler scheduler(String... serviceCentres) {
        return new DspReleaseScheduler(
                new ServiceCentreWindowPolicy(new ServiceCentrePriority(List.of(serviceCentres))),
                new DspDependencyEvaluator());
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
        return orderState(orderId, serviceCentreId, DspOrderStatus.WAITING);
    }

    private static DspSchedulerOrderState completedOrder(String orderId, String serviceCentreId) {
        return orderState(orderId, serviceCentreId, DspOrderStatus.COMPLETED);
    }

    private static DspSchedulerOrderState orderState(String orderId, String serviceCentreId, DspOrderStatus status) {
        return orderState(orderId, serviceCentreId, OrderType.FULL_PACK, true, status);
    }

    private static DspSchedulerOrderState orderState(
            String orderId,
            String serviceCentreId,
            OrderType orderType,
            boolean requiresP2p,
            DspOrderStatus status) {
        NotionalToteOrder order = new NotionalToteOrder(
                orderId,
                "notional-" + orderId,
                serviceCentreId,
                1,
                orderType,
                List.of(new DspOrderItem("line-" + orderId, "product-" + orderId, 1)),
                0);
        return new DspSchedulerOrderState(
                order,
                new RouteRequirements(false, false, false, requiresP2p, false, StartLocation.OSR),
                status);
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
        private final boolean canAcceptRelease;
        private final SchedulerCommandApplicationResult releaseResult;
        private int releaseCalls;
        private TipperTotePayload lastPayload;

        private TestReleaseTarget(boolean canAcceptRelease, SchedulerCommandApplicationResult releaseResult) {
            this.canAcceptRelease = canAcceptRelease;
            this.releaseResult = releaseResult;
        }

        @Override
        public boolean canAcceptRelease() {
            return canAcceptRelease;
        }

        @Override
        public SchedulerCommandApplicationResult release(TipperTotePayload payload) {
            releaseCalls++;
            lastPayload = payload;
            return releaseResult;
        }
    }
}
