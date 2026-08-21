package online.davisfamily.warehouse.sim.dsp.transport.routing;

import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OsrOutboundRouteLaunchRequest;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

@FunctionalInterface
public interface DetachedToteRenderableFactory {
    RenderableObject create(
            OsrOutboundRouteLaunchRequest request,
            ToteLoadPlan loadPlan);
}
