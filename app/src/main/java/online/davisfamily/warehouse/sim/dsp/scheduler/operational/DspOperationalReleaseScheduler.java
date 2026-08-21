package online.davisfamily.warehouse.sim.dsp.scheduler.operational;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.osr.release.ReleasePhysicalToteFromOsrCommand;

public final class DspOperationalReleaseScheduler {
    private final OperationalDependencyReadinessPolicy dependencyReadinessPolicy;
    private final OperationalRouteEntryAdmissionPolicy routeEntryAdmissionPolicy;
    private final OperationalCandidateRankingPolicy candidateRankingPolicy;

    public DspOperationalReleaseScheduler() {
        this(
                new OperationalDependencyReadinessPolicy(),
                new OperationalRouteEntryAdmissionPolicy(),
                new PharmacyGroupedSourceSequenceRankingPolicy());
    }

    public DspOperationalReleaseScheduler(
            OperationalDependencyReadinessPolicy dependencyReadinessPolicy,
            OperationalRouteEntryAdmissionPolicy routeEntryAdmissionPolicy,
            OperationalCandidateRankingPolicy candidateRankingPolicy) {
        if (dependencyReadinessPolicy == null) {
            throw new IllegalArgumentException("dependencyReadinessPolicy must not be null");
        }
        if (routeEntryAdmissionPolicy == null) {
            throw new IllegalArgumentException("routeEntryAdmissionPolicy must not be null");
        }
        if (candidateRankingPolicy == null) {
            throw new IllegalArgumentException("candidateRankingPolicy must not be null");
        }
        this.dependencyReadinessPolicy = dependencyReadinessPolicy;
        this.routeEntryAdmissionPolicy = routeEntryAdmissionPolicy;
        this.candidateRankingPolicy = candidateRankingPolicy;
    }

    public DspOperationalReleaseEvaluation evaluate(
            DspOperationalReleaseSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }

        List<OperationalBlockedCandidate> blockedCandidates = new ArrayList<>();
        List<OperationalReleaseSelection> eligibleCandidates = new ArrayList<>();
        for (DspOperationalReleaseCandidate candidate : snapshot.candidates()) {
            List<OperationalReleaseBlock> dependencyBlocks =
                    dependencyReadinessPolicy.findBlocks(candidate, snapshot);
            if (!dependencyBlocks.isEmpty()) {
                blockedCandidates.add(blockedCandidate(candidate, dependencyBlocks));
                continue;
            }

            OperationalRouteEntryEvaluation routeEntryEvaluation =
                    routeEntryAdmissionPolicy.evaluate(candidate, snapshot);
            if (!routeEntryEvaluation.blocks().isEmpty()) {
                blockedCandidates.add(blockedCandidate(
                        candidate, routeEntryEvaluation.blocks()));
                continue;
            }
            OperationalRouteEntry routeEntry = routeEntryEvaluation.routeEntry()
                    .orElseThrow(() -> new IllegalStateException(
                            "Eligible route-entry evaluation must contain a route entry"));
            eligibleCandidates.add(new OperationalReleaseSelection(candidate, routeEntry));
        }

        List<OperationalReleaseSelection> rankedCandidates = candidateRankingPolicy.rank(
                eligibleCandidates, snapshot);
        if (rankedCandidates.isEmpty()) {
            return new DspOperationalReleaseEvaluation(
                    Optional.empty(), blockedCandidates);
        }

        OperationalReleaseSelection selected = rankedCandidates.get(0);
        DspOperationalReleaseCandidate selectedCandidate = selected.candidate();
        ReleasePhysicalToteFromOsrCommand command = new ReleasePhysicalToteFromOsrCommand(
                selectedCandidate.physicalCandidate().physicalToteId(),
                selectedCandidate.physicalCandidate().orderSheetKey(),
                selectedCandidate.physicalCandidate().serviceCentreId(),
                selected.routeEntry().targetId());
        DspOperationalReleaseDecision decision = new DspOperationalReleaseDecision(
                selectedCandidate,
                selected.routeEntry(),
                command);
        return new DspOperationalReleaseEvaluation(
                Optional.of(decision), blockedCandidates);
    }

    private static OperationalBlockedCandidate blockedCandidate(
            DspOperationalReleaseCandidate candidate,
            List<OperationalReleaseBlock> blocks) {
        return new OperationalBlockedCandidate(
                candidate.physicalCandidate().physicalToteId(),
                candidate.physicalCandidate().orderSheetKey(),
                blocks);
    }
}
