package online.davisfamily.warehouse.sim.dsp.thirdparty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

class ThirdPartyAreaTest {

    @Test
    void shouldBlockAdmissionWhenProcessingAndWaitingCapacityAreFull() {
        ThirdPartyArea area = new ThirdPartyArea(new ThirdPartyAreaConfig(1, 1, 5d));

        assertTrue(area.submitVisit(visit("order-1")));
        assertTrue(area.submitVisit(visit("order-2")));
        assertFalse(area.canAccept());
        assertFalse(area.submitVisit(visit("order-3")));

        ThirdPartyAreaSnapshot snapshot = area.snapshot();
        assertEquals(1, snapshot.activeCount());
        assertEquals(1, snapshot.waitingCount());
        assertEquals(List.of(new OrderSheetKey("order-2", 1)), snapshot.waitingOrderSheetKeys());
    }

    @Test
    void shouldProcessUpToConfiguredConcurrentCapacity() {
        ThirdPartyArea area = new ThirdPartyArea(new ThirdPartyAreaConfig(0, 2, 5d));

        assertTrue(area.submitVisit(visit("order-1")));
        assertTrue(area.submitVisit(visit("order-2")));

        assertEquals(
                List.of("order-1", "order-2"),
                area.snapshot().activeVisits().stream()
                        .map(state -> state.orderSheetKey().orderId())
                        .toList());
    }

    @Test
    void shouldStartWaitingVisitsInFifoOrderWhenCapacityOpens() {
        ThirdPartyArea area = new ThirdPartyArea(new ThirdPartyAreaConfig(2, 1, 5d));
        area.submitVisit(visit("order-1"));
        area.submitVisit(visit("order-2"));
        area.submitVisit(visit("order-3"));

        area.update(5d);

        assertEquals(List.of("order-2"), area.snapshot().activeVisits().stream()
                .map(state -> state.orderSheetKey().orderId())
                .toList());
        assertEquals(List.of(new OrderSheetKey("order-3", 1)), area.snapshot().waitingOrderSheetKeys());
    }

    @Test
    void shouldDrainEachCompletionExactlyOnce() {
        ThirdPartyArea area = new ThirdPartyArea(new ThirdPartyAreaConfig(0, 1, 2d));
        ThirdPartyVisit visit = visit("order-1");
        area.submitVisit(visit);

        area.update(2d);

        assertEquals(List.of(new ThirdPartyCompletion(visit)), area.drainCompletions());
        assertTrue(area.drainCompletions().isEmpty());
        assertEquals(0, area.snapshot().activeCount());
    }

    @Test
    void shouldExposeImmutableSnapshotCollections() {
        ThirdPartyArea area = new ThirdPartyArea(new ThirdPartyAreaConfig(1, 1, 5d));
        area.submitVisit(visit("order-1"));
        area.submitVisit(visit("order-2"));
        ThirdPartyAreaSnapshot snapshot = area.snapshot();

        assertThrows(UnsupportedOperationException.class, () -> snapshot.waitingOrderSheetKeys().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.waitingPhysicalToteIds().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.activeVisits().clear());
    }

    @Test
    void shouldValidateConfigurationAndUpdateDelta() {
        assertThrows(IllegalArgumentException.class, () -> new ThirdPartyAreaConfig(-1, 1, 1d));
        assertThrows(IllegalArgumentException.class, () -> new ThirdPartyAreaConfig(0, 0, 1d));
        assertThrows(IllegalArgumentException.class, () -> new ThirdPartyAreaConfig(0, 1, -1d));

        ThirdPartyArea area = new ThirdPartyArea(new ThirdPartyAreaConfig(0, 1, 1d));
        assertThrows(IllegalArgumentException.class, () -> area.update(-0.1d));
    }

    private ThirdPartyVisit visit(String orderId) {
        return new ThirdPartyVisit(
                new PhysicalToteId("tote-" + orderId),
                new ThirdPartyVisitPlan(
                        new OrderSheetKey(orderId, 1),
                        OrderType.FULL_PACK,
                        List.of(new ThirdPartyLineWork(
                                "line-" + orderId,
                                "product-1",
                                1,
                                "Y74",
                                ThirdPartyWorkType.DIRECT_FULFILMENT))));
    }
}
