package online.davisfamily.warehouse.sim.dsp.osr;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record OsrInventoryConfig(
        int capacity,
        List<String> preloadServiceCentreIds) {

    public OsrInventoryConfig {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1");
        }
        if (preloadServiceCentreIds == null) {
            throw new IllegalArgumentException("preloadServiceCentreIds must not be null");
        }

        List<String> normalizedIds = new ArrayList<>();
        Set<String> seenIds = new LinkedHashSet<>();
        for (String serviceCentreId : preloadServiceCentreIds) {
            if (serviceCentreId == null || serviceCentreId.isBlank()) {
                throw new IllegalArgumentException("preload serviceCentreId must not be blank");
            }
            String normalizedId = serviceCentreId.trim();
            if (!seenIds.add(normalizedId)) {
                throw new IllegalArgumentException(
                        "Duplicate preload serviceCentreId: " + normalizedId);
            }
            normalizedIds.add(normalizedId);
        }
        preloadServiceCentreIds = List.copyOf(normalizedIds);
    }

    public static OsrInventoryConfig productionBaseline() {
        return new OsrInventoryConfig(1200, List.of("104", "108"));
    }
}
