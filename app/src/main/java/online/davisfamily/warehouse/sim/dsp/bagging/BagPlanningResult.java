package online.davisfamily.warehouse.sim.dsp.bagging;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

public record BagPlanningResult(
        List<PlannedBag> plannedBags,
        List<ToteLoadPlan> p2pToteLoadPlans,
        List<PlannedPackTrace> packTraces) {

    public BagPlanningResult {
        plannedBags = copyAndRejectNull(plannedBags, "plannedBags");
        p2pToteLoadPlans = copyAndRejectNull(p2pToteLoadPlans, "p2pToteLoadPlans");
        packTraces = copyAndRejectNull(packTraces, "packTraces");

        Map<BagKey, PlannedBag> bagsByKey = new LinkedHashMap<>();
        Map<String, PlannedBag> bagsByCorrelationId = new LinkedHashMap<>();
        for (PlannedBag plannedBag : plannedBags) {
            if (bagsByKey.putIfAbsent(plannedBag.bagKey(), plannedBag) != null) {
                throw new IllegalArgumentException("Duplicate bag key: " + plannedBag.bagKey());
            }
            String correlationId = plannedBag.bagKey().correlationId();
            if (bagsByCorrelationId.putIfAbsent(correlationId, plannedBag) != null) {
                throw new IllegalArgumentException("Duplicate bag correlation ID: " + correlationId);
            }
        }

        Map<String, PlannedPackTrace> tracesByPackId = new LinkedHashMap<>();
        for (PlannedPackTrace packTrace : packTraces) {
            if (tracesByPackId.putIfAbsent(packTrace.physicalPackId(), packTrace) != null) {
                throw new IllegalArgumentException(
                        "Duplicate planned pack trace: " + packTrace.physicalPackId());
            }
        }
    }

    public Optional<PlannedBag> findBag(BagKey bagKey) {
        if (bagKey == null) {
            throw new IllegalArgumentException("bagKey must not be null");
        }
        return plannedBags.stream()
                .filter(plannedBag -> plannedBag.bagKey().equals(bagKey))
                .findFirst();
    }

    public Optional<PlannedBag> findBagByCorrelationId(String correlationId) {
        String normalizedCorrelationId = requireTrimmedValue(correlationId, "correlationId");
        return plannedBags.stream()
                .filter(plannedBag -> plannedBag.bagKey().correlationId().equals(normalizedCorrelationId))
                .findFirst();
    }

    public Optional<PlannedPackTrace> findPackTrace(String physicalPackId) {
        String normalizedPackId = requireTrimmedValue(physicalPackId, "physicalPackId");
        return packTraces.stream()
                .filter(packTrace -> packTrace.physicalPackId().equals(normalizedPackId))
                .findFirst();
    }

    private static <T> List<T> copyAndRejectNull(List<T> values, String fieldName) {
        if (values == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        if (values.stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException(fieldName + " must not contain null");
        }
        return List.copyOf(values);
    }

    private static String requireTrimmedValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
