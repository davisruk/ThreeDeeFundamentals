package online.davisfamily.warehouse.sim.dsp.analysis.input;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import online.davisfamily.warehouse.sim.dsp.io.TwelveNRejectedInputMessage;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;

/** Immutable, construction-owned rejection detail and aggregate indexes. */
public final class DspInputRejectionCatalog {
    private final List<DspRejectedLine> rejectedLines;
    private final List<TwelveNRejectedInputMessage> rejectedMessages;
    private final Map<DspInputRejectionReason, Integer> countsByReason;
    private final Map<OrderSheetKey, List<DspRejectedLine>> rejectedLinesByTargetOrder;

    public DspInputRejectionCatalog(
            List<DspRejectedLine> rejectedLines,
            List<TwelveNRejectedInputMessage> rejectedMessages) {
        if (rejectedLines == null || rejectedLines.stream().anyMatch(line -> line == null)) {
            throw new IllegalArgumentException("rejectedLines must not be null or contain null");
        }
        if (rejectedMessages == null
                || rejectedMessages.stream().anyMatch(message -> message == null)) {
            throw new IllegalArgumentException(
                    "rejectedMessages must not be null or contain null");
        }

        this.rejectedLines = List.copyOf(rejectedLines);
        this.rejectedMessages = List.copyOf(rejectedMessages);

        EnumMap<DspInputRejectionReason, Integer> counts = new EnumMap<>(
                DspInputRejectionReason.class);
        for (DspInputRejectionReason reason : DspInputRejectionReason.values()) {
            counts.put(reason, 0);
        }
        counts.compute(
                DspInputRejectionReason.MALFORMED_12N_MESSAGE,
                (reason, count) -> count + this.rejectedMessages.size());
        for (DspRejectedLine line : this.rejectedLines) {
            counts.compute(line.reason(), (reason, count) -> count + 1);
        }
        this.countsByReason = Map.copyOf(counts);

        Map<OrderSheetKey, List<DspRejectedLine>> mutableTargetIndex = new LinkedHashMap<>();
        for (DspRejectedLine line : this.rejectedLines) {
            line.targetOrderSheetKey().ifPresent(targetOrderSheetKey ->
                    mutableTargetIndex
                            .computeIfAbsent(targetOrderSheetKey, ignored -> new ArrayList<>())
                            .add(line));
        }
        Map<OrderSheetKey, List<DspRejectedLine>> immutableTargetIndex = new LinkedHashMap<>();
        mutableTargetIndex.forEach((targetOrderSheetKey, lines) ->
                immutableTargetIndex.put(targetOrderSheetKey, List.copyOf(lines)));
        this.rejectedLinesByTargetOrder = Map.copyOf(immutableTargetIndex);
    }

    public static DspInputRejectionCatalog empty() {
        return new DspInputRejectionCatalog(List.of(), List.of());
    }

    public List<DspRejectedLine> rejectedLines() {
        return rejectedLines;
    }

    public List<TwelveNRejectedInputMessage> rejectedMessages() {
        return rejectedMessages;
    }

    public int rejectedLineCount() {
        return rejectedLines.size();
    }

    public int rejectedMessageCount() {
        return rejectedMessages.size();
    }

    public Map<DspInputRejectionReason, Integer> countsByReason() {
        return countsByReason;
    }

    public int count(DspInputRejectionReason reason) {
        if (reason == null) {
            throw new IllegalArgumentException("reason must not be null");
        }
        return countsByReason.get(reason);
    }

    public Map<OrderSheetKey, List<DspRejectedLine>> rejectedLinesByTargetOrder() {
        return rejectedLinesByTargetOrder;
    }

    public List<DspRejectedLine> rejectedLinesForTargetOrder(OrderSheetKey targetOrderSheetKey) {
        if (targetOrderSheetKey == null) {
            throw new IllegalArgumentException("targetOrderSheetKey must not be null");
        }
        return rejectedLinesByTargetOrder.getOrDefault(targetOrderSheetKey, List.of());
    }

    public List<DspRejectedLine> rejectedLinesByTargetOrder(OrderSheetKey targetOrderSheetKey) {
        return rejectedLinesForTargetOrder(targetOrderSheetKey);
    }
}
