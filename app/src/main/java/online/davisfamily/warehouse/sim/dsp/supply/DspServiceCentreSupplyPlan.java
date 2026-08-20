package online.davisfamily.warehouse.sim.dsp.supply;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public record DspServiceCentreSupplyPlan(
        List<ServiceCentreSupplyBatch> batches,
        List<ServiceCentreSupplyIssue> issues) {

    public DspServiceCentreSupplyPlan {
        if (batches == null) {
            throw new IllegalArgumentException("batches must not be null");
        }
        Set<String> serviceCentreIds = new LinkedHashSet<>();
        List<ServiceCentreSupplyBatch> copiedBatches = new ArrayList<>();
        for (ServiceCentreSupplyBatch batch : batches) {
            if (batch == null) {
                throw new IllegalArgumentException("batches must not contain null");
            }
            if (!serviceCentreIds.add(batch.serviceCentreId())) {
                throw new IllegalArgumentException(
                        "Duplicate serviceCentreId in supply plan: " + batch.serviceCentreId());
            }
            copiedBatches.add(batch);
        }
        if (issues == null) {
            throw new IllegalArgumentException("issues must not be null");
        }
        if (issues.stream().anyMatch(issue -> issue == null)) {
            throw new IllegalArgumentException("issues must not contain null");
        }
        batches = List.copyOf(copiedBatches);
        issues = List.copyOf(issues);
    }

    public Optional<ServiceCentreSupplyBatch> findBatch(String serviceCentreId) {
        if (serviceCentreId == null || serviceCentreId.isBlank()) {
            throw new IllegalArgumentException("serviceCentreId must not be blank");
        }
        String normalizedId = serviceCentreId.trim();
        return batches.stream()
                .filter(batch -> batch.serviceCentreId().equals(normalizedId))
                .findFirst();
    }

    public List<ServiceCentreSupplyBatch> postStartupBatches() {
        return batches.stream()
                .filter(batch -> !batch.preloadedAtStart())
                .toList();
    }
}
