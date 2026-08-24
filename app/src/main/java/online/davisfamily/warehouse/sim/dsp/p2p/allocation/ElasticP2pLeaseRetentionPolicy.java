package online.davisfamily.warehouse.sim.dsp.p2p.allocation;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLeaseRetentionAction;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLeaseRetentionDecision;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLeaseRetentionPolicy;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineLeaseCatalogSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineLeaseSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pServiceCentreWorkSnapshot;

public final class ElasticP2pLeaseRetentionPolicy implements P2pLeaseRetentionPolicy {

    private final Supplier<P2pElasticAllocationSnapshot> allocationSupplier;

    public ElasticP2pLeaseRetentionPolicy(
            Supplier<P2pElasticAllocationSnapshot> allocationSupplier) {
        if (allocationSupplier == null) {
            throw new IllegalArgumentException("allocationSupplier must not be null");
        }
        this.allocationSupplier = allocationSupplier;
    }

    @Override
    public Optional<P2pLeaseRetentionDecision> firstTransition(
            P2pLineLeaseCatalogSnapshot leases,
            P2pServiceCentreWorkSnapshot work) {
        if (leases == null || work == null) {
            throw new IllegalArgumentException("lease retention inputs must not be null");
        }
        P2pElasticAllocationSnapshot allocation = allocationSupplier.get();
        if (allocation == null) {
            throw new IllegalStateException("allocationSupplier returned null");
        }
        List<P2pLineId> lineIds = leases.lines().stream()
                .map(line -> line.definition().lineId())
                .toList();
        if (!allocation.configuredLineIds().equals(lineIds)) {
            throw new IllegalArgumentException(
                    "elastic allocation lines must match lease retention catalog");
        }

        for (P2pLineLeaseSnapshot line : leases.lines()) {
            if (!line.leased()) {
                continue;
            }
            String owner = line.serviceCentreId().orElseThrow();
            if (!work.hasRemainingWork(owner)) {
                Optional<P2pLeaseRetentionDecision> completion =
                        completionTransition(line, owner);
                if (completion.isPresent()) {
                    return completion;
                }
                continue;
            }

            Optional<P2pServiceCentreLineDemandSnapshot> demand = allocation.find(owner);
            if (demand.isEmpty()
                    || !demand.orElseThrow().drainingSurplusLineIds()
                            .contains(line.definition().lineId())) {
                continue;
            }
            if (hasPinnedRemainingAssignment(line, work.remainingToteIds(owner))
                    || !line.activity().processingDrained()) {
                continue;
            }
            P2pLeaseRetentionAction action = line.activity().openOutboundTote().isPresent()
                    ? P2pLeaseRetentionAction.CLOSE_FOR_SERVICE_CENTRE_CHANGE
                    : P2pLeaseRetentionAction.RELEASE_LEASE;
            return Optional.of(new P2pLeaseRetentionDecision(
                    line.definition().lineId(), owner, action));
        }
        return Optional.empty();
    }

    private static Optional<P2pLeaseRetentionDecision> completionTransition(
            P2pLineLeaseSnapshot line,
            String owner) {
        if (!line.activity().processingDrained()) {
            return Optional.empty();
        }
        P2pLeaseRetentionAction action = line.activity().openOutboundTote().isPresent()
                ? P2pLeaseRetentionAction.CLOSE_FOR_APPLICABLE_WORK_COMPLETION
                : P2pLeaseRetentionAction.RELEASE_LEASE;
        return Optional.of(new P2pLeaseRetentionDecision(
                line.definition().lineId(), owner, action));
    }

    private static boolean hasPinnedRemainingAssignment(
            P2pLineLeaseSnapshot line,
            List<PhysicalToteId> remainingToteIds) {
        Set<PhysicalToteId> remaining = remainingToteIds.stream().collect(Collectors.toSet());
        return line.physicalAssignments().stream()
                .map(assignment -> assignment.physicalToteId())
                .anyMatch(remaining::contains);
    }
}
