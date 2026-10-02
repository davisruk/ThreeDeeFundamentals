package online.davisfamily.warehouse.sim.dsp.adapting;

import java.util.List;
import java.util.Optional;

public record AdaptingBenchCompletion(
        AdaptingVisit visit,
        List<AdaptedLineRecord> collectedLines,
        Optional<AdaptingPreparedOrderGroup> preparedOrderGroup) {

    public AdaptingBenchCompletion(AdaptingVisit visit, List<AdaptedLineRecord> collectedLines) {
        this(visit, collectedLines, Optional.empty());
    }

    public AdaptingBenchCompletion {
        if (visit == null) {
            throw new IllegalArgumentException("visit must not be null");
        }
        if (collectedLines == null) {
            throw new IllegalArgumentException("collectedLines must not be null");
        }
        collectedLines = List.copyOf(collectedLines);
        if (preparedOrderGroup == null) {
            throw new IllegalArgumentException("preparedOrderGroup must not be null");
        }
        if (preparedOrderGroup.isPresent()
                && !collectedLines.equals(preparedOrderGroup.orElseThrow().records())) {
            throw new IllegalArgumentException("strict COLLECT preview must match prepared group records");
        }
    }
}
