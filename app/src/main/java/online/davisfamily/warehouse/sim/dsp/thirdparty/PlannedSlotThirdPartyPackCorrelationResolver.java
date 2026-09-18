package online.davisfamily.warehouse.sim.dsp.thirdparty;

import online.davisfamily.warehouse.sim.dsp.bagging.BagPlanningResult;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedPackSlot;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedPackSlotKey;
import online.davisfamily.warehouse.sim.dsp.bagging.PackSourceProvenance;

/** Resolves a Third Party pack against its immutable pre-runtime slot. */
public final class PlannedSlotThirdPartyPackCorrelationResolver
        implements ThirdPartyPackCorrelationResolver {
    private final BagPlanningResult bagPlanningResult;

    public PlannedSlotThirdPartyPackCorrelationResolver(BagPlanningResult bagPlanningResult) {
        if (bagPlanningResult == null) {
            throw new IllegalArgumentException("bagPlanningResult must not be null");
        }
        this.bagPlanningResult = bagPlanningResult;
    }

    @Override
    public String resolve(ThirdPartyVisit visit, ThirdPartyLineWork lineWork, int packOrdinal) {
        if (visit == null) {
            throw new IllegalArgumentException("visit must not be null");
        }
        if (lineWork == null) {
            throw new IllegalArgumentException("lineWork must not be null");
        }
        if (packOrdinal <= 0 || packOrdinal > lineWork.outstandingQuantity()) {
            throw new IllegalArgumentException("packOrdinal must identify an outstanding pack");
        }

        PlannedPackSlot slot = bagPlanningResult.requirePlannedPackSlot(new PlannedPackSlotKey(
                visit.orderSheetKey(),
                lineWork.lineReference(),
                packOrdinal));
        String expectedPackId = "pack-" + lineWork.lineReference() + "-" + packOrdinal;
        if (!slot.reservedPhysicalPackId().equals(expectedPackId)) {
            throw new IllegalStateException(
                    "Third Party slot reserved pack ID does not match station identity: "
                            + expectedPackId);
        }
        if (slot.initialPhysicalToteId().isPresent()) {
            throw new IllegalStateException(
                    "Third Party slot is already initially physical: " + slot.slotKey());
        }
        validateSourceFacts(slot.sourceProvenance(), visit, lineWork);
        return slot.bagKey().correlationId();
    }

    private static void validateSourceFacts(
            PackSourceProvenance source,
            ThirdPartyVisit visit,
            ThirdPartyLineWork lineWork) {
        if (!source.sourceOrderSheetKey().equals(visit.orderSheetKey())
                || !source.lineReference().equals(lineWork.lineReference())
                || !source.productId().equals(lineWork.productId())
                || !source.serviceCentreId().equals(visit.serviceCentreId())
                || !source.pharmacyId().equals(lineWork.line().pharmacyId())
                || !source.patientId().equals(lineWork.line().patientId())
                || !source.prescriptionId().equals(lineWork.line().prescriptionId())) {
            throw new IllegalStateException(
                    "Third Party source provenance does not match planned slot: "
                            + lineWork.lineReference());
        }
    }
}
