package online.davisfamily.warehouse.sim.dsp.bagging;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;

public record PlannedBag(
        BagKey bagKey,
        String serviceCentreId,
        String pharmacyId,
        String patientId,
        String prescriptionId,
        List<String> physicalPackIds,
        List<OrderSheetKey> owningOrderSheetKeys) {

    public PlannedBag {
        if (bagKey == null) {
            throw new IllegalArgumentException("bagKey must not be null");
        }
        serviceCentreId = requireTrimmedValue(serviceCentreId, "serviceCentreId");
        pharmacyId = requireTrimmedValue(pharmacyId, "pharmacyId");
        patientId = requireTrimmedValue(patientId, "patientId");
        prescriptionId = requireTrimmedValue(prescriptionId, "prescriptionId");
        if (!bagKey.prescriptionId().equals(prescriptionId)) {
            throw new IllegalArgumentException("bagKey prescriptionId must match prescriptionId");
        }
        physicalPackIds = validatedPackIds(physicalPackIds);
        owningOrderSheetKeys = distinctOwningOrderSheetKeys(owningOrderSheetKeys);
    }

    private static List<String> validatedPackIds(List<String> physicalPackIds) {
        if (physicalPackIds == null || physicalPackIds.isEmpty()) {
            throw new IllegalArgumentException("physicalPackIds must not be empty");
        }
        Set<String> distinctPackIds = new LinkedHashSet<>();
        for (String physicalPackId : physicalPackIds) {
            String normalizedPackId = requireTrimmedValue(physicalPackId, "physicalPackId");
            if (!distinctPackIds.add(normalizedPackId)) {
                throw new IllegalArgumentException("Duplicate physical pack ID: " + normalizedPackId);
            }
        }
        return List.copyOf(distinctPackIds);
    }

    private static List<OrderSheetKey> distinctOwningOrderSheetKeys(List<OrderSheetKey> owningOrderSheetKeys) {
        if (owningOrderSheetKeys == null || owningOrderSheetKeys.isEmpty()) {
            throw new IllegalArgumentException("owningOrderSheetKeys must not be empty");
        }
        LinkedHashSet<OrderSheetKey> distinctKeys = new LinkedHashSet<>();
        for (OrderSheetKey owningOrderSheetKey : owningOrderSheetKeys) {
            if (owningOrderSheetKey == null) {
                throw new IllegalArgumentException("owningOrderSheetKeys must not contain null");
            }
            distinctKeys.add(owningOrderSheetKey);
        }
        return List.copyOf(distinctKeys);
    }

    private static String requireTrimmedValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
