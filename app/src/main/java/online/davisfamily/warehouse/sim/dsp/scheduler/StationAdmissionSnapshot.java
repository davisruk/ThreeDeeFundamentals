package online.davisfamily.warehouse.sim.dsp.scheduler;

import online.davisfamily.warehouse.sim.dsp.model.StationType;

public record StationAdmissionSnapshot(
        StationType stationType,
        StationCapacity capacity,
        StationSnapshot snapshot,
        boolean admissionOpen,
        String blockedReason) {

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
        blockedReason = blockedReason == null ? "" : blockedReason;
        if (!admissionOpen && blockedReason.isBlank()) {
            throw new IllegalArgumentException("blockedReason must not be blank when admission is closed");
        }
    }

    public boolean canAccept() {
        return admissionOpen && capacity.canAccept(snapshot);
    }
}
