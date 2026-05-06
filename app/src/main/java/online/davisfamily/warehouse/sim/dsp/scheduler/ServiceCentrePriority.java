package online.davisfamily.warehouse.sim.dsp.scheduler;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record ServiceCentrePriority(List<String> serviceCentreIds) {
    public ServiceCentrePriority {
        if (serviceCentreIds == null || serviceCentreIds.isEmpty()) {
            throw new IllegalArgumentException("serviceCentreIds must not be empty");
        }
        Set<String> seen = new HashSet<>();
        for (String serviceCentreId : serviceCentreIds) {
            if (serviceCentreId == null || serviceCentreId.isBlank()) {
                throw new IllegalArgumentException("serviceCentreId must not be blank");
            }
            if (!seen.add(serviceCentreId)) {
                throw new IllegalArgumentException("Duplicate serviceCentreId: " + serviceCentreId);
            }
        }
        serviceCentreIds = List.copyOf(serviceCentreIds);
    }
}
