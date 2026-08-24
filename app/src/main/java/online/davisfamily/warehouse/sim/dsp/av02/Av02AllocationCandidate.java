package online.davisfamily.warehouse.sim.dsp.av02;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;

public record Av02AllocationCandidate(
        NotionalToteOrder order,
        String pharmacyId,
        List<Av02AllocationBlockReason> blockReasons) {

    public Av02AllocationCandidate {
        if (order == null) {
            throw new IllegalArgumentException("order must not be null");
        }
        if (order.orderType() != OrderType.EMPTY) {
            throw new IllegalArgumentException("AV02 allocation candidate must be an EMPTY order");
        }
        if (pharmacyId == null || pharmacyId.isBlank()) {
            throw new IllegalArgumentException("pharmacyId must not be blank");
        }
        pharmacyId = pharmacyId.trim();
        Set<String> orderPharmacyIds = new LinkedHashSet<>();
        order.items().forEach(item -> orderPharmacyIds.add(item.pharmacyId()));
        if (!orderPharmacyIds.equals(Set.of(pharmacyId))) {
            throw new IllegalArgumentException(
                    "EMPTY allocation candidate must contain exactly the declared pharmacy");
        }
        if (blockReasons == null) {
            throw new IllegalArgumentException("blockReasons must not be null");
        }
        Set<Av02AllocationBlockReason> distinctReasons = new LinkedHashSet<>();
        for (Av02AllocationBlockReason reason : blockReasons) {
            if (reason == null) {
                throw new IllegalArgumentException("blockReasons must not contain null");
            }
            if (!distinctReasons.add(reason)) {
                throw new IllegalArgumentException("blockReasons must be distinct");
            }
        }
        blockReasons = List.copyOf(distinctReasons);
    }

    public OrderSheetKey orderSheetKey() {
        return order.orderSheetKey();
    }

    public String serviceCentreId() {
        return order.serviceCentreId().trim();
    }

    public int orderPriority() {
        return order.orderPriority();
    }

    public long sourceSequenceNumber() {
        return order.sequenceNumber();
    }

    public boolean eligible() {
        return blockReasons.isEmpty();
    }
}
