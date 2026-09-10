package online.davisfamily.warehouse.sim.dsp.io;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

/** Records the deterministic DSP identity assigned to a reused inbound carrier barcode. */
public record InboundToteIdSubstitution(
        PhysicalToteId sourcePhysicalToteId,
        PhysicalToteId substitutedPhysicalToteId,
        int occurrenceNumber,
        long sourceSequenceNumber) {

    public InboundToteIdSubstitution {
        if (sourcePhysicalToteId == null) {
            throw new IllegalArgumentException("sourcePhysicalToteId must not be null");
        }
        if (substitutedPhysicalToteId == null) {
            throw new IllegalArgumentException("substitutedPhysicalToteId must not be null");
        }
        if (sourcePhysicalToteId.equals(substitutedPhysicalToteId)) {
            throw new IllegalArgumentException(
                    "sourcePhysicalToteId and substitutedPhysicalToteId must differ");
        }
        if (occurrenceNumber < 2) {
            throw new IllegalArgumentException("occurrenceNumber must be >= 2");
        }
        if (sourceSequenceNumber < 0) {
            throw new IllegalArgumentException("sourceSequenceNumber must be >= 0");
        }
    }
}
