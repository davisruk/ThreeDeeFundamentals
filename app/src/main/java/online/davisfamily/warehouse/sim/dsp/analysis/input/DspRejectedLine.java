package online.davisfamily.warehouse.sim.dsp.analysis.input;

import java.util.List;
import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;

/** Immutable detail for one source line excluded from executable input. */
public record DspRejectedLine(
        DspOrderItem orderItem,
        OrderSheetKey sourceOrderSheetKey,
        OrderType sourceOrderType,
        Optional<OrderSheetKey> targetOrderSheetKey,
        int sourceMessageEncounterIndex,
        int sourceLineIndex,
        Optional<PreparedLineKey> preparedLineKey,
        DspInputRejectionReason reason,
        String diagnostic,
        Optional<String> exceptionClassName,
        List<String> stackTraceLines) {

    public DspRejectedLine {
        if (orderItem == null) {
            throw new IllegalArgumentException("orderItem must not be null");
        }
        if (sourceOrderSheetKey == null) {
            throw new IllegalArgumentException("sourceOrderSheetKey must not be null");
        }
        if (sourceOrderType == null) {
            throw new IllegalArgumentException("sourceOrderType must not be null");
        }
        if (targetOrderSheetKey == null) {
            throw new IllegalArgumentException("targetOrderSheetKey must not be null");
        }
        if (sourceMessageEncounterIndex < 0) {
            throw new IllegalArgumentException("sourceMessageEncounterIndex must be >= 0");
        }
        if (sourceLineIndex < 0) {
            throw new IllegalArgumentException("sourceLineIndex must be >= 0");
        }
        if (preparedLineKey == null) {
            throw new IllegalArgumentException("preparedLineKey must not be null");
        }
        if (reason == null) {
            throw new IllegalArgumentException("reason must not be null");
        }
        if (diagnostic == null || diagnostic.isBlank()) {
            throw new IllegalArgumentException("diagnostic must not be blank");
        }
        diagnostic = diagnostic.trim();
        if (exceptionClassName == null) {
            throw new IllegalArgumentException("exceptionClassName must not be null");
        }
        exceptionClassName = exceptionClassName.map(DspRejectedLine::requireTrimmedValue);
        if (stackTraceLines == null || stackTraceLines.stream().anyMatch(line -> line == null)) {
            throw new IllegalArgumentException("stackTraceLines must not be null or contain null");
        }
        stackTraceLines = List.copyOf(stackTraceLines);
    }

    public DspOrderItem line() {
        return orderItem;
    }

    private static String requireTrimmedValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("exceptionClassName must not be blank");
        }
        return value.trim();
    }
}
