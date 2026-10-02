package online.davisfamily.warehouse.sim.dsp.adapting;

import java.util.List;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;

/** Prepares a read-only COLLECT decision; its returned action commits after station continuation. */
@FunctionalInterface
public interface AdaptingCollectObserver {
    Runnable prepare(OrderSheetKey collectingSheet, PhysicalToteId receivingTote,
            List<PackPlan> collectedPacks);

    static AdaptingCollectObserver noOp() {
        return (sheet, tote, packs) -> () -> { };
    }
}
