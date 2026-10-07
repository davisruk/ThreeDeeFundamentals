package online.davisfamily.warehouse.sim.dsp.adapting;

import online.davisfamily.warehouse.sim.machine.queue.MachineWaitQueueSnapshot;

public record AdaptingBenchAdmissionSnapshot(
        AdaptingBenchId benchId,
        AdaptingBenchSnapshot benchSnapshot,
        MachineWaitQueueSnapshot queueSnapshot,
        boolean admissionOpen,
        String blockedReason,
        int processingCapacity,
        int occupiedProcessingPositions) {

    public AdaptingBenchAdmissionSnapshot(AdaptingBenchId benchId,
            AdaptingBenchSnapshot benchSnapshot, MachineWaitQueueSnapshot queueSnapshot,
            boolean admissionOpen, String blockedReason) {
        this(benchId, benchSnapshot, queueSnapshot, admissionOpen, blockedReason, 1,
                benchSnapshot != null && benchSnapshot.state() != AdaptingBenchState.IDLE ? 1 : 0);
    }

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
        if (processingCapacity < 1 || occupiedProcessingPositions < 0
                || occupiedProcessingPositions > processingCapacity) {
            throw new IllegalArgumentException("Invalid processing capacity or occupied position count");
        }
        blockedReason = blockedReason == null ? "" : blockedReason;
        if (!admissionOpen && blockedReason.isBlank()) {
            throw new IllegalArgumentException("blockedReason must not be blank when admission is closed");
        }
    }

    public boolean canStartQueuedVisit() {
        return occupiedProcessingPositions < processingCapacity && !queueSnapshot.toteIds().isEmpty();
    }
}
