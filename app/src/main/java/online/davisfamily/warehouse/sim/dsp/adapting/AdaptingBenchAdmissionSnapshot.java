package online.davisfamily.warehouse.sim.dsp.adapting;

import online.davisfamily.warehouse.sim.machine.queue.MachineWaitQueueSnapshot;

public record AdaptingBenchAdmissionSnapshot(
        AdaptingBenchId benchId,
        AdaptingBenchSnapshot benchSnapshot,
        MachineWaitQueueSnapshot queueSnapshot,
        boolean admissionOpen,
        String blockedReason) {

    public AdaptingBenchAdmissionSnapshot {
        if (benchId == null) {
            throw new IllegalArgumentException("benchId must not be null");
        }
        if (benchSnapshot == null) {
            throw new IllegalArgumentException("benchSnapshot must not be null");
        }
        if (queueSnapshot == null) {
            throw new IllegalArgumentException("queueSnapshot must not be null");
        }
        blockedReason = blockedReason == null ? "" : blockedReason;
        if (!admissionOpen && blockedReason.isBlank()) {
            throw new IllegalArgumentException("blockedReason must not be blank when admission is closed");
        }
    }

    public boolean canStartQueuedVisit() {
        return benchSnapshot.state() == AdaptingBenchState.IDLE && !queueSnapshot.toteIds().isEmpty();
    }
}
