package online.davisfamily.warehouse.sim.totebag.control;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.warehouse.sim.tote.Tote;

@FunctionalInterface
public interface TipperToteCompletedListener {
    TipperToteCompletedListener NO_OP = (tote, context) -> { };

    void onToteCompleted(Tote tote, SimulationContext context);
}
