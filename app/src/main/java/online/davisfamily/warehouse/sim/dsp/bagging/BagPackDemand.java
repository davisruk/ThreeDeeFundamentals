package online.davisfamily.warehouse.sim.dsp.bagging;

import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;

/** Immutable demand for one logical pack slot. */
public record BagPackDemand(
        PlannedPackSlotKey slotKey,
        String reservedPhysicalPackId,
        PackDimensions dimensions,
        PackSourceProvenance sourceProvenance,
        OrderSheetKey fulfilmentOrderSheetKey,
        Optional<PhysicalToteId> initialPhysicalToteId) {

    public BagPackDemand {
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
    }

    private static String requireTrimmedValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
