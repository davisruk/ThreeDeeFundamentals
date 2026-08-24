package online.davisfamily.warehouse.sim.dsp.av02;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;

public record Av02AllocationSnapshot(
        long sequence,
        Av02InventorySnapshot inventory,
        List<Av02AllocationCandidate> candidates,
        Optional<AllocateEmptyToteAtAv02Command> command) {

    public Av02AllocationSnapshot {
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence must be >= 0");
        }
        if (inventory == null) {
            throw new IllegalArgumentException("inventory must not be null");
        }
        if (candidates == null) {
            throw new IllegalArgumentException("candidates must not be null");
        }
        Set<OrderSheetKey> candidateKeys = new LinkedHashSet<>();
        for (Av02AllocationCandidate candidate : candidates) {
            if (candidate == null) {
                throw new IllegalArgumentException("candidates must not contain null");
            }
            if (!candidateKeys.add(candidate.orderSheetKey())) {
                throw new IllegalArgumentException(
                        "Duplicate AV02 allocation candidate: " + candidate.orderSheetKey());
            }
        }
        candidates = List.copyOf(candidates);
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        if (command.isPresent()) {
            AllocateEmptyToteAtAv02Command selectedCommand = command.orElseThrow();
            if (selectedCommand.snapshotSequence() != sequence) {
                throw new IllegalArgumentException("command snapshot sequence must match snapshot");
            }
            Av02AllocationCandidate selectedCandidate = candidates.stream()
                    .filter(candidate -> candidate.orderSheetKey().equals(selectedCommand.orderSheetKey()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "command must identify a snapshot candidate"));
            if (!selectedCandidate.eligible()) {
                throw new IllegalArgumentException("command candidate must be eligible");
            }
            if (!selectedCandidate.serviceCentreId().equals(selectedCommand.serviceCentreId())) {
                throw new IllegalArgumentException(
                        "command service centre must match selected candidate");
            }
        }
    }

    public List<Av02AllocationCandidate> eligibleCandidates() {
        return candidates.stream().filter(Av02AllocationCandidate::eligible).toList();
    }

    public List<Av02AllocationCandidate> blockedCandidates() {
        return candidates.stream().filter(candidate -> !candidate.eligible()).toList();
    }
}
