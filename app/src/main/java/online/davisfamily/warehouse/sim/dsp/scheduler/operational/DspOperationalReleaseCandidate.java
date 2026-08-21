package online.davisfamily.warehouse.sim.dsp.scheduler.operational;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.StartLocation;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseCandidate;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;

public record DspOperationalReleaseCandidate(
        OsrProcessingReleaseCandidate physicalCandidate,
        DspSchedulerOrderState logicalOrderState,
        List<String> pharmacyIds) {

    public DspOperationalReleaseCandidate {
        if (physicalCandidate == null) {
            throw new IllegalArgumentException("physicalCandidate must not be null");
        }
        if (logicalOrderState == null) {
            throw new IllegalArgumentException("logicalOrderState must not be null");
        }
        if (pharmacyIds == null) {
            throw new IllegalArgumentException("pharmacyIds must not be null");
        }

        NotionalToteOrder logicalOrder = logicalOrderState.order();
        if (!physicalCandidate.orderSheetKey().equals(logicalOrder.orderSheetKey())) {
            throw new IllegalArgumentException("physical and logical order sheet must match");
        }
        if (physicalCandidate.orderType() != logicalOrder.orderType()) {
            throw new IllegalArgumentException("physical and logical order type must match");
        }
        if (!physicalCandidate.serviceCentreId().equals(logicalOrder.serviceCentreId().trim())) {
            throw new IllegalArgumentException("physical and logical service centre must match");
        }
        if (logicalOrderState.routeRequirements().startLocation() != StartLocation.OSR) {
            throw new IllegalArgumentException("operational release candidate must start at OSR");
        }

        Set<String> distinctPharmacyIds = new LinkedHashSet<>();
        for (String pharmacyId : pharmacyIds) {
            if (pharmacyId == null || pharmacyId.isBlank()) {
                throw new IllegalArgumentException("pharmacyIds must not contain blank values");
            }
            String normalizedPharmacyId = pharmacyId.trim();
            if (!distinctPharmacyIds.add(normalizedPharmacyId)) {
                throw new IllegalArgumentException("pharmacyIds must be distinct");
            }
        }
        if (distinctPharmacyIds.isEmpty()) {
            throw new IllegalArgumentException("pharmacyIds must not be empty");
        }
        OrderType orderType = physicalCandidate.orderType();
        if ((orderType == OrderType.FULL_PACK || orderType == OrderType.ASSOCIATED)
                && distinctPharmacyIds.size() != 1) {
            throw new IllegalArgumentException(
                    orderType + " operational candidates must contain exactly one pharmacy");
        }
        if (orderType == OrderType.EMPTY) {
            throw new IllegalArgumentException("EMPTY orders cannot be operational release candidates");
        }
        pharmacyIds = List.copyOf(distinctPharmacyIds);
    }
}
