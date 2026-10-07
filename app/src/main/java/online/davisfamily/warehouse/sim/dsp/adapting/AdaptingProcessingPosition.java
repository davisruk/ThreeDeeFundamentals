package online.davisfamily.warehouse.sim.dsp.adapting;

import java.util.List;
import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

/** One reusable, simulation-thread-owned processing position within a physical bench. */
final class AdaptingProcessingPosition {
    private final AdaptingBench bench;
    private final int ordinal;
    private final AdaptedLineStore store;
    private final double storeDurationSeconds;
    private final double collectDurationSeconds;

    private AdaptingBenchState state = AdaptingBenchState.IDLE;
    private AdaptingVisit activeVisit;
    private double remainingProcessingSeconds;
    private String blockedReason = "";
    private AdaptingBenchCompletion lastCompletion;

    AdaptingProcessingPosition(AdaptingBench bench, int ordinal, AdaptedLineStore store,
            double storeDurationSeconds, double collectDurationSeconds) {
        if (bench == null || store == null) {
            throw new IllegalArgumentException("bench and store must not be null");
        }
        if (ordinal < 1) {
            throw new IllegalArgumentException("ordinal must be positive");
        }
        if (storeDurationSeconds < 0d || collectDurationSeconds < 0d) {
            throw new IllegalArgumentException("STORE and COLLECT durations must be >= 0");
        }
        this.bench = bench;
        this.ordinal = ordinal;
        this.store = store;
        this.storeDurationSeconds = storeDurationSeconds;
        this.collectDurationSeconds = collectDurationSeconds;
    }

    int ordinal() {
        return ordinal;
    }

    AdaptingVisit activeVisit() {
        return activeVisit;
    }

    PhysicalToteId activeToteId() {
        return activeVisit == null ? null : activeVisit.physicalToteId();
    }

    AdaptingBenchState state() {
        return state;
    }

    boolean canAcceptVisit() {
        return state == AdaptingBenchState.IDLE && bench.canAcceptVisit();
    }

    void acceptVisit(AdaptingVisit visit) {
        if (visit == null) {
            throw new IllegalArgumentException("visit must not be null");
        }
        if (!canAcceptVisit()) {
            throw new IllegalStateException("Bench is not idle: " + bench.id() + ", position " + ordinal);
        }
        bench.positionAccepted();
        activeVisit = visit;
        remainingProcessingSeconds = 0d;
        blockedReason = "";
        lastCompletion = null;
        state = AdaptingBenchState.QUEUED;
    }

    void startProcessing() {
        if (activeVisit == null) {
            throw new IllegalStateException("No active visit for bench " + bench.id());
        }
        if (state != AdaptingBenchState.QUEUED) {
            throw new IllegalStateException("Bench is not queued: " + bench.id());
        }
        remainingProcessingSeconds = activeVisit.visitType() == AdaptingVisitType.STORE
                ? storeDurationSeconds : collectDurationSeconds;
        state = activeVisit.visitType() == AdaptingVisitType.STORE
                ? AdaptingBenchState.PROCESSING_STORE
                : AdaptingBenchState.PROCESSING_COLLECT;

        if (remainingProcessingSeconds == 0d) {
            completeActiveVisit();
        }
    }

    void tick(double dtSeconds) {
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

    Optional<AdaptingBenchCompletion> consumeCompletion() {
        if (state != AdaptingBenchState.COMPLETED || lastCompletion == null) {
            return Optional.empty();
        }
        AdaptingBenchCompletion completion = lastCompletion;
        bench.positionReleased();
        lastCompletion = null;
        activeVisit = null;
        blockedReason = "";
        state = AdaptingBenchState.IDLE;
        return Optional.of(completion);
    }

    Optional<AdaptingBenchCompletion> peekCompletion() {
        return state == AdaptingBenchState.COMPLETED
                ? Optional.ofNullable(lastCompletion)
                : Optional.empty();
    }

    AdaptingPreparedOrderGroup refreshOrderGroupDecision(AdaptingPreparedOrderGroup decision) {
        return store.refreshOrderGroupDecision(decision);
    }

    void commitOrderGroup(AdaptingPreparedOrderGroup decision) {
        commitOrderGroup(lastCompletion, decision);
    }

    void commitOrderGroup(AdaptingBenchCompletion expectedCompletion,
            AdaptingPreparedOrderGroup currentOrderGroup) {
        if (state != AdaptingBenchState.COMPLETED || lastCompletion == null
                || lastCompletion != expectedCompletion || currentOrderGroup == null) {
            throw new IllegalStateException("Bench has no matching strict COLLECT decision");
        }
        AdaptingPreparedOrderGroup retained = lastCompletion.preparedOrderGroup().orElseThrow(
                () -> new IllegalStateException("Completion has no strict COLLECT group"));
        if (!retained.storeId().equals(currentOrderGroup.storeId())
                || !retained.referenceOrderId().equals(currentOrderGroup.referenceOrderId())
                || retained.firstCollection() != currentOrderGroup.firstCollection()
                || !retained.records().equals(currentOrderGroup.records())
                || !lastCompletion.collectedLines().equals(currentOrderGroup.records())) {
            throw new IllegalStateException("Strict COLLECT group identity changed before commit");
        }
        store.commitOrderGroup(currentOrderGroup);
    }

    void clearBlocked() {
        if (state != AdaptingBenchState.BLOCKED) {
            throw new IllegalStateException("Bench is not blocked: " + bench.id());
        }
        bench.positionReleased();
        activeVisit = null;
        remainingProcessingSeconds = 0d;
        blockedReason = "";
        state = AdaptingBenchState.IDLE;
    }

    AdaptingBenchSnapshot snapshot() {
        return new AdaptingBenchSnapshot(
                bench.id(), state,
                activeVisit != null ? activeVisit.physicalToteId().value() : "",
                activeVisit != null ? activeVisit.visitType() : null,
                remainingProcessingSeconds, blockedReason);
    }

    private void completeActiveVisit() {
        if (activeVisit == null) {
            throw new IllegalStateException("No active visit for bench " + bench.id());
        }
        try {
            if (activeVisit.visitType() == AdaptingVisitType.STORE) {
                store.stageAll(activeVisit.preparedLines(), activeVisit.profile().orderSheetKey(),
                        activeVisit.profile().serviceCentreId());
                lastCompletion = new AdaptingBenchCompletion(activeVisit, List.of());
            } else if (store.strictStorage()) {
                List<String> pharmacies = activeVisit.profile().pharmacyIds();
                String storeId = pharmacies.getFirst();
                if (pharmacies.stream().anyMatch(pharmacy -> !pharmacy.equals(storeId))) {
                    throw new IllegalStateException("COLLECT visit mixes stores");
                }
                AdaptingPreparedOrderGroup prepared = store.prepareOrderGroup(
                        storeId, activeVisit.profile().orderSheetKey().orderId());
                lastCompletion = new AdaptingBenchCompletion(activeVisit, prepared.records(), Optional.of(prepared));
            } else {
                lastCompletion = new AdaptingBenchCompletion(activeVisit, store.takeAll(activeVisit.requestedLineKeys()));
            }
            state = AdaptingBenchState.COMPLETED;
        } catch (IllegalStateException exception) {
            blockedReason = exception.getMessage();
            lastCompletion = null;
            state = AdaptingBenchState.BLOCKED;
        } finally {
            remainingProcessingSeconds = 0d;
        }
    }
}
