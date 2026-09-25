package online.davisfamily.warehouse.sim.dsp.thirdparty;

import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;

@FunctionalInterface
public interface ThirdPartyVisitPlanSource {
    Optional<ThirdPartyVisitPlan> planFor(NotionalToteOrder order);
}
