package online.davisfamily.warehouse.sim.dsp.adapting;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;

public record AdaptedLineRecord(
        PreparedLineKey key,
        OrderSheetKey sourceOrderSheetKey,
        String sourceServiceCentreId,
        DspOrderItem line,
        AdaptingStorageLocation location) {

    public AdaptedLineRecord {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        if (sourceOrderSheetKey == null) {
            throw new IllegalArgumentException("sourceOrderSheetKey must not be null");
        }
        if (sourceServiceCentreId == null || sourceServiceCentreId.isBlank()) {
            throw new IllegalArgumentException("sourceServiceCentreId must not be blank");
        }
        sourceServiceCentreId = sourceServiceCentreId.trim();
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

    public static AdaptedLineRecord fromPreparedLine(
            DspOrderItem line,
            OrderSheetKey sourceOrderSheetKey,
            String sourceServiceCentreId) {
        if (line == null) {
            throw new IllegalArgumentException("line must not be null");
        }
        return fromPreparedLine(
                line,
                sourceOrderSheetKey,
                sourceServiceCentreId,
                new AdaptingStorageLocation(line.pharmacyId(), new AdaptingBenchId("unassigned"), 0, 0, 0));
    }

    public static AdaptedLineRecord fromPreparedLine(
            DspOrderItem line,
            OrderSheetKey sourceOrderSheetKey,
            String sourceServiceCentreId,
            AdaptingStorageLocation location) {
        if (line == null) {
            throw new IllegalArgumentException("line must not be null");
        }
        return new AdaptedLineRecord(
                PreparedLineKey.forPreparedLine(line),
                sourceOrderSheetKey,
                sourceServiceCentreId,
                line,
                location);
    }
}
