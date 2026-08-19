package online.davisfamily.warehouse.sim.dsp.adapting;

import java.util.List;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;

public record AdaptingVisitProfile(
        AdaptingVisitType visitType,
        OrderSheetKey orderSheetKey,
        String serviceCentreId,
        List<DspOrderItem> preparedLines,
        List<PreparedLineKey> requestedLineKeys,
        List<String> pharmacyIds) {

    public AdaptingVisitProfile {
        if (visitType == null) {
            throw new IllegalArgumentException("visitType must not be null");
        }
        if (orderSheetKey == null) {
            throw new IllegalArgumentException("orderSheetKey must not be null");
        }
        if (serviceCentreId == null || serviceCentreId.isBlank()) {
            throw new IllegalArgumentException("serviceCentreId must not be blank");
        }
        serviceCentreId = serviceCentreId.trim();
        if (preparedLines == null) {
            throw new IllegalArgumentException("preparedLines must not be null");
        }
        if (requestedLineKeys == null) {
            throw new IllegalArgumentException("requestedLineKeys must not be null");
        }
        if (pharmacyIds == null) {
            throw new IllegalArgumentException("pharmacyIds must not be null");
        }
        preparedLines = List.copyOf(preparedLines);
        requestedLineKeys = List.copyOf(requestedLineKeys);
        pharmacyIds = List.copyOf(pharmacyIds);

        if (visitType == AdaptingVisitType.STORE) {
            if (preparedLines.isEmpty()) {
                throw new IllegalArgumentException("STORE profile must include preparedLines");
            }
            if (!requestedLineKeys.isEmpty()) {
                throw new IllegalArgumentException("STORE profile must not include requestedLineKeys");
            }
            for (DspOrderItem line : preparedLines) {
                if (line == null) {
                    throw new IllegalArgumentException("preparedLines must not contain null");
                }
                if (line.lineType() != DspOrderLineType.ADAPTED) {
                    throw new IllegalArgumentException("STORE profile lines must be ADAPTED");
                }
            }
            if (pharmacyIds.size() != preparedLines.size()) {
                throw new IllegalArgumentException("STORE profile pharmacyIds must align with preparedLines");
            }
        } else {
            if (!preparedLines.isEmpty()) {
                throw new IllegalArgumentException("COLLECT profile must not include preparedLines");
            }
            if (requestedLineKeys.isEmpty()) {
                throw new IllegalArgumentException("COLLECT profile must include requestedLineKeys");
            }
            for (PreparedLineKey key : requestedLineKeys) {
                if (key == null) {
                    throw new IllegalArgumentException("requestedLineKeys must not contain null");
                }
            }
            if (pharmacyIds.isEmpty()) {
                throw new IllegalArgumentException("COLLECT profile must include pharmacyIds");
            }
        }
    }

    public static AdaptingVisitProfile store(
            OrderSheetKey orderSheetKey,
            String serviceCentreId,
            List<DspOrderItem> preparedLines) {
        List<String> pharmacyIds = preparedLines.stream()
                .map(DspOrderItem::pharmacyId)
                .toList();
        return new AdaptingVisitProfile(
                AdaptingVisitType.STORE,
                orderSheetKey,
                serviceCentreId,
                preparedLines,
                List.of(),
                pharmacyIds);
    }

    public static AdaptingVisitProfile collect(
            OrderSheetKey orderSheetKey,
            String serviceCentreId,
            List<PreparedLineKey> requestedLineKeys,
            List<String> pharmacyIds) {
        return new AdaptingVisitProfile(
                AdaptingVisitType.COLLECT,
                orderSheetKey,
                serviceCentreId,
                List.of(),
                requestedLineKeys,
                pharmacyIds);
    }
}
