package online.davisfamily.warehouse.sim.dsp.station.processing;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;

/**
 * Immutable lookup of the retained notional orders used to validate station processing arrivals.
 *
 * <p>This catalog is deliberately separate from scheduler status. It retains the exact order
 * values supplied by the caller and only indexes them by their logical sheet identity.</p>
 */
public final class StationProcessingOrderCatalog {
    private final List<NotionalToteOrder> orders;
    private final Map<OrderSheetKey, NotionalToteOrder> ordersBySheet;

    public StationProcessingOrderCatalog(List<NotionalToteOrder> orders) {
        if (orders == null) {
            throw new IllegalArgumentException("orders must not be null");
        }

        for (NotionalToteOrder order : orders) {
            if (order == null) {
                throw new IllegalArgumentException("orders must not contain null");
            }
        }
        List<NotionalToteOrder> retainedOrders = List.copyOf(orders);
        Map<OrderSheetKey, NotionalToteOrder> indexedOrders = new LinkedHashMap<>();
        for (NotionalToteOrder order : retainedOrders) {
            OrderSheetKey orderSheetKey = order.orderSheetKey();
            if (indexedOrders.putIfAbsent(orderSheetKey, order) != null) {
                throw new IllegalArgumentException(
                        "Duplicate retained order sheet: " + orderSheetKey);
            }
        }

        this.orders = retainedOrders;
        this.ordersBySheet = Map.copyOf(indexedOrders);
    }

    public Optional<NotionalToteOrder> find(OrderSheetKey orderSheetKey) {
        if (orderSheetKey == null) {
            throw new IllegalArgumentException("orderSheetKey must not be null");
        }
        return Optional.ofNullable(ordersBySheet.get(orderSheetKey));
    }

    public NotionalToteOrder require(OrderSheetKey orderSheetKey) {
        return find(orderSheetKey).orElseThrow(() -> new IllegalStateException(
                "No retained order for sheet: " + orderSheetKey));
    }

    public List<NotionalToteOrder> orders() {
        return orders;
    }
}
