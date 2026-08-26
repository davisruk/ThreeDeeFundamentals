package online.davisfamily.warehouse.sim.dsp.av02;

import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.osr.release.OperationalPhysicalToteReleaseCommand;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteSource;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pPhysicalToteAssignment;

public record ReleasePhysicalToteFromAv02Command(
        PhysicalToteId physicalToteId,
        OrderSheetKey orderSheetKey,
        String serviceCentreId,
        String releaseTargetId,
        Optional<P2pPhysicalToteAssignment> proposedP2pAssignment)
        implements OperationalPhysicalToteReleaseCommand {

    public ReleasePhysicalToteFromAv02Command(
            PhysicalToteId physicalToteId,
            OrderSheetKey orderSheetKey,
            String serviceCentreId,
            String releaseTargetId) {
        this(
                physicalToteId,
                orderSheetKey,
                serviceCentreId,
                releaseTargetId,
                Optional.empty());
    }

    public ReleasePhysicalToteFromAv02Command {
        if (physicalToteId == null) {
            throw new IllegalArgumentException("physicalToteId must not be null");
        }
        if (orderSheetKey == null) {
            throw new IllegalArgumentException("orderSheetKey must not be null");
        }
        String normalizedServiceCentreId = requireValue(serviceCentreId, "serviceCentreId");
        serviceCentreId = normalizedServiceCentreId;
        releaseTargetId = requireValue(releaseTargetId, "releaseTargetId");
        if (proposedP2pAssignment == null) {
            throw new IllegalArgumentException("proposedP2pAssignment must not be null");
        }
        proposedP2pAssignment.ifPresent(assignment -> {
            if (!assignment.physicalToteId().equals(physicalToteId)
                    || !assignment.serviceCentreId().equals(normalizedServiceCentreId)) {
                throw new IllegalArgumentException(
                        "proposed P2P assignment must match command physical identity and service centre");
            }
        });
    }

    @Override
    public OperationalPhysicalToteSource source() {
        return OperationalPhysicalToteSource.AV02;
    }

    private static String requireValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
