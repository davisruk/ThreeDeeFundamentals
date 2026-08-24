package online.davisfamily.warehouse.sim.dsp.p2p.arrival;

import java.time.Duration;
import java.util.List;
import java.util.Map;

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
import online.davisfamily.warehouse.sim.dsp.transport.routing.StationRoutedToteArrivalQueue;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.tote.Tote.ToteMotionState;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperInputQueue;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperTotePayload;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

final class P2pArrivalRuntimeTestFixtures {
    private P2pArrivalRuntimeTestFixtures() {
    }

    static BindingFixture binding(String targetId) {
        return binding(targetId, new AllowAllP2pArrivalAdmissionPolicy(), null);
    }

    static BindingFixture binding(
            String targetId,
            P2pArrivalAdmissionPolicy policy,
            TipperInputQueue suppliedInputQueue) {
        OperationalRouteDestination destination = new OperationalRouteDestination(
                StationType.P2P, targetId);
        RouteSegment terminal = segment(targetId + "-terminal", 0f);
        RouteSegment tipperEntry = segment(targetId + "-tipper-entry", 1f);
        terminal.connectTo(tipperEntry);
        StationRoutedToteArrivalQueue sourceQueue =
                new StationRoutedToteArrivalQueue(destination, 2);
        TipperInputQueue inputQueue = suppliedInputQueue == null
                ? new TipperInputQueue(targetId + "-input", 2)
                : suppliedInputQueue;
        P2pTipperArrivalTarget target = new P2pTipperArrivalTarget(destination, inputQueue);
        P2pArrivalConsumerBinding binding = new P2pArrivalConsumerBinding(
                sourceQueue,
                policy,
                new P2pArrivalRouteBinding(terminal, tipperEntry),
                P2pArrivalRuntimeTestFixtures::payload,
                target);
        return new BindingFixture(binding, terminal, sourceQueue, inputQueue, target);
    }

    static RoutedPhysicalTote routedTote(
            String physicalToteId,
            OperationalRouteDestination destination,
            RouteSegment terminal) {
        PhysicalToteId toteId = new PhysicalToteId(physicalToteId);
        InboundToteManifest manifest = new InboundToteManifest(
                toteId,
                new OrderSheetKey("order-" + physicalToteId, 1),
                OrderType.FULL_PACK,
                "104",
                List.of(new DspOrderItem("line-" + physicalToteId, "product-1", 1)),
                0L);
        OsrOutboundRouteLaunchRequest request = new OsrOutboundRouteLaunchRequest(
                new OsrProcessingReleaseRequest(manifest, Duration.ZERO),
                destination);
        RenderableObject renderable = renderable(physicalToteId);
        Tote tote = new Tote(
                physicalToteId,
                new RouteFollower(physicalToteId, terminal, 0f, 1d),
                renderable,
                new Vec3(),
                0f);
        tote.setInteractionMode(ToteMotionState.HELD);
        return new RoutedPhysicalTote(
                request,
                new ToteLoadPlan(toteId, List.of()),
                tote,
                renderable);
    }

    private static TipperTotePayload payload(RoutedPhysicalTote routedTote) {
        return new TipperTotePayload(
                routedTote.tote(), routedTote.renderable(), 0f, Map.of());
    }

    private static RouteSegment segment(String label, float startX) {
        return new RouteSegment(
                label,
                new LinearSegment3(
                        new Vec3(startX, 0f, 0f),
                        new Vec3(startX + 1f, 0f, 0f),
                        false));
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
                        new int[][] { {0, 1, 2} },
                        "anchor"),
                new Mat4.ObjectTransformation(0f, 0f, 0f, 0f, 0f, 0f, new Mat4()),
                triangleIndex -> 0,
                false);
    }

    record BindingFixture(
            P2pArrivalConsumerBinding binding,
            RouteSegment terminal,
            StationRoutedToteArrivalQueue sourceQueue,
            TipperInputQueue inputQueue,
            P2pTipperArrivalTarget target) {
    }
}
