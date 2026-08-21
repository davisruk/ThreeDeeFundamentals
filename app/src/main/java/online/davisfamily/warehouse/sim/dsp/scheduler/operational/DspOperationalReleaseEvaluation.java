package online.davisfamily.warehouse.sim.dsp.scheduler.operational;

import java.util.List;
import java.util.Optional;

public record DspOperationalReleaseEvaluation(
        Optional<DspOperationalReleaseDecision> releaseDecision,
        List<OperationalBlockedCandidate> blockedCandidates) {

    public DspOperationalReleaseEvaluation {
        if (releaseDecision == null) {
            throw new IllegalArgumentException("releaseDecision must not be null");
        }
        if (blockedCandidates == null) {
            throw new IllegalArgumentException("blockedCandidates must not be null");
        }
        if (blockedCandidates.stream().anyMatch(candidate -> candidate == null)) {
            throw new IllegalArgumentException(
                    "blockedCandidates must not contain null elements");
        }
        blockedCandidates = List.copyOf(blockedCandidates);
    }
}
