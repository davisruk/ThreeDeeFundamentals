package online.davisfamily.warehouse.sim.dsp.p2p.lease;

import java.util.Optional;

@FunctionalInterface
public interface P2pLeaseRetentionPolicy {
    Optional<P2pLeaseRetentionDecision> firstTransition(
            P2pLineLeaseCatalogSnapshot leases,
            P2pServiceCentreWorkSnapshot work);
}
