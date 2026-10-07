package online.davisfamily.warehouse.sim.dsp.adapting;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.bagging.PackProvenanceRegistry;
import online.davisfamily.warehouse.sim.dsp.runtime.DspSchedulerRuntimeState;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

public class AdaptingAreaController {
    private final AdaptingArea area;
    private final DspSchedulerRuntimeState runtimeState;
    private final MutableToteLoadPlanRegistry toteLoadPlanRegistry;
    private final CollectedPackPlanFactory collectedPackPlanFactory;
    private final DefaultCollectedPackPlanFactory strictPackPlanFactory;
    private final PackProvenanceRegistry provenanceRegistry;
    private final AdaptingCollectObserver collectObserver;

    public AdaptingAreaController(AdaptingArea area, DspSchedulerRuntimeState runtimeState) {
        this(area, runtimeState, null, null);
    }

    public AdaptingAreaController(
            AdaptingArea area,
            DspSchedulerRuntimeState runtimeState,
            MutableToteLoadPlanRegistry toteLoadPlanRegistry,
            CollectedPackPlanFactory collectedPackPlanFactory) {
        if (area == null) {
            throw new IllegalArgumentException("area must not be null");
        }
        if (runtimeState == null) {
            throw new IllegalArgumentException("runtimeState must not be null");
        }
        if ((toteLoadPlanRegistry == null) != (collectedPackPlanFactory == null)) {
            throw new IllegalArgumentException(
                    "toteLoadPlanRegistry and collectedPackPlanFactory must either both be set or both be null");
        }
        this.area = area;
        this.runtimeState = runtimeState;
        this.toteLoadPlanRegistry = toteLoadPlanRegistry;
        this.collectedPackPlanFactory = collectedPackPlanFactory;
        this.strictPackPlanFactory = null;
        this.provenanceRegistry = null;
        this.collectObserver = AdaptingCollectObserver.noOp();
    }

    public AdaptingAreaController(
            AdaptingArea area,
            DspSchedulerRuntimeState runtimeState,
            MutableToteLoadPlanRegistry toteLoadPlanRegistry,
            DefaultCollectedPackPlanFactory collectedPackPlanFactory,
            PackProvenanceRegistry provenanceRegistry,
            AdaptingCollectObserver collectObserver) {
        if (area == null || runtimeState == null || toteLoadPlanRegistry == null
                || collectedPackPlanFactory == null || provenanceRegistry == null
                || collectObserver == null) {
            throw new IllegalArgumentException("strict Adapting controller inputs must not be null");
        }
        this.area = area;
        this.runtimeState = runtimeState;
        this.toteLoadPlanRegistry = toteLoadPlanRegistry;
        this.collectedPackPlanFactory = collectedPackPlanFactory;
        this.strictPackPlanFactory = collectedPackPlanFactory;
        this.provenanceRegistry = provenanceRegistry;
        this.collectObserver = collectObserver;
    }

    public Optional<AdaptingBenchCompletion> applyBenchCompletion(AdaptingBenchId benchId) {
        requireSinglePosition(benchId);
        return applyBenchCompletion(benchId, 1);
    }

    public Optional<AdaptingBenchCompletion> applyBenchCompletion(AdaptingBenchId benchId,
            int positionOrdinal) {
        if (benchId == null) {
            throw new IllegalArgumentException("benchId must not be null");
        }

        AdaptingBench bench = area.bench(benchId);
        AdaptingProcessingPosition position = bench.position(positionOrdinal);
        Optional<AdaptingBenchCompletion> preview = position.peekCompletion();
        if (preview.isPresent() && preview.orElseThrow().preparedOrderGroup().isPresent()) {
            AdaptingBenchCompletion pending = preview.orElseThrow();
            ToteLoadPlan current = toteLoadPlanRegistry.getLoadPlanFor(pending.visit().physicalToteId());
            if (current == null) {
                throw new IllegalStateException("Missing current load plan for strict COLLECT");
            }
            PreparedAdaptingCollect decision = prepareBenchCollect(benchId, positionOrdinal, current);
            Runnable action = commitBenchCollect(decision);
            action.run();
            return Optional.of(pending);
        }

        Optional<AdaptingBenchCompletion> completion = position.consumeCompletion();
        if (completion.isEmpty()) {
            return Optional.empty();
        }

        AdaptingBenchCompletion value = completion.get();
        if (value.visit().visitType() == AdaptingVisitType.STORE) {
            Set<PreparedLineKey> preparedLineKeys = new LinkedHashSet<>();
            for (var line : value.visit().preparedLines()) {
                preparedLineKeys.add(PreparedLineKey.forPreparedLine(line));
            }
            runtimeState.addPreparedLineKeys(preparedLineKeys);
        } else {
            applyCollectCompletion(value);
        }

        return completion;
    }

    public PreparedAdaptingCollect prepareBenchCollect(
            AdaptingBenchId benchId,
            ToteLoadPlan currentLoadPlan) {
        requireSinglePosition(benchId);
        return prepareBenchCollect(benchId, 1, currentLoadPlan);
    }

    public PreparedAdaptingCollect prepareBenchCollect(AdaptingBenchId benchId,
            int positionOrdinal, ToteLoadPlan currentLoadPlan) {
        if (strictPackPlanFactory == null) {
            throw new IllegalStateException("Strict COLLECT preparation requires full-day composition");
        }
        if (benchId == null || currentLoadPlan == null) {
            throw new IllegalArgumentException("benchId and currentLoadPlan must not be null");
        }
        AdaptingBenchCompletion completion = area.bench(benchId).position(positionOrdinal).peekCompletion()
                .orElseThrow(() -> new IllegalStateException("No pending bench COLLECT completion"));
        if (completion.visit().visitType() != AdaptingVisitType.COLLECT
                || completion.preparedOrderGroup().isEmpty()) {
            throw new IllegalStateException("Pending bench completion is not strict COLLECT");
        }
        var physicalToteId = completion.visit().physicalToteId();
        if (!physicalToteId.equals(currentLoadPlan.physicalToteId())
                || toteLoadPlanRegistry.getLoadPlanFor(physicalToteId) != currentLoadPlan) {
            throw new IllegalStateException("Strict COLLECT load plan is not the exact registered plan");
        }
        String storeId = completion.visit().profile().pharmacyIds().getFirst();
        if (!completion.visit().profile().pharmacyIds().stream().allMatch(storeId::equals)) {
            throw new IllegalStateException("Strict COLLECT visit mixes stores");
        }
        AdaptingPreparedOrderGroup group = completion.preparedOrderGroup().orElseThrow();
        if (!group.storeId().equals(storeId)
                || !group.referenceOrderId().equals(completion.visit().profile().orderSheetKey().orderId())) {
            throw new IllegalStateException("Strict COLLECT group identity changed");
        }
        AdaptingPreparedOrderGroup currentGroup = area.bench(benchId).position(positionOrdinal)
                .refreshOrderGroupDecision(group);
        PreparedCollectedPackPlans prepared = strictPackPlanFactory.preparePackPlans(
                currentGroup.records());
        provenanceRegistry.validateBatch(prepared.provenanceByPackId());
        Runnable observerAction = collectObserver.prepare(
                completion.visit().profile().orderSheetKey(), physicalToteId, prepared.packPlans());
        if (observerAction == null) {
            throw new IllegalStateException("COLLECT observer returned no commit action");
        }
        ToteLoadPlan prospective = currentLoadPlan.withAdditionalPackPlans(prepared.packPlans());
        return new PreparedAdaptingCollect(
                benchId, completion, currentLoadPlan, prospective, prepared, observerAction,
                positionOrdinal, currentGroup);
    }

    public Runnable commitBenchCollect(PreparedAdaptingCollect decision) {
        if (decision == null) {
            throw new IllegalArgumentException("decision must not be null");
        }
        AdaptingProcessingPosition position = area.bench(decision.benchId())
                .position(decision.positionOrdinal());
        if (position.peekCompletion().orElse(null) != decision.completion()) {
            throw new IllegalStateException("Strict COLLECT bench completion changed before commit");
        }
        var physicalToteId = decision.completion().visit().physicalToteId();
        if (toteLoadPlanRegistry.getLoadPlanFor(physicalToteId) != decision.currentLoadPlan()) {
            throw new IllegalStateException("Strict COLLECT registered load plan changed before commit");
        }
        provenanceRegistry.validateBatch(decision.preparedPacks().provenanceByPackId());
        // The storage owner rechecks its mutation version and complete group before removing it.
        position.commitOrderGroup(decision.completion(), decision.currentOrderGroup());
        provenanceRegistry.registerBatch(decision.preparedPacks().provenanceByPackId());
        toteLoadPlanRegistry.putLoadPlan(decision.replacementLoadPlan());
        if (position.consumeCompletion().orElse(null) != decision.completion()) {
            throw new IllegalStateException("Strict COLLECT completion changed after drain");
        }
        return decision.observerCommit();
    }

    private void requireSinglePosition(AdaptingBenchId benchId) {
        if (area.bench(benchId).processingCapacity() != 1) {
            throw new IllegalStateException("Singular completion requires a one-position bench");
        }
    }

    private void applyCollectCompletion(AdaptingBenchCompletion completion) {
        if (toteLoadPlanRegistry == null) {
            throw new IllegalStateException("Collect completion requires a toteLoadPlanRegistry");
        }

        var physicalToteId = completion.visit().physicalToteId();
        ToteLoadPlan existingLoadPlan = toteLoadPlanRegistry.getLoadPlanFor(physicalToteId);
        java.util.List<PackPlan> combinedPackPlans = new java.util.ArrayList<>();
        if (existingLoadPlan != null) {
            combinedPackPlans.addAll(existingLoadPlan.getPackPlans());
        }
        combinedPackPlans.addAll(collectedPackPlanFactory.createPackPlans(completion.collectedLines()));
        toteLoadPlanRegistry.putLoadPlan(new ToteLoadPlan(physicalToteId, combinedPackPlans));
    }
}
