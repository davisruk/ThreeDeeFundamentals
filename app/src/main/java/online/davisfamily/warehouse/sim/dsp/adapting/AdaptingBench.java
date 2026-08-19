package online.davisfamily.warehouse.sim.dsp.adapting;

import java.util.List;
import java.util.Optional;

public class AdaptingBench {
    private final String id;
    private final AdaptedLineStore store;
    private final double processingDurationSeconds;

    private AdaptingBenchState state = AdaptingBenchState.IDLE;
    private AdaptingVisit activeVisit;
    private double remainingProcessingSeconds;
    private String blockedReason = "";
    private AdaptingBenchCompletion lastCompletion;

    public AdaptingBench(String id, AdaptedLineStore store, double processingDurationSeconds) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (store == null) {
            throw new IllegalArgumentException("store must not be null");
        }
        if (processingDurationSeconds < 0d) {
            throw new IllegalArgumentException("processingDurationSeconds must be >= 0");
        }
        this.id = id;
        this.store = store;
        this.processingDurationSeconds = processingDurationSeconds;
    }

    public String id() {
        return id;
    }

    void bindStorageMap(AdaptingStorageMap storageMap) {
        store.bindStorageMap(storageMap);
    }

    public AdaptingBenchState state() {
        return state;
    }

    public boolean canAcceptVisit() {
        return state == AdaptingBenchState.IDLE;
    }

    public void acceptVisit(AdaptingVisit visit) {
        if (visit == null) {
            throw new IllegalArgumentException("visit must not be null");
        }
        if (!canAcceptVisit()) {
            throw new IllegalStateException("Bench is not idle: " + id);
        }
        activeVisit = visit;
        remainingProcessingSeconds = 0d;
        blockedReason = "";
        lastCompletion = null;
        state = AdaptingBenchState.QUEUED;
    }

    public void startProcessing() {
        if (activeVisit == null) {
            throw new IllegalStateException("No active visit for bench " + id);
        }
        if (state != AdaptingBenchState.QUEUED) {
            throw new IllegalStateException("Bench is not queued: " + id);
        }
        remainingProcessingSeconds = processingDurationSeconds;
        state = activeVisit.visitType() == AdaptingVisitType.STORE
                ? AdaptingBenchState.PROCESSING_STORE
                : AdaptingBenchState.PROCESSING_COLLECT;

        if (remainingProcessingSeconds == 0d) {
            completeActiveVisit();
        }
    }

    public void tick(double dtSeconds) {
        if (dtSeconds < 0d) {
            throw new IllegalArgumentException("dtSeconds must be >= 0");
        }
        if (state != AdaptingBenchState.PROCESSING_STORE && state != AdaptingBenchState.PROCESSING_COLLECT) {
            return;
        }

        remainingProcessingSeconds = Math.max(0d, remainingProcessingSeconds - dtSeconds);
        if (remainingProcessingSeconds == 0d) {
            completeActiveVisit();
        }
    }

    public Optional<AdaptingBenchCompletion> consumeCompletion() {
        if (state != AdaptingBenchState.COMPLETED || lastCompletion == null) {
            return Optional.empty();
        }

        AdaptingBenchCompletion completion = lastCompletion;
        lastCompletion = null;
        activeVisit = null;
        blockedReason = "";
        state = AdaptingBenchState.IDLE;
        return Optional.of(completion);
    }

    public void clearBlocked() {
        if (state != AdaptingBenchState.BLOCKED) {
            throw new IllegalStateException("Bench is not blocked: " + id);
        }
        activeVisit = null;
        remainingProcessingSeconds = 0d;
        blockedReason = "";
        state = AdaptingBenchState.IDLE;
    }

    public AdaptingBenchSnapshot snapshot() {
        return new AdaptingBenchSnapshot(
                id,
                state,
                activeVisit != null ? activeVisit.physicalToteId().value() : "",
                activeVisit != null ? activeVisit.visitType() : null,
                remainingProcessingSeconds,
                blockedReason);
    }

    private void completeActiveVisit() {
        if (activeVisit == null) {
            throw new IllegalStateException("No active visit for bench " + id);
        }

        try {
            if (activeVisit.visitType() == AdaptingVisitType.STORE) {
                for (var line : activeVisit.preparedLines()) {
                    store.stage(
                            line,
                            activeVisit.profile().orderSheetKey(),
                            activeVisit.profile().serviceCentreId());
                }
                lastCompletion = new AdaptingBenchCompletion(activeVisit, List.of());
            } else {
                lastCompletion = new AdaptingBenchCompletion(
                        activeVisit,
                        store.takeAll(activeVisit.requestedLineKeys()));
            }
            state = AdaptingBenchState.COMPLETED;
        } catch (IllegalStateException ex) {
            blockedReason = ex.getMessage();
            lastCompletion = null;
            state = AdaptingBenchState.BLOCKED;
        } finally {
            remainingProcessingSeconds = 0d;
        }
    }
}
