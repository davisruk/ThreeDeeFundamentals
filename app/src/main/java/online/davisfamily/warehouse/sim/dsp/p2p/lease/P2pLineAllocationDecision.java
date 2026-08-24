package online.davisfamily.warehouse.sim.dsp.p2p.lease;

import java.util.Optional;

public record P2pLineAllocationDecision(
        Optional<P2pPhysicalToteAssignment> assignment,
        boolean activePharmacyAffinity,
        Optional<P2pLineAllocationBlockReason> blockReason) {

    public P2pLineAllocationDecision {
        if (assignment == null) {
            throw new IllegalArgumentException("assignment must not be null");
        }
        if (blockReason == null) {
            throw new IllegalArgumentException("blockReason must not be null");
        }
        if (assignment.isPresent() == blockReason.isPresent()) {
            throw new IllegalArgumentException(
                    "decision must contain either an assignment or a block reason");
        }
        if (assignment.isEmpty() && activePharmacyAffinity) {
            throw new IllegalArgumentException("a blocked decision cannot have pharmacy affinity");
        }
    }

    public static P2pLineAllocationDecision assigned(
            P2pPhysicalToteAssignment assignment,
            boolean activePharmacyAffinity) {
        if (assignment == null) {
            throw new IllegalArgumentException("assignment must not be null");
        }
        return new P2pLineAllocationDecision(
                Optional.of(assignment), activePharmacyAffinity, Optional.empty());
    }

    public static P2pLineAllocationDecision blocked(
            P2pLineAllocationBlockReason blockReason) {
        if (blockReason == null) {
            throw new IllegalArgumentException("blockReason must not be null");
        }
        return new P2pLineAllocationDecision(
                Optional.empty(), false, Optional.of(blockReason));
    }

    public boolean allocated() {
        return assignment.isPresent();
    }
}
