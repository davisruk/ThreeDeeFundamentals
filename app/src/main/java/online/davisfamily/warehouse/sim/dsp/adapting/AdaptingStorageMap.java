package online.davisfamily.warehouse.sim.dsp.adapting;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AdaptingStorageMap {
    private final Map<String, AdaptingBenchId> explicitAssignments = new LinkedHashMap<>();
    private List<AdaptingBenchId> availableBenchIds = List.of(new AdaptingBenchId("bench-1"));

    public void configureAvailableBenches(List<AdaptingBenchId> benchIds) {
        if (benchIds == null || benchIds.isEmpty()) {
            throw new IllegalArgumentException("benchIds must not be empty");
        }
        for (AdaptingBenchId benchId : benchIds) {
            if (benchId == null) {
                throw new IllegalArgumentException("benchIds must not contain null");
            }
        }
        availableBenchIds = List.copyOf(benchIds);
    }

    public void assignPharmacyToBench(String pharmacyId, AdaptingBenchId benchId) {
        if (pharmacyId == null || pharmacyId.isBlank()) {
            throw new IllegalArgumentException("pharmacyId must not be blank");
        }
        if (benchId == null) {
            throw new IllegalArgumentException("benchId must not be null");
        }
        explicitAssignments.put(pharmacyId.trim(), benchId);
    }

    public AdaptingBenchId preferredBenchFor(String pharmacyId) {
        if (pharmacyId == null || pharmacyId.isBlank()) {
            throw new IllegalArgumentException("pharmacyId must not be blank");
        }
        String normalizedPharmacyId = pharmacyId.trim();
        AdaptingBenchId explicit = explicitAssignments.get(normalizedPharmacyId);
        if (explicit != null) {
            return explicit;
        }
        int index = Math.floorMod(normalizedPharmacyId.hashCode(), availableBenchIds.size());
        return availableBenchIds.get(index);
    }
}
