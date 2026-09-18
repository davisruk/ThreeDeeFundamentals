package online.davisfamily.warehouse.sim.dsp.bagging;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

/** Test-only adapter for publishing complete immutable bag-plan fixtures. */
public final class BagPlanningResultTestFixtures {
    private static final PackDimensions DEFAULT_DIMENSIONS = new PackDimensions(0.20f, 0.10f, 0.08f);

    private BagPlanningResultTestFixtures() {
    }

    public static BagPlanningResult complete(
            List<PlannedBag> bags,
            List<ToteLoadPlan> p2pToteLoadPlans,
            List<PlannedPackTrace> traces) {
        Map<String, PlannedPackTrace> tracesByPackId = new LinkedHashMap<>();
        for (PlannedPackTrace trace : traces) {
            tracesByPackId.put(trace.physicalPackId(), trace);
        }
        Map<String, PackPlan> packPlansById = new LinkedHashMap<>();
        for (ToteLoadPlan loadPlan : p2pToteLoadPlans) {
            for (PackPlan packPlan : loadPlan.getPackPlans()) {
                packPlansById.put(packPlan.packId(), packPlan);
            }
        }

        List<PlannedPackSlot> slots = new ArrayList<>();
        Map<String, Integer> ordinalsByLine = new LinkedHashMap<>();
        for (PlannedBag bag : bags) {
            for (String packId : bag.physicalPackIds()) {
                PlannedPackTrace trace = tracesByPackId.get(packId);
                if (trace == null) {
                    continue;
                }
                String lineKey = trace.sourceProvenance().sourceOrderSheetKey() + ":"
                        + trace.sourceProvenance().lineReference();
                int ordinal = ordinalsByLine.merge(lineKey, 1, Integer::sum);
                PackPlan packPlan = packPlansById.get(packId);
                PackDimensions dimensions = packPlan == null
                        ? DEFAULT_DIMENSIONS
                        : packPlan.dimensions();
                slots.add(new PlannedPackSlot(
                        new PlannedPackSlotKey(
                                trace.sourceProvenance().sourceOrderSheetKey(),
                                trace.sourceProvenance().lineReference(),
                                ordinal),
                        packId,
                        dimensions,
                        trace.sourceProvenance(),
                        trace.fulfilmentOrderSheetKey(),
                        Optional.of(trace.inputPhysicalToteId()),
                        bag.bagKey()));
            }
        }

        List<ToteLoadPlan> completeP2pPlans = completeP2pPlans(
                p2pToteLoadPlans,
                traces,
                packPlansById);
        Map<String, Integer> totalByPrescription = new LinkedHashMap<>();
        for (PlannedBag bag : bags) {
            totalByPrescription.merge(
                    bag.bagKey().prescriptionId(),
                    bag.bagKey().bagOrdinal(),
                    Math::max);
        }
        List<BagSequencePosition> positions = bags.stream()
                .map(bag -> new BagSequencePosition(
                        bag.bagKey().bagOrdinal(),
                        totalByPrescription.get(bag.bagKey().prescriptionId())))
                .toList();
        return new BagPlanningResult(bags, completeP2pPlans, traces, slots, positions);
    }

    private static List<ToteLoadPlan> completeP2pPlans(
            List<ToteLoadPlan> sourcePlans,
            List<PlannedPackTrace> traces,
            Map<String, PackPlan> packPlansById) {
        Map<PhysicalToteId, List<PackPlan>> packsByTote = new LinkedHashMap<>();
        for (ToteLoadPlan sourcePlan : sourcePlans) {
            packsByTote.put(sourcePlan.physicalToteId(), new ArrayList<>(sourcePlan.getPackPlans()));
        }
        for (PlannedPackTrace trace : traces) {
            boolean present = packsByTote.values().stream()
                    .flatMap(List::stream)
                    .anyMatch(packPlan -> packPlan.packId().equals(trace.physicalPackId()));
            if (!present) {
                PackPlan sourcePack = packPlansById.get(trace.physicalPackId());
                String correlationId = trace.bagKey().correlationId();
                packsByTote.computeIfAbsent(trace.inputPhysicalToteId(), ignored -> new ArrayList<>())
                        .add(new PackPlan(
                                trace.physicalPackId(),
                                correlationId,
                                sourcePack == null ? DEFAULT_DIMENSIONS : sourcePack.dimensions()));
            }
        }
        return packsByTote.entrySet().stream()
                .map(entry -> new ToteLoadPlan(entry.getKey(), entry.getValue()))
                .toList();
    }
}
