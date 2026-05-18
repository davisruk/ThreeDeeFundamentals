package online.davisfamily.warehouse.sim.dsp.adapting;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;

public record AdaptedLineRecord(
        PreparedLineKey key,
        DspOrderItem line) {

    public AdaptedLineRecord {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        if (line == null) {
            throw new IllegalArgumentException("line must not be null");
        }
        if (line.lineType() != DspOrderLineType.ADAPTED) {
            throw new IllegalArgumentException("line must be ADAPTED");
        }
        if (!key.equals(PreparedLineKey.forPreparedLine(line))) {
            throw new IllegalArgumentException("key must match line prepared-line identity");
        }
    }

    public static AdaptedLineRecord fromPreparedLine(DspOrderItem line) {
        if (line == null) {
            throw new IllegalArgumentException("line must not be null");
        }
        return new AdaptedLineRecord(PreparedLineKey.forPreparedLine(line), line);
    }
}
