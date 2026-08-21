package online.davisfamily.warehouse.sim.dsp.transport;

import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OsrOutboundRouteLaunchRequest;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

@FunctionalInterface
public interface DetachedOutboundToteFactory {
    RoutedPhysicalTote create(
            OsrOutboundRouteLaunchRequest request,
            ToteLoadPlan loadPlan);
}
