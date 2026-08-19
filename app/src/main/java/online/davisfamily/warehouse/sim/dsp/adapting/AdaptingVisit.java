package online.davisfamily.warehouse.sim.dsp.adapting;

import java.util.List;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;

public record AdaptingVisit(
        PhysicalToteId physicalToteId,
        AdaptingVisitProfile profile) {

    public AdaptingVisit {
        if (physicalToteId == null) {
            throw new IllegalArgumentException("physicalToteId must not be null");
        }
        if (profile == null) {
            throw new IllegalArgumentException("profile must not be null");
        }
    }

    public AdaptingVisitType visitType() {
        return profile.visitType();
    }

    public List<DspOrderItem> preparedLines() {
        return profile.preparedLines();
    }

    public List<PreparedLineKey> requestedLineKeys() {
        return profile.requestedLineKeys();
    }

    public List<String> pharmacyIds() {
        return profile.pharmacyIds();
    }

    public static AdaptingVisit store(
            PhysicalToteId physicalToteId,
            OrderSheetKey orderSheetKey,
            String serviceCentreId,
            List<DspOrderItem> preparedLines) {
        return new AdaptingVisit(
                physicalToteId,
                AdaptingVisitProfile.store(orderSheetKey, serviceCentreId, preparedLines));
    }

    public static AdaptingVisit collect(
            PhysicalToteId physicalToteId,
            OrderSheetKey orderSheetKey,
            String serviceCentreId,
            List<PreparedLineKey> requestedLineKeys,
            List<String> pharmacyIds) {
        return new AdaptingVisit(
                physicalToteId,
                AdaptingVisitProfile.collect(
                        orderSheetKey, serviceCentreId, requestedLineKeys, pharmacyIds));
    }
}
