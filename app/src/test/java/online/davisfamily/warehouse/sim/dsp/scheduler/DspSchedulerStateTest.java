package online.davisfamily.warehouse.sim.dsp.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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

class DspSchedulerStateTest {

    @Test
    void shouldAcceptWhenProcessingOrQueueCapacityExists() {
        StationCapacity capacity = new StationCapacity(1, 2);

        assertTrue(capacity.canAccept(new StationSnapshot(StationType.P2P, 0, 2)));
        assertTrue(capacity.canAccept(new StationSnapshot(StationType.P2P, 1, 1)));
    }

    @Test
    void shouldRejectWhenProcessingAndQueueAreFull() {
        StationCapacity capacity = new StationCapacity(1, 2);

        assertFalse(capacity.canAccept(new StationSnapshot(StationType.P2P, 1, 2)));
    }

    @Test
    void shouldRejectWhenStationAdmissionIsClosed() {
        StationAdmissionSnapshot admission = new StationAdmissionSnapshot(
                StationType.P2P,
                new StationCapacity(1, 1),
                new StationSnapshot(StationType.P2P, 0, 0),
                false,
                "blocked for test");

        assertFalse(admission.canAccept());
    }

    @Test
    void shouldRejectInvalidCapacityAndSnapshotCounts() {
        assertThrows(IllegalArgumentException.class, () -> new StationCapacity(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> new StationCapacity(0, -1));
        assertThrows(IllegalArgumentException.class, () -> new StationSnapshot(StationType.P2P, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new StationSnapshot(StationType.P2P, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> new StationAdmissionSnapshot(
                StationType.P2P,
                new StationCapacity(1, 1),
                new StationSnapshot(StationType.MANUAL, 0, 0),
                true,
                ""));
        assertThrows(IllegalArgumentException.class, () -> new StationAdmissionSnapshot(
                StationType.P2P,
                new StationCapacity(1, 1),
                new StationSnapshot(StationType.P2P, 0, 0),
                false,
                ""));
    }

    @Test
    void shouldDefensivelyCopySchedulerSnapshotCollections() {
        List<DspSchedulerOrderState> orderStates = new ArrayList<>();
        orderStates.add(validOrderState());

        Map<StationType, StationAdmissionSnapshot> stationAdmissions = new LinkedHashMap<>();
        stationAdmissions.put(StationType.P2P, new StationAdmissionSnapshot(
                StationType.P2P,
                new StationCapacity(1, 1),
                new StationSnapshot(StationType.P2P, 0, 0),
                true,
                ""));

        Set<String> completedAdapted = new LinkedHashSet<>();
        completedAdapted.add("notional-a");
        Set<String> manualReady = new LinkedHashSet<>();
        manualReady.add("notional-b");

        WarehouseSchedulerSnapshot snapshot = new WarehouseSchedulerSnapshot(
                orderStates,
                stationAdmissions,
                completedAdapted,
                manualReady,
                Optional.of("sc-1"));

        orderStates.add(validOrderState().withStatus(DspOrderStatus.RELEASED));
        stationAdmissions.put(StationType.MANUAL, new StationAdmissionSnapshot(
                StationType.MANUAL,
                new StationCapacity(1, 1),
                new StationSnapshot(StationType.MANUAL, 0, 0),
                true,
                ""));
        completedAdapted.add("notional-c");
        manualReady.add("notional-d");

        assertEquals(1, snapshot.orderStates().size());
        assertEquals(1, snapshot.stationAdmissions().size());
        assertEquals(Set.of("notional-a"), snapshot.completedAdaptedNotionalToteIds());
        assertEquals(Set.of("notional-b"), snapshot.manualReadyNotionalToteIds());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.orderStates().add(validOrderState()));
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.stationAdmissions().put(StationType.MANUAL, stationAdmissions.get(StationType.MANUAL)));
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.completedAdaptedNotionalToteIds().add("notional-x"));
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.manualReadyNotionalToteIds().add("notional-y"));
    }

    private static DspSchedulerOrderState validOrderState() {
        NotionalToteOrder order = new NotionalToteOrder(
                "order-1",
                "notional-1",
                "sc-1",
                1,
                OrderType.ASSOCIATED,
                List.of(new DspOrderItem("item-1", "product-1", 1)),
                0);
        RouteRequirements routeRequirements = new RouteRequirements(false, false, false, true, false, StartLocation.OSR);
        return new DspSchedulerOrderState(order, routeRequirements, DspOrderStatus.WAITING);
    }
}
