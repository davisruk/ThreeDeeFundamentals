package online.davisfamily.warehouse.sim.dsp.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.StartLocation;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements;
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

class SynchronousSchedulerEvaluationSourceTest {

    @Test
    void shouldRejectNullSchedulerAndNullSnapshot() {
        DspReleaseScheduler scheduler = scheduler();
        SynchronousSchedulerEvaluationSource source = new SynchronousSchedulerEvaluationSource(scheduler);

        assertThrows(IllegalArgumentException.class, () -> new SynchronousSchedulerEvaluationSource(null));
        assertThrows(IllegalArgumentException.class, () -> source.submit(null));
    }

    @Test
    void shouldEvaluateImmediatelyAndReturnPendingResultOnce() {
        WarehouseSchedulerSnapshot snapshot = snapshot("order-1");
        SynchronousSchedulerEvaluationSource source = new SynchronousSchedulerEvaluationSource(scheduler());

        assertTrue(source.canSubmit());

        source.submit(snapshot);

        assertFalse(source.canSubmit());
        SchedulerEvaluationResult result = source.pollResult().orElseThrow();
        assertEquals(0L, result.sequence());
        assertSame(snapshot, result.snapshot());
        assertEquals(Optional.of("order-1"), result.evaluation().releaseDecision().map(decision -> decision.orderId()));
        assertTrue(source.canSubmit());
        assertTrue(source.pollResult().isEmpty());
    }

    @Test
    void shouldRejectSubmitWhileResultIsPendingAndAdvanceSequenceAfterPoll() {
        WarehouseSchedulerSnapshot firstSnapshot = snapshot("order-1");
        WarehouseSchedulerSnapshot secondSnapshot = snapshot("order-2");
        SynchronousSchedulerEvaluationSource source = new SynchronousSchedulerEvaluationSource(scheduler());

        source.submit(firstSnapshot);
        assertThrows(IllegalStateException.class, () -> source.submit(secondSnapshot));
        assertEquals(0L, source.pollResult().orElseThrow().sequence());

        source.submit(secondSnapshot);
        assertEquals(1L, source.pollResult().orElseThrow().sequence());
    }

    @Test
    void shouldTreatCloseAsNoOp() {
        SynchronousSchedulerEvaluationSource source = new SynchronousSchedulerEvaluationSource(scheduler());

        source.close();

        assertTrue(source.canSubmit());
    }

    private static DspReleaseScheduler scheduler() {
        return new DspReleaseScheduler(
                new ServiceCentreWindowPolicy(new ServiceCentrePriority(List.of("sc-1"))),
                new DspDependencyEvaluator());
    }

    private static WarehouseSchedulerSnapshot snapshot(String orderId) {
        return new WarehouseSchedulerSnapshot(
                List.of(orderState(orderId)),
                Map.of(StationType.P2P, new StationAdmissionSnapshot(
                        StationType.P2P,
                        new StationCapacity(1, 1),
                        new StationSnapshot(StationType.P2P, 0, 0),
                        true,
                        "")),
                Set.of(),
                Optional.empty());
    }

    private static DspSchedulerOrderState orderState(String orderId) {
        NotionalToteOrder order = new NotionalToteOrder(
                orderId,
                "notional-" + orderId,
                "sc-1",
                1,
                OrderType.FULL_PACK,
                List.of(new DspOrderItem("line-" + orderId, "product-" + orderId, 1)),
                0);
        return new DspSchedulerOrderState(
                order,
                new RouteRequirements(false, false, false, true, false, StartLocation.OSR),
                DspOrderStatus.WAITING);
    }
}
