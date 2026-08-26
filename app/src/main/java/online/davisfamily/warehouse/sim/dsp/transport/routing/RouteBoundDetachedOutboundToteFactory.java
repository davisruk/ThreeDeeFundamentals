package online.davisfamily.warehouse.sim.dsp.transport.routing;

import online.davisfamily.threedee.behaviour.routing.RouteFollower;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteLaunchRequest;
import online.davisfamily.warehouse.sim.dsp.transport.DetachedOutboundToteFactory;
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

public final class RouteBoundDetachedOutboundToteFactory
        implements DetachedOutboundToteFactory {
    private final WarehouseRouteCatalog routeCatalog;
    private final DetachedToteRenderableFactory renderableFactory;
    private final double routeSpeedUnitsPerSecond;
    private final Vec3 toteRenderOffsets;
    private final float toteYawOffsetRadians;

    public RouteBoundDetachedOutboundToteFactory(
            WarehouseRouteCatalog routeCatalog,
            DetachedToteRenderableFactory renderableFactory,
            double routeSpeedUnitsPerSecond,
            Vec3 toteRenderOffsets,
            float toteYawOffsetRadians) {
        if (routeCatalog == null) {
            throw new IllegalArgumentException("routeCatalog must not be null");
        }
        if (renderableFactory == null) {
            throw new IllegalArgumentException("renderableFactory must not be null");
        }
        if (!Double.isFinite(routeSpeedUnitsPerSecond)
                || routeSpeedUnitsPerSecond <= 0d) {
            throw new IllegalArgumentException(
                    "routeSpeedUnitsPerSecond must be finite and > 0");
        }
        requireFiniteOffsets(toteRenderOffsets);
        if (!Float.isFinite(toteYawOffsetRadians)) {
            throw new IllegalArgumentException("toteYawOffsetRadians must be finite");
        }
        this.routeCatalog = routeCatalog;
        this.renderableFactory = renderableFactory;
        this.routeSpeedUnitsPerSecond = routeSpeedUnitsPerSecond;
        this.toteRenderOffsets = copy(toteRenderOffsets);
        this.toteYawOffsetRadians = toteYawOffsetRadians;
    }

    @Override
    public RoutedPhysicalTote create(
            OperationalRouteLaunchRequest request,
            ToteLoadPlan loadPlan) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        if (loadPlan == null) {
            throw new IllegalArgumentException("loadPlan must not be null");
        }
        if (!request.physicalToteId().equals(loadPlan.physicalToteId())) {
            throw new IllegalArgumentException(
                    "load plan physical ID must match launch request");
        }

        WarehouseRouteDefinition routeDefinition = routeCatalog.find(request.destination())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No warehouse route is configured for destination: "
                                + request.destination().targetId()));
        RenderableObject renderable = renderableFactory.create(request, loadPlan);
        if (renderable == null) {
            throw new IllegalArgumentException("renderableFactory returned null");
        }
        String physicalToteId = request.physicalToteId().value();
        if (!physicalToteId.equals(renderable.id)) {
            throw new IllegalArgumentException(
                    "renderable ID must match physical tote ID: " + physicalToteId);
        }

        RouteFollower routeFollower = new RouteFollower(
                physicalToteId,
                routeDefinition.entrySegment(),
                routeDefinition.entryDistance(),
                routeSpeedUnitsPerSecond);
        routeFollower.setTravelDirection(routeDefinition.entryDirection());
        Tote tote = new Tote(
                physicalToteId,
                routeFollower,
                renderable,
                copy(toteRenderOffsets),
                toteYawOffsetRadians);
        tote.closeLids();
        return new RoutedPhysicalTote(request, loadPlan, tote, renderable);
    }

    private static void requireFiniteOffsets(Vec3 offsets) {
        if (offsets == null) {
            throw new IllegalArgumentException("toteRenderOffsets must not be null");
        }
        if (!Float.isFinite(offsets.x)
                || !Float.isFinite(offsets.y)
                || !Float.isFinite(offsets.z)) {
            throw new IllegalArgumentException("toteRenderOffsets must be finite");
        }
    }

    private static Vec3 copy(Vec3 source) {
        return new Vec3(source.x, source.y, source.z);
    }
}
