package online.davisfamily.warehouse.sim.dsp.p2p.lease;

public final class StickyP2pLineAllocationPolicy implements P2pLineAllocationPolicy {
    private static final int MATCHING_PHARMACY_OWNER_TIER = 0;
    private static final int SAME_OWNER_TIER = 1;
    private static final int QUIESCENT_UNLEASED_TIER = 2;

    @Override
    public P2pLineAllocationDecision allocate(P2pLineAllocationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }

        P2pLineLeaseSnapshot selected = null;
        int selectedTier = Integer.MAX_VALUE;
        for (P2pLineLeaseSnapshot line : request.lineCatalog().lines()) {
            int tier = compatibilityTier(line, request);
            if (tier < selectedTier) {
                selected = line;
                selectedTier = tier;
            }
        }

        if (selected == null) {
            return P2pLineAllocationDecision.blocked(
                    P2pLineAllocationBlockReason.NO_COMPATIBLE_P2P_LINE);
        }
        boolean pharmacyAffinity = selectedTier == MATCHING_PHARMACY_OWNER_TIER;
        return P2pLineAllocationDecision.assigned(
                new P2pPhysicalToteAssignment(
                        request.physicalToteId(),
                        request.serviceCentreId(),
                        selected.definition().lineId(),
                        selected.definition().destination()),
                pharmacyAffinity);
    }

    private static int compatibilityTier(
            P2pLineLeaseSnapshot line,
            P2pLineAllocationRequest request) {
        if (request.p2pFirstRouteStation()
                && !request.routeAdmissible(line.definition().destination())) {
            return Integer.MAX_VALUE;
        }
        if (line.leased()) {
            if (!line.serviceCentreId().orElseThrow().equals(request.serviceCentreId())) {
                return Integer.MAX_VALUE;
            }
            boolean matchingPharmacy = line.activePharmacyId()
                    .filter(request::includesPharmacy)
                    .isPresent();
            return matchingPharmacy ? MATCHING_PHARMACY_OWNER_TIER : SAME_OWNER_TIER;
        }
        return line.activity().quiescent() ? QUIESCENT_UNLEASED_TIER : Integer.MAX_VALUE;
    }
}
