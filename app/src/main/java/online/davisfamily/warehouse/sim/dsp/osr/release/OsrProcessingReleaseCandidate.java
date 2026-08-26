package online.davisfamily.warehouse.sim.dsp.osr.release;

import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteSource;

public record OsrProcessingReleaseCandidate(
        PhysicalToteId physicalToteId,
        OrderSheetKey orderSheetKey,
        OrderType orderType,
        String serviceCentreId,
        long sourceSequenceNumber,
        OsrProcessingReleaseAvailability availability,
        Optional<PhysicalToteId> blockingPhysicalToteId)
        implements OperationalPhysicalToteCandidate {

    public OsrProcessingReleaseCandidate {
        if (physicalToteId == null) {
            throw new IllegalArgumentException("physicalToteId must not be null");
        }
        if (orderSheetKey == null) {
            throw new IllegalArgumentException("orderSheetKey must not be null");
        }
        if (orderType == null) {
            throw new IllegalArgumentException("orderType must not be null");
        }
        if (orderType == OrderType.EMPTY) {
            throw new IllegalArgumentException("EMPTY orders cannot be physical OSR release candidates");
        }
        if (serviceCentreId == null || serviceCentreId.isBlank()) {
            throw new IllegalArgumentException("serviceCentreId must not be blank");
        }
        serviceCentreId = serviceCentreId.trim();
        if (sourceSequenceNumber < 0) {
            throw new IllegalArgumentException("sourceSequenceNumber must be >= 0");
        }
        if (availability == null) {
            throw new IllegalArgumentException("availability must not be null");
        }
        if (blockingPhysicalToteId == null) {
            throw new IllegalArgumentException("blockingPhysicalToteId must not be null");
        }
        if (availability == OsrProcessingReleaseAvailability.AVAILABLE
                && blockingPhysicalToteId.isPresent()) {
            throw new IllegalArgumentException(
                    "AVAILABLE candidate must not have a blocking physical tote");
        }
        if (availability == OsrProcessingReleaseAvailability.BLOCKED_BY_ACTIVE_SHEET_ASSIGNMENT) {
            PhysicalToteId blockingToteId = blockingPhysicalToteId.orElseThrow(
                    () -> new IllegalArgumentException(
                            "Blocked candidate must have a blocking physical tote"));
            if (physicalToteId.equals(blockingToteId)) {
                throw new IllegalArgumentException(
                        "Candidate physical tote cannot block its own release");
            }
        }
    }

    @Override
    public OperationalPhysicalToteSource source() {
        return OperationalPhysicalToteSource.OSR;
    }
}
