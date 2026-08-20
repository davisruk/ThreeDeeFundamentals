package online.davisfamily.warehouse.sim.dsp.osr.release;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

public record OsrProcessingReleaseSnapshot(
        List<OsrProcessingReleaseCandidate> candidates) {

    public OsrProcessingReleaseSnapshot {
        if (candidates == null) {
            throw new IllegalArgumentException("candidates must not be null");
        }
        Set<PhysicalToteId> physicalToteIds = new LinkedHashSet<>();
        for (OsrProcessingReleaseCandidate candidate : candidates) {
            if (candidate == null) {
                throw new IllegalArgumentException("candidates must not contain null");
            }
            if (!physicalToteIds.add(candidate.physicalToteId())) {
                throw new IllegalArgumentException(
                        "Duplicate physical release candidate: "
                                + candidate.physicalToteId().value());
            }
        }
        candidates = List.copyOf(candidates);
    }

    public List<OsrProcessingReleaseCandidate> availableCandidates() {
        return candidates.stream()
                .filter(candidate -> candidate.availability()
                        == OsrProcessingReleaseAvailability.AVAILABLE)
                .toList();
    }

    public Optional<OsrProcessingReleaseCandidate> findByPhysicalToteId(
            PhysicalToteId physicalToteId) {
        if (physicalToteId == null) {
            throw new IllegalArgumentException("physicalToteId must not be null");
        }
        return candidates.stream()
                .filter(candidate -> candidate.physicalToteId().equals(physicalToteId))
                .findFirst();
    }
}
