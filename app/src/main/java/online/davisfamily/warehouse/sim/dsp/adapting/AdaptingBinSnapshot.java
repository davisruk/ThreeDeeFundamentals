package online.davisfamily.warehouse.sim.dsp.adapting;

import java.util.List;
import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;

/** Immutable on-demand inspection of one physical sheet-owned Adapting bin. */
public record AdaptingBinSnapshot(
        AdaptingStorageLocation location,
        OrderSheetKey targetOrderSheetKey,
        int ordinal,
        Optional<AdaptingStorageLocation> nextLocation,
        List<AdaptedLineRecord> stagedRecords) {

    public AdaptingBinSnapshot {
        if (location == null || targetOrderSheetKey == null || nextLocation == null || stagedRecords == null) {
            throw new IllegalArgumentException("Bin snapshot fields must not be null");
        }
        if (ordinal < 1) {
            throw new IllegalArgumentException("ordinal must be >= 1");
        }
        nextLocation = Optional.ofNullable(nextLocation.orElse(null));
        stagedRecords = List.copyOf(stagedRecords);
    }
}
