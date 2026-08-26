package online.davisfamily.warehouse.sim.dsp.p2p.arrival;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

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
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.tote.Tote.ToteMotionState;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperInputQueue;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperTotePayload;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

class P2pTipperArrivalTargetTest {

    @Test
    void shouldAcceptExactPayloadAndExposeItsLoadPlanAndSnapshot() {
        OperationalRouteDestination destination = p2pDestination("p2p-1");
        TipperInputQueue inputQueue = new TipperInputQueue("tipper-1-input", 2);
        List<TipperTotePayload> notifiedPayloads = new ArrayList<>();
        List<ToteLoadPlan> notifiedPlans = new ArrayList<>();
        P2pTipperArrivalTarget target = new P2pTipperArrivalTarget(
                destination,
                inputQueue,
                (payload, loadPlan) -> {
                    notifiedPayloads.add(payload);
                    notifiedPlans.add(loadPlan);
                });
        RoutedPhysicalTote routedTote = routedTote("tote-1", destination);
        P2pTipperPayloadFactory payloadFactory = P2pTipperArrivalTargetTest::payloadFor;
        TipperTotePayload payload = payloadFactory.create(routedTote);

        target.accept(routedTote, payload);

        assertSame(payload, inputQueue.peekPayload());
        assertSame(routedTote.tote(), payload.getTote());
        assertSame(routedTote.renderable(), payload.getToteRenderable());
        assertSame(routedTote.loadPlan(), target.getLoadPlanFor("tote-1"));
        assertTrue(target.hasAccepted(new PhysicalToteId("tote-1")));
        assertEquals(ToteMotionState.HELD, routedTote.tote().getInteractionMode());
        assertEquals(List.of(payload), notifiedPayloads);
        assertEquals(List.of(routedTote.loadPlan()), notifiedPlans);

        P2pTipperArrivalTargetSnapshot snapshot = target.snapshot();
        assertEquals(destination, snapshot.destination());
        assertEquals(2, snapshot.capacity());
        assertEquals(1, snapshot.occupancy());
        assertTrue(snapshot.canAccept());
        assertEquals(List.of(new PhysicalToteId("tote-1")), snapshot.queuedPhysicalToteIds());
        assertEquals(1L, snapshot.acceptedCount());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.queuedPhysicalToteIds().add(new PhysicalToteId("tote-2")));
    }

    @Test
    void shouldRejectWrongDestinationBeforeMutatingTargetOrTote() {
        OperationalRouteDestination targetDestination = p2pDestination("p2p-1");
        P2pTipperArrivalTarget target = new P2pTipperArrivalTarget(
                targetDestination,
                new TipperInputQueue("tipper-1-input", 1));
        RoutedPhysicalTote routedTote = routedTote("tote-1", p2pDestination("p2p-2"));

        assertThrows(IllegalArgumentException.class,
                () -> target.accept(routedTote, payloadFor(routedTote)));

        assertUnchanged(target, routedTote);
    }

    @Test
    void shouldRejectReplacementToteAndRenderableBeforeMutation() {
        OperationalRouteDestination destination = p2pDestination("p2p-1");
        P2pTipperArrivalTarget target = new P2pTipperArrivalTarget(
                destination,
                new TipperInputQueue("tipper-1-input", 1));
        RoutedPhysicalTote routedTote = routedTote("tote-1", destination);
        Tote replacementTote = tote("tote-1");
        TipperTotePayload replacementTotePayload = new TipperTotePayload(
                replacementTote,
                replacementTote.getRenderable(),
                0f,
                Map.of());
        TipperTotePayload replacementRenderablePayload = new TipperTotePayload(
                routedTote.tote(),
                renderable("tote-1"),
                0f,
                Map.of());

        assertThrows(IllegalArgumentException.class,
                () -> target.accept(routedTote, replacementTotePayload));
        assertThrows(IllegalArgumentException.class,
                () -> target.accept(routedTote, replacementRenderablePayload));

        assertUnchanged(target, routedTote);
        assertEquals(ToteMotionState.MOVING, replacementTote.getInteractionMode());
    }

    @Test
    void shouldRejectDuplicateAfterQueueDrainAndNotifyOnlyOnce() {
        OperationalRouteDestination destination = p2pDestination("p2p-1");
        TipperInputQueue inputQueue = new TipperInputQueue("tipper-1-input", 1);
        List<String> notifiedIds = new ArrayList<>();
        P2pTipperArrivalTarget target = new P2pTipperArrivalTarget(
                destination,
                inputQueue,
                (payload, loadPlan) -> notifiedIds.add(payload.getTote().getId()));
        RoutedPhysicalTote routedTote = routedTote("tote-1", destination);
        TipperTotePayload payload = payloadFor(routedTote);
        target.accept(routedTote, payload);
        inputQueue.dequeuePayload();

        assertThrows(IllegalArgumentException.class, () -> target.accept(routedTote, payload));

        assertEquals(List.of("tote-1"), notifiedIds);
        assertEquals(0, target.snapshot().occupancy());
        assertEquals(1L, target.snapshot().acceptedCount());
        assertSame(routedTote.loadPlan(), target.getLoadPlanFor("tote-1"));
    }

    @Test
    void shouldRejectConflictingPlanForPreviouslyAcceptedPhysicalTote() {
        OperationalRouteDestination destination = p2pDestination("p2p-1");
        TipperInputQueue inputQueue = new TipperInputQueue("tipper-1-input", 1);
        P2pTipperArrivalTarget target = new P2pTipperArrivalTarget(destination, inputQueue);
        RoutedPhysicalTote original = routedTote("tote-1", destination);
        target.accept(original, payloadFor(original));
        inputQueue.dequeuePayload();
        ToteLoadPlan conflictingPlan = new ToteLoadPlan(new PhysicalToteId("tote-1"), List.of());
        RoutedPhysicalTote conflicting = new RoutedPhysicalTote(
                original.launchRequest(),
                conflictingPlan,
                original.tote(),
                original.renderable());

        assertThrows(IllegalStateException.class,
                () -> target.accept(conflicting, payloadFor(conflicting)));

        assertSame(original.loadPlan(), target.getLoadPlanFor("tote-1"));
        assertEquals(1L, target.snapshot().acceptedCount());
        assertEquals(0, target.snapshot().occupancy());
    }

    @Test
    void shouldRejectFullTargetWithoutChangingRejectedToteOrPlanState() {
        OperationalRouteDestination destination = p2pDestination("p2p-1");
        TipperInputQueue inputQueue = new TipperInputQueue("tipper-1-input", 1);
        P2pTipperArrivalTarget target = new P2pTipperArrivalTarget(destination, inputQueue);
        RoutedPhysicalTote accepted = routedTote("tote-1", destination);
        RoutedPhysicalTote rejected = routedTote("tote-2", destination);
        target.accept(accepted, payloadFor(accepted));

        assertThrows(IllegalStateException.class,
                () -> target.accept(rejected, payloadFor(rejected)));

        assertEquals(ToteMotionState.MOVING, rejected.tote().getInteractionMode());
        assertFalse(target.hasAccepted(rejected.physicalToteId()));
        assertNull(target.getLoadPlanFor("tote-2"));
        assertSame(accepted.tote(), inputQueue.peekPayload().getTote());
        assertEquals(1L, target.snapshot().acceptedCount());
    }

    @Test
    void shouldValidateConstructionAndLookupInputs() {
        TipperInputQueue inputQueue = new TipperInputQueue("tipper-input", 1);

        assertThrows(IllegalArgumentException.class,
                () -> new P2pTipperArrivalTarget(null, inputQueue));
        assertThrows(IllegalArgumentException.class,
                () -> new P2pTipperArrivalTarget(
                        new OperationalRouteDestination(StationType.ADAPTING, "bench-1"),
                        inputQueue));
        assertThrows(IllegalArgumentException.class,
                () -> new P2pTipperArrivalTarget(p2pDestination("p2p-1"), null));
        assertThrows(IllegalArgumentException.class,
                () -> new P2pTipperArrivalTarget(
                        p2pDestination("p2p-1"), inputQueue, null));

        P2pTipperArrivalTarget target = new P2pTipperArrivalTarget(
                p2pDestination("p2p-1"), inputQueue);
        assertThrows(IllegalArgumentException.class, () -> target.hasAccepted(null));
        assertThrows(IllegalArgumentException.class, () -> target.getLoadPlanFor(null));
        assertThrows(IllegalArgumentException.class, () -> target.getLoadPlanFor(" "));
        assertThrows(IllegalArgumentException.class, () -> target.accept(null, null));
    }

    private static void assertUnchanged(
            P2pTipperArrivalTarget target,
            RoutedPhysicalTote routedTote) {
        assertEquals(ToteMotionState.MOVING, routedTote.tote().getInteractionMode());
        assertFalse(target.hasAccepted(routedTote.physicalToteId()));
        assertNull(target.getLoadPlanFor(routedTote.physicalToteId().value()));
        assertEquals(0, target.snapshot().occupancy());
        assertEquals(0L, target.snapshot().acceptedCount());
    }

    private static TipperTotePayload payloadFor(RoutedPhysicalTote routedTote) {
        return new TipperTotePayload(
                routedTote.tote(),
                routedTote.renderable(),
                0f,
                Map.of());
    }

    private static RoutedPhysicalTote routedTote(
            String physicalToteId,
            OperationalRouteDestination destination) {
        PhysicalToteId toteId = new PhysicalToteId(physicalToteId);
        InboundToteManifest manifest = new InboundToteManifest(
                toteId,
                new OrderSheetKey("order-" + physicalToteId, 1),
                OrderType.FULL_PACK,
                "104",
                List.of(new DspOrderItem("line-" + physicalToteId, "product-1", 1)),
                0L);
        OperationalRouteLaunchRequest launchRequest = OperationalRouteLaunchRequestFactory.fromOsr(
                new OsrProcessingReleaseRequest(manifest, Duration.ZERO),
                destination);
        Tote tote = tote(physicalToteId);
        return new RoutedPhysicalTote(
                launchRequest,
                new ToteLoadPlan(toteId, List.of()),
                tote,
                tote.getRenderable());
    }

    private static Tote tote(String toteId) {
        RenderableObject renderable = renderable(toteId);
        return new Tote(
                toteId,
                new RouteFollower(toteId, routeSegment(), 0f, 1d),
                renderable,
                new Vec3(),
                0f);
    }

    private static OperationalRouteDestination p2pDestination(String targetId) {
        return new OperationalRouteDestination(StationType.P2P, targetId);
    }

    private static RouteSegment routeSegment() {
        return new RouteSegment(
                "p2p-arrival-test-route",
                new LinearSegment3(
                        new Vec3(0f, 0f, 0f),
                        new Vec3(1f, 0f, 0f),
                        false));
    }

    private static RenderableObject renderable(String id) {
        return RenderableObject.create(
                id,
                null,
                anchorMesh(),
                new Mat4.ObjectTransformation(0f, 0f, 0f, 0f, 0f, 0f, new Mat4()),
                triangleIndex -> 0,
                false);
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
