package online.davisfamily.warehouse.sim.dsp.supply;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record ServiceCentreSupplyIssue(
        ServiceCentreSupplyIssueType type,
        int priority,
        List<String> serviceCentreIds,
        List<Integer> observedPriorities) {

    public ServiceCentreSupplyIssue {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (priority <= 0) {
            throw new IllegalArgumentException("priority must be positive");
        }
        if (serviceCentreIds == null || serviceCentreIds.isEmpty()) {
            throw new IllegalArgumentException("serviceCentreIds must not be null or empty");
        }
        Set<String> seenServiceCentreIds = new LinkedHashSet<>();
        List<String> normalizedServiceCentreIds = new ArrayList<>();
        for (String serviceCentreId : serviceCentreIds) {
            if (serviceCentreId == null || serviceCentreId.isBlank()) {
                throw new IllegalArgumentException("serviceCentreIds must not contain blank values");
            }
            String normalizedId = serviceCentreId.trim();
            if (!seenServiceCentreIds.add(normalizedId)) {
                throw new IllegalArgumentException(
                        "serviceCentreIds must not contain duplicates: " + normalizedId);
            }
            normalizedServiceCentreIds.add(normalizedId);
        }
        if (observedPriorities == null || observedPriorities.isEmpty()) {
            throw new IllegalArgumentException("observedPriorities must not be null or empty");
        }
        Set<Integer> seenPriorities = new LinkedHashSet<>();
        List<Integer> copiedPriorities = new ArrayList<>();
        for (Integer observedPriority : observedPriorities) {
            if (observedPriority == null || observedPriority < 0) {
                throw new IllegalArgumentException(
                        "observedPriorities must contain nonnegative values");
            }
            if (!seenPriorities.add(observedPriority)) {
                throw new IllegalArgumentException(
                        "observedPriorities must not contain duplicates: " + observedPriority);
            }
            copiedPriorities.add(observedPriority);
        }
        serviceCentreIds = List.copyOf(normalizedServiceCentreIds);
        observedPriorities = List.copyOf(copiedPriorities);
    }
}
