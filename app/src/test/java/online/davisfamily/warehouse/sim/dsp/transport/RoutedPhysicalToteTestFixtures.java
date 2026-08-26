package online.davisfamily.warehouse.sim.dsp.transport;

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
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteLaunchRequest;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteLaunchRequestFactory;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

final class RoutedPhysicalToteTestFixtures {
    private RoutedPhysicalToteTestFixtures() {
    }

    static RoutedPhysicalTote routedTote(
            String physicalToteId,
            StationType stationType,
            String targetId) {
        OperationalRouteLaunchRequest launchRequest = launchRequest(
                physicalToteId, stationType, targetId);
        ToteLoadPlan loadPlan = new ToteLoadPlan(
                new PhysicalToteId(physicalToteId), List.of());
        Tote tote = tote(physicalToteId, physicalToteId, physicalToteId);
        return new RoutedPhysicalTote(
                launchRequest,
                loadPlan,
                tote,
                tote.getRenderable());
    }

    static OperationalRouteLaunchRequest launchRequest(
            String physicalToteId,
            StationType stationType,
            String targetId) {
        InboundToteManifest manifest = new InboundToteManifest(
                new PhysicalToteId(physicalToteId),
                new OrderSheetKey("order-" + physicalToteId, 1),
                OrderType.FULL_PACK,
                "104",
                List.of(new DspOrderItem(
                        "line-" + physicalToteId,
                        "product-" + physicalToteId,
                        1)),
                0);
        return OperationalRouteLaunchRequestFactory.fromOsr(
                new OsrProcessingReleaseRequest(manifest, Duration.ofSeconds(5)),
                new OperationalRouteDestination(stationType, targetId));
    }

    static Tote tote(String toteId, String renderableId, String followerId) {
        RenderableObject renderable = renderable(renderableId);
        return new Tote(
                toteId,
                new RouteFollower(followerId, routeSegment(), 0f, 1d),
                renderable,
                new Vec3(),
                0f);
    }

    static RenderableObject renderable(String id) {
        return RenderableObject.create(
                id,
                null,
                anchorMesh(),
                new Mat4.ObjectTransformation(0f, 0f, 0f, 0f, 0f, 0f, new Mat4()),
                triangleIndex -> 0,
                false);
    }

    private static RouteSegment routeSegment() {
        return new RouteSegment(
                "osr-outbound-test-route",
                new LinearSegment3(
                        new Vec3(0f, 0f, 0f),
                        new Vec3(1f, 0f, 0f),
                        false));
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
