package online.davisfamily.warehouse.sim.dsp.adapting;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.runtime.DspSchedulerRuntimeState;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

public class AdaptingAreaController {
    private final AdaptingArea area;
    private final DspSchedulerRuntimeState runtimeState;
    private final MutableToteLoadPlanRegistry toteLoadPlanRegistry;
    private final CollectedPackPlanFactory collectedPackPlanFactory;

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
    }

    public Optional<AdaptingBenchCompletion> applyBenchCompletion(AdaptingBenchId benchId) {
        if (benchId == null) {
            throw new IllegalArgumentException("benchId must not be null");
        }

        Optional<AdaptingBenchCompletion> completion = area.bench(benchId).consumeCompletion();
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
