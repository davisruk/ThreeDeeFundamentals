package online.davisfamily.warehouse.sim.dsp.p2p.lease;

import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.osr.release.OperationalPhysicalToteReleaseCommand;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteSource;

/**
 * Source-neutral identity used while validating a proposed P2P lease commit.
 */
public record P2pReleaseAssignmentRequest(
        PhysicalToteId physicalToteId,
        OrderSheetKey orderSheetKey,
        String serviceCentreId,
        String releaseTargetId,
        OperationalPhysicalToteSource source,
        Optional<P2pPhysicalToteAssignment> proposedP2pAssignment) {

    public P2pReleaseAssignmentRequest {
        if (physicalToteId == null) {
            throw new IllegalArgumentException("physicalToteId must not be null");
        }
        if (orderSheetKey == null) {
            throw new IllegalArgumentException("orderSheetKey must not be null");
        }
        final String normalizedServiceCentreId = requireValue(serviceCentreId, "serviceCentreId");
        releaseTargetId = requireValue(releaseTargetId, "releaseTargetId");
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        if (proposedP2pAssignment == null) {
            throw new IllegalArgumentException("proposedP2pAssignment must not be null");
        }
        proposedP2pAssignment.ifPresent(assignment -> {
            if (!assignment.physicalToteId().equals(physicalToteId)
                    || !assignment.serviceCentreId().equals(normalizedServiceCentreId)) {
                throw new IllegalArgumentException(
                        "proposed P2P assignment must match request physical identity and service centre");
            }
        });
        serviceCentreId = normalizedServiceCentreId;
    }

    public static P2pReleaseAssignmentRequest from(
            OperationalPhysicalToteReleaseCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        return new P2pReleaseAssignmentRequest(
                command.physicalToteId(),
                command.orderSheetKey(),
                command.serviceCentreId(),
                command.releaseTargetId(),
                command.source(),
                command.proposedP2pAssignment());
    }

    private static String requireValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
