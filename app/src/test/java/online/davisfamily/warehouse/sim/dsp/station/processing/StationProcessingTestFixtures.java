package online.davisfamily.warehouse.sim.dsp.station.processing;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import online.davisfamily.threedee.behaviour.routing.RouteFollower;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.matrices.Vec4;
import online.davisfamily.threedee.model.Mesh;
import online.davisfamily.threedee.path.LinearSegment3;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRole;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.OperationalPhysicalToteReleaseRequest;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteIdentity;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteSource;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteLaunchRequest;
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

final class StationProcessingTestFixtures {
    private StationProcessingTestFixtures() {
    }

    static OperationalRouteDestination destination(String targetId) {
        return new OperationalRouteDestination(StationType.THIRD_PARTY, targetId);
    }

    static RoutedPhysicalTote routedTote(
            String physicalToteId,
            OperationalRouteDestination destination) {
        PhysicalToteId id = new PhysicalToteId(physicalToteId);
        OperationalPhysicalToteIdentity identity = new OperationalPhysicalToteIdentity(
                OperationalPhysicalToteSource.OSR,
                id,
                new OrderSheetKey("order-" + physicalToteId, 1),
                OrderType.FULL_PACK,
                "104",
                PhysicalToteRole.INBOUND_PACK,
                0);
        OperationalPhysicalToteReleaseRequest releaseRequest =
                new OperationalPhysicalToteReleaseRequest(
                        identity,
                        List.of("pharmacy-1"),
                        Duration.ZERO,
                        Optional.empty());
        OperationalRouteLaunchRequest launchRequest = new OperationalRouteLaunchRequest(
                releaseRequest,
                destination);
        RenderableObject renderable = renderable(physicalToteId);
        RouteSegment segment = new RouteSegment(
                "route-" + physicalToteId,
                new LinearSegment3(
                        new Vec3(0f, 0f, 0f),
                        new Vec3(1f, 0f, 0f),
                        false));
        Tote tote = new Tote(
                physicalToteId,
                new RouteFollower(physicalToteId, segment, 0f, 1d),
                renderable,
                new Vec3(),
                0f);
        ToteLoadPlan loadPlan = new ToteLoadPlan(id, List.of());
        return new RoutedPhysicalTote(launchRequest, loadPlan, tote, renderable);
    }

    static ToteLoadPlan replacementPlan(String physicalToteId) {
        return new ToteLoadPlan(new PhysicalToteId(physicalToteId), List.of());
    }

    private static RenderableObject renderable(String id) {
        return RenderableObject.create(
                id,
                null,
                new Mesh(
                        new Vec4[] {
                                new Vec4(0f, 0f, 0f, 1f),
                                new Vec4(0f, 0f, 0f, 1f),
                                new Vec4(0f, 0f, 0f, 1f)
                        },
                        new int[][] {{0, 1, 2}},
                        "anchor"),
                new Mat4.ObjectTransformation(
                        0f, 0f, 0f, 0f, 0f, 0f, new Mat4()),
                triangleIndex -> 0,
                false);
    }
}
