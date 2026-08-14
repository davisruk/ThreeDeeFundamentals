package online.davisfamily.warehouse.sim.dsp.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.StartLocation;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspOrderStatus;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationCapacity;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;

class DspSchedulerRuntimeStateTest {

    @Test
    void shouldExposeImmutableSnapshotFromRuntimeState() {
        Map<StationType, StationAdmissionSnapshot> stationAdmissions = new LinkedHashMap<>();
        stationAdmissions.put(StationType.P2P, admission(StationType.P2P, 0, 0, true, ""));
        Set<PreparedLineKey> preparedLineKeys = new LinkedHashSet<>();
        preparedLineKeys.add(new PreparedLineKey("order-1", "line-1"));

        DspSchedulerRuntimeState runtimeState = new DspSchedulerRuntimeState(new WarehouseSchedulerSnapshot(
                List.of(waitingOrder("order-1", "sc-1")),
                stationAdmissions,
                preparedLineKeys,
                Optional.empty()));

        WarehouseSchedulerSnapshot snapshot = runtimeState.snapshot();
        stationAdmissions.put(StationType.MANUAL, admission(StationType.MANUAL, 0, 0, true, ""));
        preparedLineKeys.add(new PreparedLineKey("order-2", "line-2"));

        assertEquals(1, snapshot.stationAdmissions().size());
        assertEquals(1, snapshot.preparedLineKeys().size());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.orderStates().add(waitingOrder("order-2", "sc-1")));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.stationAdmissions().put(StationType.MANUAL, admission(StationType.MANUAL, 0, 0, true, "")));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.preparedLineKeys().add(new PreparedLineKey("order-3", "line-3")));
    }

    @Test
    void shouldMarkWaitingOrderReleasedAndSetActiveServiceCentre() {
        DspSchedulerRuntimeState runtimeState = new DspSchedulerRuntimeState(new WarehouseSchedulerSnapshot(
                List.of(waitingOrder("order-1", "sc-1"), waitingOrder("order-2", "sc-2")),
                Map.of(StationType.P2P, admission(StationType.P2P, 0, 0, true, "")),
                Set.of(),
                Optional.empty()));

        runtimeState.markReleased("order-2");

        WarehouseSchedulerSnapshot snapshot = runtimeState.snapshot();
        assertEquals(DspOrderStatus.WAITING, snapshot.orderStates().getFirst().status());
        assertEquals(DspOrderStatus.RELEASED, snapshot.orderStates().get(1).status());
        assertEquals(Optional.of("sc-2"), snapshot.activeServiceCentreId());
    }

    @Test
    void shouldRejectUnknownOrCompletedOrderRelease() {
        DspSchedulerRuntimeState runtimeState = new DspSchedulerRuntimeState(new WarehouseSchedulerSnapshot(
                List.of(completedOrder("order-1", "sc-1"), releasedOrder("order-2", "sc-2")),
                Map.of(),
                Set.of(),
                Optional.empty()));

        assertThrows(IllegalArgumentException.class, () -> runtimeState.markReleased("missing"));
        assertThrows(IllegalArgumentException.class, () -> runtimeState.markReleased("order-1"));
        assertThrows(IllegalArgumentException.class, () -> runtimeState.markReleased("order-2"));
    }

    @Test
    void shouldReplaceStationAdmissionByStationType() {
        DspSchedulerRuntimeState runtimeState = new DspSchedulerRuntimeState(new WarehouseSchedulerSnapshot(
                List.of(waitingOrder("order-1", "sc-1")),
                Map.of(StationType.P2P, admission(StationType.P2P, 0, 0, true, "")),
                Set.of(),
                Optional.empty()));

        runtimeState.replaceStationAdmission(admission(StationType.P2P, 1, 1, false, "blocked"));
        runtimeState.replaceStationAdmission(admission(StationType.MANUAL, 0, 0, true, ""));

        WarehouseSchedulerSnapshot snapshot = runtimeState.snapshot();
        assertEquals(2, snapshot.stationAdmissions().size());
        assertFalse(snapshot.stationAdmissions().get(StationType.P2P).admissionOpen());
        assertEquals("blocked", snapshot.stationAdmissions().get(StationType.P2P).blockedReason());
        assertTrue(snapshot.stationAdmissions().containsKey(StationType.MANUAL));
    }

    @Test
    void shouldTrackPreparedLineKeys() {
        DspSchedulerRuntimeState runtimeState = new DspSchedulerRuntimeState(new WarehouseSchedulerSnapshot(
                List.of(waitingOrder("order-1", "sc-1")),
                Map.of(),
                Set.of(),
                Optional.empty()));

        PreparedLineKey adaptedKey = new PreparedLineKey("order-1", "line-1");
        PreparedLineKey manualKey = new PreparedLineKey("order-1", "line-2");

        runtimeState.addPreparedLineKey(adaptedKey);
        runtimeState.addPreparedLineKeys(Set.of(adaptedKey, manualKey));

        WarehouseSchedulerSnapshot snapshot = runtimeState.snapshot();
        assertEquals(Set.of(adaptedKey, manualKey), snapshot.preparedLineKeys());
        assertThrows(IllegalArgumentException.class, () -> runtimeState.addPreparedLineKey(null));
        assertThrows(IllegalArgumentException.class, () -> runtimeState.addPreparedLineKeys(null));
    }

    @Test
    void shouldValidateCommandApplicationResultShapes() {
        assertTrue(SchedulerCommandApplicationResult.appliedResult().applied());
        assertTrue(SchedulerCommandApplicationResult.deferredResult("later").deferred());
        assertEquals("bad", SchedulerCommandApplicationResult.rejectedResult("bad").reason());
        assertThrows(IllegalArgumentException.class, () -> new SchedulerCommandApplicationResult(true, true, ""));
        assertThrows(IllegalArgumentException.class, () -> new SchedulerCommandApplicationResult(true, false, "reason"));
        assertThrows(IllegalArgumentException.class, () -> new SchedulerCommandApplicationResult(false, false, ""));
    }

    private static DspSchedulerOrderState waitingOrder(String orderId, String serviceCentreId) {
        return order(orderId, serviceCentreId, DspOrderStatus.WAITING);
    }

    private static DspSchedulerOrderState releasedOrder(String orderId, String serviceCentreId) {
        return order(orderId, serviceCentreId, DspOrderStatus.RELEASED);
    }

    private static DspSchedulerOrderState completedOrder(String orderId, String serviceCentreId) {
        return order(orderId, serviceCentreId, DspOrderStatus.COMPLETED);
    }

    private static DspSchedulerOrderState order(String orderId, String serviceCentreId, DspOrderStatus status) {
        NotionalToteOrder order = new NotionalToteOrder(
                orderId,
                "notional-" + orderId,
                serviceCentreId,
                1,
                OrderType.ASSOCIATED,
                List.of(new DspOrderItem("line-" + orderId, "product-" + orderId, 1)),
                0);
        RouteRequirements routeRequirements = new RouteRequirements(false, false, false, true, false, StartLocation.OSR);
        return new DspSchedulerOrderState(order, routeRequirements, status);
    }

    private static StationAdmissionSnapshot admission(
            StationType stationType,
            int inProgress,
            int queued,
            boolean admissionOpen,
            String blockedReason) {
        return new StationAdmissionSnapshot(
                stationType,
                new StationCapacity(1, 1),
                new StationSnapshot(stationType, inProgress, queued),
                admissionOpen,
                blockedReason);
    }
}
