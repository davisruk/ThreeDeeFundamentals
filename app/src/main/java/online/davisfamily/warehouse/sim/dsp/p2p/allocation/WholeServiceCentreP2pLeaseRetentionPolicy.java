package online.davisfamily.warehouse.sim.dsp.p2p.allocation;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLeaseRetentionAction;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLeaseRetentionDecision;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLeaseRetentionPolicy;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineLeaseCatalogSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pPhysicalToteAssignment;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pServiceCentreWorkSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.policy.WholeServiceCentreReleaseSnapshot;

/** Simulation-thread-local projection cache; fresh activity is always checked separately. */
public final class WholeServiceCentreP2pLeaseRetentionPolicy implements P2pLeaseRetentionPolicy {
    private final Supplier<WholeServiceCentreReleaseSnapshot> releasesSupplier;
    private PendingProjection pendingProjection;

    public WholeServiceCentreP2pLeaseRetentionPolicy(
            Supplier<WholeServiceCentreReleaseSnapshot> releasesSupplier) {
        if (releasesSupplier == null) {
            throw new IllegalArgumentException("releasesSupplier must not be null");
        }
        this.releasesSupplier = releasesSupplier;
    }

    @Override
    public Optional<P2pLeaseRetentionDecision> firstTransition(
            P2pLineLeaseCatalogSnapshot leases, P2pServiceCentreWorkSnapshot work) {
        if (leases == null || work == null) {
            throw new IllegalArgumentException("retention inputs must not be null");
        }
        var releases = releasesSupplier.get();
        if (releases == null) {
            throw new IllegalStateException("releasesSupplier returned null");
        }
        for (var line : leases.lines()) {
            line.serviceCentreId().ifPresent(releases::allReleased);
        }
        if (pendingProjection == null || !pendingProjection.matches(leases, work)) {
            pendingProjection = project(leases, work);
        }
        for (var line : leases.lines()) {
            if (!line.leased()) {
                continue;
            }
            String owner = line.serviceCentreId().orElseThrow();
            P2pLineId lineId = line.definition().lineId();
            if (!releases.allReleased(owner)
                    || pendingProjection.counts().get(lineId).getOrDefault(owner, 0) > 0
                    || !line.activity().processingDrained()) {
                continue;
            }
            var action = line.activity().openOutboundTote().isPresent()
                    ? P2pLeaseRetentionAction.CLOSE_FOR_APPLICABLE_WORK_COMPLETION
                    : P2pLeaseRetentionAction.RELEASE_LEASE;
            return Optional.of(new P2pLeaseRetentionDecision(lineId, owner, action));
        }
        return Optional.empty();
    }

    private static PendingProjection project(
            P2pLineLeaseCatalogSnapshot leases, P2pServiceCentreWorkSnapshot work) {
        Map<String, Set<PhysicalToteId>> remaining = new LinkedHashMap<>();
        work.remainingToteIdsByServiceCentre().forEach((owner, ids) ->
                remaining.put(owner, new HashSet<>(ids)));
        Map<P2pLineId, Map<String, Integer>> counts = new LinkedHashMap<>();
        for (var line : leases.lines()) {
            Map<String, Integer> byOwner = new LinkedHashMap<>();
            for (var assignment : line.physicalAssignments()) {
                Set<PhysicalToteId> ids = remaining.get(assignment.serviceCentreId());
                if (ids != null && ids.contains(assignment.physicalToteId())) {
                    byOwner.merge(assignment.serviceCentreId(), 1, Math::addExact);
                }
            }
            counts.put(line.definition().lineId(), Map.copyOf(byOwner));
        }
        return new PendingProjection(work,
                leases.lines().stream().map(line -> line.definition().lineId()).toList(),
                leases.lines().stream().map(line -> line.physicalAssignments()).toList(),
                Map.copyOf(counts));
    }

    private record PendingProjection(
            P2pServiceCentreWorkSnapshot work,
            List<P2pLineId> lineIds,
            List<List<P2pPhysicalToteAssignment>> assignments,
            Map<P2pLineId, Map<String, Integer>> counts) {
        private boolean matches(P2pLineLeaseCatalogSnapshot leases, P2pServiceCentreWorkSnapshot work) {
            if (this.work != work || leases.lines().size() != assignments.size()) {
                return false;
            }
            for (int index = 0; index < assignments.size(); index++) {
                var line = leases.lines().get(index);
                if (!lineIds.get(index).equals(line.definition().lineId())
                        || assignments.get(index) != line.physicalAssignments()) {
                    return false;
                }
            }
            return true;
        }
    }
}
