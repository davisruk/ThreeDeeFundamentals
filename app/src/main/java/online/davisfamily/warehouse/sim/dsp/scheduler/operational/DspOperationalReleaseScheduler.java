package online.davisfamily.warehouse.sim.dsp.scheduler.operational;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.av02.ReleasePhysicalToteFromAv02Command;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.OperationalPhysicalToteReleaseCommand;
import online.davisfamily.warehouse.sim.dsp.osr.release.ReleasePhysicalToteFromOsrCommand;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineAllocationDecision;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineAllocationPolicy;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineAllocationRequest;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pPhysicalToteAssignment;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.StickyP2pLineAllocationPolicy;

public final class DspOperationalReleaseScheduler {
    private final OperationalDependencyReadinessPolicy dependencyReadinessPolicy;
    private final OperationalRouteEntryAdmissionPolicy routeEntryAdmissionPolicy;
    private final OperationalCandidateRankingPolicy candidateRankingPolicy;
    private final P2pLineAllocationPolicy p2pLineAllocationPolicy;
    private final OperationalRouteEntrySelector routeEntrySelector;

    public DspOperationalReleaseScheduler() {
        this(
                new OperationalDependencyReadinessPolicy(),
                new OperationalRouteEntryAdmissionPolicy(),
                new PharmacyGroupedSourceSequenceRankingPolicy(),
                new StickyP2pLineAllocationPolicy());
    }

    public DspOperationalReleaseScheduler(
            OperationalDependencyReadinessPolicy dependencyReadinessPolicy,
            OperationalRouteEntryAdmissionPolicy routeEntryAdmissionPolicy,
            OperationalCandidateRankingPolicy candidateRankingPolicy) {
        this(
                dependencyReadinessPolicy,
                routeEntryAdmissionPolicy,
                candidateRankingPolicy,
                new StickyP2pLineAllocationPolicy());
    }

    public DspOperationalReleaseScheduler(
            OperationalDependencyReadinessPolicy dependencyReadinessPolicy,
            OperationalRouteEntryAdmissionPolicy routeEntryAdmissionPolicy,
            OperationalCandidateRankingPolicy candidateRankingPolicy,
            P2pLineAllocationPolicy p2pLineAllocationPolicy) {
        if (dependencyReadinessPolicy == null) {
            throw new IllegalArgumentException("dependencyReadinessPolicy must not be null");
        }
        if (routeEntryAdmissionPolicy == null) {
            throw new IllegalArgumentException("routeEntryAdmissionPolicy must not be null");
        }
        if (candidateRankingPolicy == null) {
            throw new IllegalArgumentException("candidateRankingPolicy must not be null");
        }
        if (p2pLineAllocationPolicy == null) {
            throw new IllegalArgumentException("p2pLineAllocationPolicy must not be null");
        }
        this.dependencyReadinessPolicy = dependencyReadinessPolicy;
        this.routeEntryAdmissionPolicy = routeEntryAdmissionPolicy;
        this.candidateRankingPolicy = candidateRankingPolicy;
        this.p2pLineAllocationPolicy = p2pLineAllocationPolicy;
        this.routeEntrySelector = new OperationalRouteEntrySelector();
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

            boolean stickyP2pCandidate = snapshot.stickyP2pAllocationEnabled()
                    && candidate.logicalOrderState().routeRequirements().requiresP2p();
            boolean directP2p = stickyP2pCandidate
                    && routeEntrySelector.firstStation(
                            candidate.logicalOrderState().routeRequirements())
                            .filter(stationType -> stationType == StationType.P2P)
                            .isPresent();

            OperationalRouteEntry routeEntry = null;
            if (!directP2p) {
                OperationalRouteEntryEvaluation routeEntryEvaluation =
                        routeEntryAdmissionPolicy.evaluate(candidate, snapshot);
                if (!routeEntryEvaluation.blocks().isEmpty()) {
                    blockedCandidates.add(blockedCandidate(
                            candidate, routeEntryEvaluation.blocks()));
                    continue;
                }
                routeEntry = routeEntryEvaluation.routeEntry()
                        .orElseThrow(() -> new IllegalStateException(
                                "Eligible route-entry evaluation must contain a route entry"));
            }

            Optional<P2pPhysicalToteAssignment> assignment = Optional.empty();
            boolean activePharmacyAffinity = false;
            if (stickyP2pCandidate) {
                P2pLineAllocationDecision allocation = p2pLineAllocationPolicy.allocate(
                        new P2pLineAllocationRequest(
                                candidate.physicalCandidate().physicalToteId(),
                                candidate.physicalCandidate().serviceCentreId(),
                                candidate.pharmacyIds(),
                                directP2p,
                                snapshot.p2pLineLeases(),
                                snapshot.p2pRouteAdmissions(),
                                snapshot.elasticP2pAllocation()));
                if (allocation == null) {
                    throw new IllegalStateException("P2P line allocation policy returned null");
                }
                if (!allocation.allocated()) {
                    blockedCandidates.add(blockedCandidate(
                            candidate,
                            List.of(new OperationalReleaseBlock(
                                    OperationalReleaseBlockType.P2P_LINE_ALLOCATION,
                                    allocation.blockReason().orElseThrow().name()))));
                    continue;
                }
                assignment = allocation.assignment();
                P2pPhysicalToteAssignment proposedAssignment = assignment.orElseThrow();
                var assignedLine = snapshot.p2pLineLeases()
                        .findLine(proposedAssignment.lineId())
                        .orElseThrow(() -> new IllegalStateException(
                                "P2P allocation selected a line absent from the operational snapshot"));
                if (!assignedLine.definition().destination().equals(
                        proposedAssignment.destination())) {
                    throw new IllegalStateException(
                            "P2P allocation destination does not match its snapshot line");
                }
                activePharmacyAffinity = allocation.activePharmacyAffinity();
                if (directP2p) {
                    routeEntry = new OperationalRouteEntry(
                            StationType.P2P,
                            proposedAssignment.destination().targetId());
                }
            }
            eligibleCandidates.add(new OperationalReleaseSelection(
                    candidate,
                    routeEntry,
                    assignment,
                    activePharmacyAffinity));
        }

        List<OperationalReleaseSelection> rankedCandidates = candidateRankingPolicy.rank(
                eligibleCandidates, snapshot);
        if (rankedCandidates.isEmpty()) {
            return new DspOperationalReleaseEvaluation(
                    Optional.empty(), blockedCandidates);
        }

        OperationalReleaseSelection selected = rankedCandidates.get(0);
        DspOperationalReleaseCandidate selectedCandidate = selected.candidate();
        OperationalPhysicalToteReleaseCommand command = physicalReleaseCommand(
                selectedCandidate,
                selected.routeEntry(),
                selected.proposedP2pAssignment());
        DspOperationalReleaseDecision decision = new DspOperationalReleaseDecision(
                selectedCandidate,
                selected.routeEntry(),
                command);
        return new DspOperationalReleaseEvaluation(
                Optional.of(decision), blockedCandidates);
    }

    public String p2pAllocationProfileId() {
        return p2pLineAllocationPolicy.profileId();
    }

    private static OperationalPhysicalToteReleaseCommand physicalReleaseCommand(
            DspOperationalReleaseCandidate candidate,
            OperationalRouteEntry routeEntry,
            Optional<P2pPhysicalToteAssignment> proposedP2pAssignment) {
        PhysicalToteId physicalToteId = candidate.physicalCandidate().physicalToteId();
        OrderSheetKey orderSheetKey = candidate.physicalCandidate().orderSheetKey();
        String serviceCentreId = candidate.physicalCandidate().serviceCentreId();
        String releaseTargetId = routeEntry.targetId();
        return switch (candidate.physicalCandidate().source()) {
            case OSR -> new ReleasePhysicalToteFromOsrCommand(
                    physicalToteId,
                    orderSheetKey,
                    serviceCentreId,
                    releaseTargetId,
                    proposedP2pAssignment);
            case AV02 -> new ReleasePhysicalToteFromAv02Command(
                    physicalToteId,
                    orderSheetKey,
                    serviceCentreId,
                    releaseTargetId,
                    proposedP2pAssignment);
        };
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
