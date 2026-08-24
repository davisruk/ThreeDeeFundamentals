package online.davisfamily.warehouse.sim.dsp.p2p.lease;

import java.util.Optional;

public final class CompletionOnlyP2pLeaseRetentionPolicy implements P2pLeaseRetentionPolicy {

    @Override
    public Optional<P2pLeaseRetentionDecision> firstTransition(
            P2pLineLeaseCatalogSnapshot leases,
            P2pServiceCentreWorkSnapshot work) {
        if (leases == null || work == null) {
            throw new IllegalArgumentException("lease retention inputs must not be null");
        }
        for (P2pLineLeaseSnapshot line : leases.lines()) {
            if (!line.leased()) {
                continue;
            }
            String owner = line.serviceCentreId().orElseThrow();
            if (work.hasRemainingWork(owner) || !line.activity().processingDrained()) {
                continue;
            }
            P2pLeaseRetentionAction action = line.activity().openOutboundTote().isPresent()
                    ? P2pLeaseRetentionAction.CLOSE_FOR_APPLICABLE_WORK_COMPLETION
                    : P2pLeaseRetentionAction.RELEASE_LEASE;
            return Optional.of(new P2pLeaseRetentionDecision(
                    line.definition().lineId(), owner, action));
        }
        return Optional.empty();
    }
}
