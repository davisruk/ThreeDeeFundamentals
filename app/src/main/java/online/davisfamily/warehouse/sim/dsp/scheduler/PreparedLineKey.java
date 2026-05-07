package online.davisfamily.warehouse.sim.dsp.scheduler;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;

public record PreparedLineKey(
        String targetOrderId,
        int targetSheetNumber,
        String orderLineNumber,
        DspOrderLineType lineType) {

    public PreparedLineKey {
        targetOrderId = requireTrimmedValue(targetOrderId, "targetOrderId");
        orderLineNumber = requireTrimmedValue(orderLineNumber, "orderLineNumber");
        if (targetSheetNumber < 1) {
            throw new IllegalArgumentException("targetSheetNumber must be >= 1");
        }
        if (lineType == null) {
            throw new IllegalArgumentException("lineType must not be null");
        }
    }

    public static PreparedLineKey forPreparedLine(DspOrderItem preparedLine) {
        if (preparedLine == null) {
            throw new IllegalArgumentException("preparedLine must not be null");
        }
        return new PreparedLineKey(
                preparedLine.referenceOrderId(),
                preparedLine.referenceSheetNumber(),
                preparedLine.itemId(),
                preparedLine.lineType());
    }

    public static PreparedLineKey forDispatchLine(NotionalToteOrder dispatchOrder, DspOrderItem dispatchLine) {
        if (dispatchOrder == null) {
            throw new IllegalArgumentException("dispatchOrder must not be null");
        }
        if (dispatchLine == null) {
            throw new IllegalArgumentException("dispatchLine must not be null");
        }
        return new PreparedLineKey(
                dispatchOrder.orderId(),
                dispatchOrder.sheetNumber(),
                dispatchLine.itemId(),
                dispatchLine.lineType());
    }

    private static String requireTrimmedValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
