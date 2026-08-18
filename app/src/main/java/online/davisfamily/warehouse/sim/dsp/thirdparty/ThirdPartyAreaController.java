package online.davisfamily.warehouse.sim.dsp.thirdparty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.adapting.MutableToteLoadPlanRegistry;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

public class ThirdPartyAreaController {
    private final ThirdPartyArea area;
    private final MutableToteLoadPlanRegistry toteLoadPlanRegistry;
    private final ThirdPartyPackPlanFactory packPlanFactory;
    private final Set<String> completedLineReferences = new LinkedHashSet<>();
    private final Map<PhysicalToteId, ThirdPartyCompletion> completionsByToteId = new LinkedHashMap<>();
    private ThirdPartyCompletion lastCompletion;

    public ThirdPartyAreaController(
            ThirdPartyArea area,
            MutableToteLoadPlanRegistry toteLoadPlanRegistry,
            ThirdPartyPackPlanFactory packPlanFactory) {
        if (area == null) {
            throw new IllegalArgumentException("area must not be null");
        }
        if (toteLoadPlanRegistry == null) {
            throw new IllegalArgumentException("toteLoadPlanRegistry must not be null");
        }
        if (packPlanFactory == null) {
            throw new IllegalArgumentException("packPlanFactory must not be null");
        }
        this.area = area;
        this.toteLoadPlanRegistry = toteLoadPlanRegistry;
        this.packPlanFactory = packPlanFactory;
    }

    public void update(double dtSeconds) {
        area.update(dtSeconds);
        for (ThirdPartyCompletion completion : area.drainCompletions()) {
            applyCompletion(completion);
        }
    }

    public ThirdPartyAreaSnapshot areaSnapshot() {
        return area.snapshot();
    }

    public Set<String> completedLineReferences() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(completedLineReferences));
    }

    public Optional<ThirdPartyCompletion> completionForTote(PhysicalToteId physicalToteId) {
        if (physicalToteId == null) {
            throw new IllegalArgumentException("physicalToteId must not be null");
        }
        return Optional.ofNullable(completionsByToteId.get(physicalToteId));
    }

    public Optional<ThirdPartyCompletion> lastCompletion() {
        return Optional.ofNullable(lastCompletion);
    }

    private void applyCompletion(ThirdPartyCompletion completion) {
        ThirdPartyVisit visit = completion.visit();
        List<ThirdPartyLineWork> newLineWork = visit.lineWork().stream()
                .filter(lineWork -> !completedLineReferences.contains(lineWork.lineReference()))
                .toList();
        if (newLineWork.isEmpty()) {
            completionsByToteId.putIfAbsent(visit.physicalToteId(), completion);
            return;
        }

        ToteLoadPlan existingPlan = toteLoadPlanRegistry.getLoadPlanFor(visit.physicalToteId());
        if (existingPlan == null) {
            throw new IllegalStateException("Missing tote load plan for " + visit.physicalToteId().value());
        }

        List<PackPlan> additionalPackPlans = new ArrayList<>();
        for (ThirdPartyLineWork lineWork : newLineWork) {
            for (int packOrdinal = 1; packOrdinal <= lineWork.outstandingQuantity(); packOrdinal++) {
                additionalPackPlans.add(packPlanFactory.createPackPlan(visit, lineWork, packOrdinal));
            }
        }

        ToteLoadPlan updatedPlan = existingPlan.withAdditionalPackPlans(additionalPackPlans);
        toteLoadPlanRegistry.putLoadPlan(updatedPlan);
        for (ThirdPartyLineWork lineWork : newLineWork) {
            completedLineReferences.add(lineWork.lineReference());
        }
        completionsByToteId.put(visit.physicalToteId(), completion);
        lastCompletion = completion;
    }
}
