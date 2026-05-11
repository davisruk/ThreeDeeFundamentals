package online.davisfamily.warehouse.sim.dsp.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.StartLocation;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspOrderStatus;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;
import online.davisfamily.warehouse.sim.dsp.scheduler.ReleaseDecision;
import online.davisfamily.warehouse.sim.dsp.scheduler.ReleaseOrderCommand;
import online.davisfamily.warehouse.sim.dsp.scheduler.SchedulerEvaluation;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationCapacity;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;

class SchedulerEvaluationResultTest {

    @Test
    void shouldExposeSequenceSnapshotAndEvaluation() {
        WarehouseSchedulerSnapshot snapshot = snapshot();
        SchedulerEvaluation evaluation = evaluation();

        SchedulerEvaluationResult result = new SchedulerEvaluationResult(7L, snapshot, evaluation);

        assertEquals(7L, result.sequence());
        assertSame(snapshot, result.snapshot());
        assertSame(evaluation, result.evaluation());
    }

    @Test
    void shouldRejectInvalidInputs() {
        WarehouseSchedulerSnapshot snapshot = snapshot();
        SchedulerEvaluation evaluation = evaluation();

        assertThrows(IllegalArgumentException.class, () -> new SchedulerEvaluationResult(-1L, snapshot, evaluation));
        assertThrows(IllegalArgumentException.class, () -> new SchedulerEvaluationResult(0L, null, evaluation));
        assertThrows(IllegalArgumentException.class, () -> new SchedulerEvaluationResult(0L, snapshot, null));
    }

    private static WarehouseSchedulerSnapshot snapshot() {
        return new WarehouseSchedulerSnapshot(
                List.of(orderState("order-1", "sc-1")),
                Map.of(StationType.P2P, new StationAdmissionSnapshot(
                        StationType.P2P,
                        new StationCapacity(1, 1),
                        new StationSnapshot(StationType.P2P, 0, 0),
                        true,
                        "")),
                Set.of(),
                Optional.of("sc-1"));
    }

    private static SchedulerEvaluation evaluation() {
        ReleaseOrderCommand command = new ReleaseOrderCommand("order-1", "sc-1", StartLocation.OSR);
        ReleaseDecision decision = new ReleaseDecision(
                "order-1",
                "sc-1",
                StartLocation.OSR,
                new RouteRequirements(false, false, false, true, false, StartLocation.OSR),
                command);
        return SchedulerEvaluation.release(decision);
    }

    private static DspSchedulerOrderState orderState(String orderId, String serviceCentreId) {
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
}
