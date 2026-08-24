package online.davisfamily.warehouse.sim.dsp.p2p.allocation;

import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineAllocationBlockReason;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineAllocationDecision;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineAllocationPolicy;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineAllocationRequest;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineLeaseSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pPhysicalToteAssignment;

public final class DeadlineAwareElasticStickyP2pLineAllocationPolicy
        implements P2pLineAllocationPolicy {

    private static final int MATCHING_PHARMACY_OWNER_TIER = 0;
    private static final int SAME_OWNER_TIER = 1;
    private static final int QUIESCENT_UNLEASED_TIER = 2;

    @Override
    public P2pLineAllocationDecision allocate(P2pLineAllocationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        P2pElasticAllocationSnapshot allocation = request.elasticAllocation()
                .orElseThrow(() -> new IllegalArgumentException(
                        "elastic allocation policy requires an elastic allocation snapshot"));
        P2pServiceCentreLineDemandSnapshot demand = allocation
                .find(request.serviceCentreId())
                .orElse(null);
        if (demand == null || !hasBudget(demand)) {
            return P2pLineAllocationDecision.blocked(
                    P2pLineAllocationBlockReason.NO_ELASTIC_LINE_BUDGET);
        }

        Set<P2pLineId> feedingLineIds = Set.copyOf(demand.feedingOwnedLineIds());
        P2pLineLeaseSnapshot selected = null;
        int selectedTier = Integer.MAX_VALUE;
        for (P2pLineLeaseSnapshot line : request.lineCatalog().lines()) {
            int tier = compatibilityTier(line, request, demand, feedingLineIds);
            if (tier < selectedTier) {
                selected = line;
                selectedTier = tier;
            }
        }
        if (selected == null) {
            return P2pLineAllocationDecision.blocked(
                    P2pLineAllocationBlockReason.NO_COMPATIBLE_P2P_LINE);
        }
        return P2pLineAllocationDecision.assigned(
                new P2pPhysicalToteAssignment(
                        request.physicalToteId(),
                        request.serviceCentreId(),
                        selected.definition().lineId(),
                        selected.definition().destination()),
                selectedTier == MATCHING_PHARMACY_OWNER_TIER);
    }

    private static boolean hasBudget(P2pServiceCentreLineDemandSnapshot demand) {
        return !demand.feedingOwnedLineIds().isEmpty() || demand.additionalLineSlots() > 0;
    }

    private static int compatibilityTier(
            P2pLineLeaseSnapshot line,
            P2pLineAllocationRequest request,
            P2pServiceCentreLineDemandSnapshot demand,
            Set<P2pLineId> feedingLineIds) {
        if (request.p2pFirstRouteStation()
                && !request.routeAdmissible(line.definition().destination())) {
            return Integer.MAX_VALUE;
        }
        if (line.leased()) {
            if (!line.serviceCentreId().orElseThrow().equals(request.serviceCentreId())
                    || !feedingLineIds.contains(line.definition().lineId())) {
                return Integer.MAX_VALUE;
            }
            boolean matchingPharmacy = line.activePharmacyId()
                    .filter(request::includesPharmacy)
                    .isPresent();
            return matchingPharmacy ? MATCHING_PHARMACY_OWNER_TIER : SAME_OWNER_TIER;
        }
        return demand.additionalLineSlots() > 0 && line.activity().quiescent()
                ? QUIESCENT_UNLEASED_TIER
                : Integer.MAX_VALUE;
    }
}
