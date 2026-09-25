package online.davisfamily.warehouse.sim.dsp.thirdparty;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;

public final class ThirdPartyVisitPlanCatalog implements ThirdPartyVisitPlanSource {
    private final Map<OrderSheetKey, Entry> entries;

    public ThirdPartyVisitPlanCatalog(List<NotionalToteOrder> orders, ThirdPartyVisitFactory visitFactory) {
        if (orders == null) {
            throw new IllegalArgumentException("orders must not be null");
        }
        if (visitFactory == null) {
            throw new IllegalArgumentException("visitFactory must not be null");
        }
        Map<OrderSheetKey, Entry> built = new LinkedHashMap<>();
        for (NotionalToteOrder order : orders) {
            if (order == null) {
                throw new IllegalArgumentException("orders must not contain null");
            }
            OrderSheetKey key = order.orderSheetKey();
            if (built.containsKey(key)) {
                throw new IllegalArgumentException("duplicate order sheet: " + key);
            }
            Optional<ThirdPartyVisitPlan> plan = visitFactory.planFor(order);
            if (plan == null) {
                throw new IllegalArgumentException("visitFactory returned null for " + key);
            }
            plan.ifPresent(value -> {
                if (!key.equals(value.orderSheetKey())
                        || !order.serviceCentreId().trim().equals(value.serviceCentreId())
                        || order.orderType() != value.orderType()) {
                    throw new IllegalArgumentException("visit plan does not match order: " + key);
                }
            });
            built.put(key, new Entry(order, plan));
        }
        entries = Collections.unmodifiableMap(built);
    }

    @Override
    public Optional<ThirdPartyVisitPlan> planFor(NotionalToteOrder order) {
        if (order == null) {
            throw new IllegalArgumentException("order must not be null");
        }
        Entry entry = entries.get(order.orderSheetKey());
        if (entry == null || !entry.order().equals(order)) {
            throw new IllegalArgumentException("unknown or altered order sheet: " + order.orderSheetKey());
        }
        return entry.plan();
    }

    private record Entry(NotionalToteOrder order, Optional<ThirdPartyVisitPlan> plan) {
    }
}
