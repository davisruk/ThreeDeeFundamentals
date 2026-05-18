package online.davisfamily.warehouse.sim.dsp.adapting;

import java.util.List;

public record AdaptingBenchCompletion(
        AdaptingVisit visit,
        List<AdaptedLineRecord> collectedLines) {

    public AdaptingBenchCompletion {
        if (visit == null) {
            throw new IllegalArgumentException("visit must not be null");
        }
        if (collectedLines == null) {
            throw new IllegalArgumentException("collectedLines must not be null");
        }
        collectedLines = List.copyOf(collectedLines);
    }
}
