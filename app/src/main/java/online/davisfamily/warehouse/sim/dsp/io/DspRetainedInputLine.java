package online.davisfamily.warehouse.sim.dsp.io;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;

/** Immutable source identity retained for one scheduler-visible input line. */
public record DspRetainedInputLine(
        DspOrderItem orderItem,
        OrderSheetKey sourceOrderSheetKey,
        OrderType sourceOrderType,
        int sourceMessageEncounterIndex,
        int sourceLineIndex) {

    public DspRetainedInputLine {
        if (orderItem == null) {
            throw new IllegalArgumentException("orderItem must not be null");
        }
        if (sourceOrderSheetKey == null) {
            throw new IllegalArgumentException("sourceOrderSheetKey must not be null");
        }
        if (sourceOrderType == null) {
            throw new IllegalArgumentException("sourceOrderType must not be null");
        }
        if (sourceMessageEncounterIndex < 0) {
            throw new IllegalArgumentException("sourceMessageEncounterIndex must be >= 0");
        }
        if (sourceLineIndex < 0) {
            throw new IllegalArgumentException("sourceLineIndex must be >= 0");
        }
    }

    public DspOrderItem line() {
        return orderItem;
    }
}
