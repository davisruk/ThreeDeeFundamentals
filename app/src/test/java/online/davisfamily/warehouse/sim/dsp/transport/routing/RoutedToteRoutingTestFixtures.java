package online.davisfamily.warehouse.sim.dsp.transport.routing;

import java.time.Duration;
import java.util.List;

import online.davisfamily.threedee.behaviour.routing.RouteFollower;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.matrices.Vec4;
import online.davisfamily.threedee.model.Mesh;
import online.davisfamily.threedee.path.LinearSegment3;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseRequest;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OsrOutboundRouteLaunchRequest;
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

final class RoutedToteRoutingTestFixtures {
    private RoutedToteRoutingTestFixtures() {
    }

    static OperationalRouteDestination destination(
            StationType stationType,
            String targetId) {
        return new OperationalRouteDestination(stationType, targetId);
    }

    static RoutedPhysicalTote routedTote(
            String physicalToteId,
            OperationalRouteDestination destination) {
        OsrOutboundRouteLaunchRequest launchRequest = new OsrOutboundRouteLaunchRequest(
                new OsrProcessingReleaseRequest(
                        manifest(physicalToteId),
                        Duration.ofSeconds(5)),
                destination);
        Tote tote = tote(physicalToteId);
        return new RoutedPhysicalTote(
                launchRequest,
                new ToteLoadPlan(new PhysicalToteId(physicalToteId), List.of()),
                tote,
                tote.getRenderable());
    }

    private static InboundToteManifest manifest(String physicalToteId) {
        return new InboundToteManifest(
                new PhysicalToteId(physicalToteId),
                new OrderSheetKey("order-" + physicalToteId, 1),
                OrderType.FULL_PACK,
                "104",
                List.of(new DspOrderItem(
                        "line-" + physicalToteId,
                        "product-" + physicalToteId,
                        1)),
                0);
    }

    private static Tote tote(String physicalToteId) {
        RenderableObject renderable = RenderableObject.create(
                physicalToteId,
                null,
                anchorMesh(),
                new Mat4.ObjectTransformation(
                        0f, 0f, 0f, 0f, 0f, 0f, new Mat4()),
                triangleIndex -> 0,
                false);
        RouteSegment routeSegment = new RouteSegment(
                "route-" + physicalToteId,
                new LinearSegment3(
                        new Vec3(0f, 0f, 0f),
                        new Vec3(1f, 0f, 0f),
                        false));
        return new Tote(
                physicalToteId,
                new RouteFollower(physicalToteId, routeSegment, 0f, 1d),
                renderable,
                new Vec3(),
                0f);
    }

    private static Mesh anchorMesh() {
        return new Mesh(
                new Vec4[] {
                        new Vec4(0f, 0f, 0f, 1f),
                        new Vec4(0f, 0f, 0f, 1f),
                        new Vec4(0f, 0f, 0f, 1f)
                },
                new int[][] { {0, 1, 2} },
                "anchor");
    }
}
