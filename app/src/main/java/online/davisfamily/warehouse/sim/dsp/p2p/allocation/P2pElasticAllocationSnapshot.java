package online.davisfamily.warehouse.sim.dsp.p2p.allocation;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;

public record P2pElasticAllocationSnapshot(
        String profileId,
        P2pElasticAllocationCalibrationStatus calibrationStatus,
        LocalDateTime evaluatedAt,
        List<P2pLineId> configuredLineIds,
        int maximumConcurrentServiceCentres,
        List<P2pServiceCentreLineDemandSnapshot> serviceCentres,
        List<P2pElasticAllocationIssue> issues) {

    public static final String DEADLINE_AWARE_ELASTIC_STICKY_LEASES =
            "DEADLINE_AWARE_ELASTIC_STICKY_LEASES";

    public P2pElasticAllocationSnapshot {
        if (!DEADLINE_AWARE_ELASTIC_STICKY_LEASES.equals(profileId)) {
            throw new IllegalArgumentException("unsupported elastic allocation profileId");
        }
        if (calibrationStatus == null || evaluatedAt == null) {
            throw new IllegalArgumentException(
                    "calibrationStatus and evaluatedAt must not be null");
        }
        configuredLineIds = distinctCopy(configuredLineIds, "configuredLineIds");
        if (maximumConcurrentServiceCentres < 1
                || maximumConcurrentServiceCentres > configuredLineIds.size()) {
            throw new IllegalArgumentException(
                    "maximumConcurrentServiceCentres must fit configured lines");
        }
        if (serviceCentres == null || serviceCentres.stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException(
                    "serviceCentres must not be null or contain null");
        }
        Set<String> serviceCentreIds = new LinkedHashSet<>();
        Set<P2pLineId> classifiedLineIds = new LinkedHashSet<>();
        for (P2pServiceCentreLineDemandSnapshot serviceCentre : serviceCentres) {
            if (!serviceCentreIds.add(serviceCentre.serviceCentreId())) {
                throw new IllegalArgumentException("serviceCentre IDs must be distinct");
            }
            if (!serviceCentre.deadline().evaluatedAt().equals(evaluatedAt)) {
                throw new IllegalArgumentException("all deadlines must match evaluatedAt");
            }
            List<P2pLineId> ownedLineIds = java.util.stream.Stream.concat(
                    serviceCentre.feedingOwnedLineIds().stream(),
                    serviceCentre.drainingSurplusLineIds().stream()).toList();
            if (!configuredLineIds.containsAll(ownedLineIds)) {
                throw new IllegalArgumentException(
                        "classified owner lines must be configured allocation lines");
            }
            for (P2pLineId lineId : ownedLineIds) {
                if (!classifiedLineIds.add(lineId)) {
                    throw new IllegalArgumentException(
                            "a configured line must not be classified for multiple owners");
                }
            }
        }
        serviceCentres = List.copyOf(serviceCentres);
        if (issues == null || issues.stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException("issues must not be null or contain null");
        }
        issues = List.copyOf(issues);
    }

    public Optional<P2pServiceCentreLineDemandSnapshot> find(String serviceCentreId) {
        String normalizedId = requireValue(serviceCentreId);
        return serviceCentres.stream()
                .filter(serviceCentre -> serviceCentre.serviceCentreId().equals(normalizedId))
                .findFirst();
    }

    public P2pServiceCentreLineDemandSnapshot require(String serviceCentreId) {
        String normalizedId = requireValue(serviceCentreId);
        return find(normalizedId).orElseThrow(() -> new IllegalArgumentException(
                "Unknown serviceCentreId: " + normalizedId));
    }

    public int totalDesiredLines() {
        return serviceCentres.stream()
                .mapToInt(P2pServiceCentreLineDemandSnapshot::desiredLines)
                .sum();
    }

    public int totalUnmetRequiredLines() {
        return serviceCentres.stream()
                .mapToInt(P2pServiceCentreLineDemandSnapshot::unmetRequiredLines)
                .sum();
    }

    public boolean infeasible() {
        return !issues.isEmpty();
    }

    private static List<P2pLineId> distinctCopy(List<P2pLineId> values, String fieldName) {
        if (values == null || values.isEmpty()
                || values.stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException(fieldName + " must not be null, empty, or contain null");
        }
        Set<P2pLineId> distinct = new LinkedHashSet<>(values);
        if (distinct.size() != values.size()) {
            throw new IllegalArgumentException(fieldName + " must contain distinct values");
        }
        return List.copyOf(values);
    }

    private static String requireValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("serviceCentreId must not be blank");
        }
        return value.trim();
    }
}
