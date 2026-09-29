package online.davisfamily.warehouse.sim.dsp.adapting;

import java.util.List;
import java.util.Optional;

/** Immutable on-demand inspection of one sheet-owned Adapting bin. */
public record AdaptingBinSnapshot(
        AdaptingBinId id,
        Optional<AdaptingBinId> nextBinId,
        List<AdaptedLineRecord> stagedRecords) {

    public AdaptingBinSnapshot {
        if (id == null || nextBinId == null || stagedRecords == null) {
            throw new IllegalArgumentException("Bin snapshot fields must not be null");
        }
        stagedRecords = List.copyOf(stagedRecords);
    }
}
