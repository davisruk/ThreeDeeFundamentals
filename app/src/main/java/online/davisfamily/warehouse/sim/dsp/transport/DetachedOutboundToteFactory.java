package online.davisfamily.warehouse.sim.dsp.transport;

import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteLaunchRequest;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

@FunctionalInterface
public interface DetachedOutboundToteFactory {
    RoutedPhysicalTote create(
            OperationalRouteLaunchRequest request,
            ToteLoadPlan loadPlan);
}
