package online.davisfamily.warehouse.sim.dsp.av02;

import java.util.Optional;

/** Immutable diagnostic boundary for the full-day AV02 allocation controller. */
public record DspAv02AllocationRuntimeSnapshot(
        long sequence,
        Av02AllocationSnapshot allocationSnapshot,
        Optional<Av02AllocationSnapshot> revalidationSnapshot,
        Optional<AllocateEmptyToteAtAv02Command> selectedCommand,
        Optional<Av02AllocatedTote> lastAllocatedTote,
        boolean blocked,
        String diagnostic) {

    public DspAv02AllocationRuntimeSnapshot {
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence must be >= 0");
        }
        if (allocationSnapshot == null) {
            throw new IllegalArgumentException("allocationSnapshot must not be null");
        }
        if (revalidationSnapshot == null || selectedCommand == null || lastAllocatedTote == null) {
            throw new IllegalArgumentException("optional snapshot fields must not be null");
        }
        if (revalidationSnapshot.isPresent()
                && revalidationSnapshot.orElseThrow().sequence() != sequence) {
            throw new IllegalArgumentException(
                    "revalidationSnapshot sequence must match runtime sequence");
        }
        if (selectedCommand.isPresent()
                && selectedCommand.orElseThrow().snapshotSequence() != sequence) {
            throw new IllegalArgumentException(
                    "selectedCommand sequence must match runtime sequence");
        }
        diagnostic = diagnostic == null ? "" : diagnostic.trim();
        if (!blocked && !diagnostic.isEmpty()) {
            throw new IllegalArgumentException(
                    "diagnostic must be empty when the allocation is not blocked");
        }
    }

    public Av02AllocationSnapshot snapshot() {
        return allocationSnapshot;
    }

    public Optional<Av02AllocationSnapshot> freshRevalidationSnapshot() {
        return revalidationSnapshot;
    }

    public Optional<AllocateEmptyToteAtAv02Command> command() {
        return selectedCommand;
    }
}
