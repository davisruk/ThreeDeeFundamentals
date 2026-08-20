package online.davisfamily.warehouse.sim.dsp.osr.release;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteAssignment;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleSnapshot;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleState;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRecord;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRole;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.osr.OsrInventorySnapshot;

public final class OsrProcessingReleaseSnapshotFactory {

    public OsrProcessingReleaseSnapshot create(
            OsrInventorySnapshot inventorySnapshot,
            PhysicalToteLifecycleSnapshot lifecycleSnapshot) {
        if (inventorySnapshot == null) {
            throw new IllegalArgumentException("inventorySnapshot must not be null");
        }
        if (lifecycleSnapshot == null) {
            throw new IllegalArgumentException("lifecycleSnapshot must not be null");
        }

        List<OsrProcessingReleaseCandidate> candidates = new ArrayList<>();
        for (InboundToteManifest manifest : inventorySnapshot.storedTotes()) {
            PhysicalToteId physicalToteId = manifest.physicalToteId();
            PhysicalToteRecord tote = lifecycleSnapshot.totes().get(physicalToteId);
            if (tote == null) {
                throw new IllegalArgumentException(
                        "Stored OSR tote is missing from lifecycle state: "
                                + physicalToteId.value());
            }
            if (tote.role() != PhysicalToteRole.INBOUND_PACK
                    || tote.state() != PhysicalToteLifecycleState.INBOUND_PACK_TOTE) {
                throw new IllegalArgumentException(
                        "Stored OSR tote must be an inactive inbound pack tote: "
                                + physicalToteId.value());
            }

            List<PhysicalToteAssignment> physicalAssignments =
                    lifecycleSnapshot.activeAssignmentsFor(physicalToteId);
            if (!physicalAssignments.isEmpty()) {
                throw new IllegalArgumentException(
                        "Stored OSR tote already has an active lifecycle assignment: "
                                + physicalToteId.value());
            }

            Optional<PhysicalToteAssignment> sheetAssignment =
                    lifecycleSnapshot.activeAssignmentFor(manifest.orderSheetKey());
            OsrProcessingReleaseAvailability availability =
                    OsrProcessingReleaseAvailability.AVAILABLE;
            Optional<PhysicalToteId> blockingPhysicalToteId = Optional.empty();
            if (sheetAssignment.isPresent()) {
                PhysicalToteId assignedPhysicalToteId = sheetAssignment.orElseThrow()
                        .physicalToteId();
                if (assignedPhysicalToteId.equals(physicalToteId)) {
                    throw new IllegalArgumentException(
                            "Stored OSR tote is its own active sheet-assignment blocker: "
                                    + physicalToteId.value());
                }
                availability =
                        OsrProcessingReleaseAvailability.BLOCKED_BY_ACTIVE_SHEET_ASSIGNMENT;
                blockingPhysicalToteId = Optional.of(assignedPhysicalToteId);
            }

            candidates.add(new OsrProcessingReleaseCandidate(
                    physicalToteId,
                    manifest.orderSheetKey(),
                    manifest.orderType(),
                    manifest.serviceCentreId(),
                    manifest.sourceSequenceNumber(),
                    availability,
                    blockingPhysicalToteId));
        }
        return new OsrProcessingReleaseSnapshot(candidates);
    }
}
