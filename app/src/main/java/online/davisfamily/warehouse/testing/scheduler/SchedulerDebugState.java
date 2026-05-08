package online.davisfamily.warehouse.testing.scheduler;

import java.util.List;
import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.scheduler.BlockedDecision;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspOrderStatus;
import online.davisfamily.warehouse.sim.dsp.scheduler.ReleaseDecision;
import online.davisfamily.warehouse.sim.dsp.scheduler.SchedulerEvaluation;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;

public class SchedulerDebugState {
    private volatile SchedulerDebugSnapshot snapshot = SchedulerDebugSnapshot.empty();

    public SchedulerDebugSnapshot snapshot() {
        return snapshot;
    }

    public synchronized void recordEvaluation(
            WarehouseSchedulerSnapshot schedulerSnapshot,
            SchedulerEvaluation evaluation) {
        if (schedulerSnapshot == null || evaluation == null) {
            throw new IllegalArgumentException("schedulerSnapshot and evaluation must not be null");
        }

        List<String> waitingOrderIds = schedulerSnapshot.orderStates().stream()
                .filter(orderState -> orderState.status() == DspOrderStatus.WAITING)
                .map(orderState -> orderState.order().orderId())
                .toList();
        Optional<ReleaseDecision> releaseDecision = evaluation.releaseDecision();
        Optional<String> releaseOrderId = releaseDecision.map(ReleaseDecision::orderId);
        Optional<BlockedDecision> blockedDecision = evaluation.blockedDecision();
        Optional<String> activeServiceCentreId = schedulerSnapshot.activeServiceCentreId()
                .or(() -> releaseDecision.map(ReleaseDecision::serviceCentreId))
                .or(() -> blockedDecision.map(BlockedDecision::activeServiceCentreId));

        snapshot = new SchedulerDebugSnapshot(
                activeServiceCentreId,
                waitingOrderIds,
                releaseOrderId,
                blockedDecision.map(BlockedDecision::activeServiceCentreId),
                blockedDecision.map(BlockedDecision::candidateOrderIds).orElse(List.of()),
                blockedDecision.map(BlockedDecision::blockReasons).orElse(List.of()),
                snapshot.lastAppliedOrderId(),
                snapshot.lastDeferredOrderId(),
                snapshot.lastDeferredReason(),
                snapshot.lastRejectedOrderId(),
                snapshot.lastRejectedReason());
    }

    public synchronized void recordApplied(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId must not be blank");
        }
        snapshot = new SchedulerDebugSnapshot(
                snapshot.activeServiceCentreId(),
                snapshot.waitingOrderIds(),
                snapshot.releaseOrderId(),
                snapshot.blockedServiceCentreId(),
                snapshot.blockedCandidateOrderIds(),
                snapshot.blockedReasons(),
                Optional.of(orderId),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    public synchronized void recordDeferred(String orderId, String reason) {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId must not be blank");
        }
        snapshot = new SchedulerDebugSnapshot(
                snapshot.activeServiceCentreId(),
                snapshot.waitingOrderIds(),
                snapshot.releaseOrderId(),
                snapshot.blockedServiceCentreId(),
                snapshot.blockedCandidateOrderIds(),
                snapshot.blockedReasons(),
                Optional.empty(),
                Optional.of(orderId),
                Optional.of(normalizeReason(reason)),
                Optional.empty(),
                Optional.empty());
    }

    public synchronized void recordRejected(String orderId, String reason) {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId must not be blank");
        }
        snapshot = new SchedulerDebugSnapshot(
                snapshot.activeServiceCentreId(),
                snapshot.waitingOrderIds(),
                snapshot.releaseOrderId(),
                snapshot.blockedServiceCentreId(),
                snapshot.blockedCandidateOrderIds(),
                snapshot.blockedReasons(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(orderId),
                Optional.of(normalizeReason(reason)));
    }

    private static String normalizeReason(String reason) {
        String normalized = reason == null ? "" : reason.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        return normalized;
    }
}
