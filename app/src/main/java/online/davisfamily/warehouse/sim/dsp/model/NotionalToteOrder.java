package online.davisfamily.warehouse.sim.dsp.model;

import java.util.List;

public record NotionalToteOrder(
        String orderId,
        String notionalToteId,
        String serviceCentreId,
        int sheetNumber,
        OrderType orderType,
        List<DspOrderItem> items,
        long sequenceNumber) {

    public NotionalToteOrder {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId must not be blank");
        }
        if (notionalToteId == null || notionalToteId.isBlank()) {
            throw new IllegalArgumentException("notionalToteId must not be blank");
        }
        if (serviceCentreId == null || serviceCentreId.isBlank()) {
            throw new IllegalArgumentException("serviceCentreId must not be blank");
        }
        if (sheetNumber < 1) {
            throw new IllegalArgumentException("sheetNumber must be >= 1");
        }
        if (orderType == null) {
            throw new IllegalArgumentException("orderType must not be null");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("items must not be empty");
        }
        if (sequenceNumber < 0) {
            throw new IllegalArgumentException("sequenceNumber must be >= 0");
        }
        items = List.copyOf(items);
    }
}
