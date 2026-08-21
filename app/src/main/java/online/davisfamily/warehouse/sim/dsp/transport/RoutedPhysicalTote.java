package online.davisfamily.warehouse.sim.dsp.transport;

import online.davisfamily.threedee.behaviour.routing.RouteFollower;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OsrOutboundRouteLaunchRequest;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

public record RoutedPhysicalTote(
        OsrOutboundRouteLaunchRequest launchRequest,
        ToteLoadPlan loadPlan,
        Tote tote,
        RenderableObject renderable) {

    public RoutedPhysicalTote {
        if (launchRequest == null) {
            throw new IllegalArgumentException("launchRequest must not be null");
        }
        if (loadPlan == null) {
            throw new IllegalArgumentException("loadPlan must not be null");
        }
        if (tote == null) {
            throw new IllegalArgumentException("tote must not be null");
        }
        if (renderable == null) {
            throw new IllegalArgumentException("renderable must not be null");
        }
        if (tote.getRenderable() != renderable) {
            throw new IllegalArgumentException(
                    "renderable must be the exact renderable owned by tote");
        }

        PhysicalToteId physicalToteId = launchRequest.physicalToteId();
        requireMatchingPhysicalId("load plan", physicalToteId, loadPlan.physicalToteId().value());
        requireMatchingPhysicalId("tote", physicalToteId, tote.getId());
        requireMatchingPhysicalId("renderable", physicalToteId, renderable.id);

        RouteFollower routeFollower = tote.getRouteFollower();
        if (routeFollower == null) {
            throw new IllegalArgumentException("tote route follower must not be null");
        }
        String followerId;
        try {
            followerId = routeFollower.buildSnapshot().followerId();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "tote route follower must provide a valid identity snapshot", exception);
        }
        requireMatchingPhysicalId("route follower", physicalToteId, followerId);
    }

    public PhysicalToteId physicalToteId() {
        return launchRequest.physicalToteId();
    }

    public OperationalRouteDestination destination() {
        return launchRequest.destination();
    }

    private static void requireMatchingPhysicalId(
            String owner,
            PhysicalToteId expected,
            String actual) {
        if (!expected.value().equals(actual)) {
            throw new IllegalArgumentException(
                    owner + " physical ID must match launch request: expected "
                            + expected.value() + " but was " + actual);
        }
    }
}
