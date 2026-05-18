package online.davisfamily.warehouse.sim.dsp.adapting;

import java.util.List;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;

public record AdaptingVisit(
        String toteId,
        AdaptingVisitType visitType,
        List<DspOrderItem> preparedLines,
        List<PreparedLineKey> requestedLineKeys) {

    public AdaptingVisit {
        if (toteId == null || toteId.isBlank()) {
            throw new IllegalArgumentException("toteId must not be blank");
        }
        if (visitType == null) {
            throw new IllegalArgumentException("visitType must not be null");
        }
        if (preparedLines == null) {
            throw new IllegalArgumentException("preparedLines must not be null");
        }
        if (requestedLineKeys == null) {
            throw new IllegalArgumentException("requestedLineKeys must not be null");
        }
        preparedLines = List.copyOf(preparedLines);
        requestedLineKeys = List.copyOf(requestedLineKeys);

        if (visitType == AdaptingVisitType.STORE) {
            if (preparedLines.isEmpty()) {
                throw new IllegalArgumentException("STORE visit must include preparedLines");
            }
            if (!requestedLineKeys.isEmpty()) {
                throw new IllegalArgumentException("STORE visit must not include requestedLineKeys");
            }
            for (DspOrderItem line : preparedLines) {
                if (line == null) {
                    throw new IllegalArgumentException("preparedLines must not contain null");
                }
                if (line.lineType() != DspOrderLineType.ADAPTED) {
                    throw new IllegalArgumentException("STORE visit lines must be ADAPTED");
                }
            }
        }

        if (visitType == AdaptingVisitType.COLLECT) {
            if (!preparedLines.isEmpty()) {
                throw new IllegalArgumentException("COLLECT visit must not include preparedLines");
            }
            if (requestedLineKeys.isEmpty()) {
                throw new IllegalArgumentException("COLLECT visit must include requestedLineKeys");
            }
            for (PreparedLineKey key : requestedLineKeys) {
                if (key == null) {
                    throw new IllegalArgumentException("requestedLineKeys must not contain null");
                }
                if (key.lineType() != DspOrderLineType.ADAPTED) {
                    throw new IllegalArgumentException("COLLECT visit keys must be ADAPTED");
                }
            }
        }
    }

    public static AdaptingVisit store(String toteId, List<DspOrderItem> preparedLines) {
        return new AdaptingVisit(toteId, AdaptingVisitType.STORE, preparedLines, List.of());
    }

    public static AdaptingVisit collect(String toteId, List<PreparedLineKey> requestedLineKeys) {
        return new AdaptingVisit(toteId, AdaptingVisitType.COLLECT, List.of(), requestedLineKeys);
    }
}
