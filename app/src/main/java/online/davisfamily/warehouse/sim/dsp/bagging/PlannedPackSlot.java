package online.davisfamily.warehouse.sim.dsp.bagging;

import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;

/** One immutable logical pack slot with its assigned immutable bag. */
public record PlannedPackSlot(
        PlannedPackSlotKey slotKey,
        String reservedPhysicalPackId,
        PackDimensions dimensions,
        PackSourceProvenance sourceProvenance,
        OrderSheetKey fulfilmentOrderSheetKey,
        Optional<PhysicalToteId> initialPhysicalToteId,
        BagKey bagKey) {

    public PlannedPackSlot {
        if (slotKey == null) {
            throw new IllegalArgumentException("slotKey must not be null");
        }
        reservedPhysicalPackId = requireTrimmedValue(
                reservedPhysicalPackId,
                "reservedPhysicalPackId");
        if (dimensions == null) {
            throw new IllegalArgumentException("dimensions must not be null");
        }
        if (sourceProvenance == null) {
            throw new IllegalArgumentException("sourceProvenance must not be null");
        }
        if (!slotKey.sourceOrderSheetKey().equals(sourceProvenance.sourceOrderSheetKey())) {
            throw new IllegalArgumentException(
                    "slotKey sourceOrderSheetKey must match sourceProvenance");
        }
        if (!slotKey.lineReference().equals(sourceProvenance.lineReference())) {
            throw new IllegalArgumentException("slotKey lineReference must match sourceProvenance");
        }
        if (fulfilmentOrderSheetKey == null) {
            throw new IllegalArgumentException("fulfilmentOrderSheetKey must not be null");
        }
        if (initialPhysicalToteId == null) {
            throw new IllegalArgumentException("initialPhysicalToteId must not be null");
        }
        initialPhysicalToteId = Optional.ofNullable(initialPhysicalToteId.orElse(null));
        if (bagKey == null) {
            throw new IllegalArgumentException("bagKey must not be null");
        }
        if (!bagKey.prescriptionId().equals(sourceProvenance.prescriptionId())) {
            throw new IllegalArgumentException("bagKey prescriptionId must match sourceProvenance");
        }
    }

    public PlannedPackSlot(BagPackDemand demand, BagKey bagKey) {
        this(
                demand == null ? null : demand.slotKey(),
                demand == null ? null : demand.reservedPhysicalPackId(),
                demand == null ? null : demand.dimensions(),
                demand == null ? null : demand.sourceProvenance(),
                demand == null ? null : demand.fulfilmentOrderSheetKey(),
                demand == null ? null : demand.initialPhysicalToteId(),
                bagKey);
    }

    private static String requireTrimmedValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
