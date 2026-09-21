package online.davisfamily.warehouse.sim.dsp.adapting;

import online.davisfamily.warehouse.sim.dsp.bagging.BagPlanningResult;
import online.davisfamily.warehouse.sim.dsp.bagging.PackSourceProvenance;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedPackSlot;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedPackSlotKey;

/** Resolves an Adapting collection against its immutable pre-runtime slot. */
public final class PlannedSlotCollectedPackCorrelationResolver
        implements CollectedPackCorrelationResolver {
    private final BagPlanningResult bagPlanningResult;

    public PlannedSlotCollectedPackCorrelationResolver(BagPlanningResult bagPlanningResult) {
        if (bagPlanningResult == null) {
            throw new IllegalArgumentException("bagPlanningResult must not be null");
        }
        this.bagPlanningResult = bagPlanningResult;
    }

    @Override
    public String resolve(AdaptedLineRecord collectedLine, int packOrdinal) {
        if (collectedLine == null) {
            throw new IllegalArgumentException("collectedLine must not be null");
        }
        if (packOrdinal != 1) {
            throw new IllegalArgumentException("packOrdinal must be 1");
        }

        PlannedPackSlot slot = bagPlanningResult.requirePlannedPackSlot(new PlannedPackSlotKey(
                collectedLine.sourceOrderSheetKey(),
                collectedLine.line().lineReference(),
                packOrdinal));
        String expectedPackId = "pack-" + collectedLine.line().lineReference() + "-" + packOrdinal;
        if (!slot.reservedPhysicalPackId().equals(expectedPackId)) {
            throw new IllegalStateException(
                    "Collected slot reserved pack ID does not match station identity: "
                            + expectedPackId);
        }
        if (slot.initialPhysicalToteId().isPresent()) {
            throw new IllegalStateException(
                    "Collected slot is already initially physical: " + slot.slotKey());
        }
        validateSourceFacts(slot.sourceProvenance(), collectedLine);
        return slot.bagKey().correlationId();
    }

    private static void validateSourceFacts(
            PackSourceProvenance source,
            AdaptedLineRecord collectedLine) {
        if (!source.sourceOrderSheetKey().equals(collectedLine.sourceOrderSheetKey())
                || !source.lineReference().equals(collectedLine.line().lineReference())
                || !source.productId().equals(collectedLine.line().productId())
                || !source.serviceCentreId().equals(collectedLine.sourceServiceCentreId())
                || !source.pharmacyId().equals(collectedLine.line().pharmacyId())
                || !source.patientId().equals(collectedLine.line().patientId())
                || !source.prescriptionId().equals(collectedLine.line().prescriptionId())) {
            throw new IllegalStateException(
                    "Collected source provenance does not match planned slot: "
                            + collectedLine.line().lineReference());
        }
    }
}
