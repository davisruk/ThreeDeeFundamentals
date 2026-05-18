package online.davisfamily.warehouse.sim.dsp.scheduler;

import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.model.StationType;

public record StationAdmissionSnapshot(
        StationType stationType,
        StationCapacity capacity,
        StationSnapshot snapshot,
        boolean admissionOpen,
        String blockedReason,
        Optional<String> selectedTargetId) {

    public StationAdmissionSnapshot(
            StationType stationType,
            StationCapacity capacity,
            StationSnapshot snapshot,
            boolean admissionOpen,
            String blockedReason) {
        this(stationType, capacity, snapshot, admissionOpen, blockedReason, Optional.empty());
    }

    public StationAdmissionSnapshot {
        if (stationType == null) {
            throw new IllegalArgumentException("stationType must not be null");
        }
        if (capacity == null) {
            throw new IllegalArgumentException("capacity must not be null");
        }
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }
        if (snapshot.stationType() != stationType) {
            throw new IllegalArgumentException("snapshot stationType must match stationType");
        }
        if (selectedTargetId == null) {
            throw new IllegalArgumentException("selectedTargetId must not be null");
        }
        blockedReason = blockedReason == null ? "" : blockedReason;
        if (!admissionOpen && blockedReason.isBlank()) {
            throw new IllegalArgumentException("blockedReason must not be blank when admission is closed");
        }
        selectedTargetId = selectedTargetId.map(String::trim).filter(value -> !value.isEmpty());
    }

    public boolean canAccept() {
        return admissionOpen && capacity.canAccept(snapshot);
    }
}
