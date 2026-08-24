package online.davisfamily.warehouse.sim.dsp.p2p.arrival;

import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;

public record P2pArrivalConsumerControllerSnapshot(
        OperationalRouteDestination sourceDestination,
        int sourceCapacity,
        int sourceOccupancy,
        OperationalRouteDestination targetDestination,
        int targetCapacity,
        int targetOccupancy,
        String terminalSegmentLabel,
        String tipperEntrySegmentLabel,
        Optional<PhysicalToteId> headPhysicalToteId,
        Optional<PhysicalToteId> lastAcceptedPhysicalToteId,
        Optional<PhysicalToteId> blockedPhysicalToteId,
        String blockedReason,
        String policyReason,
        long successfulAcceptanceCount) {

    public P2pArrivalConsumerControllerSnapshot {
        requireP2pDestination(sourceDestination, "sourceDestination");
        requireP2pDestination(targetDestination, "targetDestination");
        if (!sourceDestination.equals(targetDestination)) {
            throw new IllegalArgumentException("source and target destinations must match");
        }
        requireOccupancy("source", sourceCapacity, sourceOccupancy);
        requireOccupancy("target", targetCapacity, targetOccupancy);
        terminalSegmentLabel = requireTrimmed(terminalSegmentLabel, "terminalSegmentLabel");
        tipperEntrySegmentLabel = requireTrimmed(
                tipperEntrySegmentLabel,
                "tipperEntrySegmentLabel");
        if (headPhysicalToteId == null
                || lastAcceptedPhysicalToteId == null
                || blockedPhysicalToteId == null) {
            throw new IllegalArgumentException("physical tote optionals must not be null");
        }
        blockedReason = blockedReason == null ? "" : blockedReason.trim();
        policyReason = policyReason == null ? "" : policyReason.trim();
        if (blockedPhysicalToteId.isPresent() != !blockedReason.isEmpty()) {
            throw new IllegalArgumentException(
                    "blocked physical tote and reason must both be present or both be absent");
        }
        if (!policyReason.isEmpty()
                && !P2pArrivalConsumerController.ADMISSION_DEFERRED.equals(blockedReason)) {
            throw new IllegalArgumentException(
                    "policyReason is only valid for deferred admission");
        }
        if (P2pArrivalConsumerController.ADMISSION_DEFERRED.equals(blockedReason)
                && policyReason.isEmpty()) {
            throw new IllegalArgumentException(
                    "deferred admission must retain its policy reason");
        }
        if (successfulAcceptanceCount < 0) {
            throw new IllegalArgumentException("successfulAcceptanceCount must be >= 0");
        }
        if ((successfulAcceptanceCount == 0) != lastAcceptedPhysicalToteId.isEmpty()) {
            throw new IllegalArgumentException(
                    "last accepted identity must be present exactly when count is positive");
        }
    }

    public boolean blocked() {
        return blockedPhysicalToteId.isPresent();
    }

    private static void requireP2pDestination(
            OperationalRouteDestination destination,
            String fieldName) {
        if (destination == null || destination.stationType() != StationType.P2P) {
            throw new IllegalArgumentException(fieldName + " must identify a P2P station");
        }
    }

    private static void requireOccupancy(String owner, int capacity, int occupancy) {
        if (capacity < 0) {
            throw new IllegalArgumentException(owner + " capacity must be >= 0");
        }
        if (occupancy < 0 || occupancy > capacity) {
            throw new IllegalArgumentException(
                    owner + " occupancy must be between zero and capacity");
        }
    }

    private static String requireTrimmed(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
