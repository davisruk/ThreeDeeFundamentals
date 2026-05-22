package online.davisfamily.warehouse.sim.dsp.adapting;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;

public record AdaptedLineRecord(
        PreparedLineKey key,
        DspOrderItem line,
        AdaptingStorageLocation location) {

    public AdaptedLineRecord {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        if (line == null) {
            throw new IllegalArgumentException("line must not be null");
        }
        if (location == null) {
            throw new IllegalArgumentException("location must not be null");
        }
        if (line.lineType() != DspOrderLineType.ADAPTED) {
            throw new IllegalArgumentException("line must be ADAPTED");
        }
        if (!key.equals(PreparedLineKey.forPreparedLine(line))) {
            throw new IllegalArgumentException("key must match line prepared-line identity");
        }
    }

    public static AdaptedLineRecord fromPreparedLine(DspOrderItem line) {
        return fromPreparedLine(
                line,
                new AdaptingStorageLocation(line.pharmacyId(), new AdaptingBenchId("unassigned"), 0, 0, 0));
    }

    public static AdaptedLineRecord fromPreparedLine(DspOrderItem line, AdaptingStorageLocation location) {
        if (line == null) {
            throw new IllegalArgumentException("line must not be null");
        }
        return new AdaptedLineRecord(PreparedLineKey.forPreparedLine(line), line, location);
    }
}
