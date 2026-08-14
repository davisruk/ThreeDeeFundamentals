package online.davisfamily.warehouse.sim.dsp.scheduler;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;

public record PreparedLineKey(
        String targetOrderId,
        String lineReference) {

    public PreparedLineKey {
        targetOrderId = requireTrimmedValue(targetOrderId, "targetOrderId");
        lineReference = requireTrimmedValue(lineReference, "lineReference");
    }

    public static PreparedLineKey forPreparedLine(DspOrderItem preparedLine) {
        if (preparedLine == null) {
            throw new IllegalArgumentException("preparedLine must not be null");
        }
        return new PreparedLineKey(
                preparedLine.referenceOrderId(),
                preparedLine.lineReference());
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
                dispatchLine.lineReference());
    }

    private static String requireTrimmedValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
