package online.davisfamily.warehouse.sim.dsp.p2p.allocation;

import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineAllocationBlockReason;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineAllocationDecision;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineAllocationPolicy;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineAllocationRequest;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineLeaseSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pPhysicalToteAssignment;
import online.davisfamily.warehouse.sim.dsp.scheduler.policy.DspSchedulerPolicy;

public final class WholeServiceCentreP2pLineAllocationPolicy implements P2pLineAllocationPolicy {
    @Override
    public String profileId() {
        return DspSchedulerPolicy.WHOLE_SERVICE_CENTRE_DRAINED_HANDOVER.name();
    }

    @Override
    public P2pLineAllocationDecision allocate(P2pLineAllocationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        var allocation = request.elasticAllocation().orElseThrow(() ->
                new IllegalArgumentException("whole policy requires allocation metadata"));
        if (!profileId().equals(allocation.profileId())) {
            throw new IllegalArgumentException("allocation profile must match whole policy");
        }
        var policy = allocation.wholeServiceCentrePolicy().orElseThrow();
        if (!policy.eligibleServiceCentreId().filter(request.serviceCentreId()::equals).isPresent()) {
            return P2pLineAllocationDecision.blocked(P2pLineAllocationBlockReason.NO_ELASTIC_LINE_BUDGET);
        }
        P2pLineLeaseSnapshot selected = null;
        int selectedTier = Integer.MAX_VALUE;
        int selectedCount = Integer.MAX_VALUE;
        boolean selectedAffinity = false;
        for (var line : request.lineCatalog().lines()) {
            var lineId = line.definition().lineId();
            if (!request.bagCorrelationsCompatibleWith(lineId)
                    || (request.p2pFirstRouteStation()
                            && !request.routeAdmissible(line.definition().destination()))) {
                continue;
            }
            if (line.leased() ? !line.serviceCentreId().filter(request.serviceCentreId()::equals).isPresent()
                    : !line.activity().quiescent() || !policy.availableUnleasedLineIds().contains(lineId)) {
                continue;
            }
            int tier = line.leased() ? 1 : 0;
            int count = policy.releases().committedToteCount(request.serviceCentreId(), lineId);
            boolean affinity = line.activePharmacyId().filter(request::includesPharmacy).isPresent();
            if (tier < selectedTier || (tier == selectedTier
                    && (count < selectedCount || (count == selectedCount && affinity && !selectedAffinity)))) {
                selected = line;
                selectedTier = tier;
                selectedCount = count;
                selectedAffinity = affinity;
            }
        }
        if (selected == null) {
            return P2pLineAllocationDecision.blocked(P2pLineAllocationBlockReason.NO_COMPATIBLE_P2P_LINE);
        }
        return P2pLineAllocationDecision.assigned(new P2pPhysicalToteAssignment(
                request.physicalToteId(), request.serviceCentreId(), selected.definition().lineId(),
                selected.definition().destination()), selectedAffinity);
    }
}
