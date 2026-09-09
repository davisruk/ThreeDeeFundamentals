package online.davisfamily.warehouse.sim.totebag.plan;

import java.util.OptionalInt;
import java.util.Set;

/**
 * Live, simulation-thread-owned view of the bag correlations a P2P line must
 * complete.
 */
public interface ToteToBagWorkPlanProvider {
    OptionalInt expectedPackCount(String correlationId);

    Set<String> expectedCorrelationIds();
}
