package online.davisfamily.warehouse.sim.dsp.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

class ServiceCentreWindowPolicyTest {

    @Test
    void shouldChooseFirstPriorityServiceCentreWithUnreleasedWork() {
        ServiceCentreWindowPolicy policy = new ServiceCentreWindowPolicy(
                new ServiceCentrePriority(List.of("sc-1", "sc-2", "sc-3")));
        WarehouseSchedulerSnapshot snapshot = snapshot(List.of(
                orderState(order("order-1", "sc-2"), DspOrderStatus.WAITING),
                orderState(order("order-2", "sc-3"), DspOrderStatus.WAITING)));

        assertEquals(Optional.of("sc-2"), policy.activeWindowFor(snapshot));
    }

    @Test
    void shouldKeepActiveServiceCentreUntilItsWorkIsReleased() {
        ServiceCentreWindowPolicy policy = new ServiceCentreWindowPolicy(
                new ServiceCentrePriority(List.of("sc-1", "sc-2")));
        WarehouseSchedulerSnapshot snapshot = snapshot(
                List.of(
                        orderState(order("order-1", "sc-1"), DspOrderStatus.BLOCKED),
                        orderState(order("order-2", "sc-2"), DspOrderStatus.WAITING)),
                Optional.of("sc-1"));

        assertEquals(Optional.of("sc-1"), policy.activeWindowFor(snapshot));
    }

    @Test
    void shouldMoveToNextServiceCentreAfterActiveWindowCompletes() {
        ServiceCentreWindowPolicy policy = new ServiceCentreWindowPolicy(
                new ServiceCentrePriority(List.of("sc-1", "sc-2")));
        WarehouseSchedulerSnapshot snapshot = snapshot(
                List.of(
                        orderState(order("order-1", "sc-1"), DspOrderStatus.COMPLETED),
                        orderState(order("order-2", "sc-2"), DspOrderStatus.WAITING)),
                Optional.of("sc-1"));

        assertEquals(Optional.of("sc-2"), policy.activeWindowFor(snapshot));
    }

    @Test
    void shouldNotSkipBlockedActiveServiceCentre() {
        ServiceCentreWindowPolicy policy = new ServiceCentreWindowPolicy(
                new ServiceCentrePriority(List.of("sc-1", "sc-2")));
        WarehouseSchedulerSnapshot snapshot = snapshot(
                List.of(
                        orderState(order("order-1", "sc-1"), DspOrderStatus.BLOCKED),
                        orderState(order("order-2", "sc-2"), DspOrderStatus.WAITING)),
                Optional.of("sc-1"));

        assertEquals(Optional.of("sc-1"), policy.activeWindowFor(snapshot));
        assertTrue(policy.isOrderInActiveWindow(snapshot.orderStates().get(0), snapshot));
        assertFalse(policy.isOrderInActiveWindow(snapshot.orderStates().get(1), snapshot));
    }

    @Test
    void shouldRejectDuplicateOrBlankServiceCentreIds() {
        assertThrows(IllegalArgumentException.class, () -> new ServiceCentrePriority(List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ServiceCentrePriority(List.of("sc-1", " ")));
        assertThrows(IllegalArgumentException.class, () -> new ServiceCentrePriority(List.of("sc-1", "sc-1")));
    }

    private static WarehouseSchedulerSnapshot snapshot(List<DspSchedulerOrderState> orderStates) {
        return snapshot(orderStates, Optional.empty());
    }

    private static WarehouseSchedulerSnapshot snapshot(
            List<DspSchedulerOrderState> orderStates,
            Optional<String> activeServiceCentreId) {
        return new WarehouseSchedulerSnapshot(
                orderStates,
                Map.of(),
                Set.of(),
                Set.of(),
                activeServiceCentreId);
    }

    private static DspSchedulerOrderState orderState(NotionalToteOrder order, DspOrderStatus status) {
        return new DspSchedulerOrderState(
                order,
                new online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements(false, false, false, false, false, StartLocation.OSR),
                status);
    }

    private static NotionalToteOrder order(String orderId, String serviceCentreId) {
        return new NotionalToteOrder(
                orderId,
                "notional-" + orderId,
                serviceCentreId,
                1,
                OrderType.FULL_PACK,
                List.of(new DspOrderItem("item-" + orderId, "product-1", 1)),
                0);
    }
}
