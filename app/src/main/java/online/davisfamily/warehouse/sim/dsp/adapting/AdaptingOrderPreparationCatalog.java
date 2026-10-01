package online.davisfamily.warehouse.sim.dsp.adapting;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.io.LoadedDspData;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;

/** Immutable order-wide preparation requirements for executable fulfilment orders. */
public final class AdaptingOrderPreparationCatalog {
    private final Map<String, List<PreparedLineKey>> keysByOrderId;
    private final Map<String, String> storeByOrderId;
    private final Map<PreparedLineKey, OrderSheetKey> targetSheetByKey;

    public AdaptingOrderPreparationCatalog(
            LoadedDspData executableData,
            AdaptingTargetSheetCatalog targetSheetCatalog) {
        if (executableData == null || targetSheetCatalog == null) {
            throw new IllegalArgumentException("executableData and targetSheetCatalog must not be null");
        }

        Map<String, String> stores = new LinkedHashMap<>();
        Map<PreparedLineKey, OrderSheetKey> aliases = new LinkedHashMap<>();
        Map<String, Set<PreparedLineKey>> keys = new LinkedHashMap<>();
        for (NotionalToteOrder order : executableData.orders()) {
            if (order.orderType() != OrderType.ASSOCIATED && order.orderType() != OrderType.EMPTY) {
                continue;
            }
            keys.computeIfAbsent(order.orderId(), ignored -> new LinkedHashSet<>());
            for (DspOrderItem item : order.items()) {
                String previousStore = stores.putIfAbsent(order.orderId(), item.pharmacyId());
                if (previousStore != null && !previousStore.equals(item.pharmacyId())) {
                    throw new IllegalStateException("Conflicting store for fulfilment order " + order.orderId());
                }
                if (item.lineType() == DspOrderLineType.ADAPTED) {
                    PreparedLineKey key = PreparedLineKey.forDispatchLine(order, item);
                    OrderSheetKey previousSheet = aliases.putIfAbsent(key, order.orderSheetKey());
                    if (previousSheet != null && !previousSheet.equals(order.orderSheetKey())) {
                        throw new IllegalStateException("Conflicting sheet aliases for prepared line " + key);
                    }
                }
            }
        }

        Map<PreparedLineKey, OrderSheetKey> targets = new LinkedHashMap<>();
        for (NotionalToteOrder source : executableData.orders()) {
            if (source.orderType() != OrderType.ADAPTED) {
                continue;
            }
            for (DspOrderItem item : source.items()) {
                PreparedLineKey key = PreparedLineKey.forPreparedLine(item);
                OrderSheetKey target = targetSheetCatalog.requireTargetSheet(key);
                OrderSheetKey alias = aliases.get(key);
                if (!target.equals(alias)) {
                    throw new IllegalStateException("Prepared line target has no matching fulfilment alias: " + key);
                }
                String store = stores.get(key.targetOrderId());
                if (!item.pharmacyId().equals(store)) {
                    throw new IllegalStateException("Conflicting store for prepared line " + key);
                }
                keys.get(key.targetOrderId()).add(key);
                targets.put(key, target);
            }
        }
        Map<String, List<PreparedLineKey>> immutableKeys = new LinkedHashMap<>();
        keys.forEach((orderId, orderKeys) -> immutableKeys.put(orderId, List.copyOf(orderKeys)));
        keysByOrderId = Collections.unmodifiableMap(immutableKeys);
        storeByOrderId = Collections.unmodifiableMap(new LinkedHashMap<>(stores));
        targetSheetByKey = Collections.unmodifiableMap(targets);
    }

    public List<PreparedLineKey> requiredKeysFor(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId must not be blank");
        }
        return keysByOrderId.getOrDefault(orderId, List.of());
    }

    public String requireStoreId(String orderId) {
        String store = storeByOrderId.get(orderId);
        if (store == null) {
            throw new IllegalStateException("No executable fulfilment order " + orderId);
        }
        return store;
    }

    public OrderSheetKey requireTargetSheet(PreparedLineKey key) {
        OrderSheetKey target = targetSheetByKey.get(key);
        if (target == null) {
            throw new IllegalStateException("No executable prepared line " + key);
        }
        return target;
    }
}
