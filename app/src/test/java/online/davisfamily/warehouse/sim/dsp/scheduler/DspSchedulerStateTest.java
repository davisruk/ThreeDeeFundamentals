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
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
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
    void shouldCreatePreparedLineKeyFromPreparedLineReferenceFields() {
        DspOrderItem preparedLine = new DspOrderItem(
                " line-1 ",
                " product-1 ",
                1,
                "0006515",
                DspOrderLineType.ADAPTED,
                " target-order-1 ",
                2,
                0);

        PreparedLineKey key = PreparedLineKey.forPreparedLine(preparedLine);

        assertEquals("target-order-1", key.targetOrderId());
        assertEquals(2, key.targetSheetNumber());
        assertEquals("line-1", key.orderLineNumber());
        assertEquals(DspOrderLineType.ADAPTED, key.lineType());
    }

    @Test
    void shouldCreatePreparedLineKeyFromDispatchOrderAndLine() {
        NotionalToteOrder dispatchOrder = new NotionalToteOrder(
                "order-1",
                "notional-1",
                "sc-1",
                3,
                OrderType.ASSOCIATED,
                List.of(new DspOrderItem(
                        "line-1",
                        "product-1",
                        1,
                        "0006515",
                        DspOrderLineType.MANUAL,
                        "target-order-9",
                        1,
                        0)),
                0);

        PreparedLineKey key = PreparedLineKey.forDispatchLine(dispatchOrder, dispatchOrder.items().getFirst());

        assertEquals("order-1", key.targetOrderId());
        assertEquals(3, key.targetSheetNumber());
        assertEquals("line-1", key.orderLineNumber());
        assertEquals(DspOrderLineType.MANUAL, key.lineType());
    }

    @Test
    void shouldRejectInvalidPreparedLineKeyFields() {
        assertThrows(IllegalArgumentException.class, () -> new PreparedLineKey("", 1, "line-1", DspOrderLineType.ADAPTED));
        assertThrows(IllegalArgumentException.class, () -> new PreparedLineKey("order-1", 0, "line-1", DspOrderLineType.ADAPTED));
        assertThrows(IllegalArgumentException.class, () -> new PreparedLineKey("order-1", 1, " ", DspOrderLineType.ADAPTED));
        assertThrows(IllegalArgumentException.class, () -> new PreparedLineKey("order-1", 1, "line-1", null));
        assertThrows(IllegalArgumentException.class, () -> PreparedLineKey.forPreparedLine(null));
        assertThrows(IllegalArgumentException.class, () -> PreparedLineKey.forDispatchLine(null, validOrderState().order().items().getFirst()));
        assertThrows(IllegalArgumentException.class, () -> PreparedLineKey.forDispatchLine(validOrderState().order(), null));
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

        Set<PreparedLineKey> preparedLineKeys = new LinkedHashSet<>();
        preparedLineKeys.add(new PreparedLineKey("order-a", 1, "line-a", DspOrderLineType.ADAPTED));

        WarehouseSchedulerSnapshot snapshot = new WarehouseSchedulerSnapshot(
                orderStates,
                stationAdmissions,
                preparedLineKeys,
                Optional.of("sc-1"));

        orderStates.add(validOrderState().withStatus(DspOrderStatus.RELEASED));
        stationAdmissions.put(StationType.MANUAL, new StationAdmissionSnapshot(
                StationType.MANUAL,
                new StationCapacity(1, 1),
                new StationSnapshot(StationType.MANUAL, 0, 0),
                true,
                ""));
        preparedLineKeys.add(new PreparedLineKey("order-b", 1, "line-b", DspOrderLineType.MANUAL));

        assertEquals(1, snapshot.orderStates().size());
        assertEquals(1, snapshot.stationAdmissions().size());
        assertEquals(Set.of(new PreparedLineKey("order-a", 1, "line-a", DspOrderLineType.ADAPTED)), snapshot.preparedLineKeys());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.orderStates().add(validOrderState()));
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.stationAdmissions().put(StationType.MANUAL, stationAdmissions.get(StationType.MANUAL)));
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.preparedLineKeys().add(new PreparedLineKey("order-x", 1, "line-x", DspOrderLineType.FULL_PACK)));
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
