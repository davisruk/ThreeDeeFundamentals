package online.davisfamily.warehouse.sim.dsp.model;

import java.util.LinkedHashSet;
import java.util.Set;

public class DspOrderValidator {

    public void validateForScheduler(NotionalToteOrder order) {
        if (order == null) {
            throw new IllegalArgumentException("order must not be null");
        }

        if (requiresPharmacyPurity(order.orderType()) && !isPharmacyPure(order)) {
            throw new IllegalArgumentException(
                    "Order " + order.orderId() + " with type " + order.orderType()
                            + " must be pharmacy-pure but contained pharmacies " + pharmacyIds(order));
        }
    }

    public boolean isPharmacyPure(NotionalToteOrder order) {
        if (order == null) {
            throw new IllegalArgumentException("order must not be null");
        }
        return pharmacyIds(order).size() == 1;
    }

    public Set<String> pharmacyIds(NotionalToteOrder order) {
        if (order == null) {
            throw new IllegalArgumentException("order must not be null");
        }

        Set<String> pharmacyIds = new LinkedHashSet<>();
        for (DspOrderItem item : order.items()) {
            pharmacyIds.add(item.pharmacyId());
        }
        return Set.copyOf(pharmacyIds);
    }

    private boolean requiresPharmacyPurity(OrderType orderType) {
        return orderType == OrderType.ASSOCIATED
                || orderType == OrderType.EMPTY
                || orderType == OrderType.FULL_PACK;
    }
}
