package online.davisfamily.warehouse.sim.dsp.io;

import java.util.ArrayList;
import java.util.List;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;

final class TwelveNLineMappingSupport {
    private TwelveNLineMappingSupport() {
    }

    static List<DspOrderItem> toItems(TwelveNMessageJson message) {
        validateMessage(message);
        validateOrderLineCount(message.orderDetail());

        List<DspOrderItem> items = new ArrayList<>();
        for (TwelveNOrderLineJson orderLine : message.orderDetail().orderLines()) {
            items.add(new DspOrderItem(
                    requireTrimmedValue(orderLine.orderLineNumber(), "orderLineNumber"),
                    requireTrimmedValue(orderLine.productId(), "productId"),
                    parsePositiveInt(orderLine.numberOfPacks(), "numberOfPacks"),
                    requireTrimmedValue(orderLine.pharmacyId(), "pharmacyId"),
                    requireTrimmedValue(orderLine.patientId(), "patientId"),
                    requireTrimmedValue(orderLine.prescriptionId(), "prescriptionId"),
                    DspOrderLineType.fromCode(orderLine.orderLineType()),
                    requireTrimmedValue(orderLine.referenceOrderId(), "referenceOrderId"),
                    parsePositiveInt(orderLine.referenceSheetNumber(), "referenceSheetNumber"),
                    parseNonNegativeInt(orderLine.numberOfPacksPicked(), "numberOfPacksPicked")));
        }
        return List.copyOf(items);
    }

    static void validateMessage(TwelveNMessageJson message) {
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }
        if (message.header() == null) {
            throw new IllegalArgumentException("message.header must not be null");
        }
        if (message.toteIdentifier() == null) {
            throw new IllegalArgumentException("message.toteIdentifier must not be null");
        }
        if (message.orderPriority() == null) {
            throw new IllegalArgumentException("message.orderPriority must not be null");
        }
        if (message.serviceCentre() == null) {
            throw new IllegalArgumentException("message.serviceCentre must not be null");
        }
        if (message.orderDetail() == null) {
            throw new IllegalArgumentException("message.orderDetail must not be null");
        }
        if (message.orderDetail().orderLines() == null) {
            throw new IllegalArgumentException("message.orderDetail.orderLines must not be null");
        }
    }

    static void validateOrderLineCount(TwelveNOrderDetailJson orderDetail) {
        if (orderDetail.numberOfOrderLines() == null) {
            throw new IllegalArgumentException("orderDetail.numberOfOrderLines must not be null");
        }
        if (orderDetail.numberOfOrderLines().intValue() != orderDetail.orderLines().size()) {
            throw new IllegalArgumentException(
                    "orderDetail.numberOfOrderLines did not match orderLines.size(): "
                            + orderDetail.numberOfOrderLines() + " vs " + orderDetail.orderLines().size());
        }
    }

    static String requireTrimmedValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    static int parsePositiveInt(String value, String fieldName) {
        int parsed = parseInt(value, fieldName);
        if (parsed <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return parsed;
    }

    static int parseNonNegativeInt(String value, String fieldName) {
        int parsed = parseInt(value, fieldName);
        if (parsed < 0) {
            throw new IllegalArgumentException(fieldName + " must be >= 0");
        }
        return parsed;
    }

    private static int parseInt(String value, String fieldName) {
        String trimmed = requireTrimmedValue(value, fieldName);
        try {
            return Integer.parseInt(trimmed);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + " must be an integer: " + trimmed, e);
        }
    }
}
