package online.davisfamily.warehouse.sim.dsp.p2p.arrival;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.behaviour.routing.RouteFollower;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.matrices.Vec4;
import online.davisfamily.threedee.model.Mesh;
import online.davisfamily.threedee.path.LinearSegment3;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
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

class P2pArrivalConsumerControllerTest {

    @Test
    void shouldDoNothingWhenSourceIsEmpty() {
        Fixture fixture = fixture(2, 2, new AllowAllP2pArrivalAdmissionPolicy());

        fixture.controller().update(new SimulationContext(), 0d);

        P2pArrivalConsumerControllerSnapshot snapshot = fixture.controller().snapshot();
        assertEquals(0, snapshot.sourceOccupancy());
        assertEquals(0, snapshot.targetOccupancy());
        assertEquals(Optional.empty(), snapshot.headPhysicalToteId());
        assertFalse(snapshot.blocked());
        assertEquals(0L, snapshot.successfulAcceptanceCount());
    }

    @Test
    void shouldConsumeAtMostOneHeadPerUpdateInFifoOrder() {
        Fixture fixture = fixture(2, 2, new AllowAllP2pArrivalAdmissionPolicy());
        RoutedPhysicalTote first = routedTote("tote-1", fixture.destination(), fixture.terminal());
        RoutedPhysicalTote second = routedTote("tote-2", fixture.destination(), fixture.terminal());
        holdAndEnqueue(fixture.sourceQueue(), first);
        holdAndEnqueue(fixture.sourceQueue(), second);

        fixture.controller().update(new SimulationContext(), 0.1d);

        assertSame(second, fixture.sourceQueue().peek().orElseThrow());
        assertSame(first.tote(), fixture.inputQueue().peekPayload().getTote());
        P2pArrivalConsumerControllerSnapshot firstSnapshot = fixture.controller().snapshot();
        assertEquals(1, firstSnapshot.sourceOccupancy());
        assertEquals(1, firstSnapshot.targetOccupancy());
        assertEquals(Optional.of(second.physicalToteId()), firstSnapshot.headPhysicalToteId());
        assertEquals(Optional.of(first.physicalToteId()), firstSnapshot.lastAcceptedPhysicalToteId());
        assertEquals(1L, firstSnapshot.successfulAcceptanceCount());

        fixture.controller().update(new SimulationContext(), 0.1d);

        assertTrue(fixture.sourceQueue().peek().isEmpty());
        assertEquals(List.of("tote-1", "tote-2"), fixture.inputQueue().snapshot().toteIds());
        assertEquals(2L, fixture.controller().snapshot().successfulAcceptanceCount());
        assertEquals(1L, firstSnapshot.successfulAcceptanceCount());
    }

    @Test
    void shouldDeferHeadWithoutCreatingPayloadOrSkippingIt() {
        AtomicReference<P2pArrivalAdmissionDecision> decision = new AtomicReference<>(
                P2pArrivalAdmissionDecision.defer("line belongs to service centre 108"));
        AtomicReference<P2pArrivalAdmissionRequest> evaluatedRequest = new AtomicReference<>();
        P2pArrivalAdmissionPolicy policy = request -> {
            evaluatedRequest.set(request);
            return decision.get();
        };
        Fixture fixture = fixture(2, 2, policy);
        AtomicInteger payloadCreations = new AtomicInteger();
        P2pArrivalConsumerController controller = controller(
                fixture,
                routedTote -> {
                    payloadCreations.incrementAndGet();
                    return payloadFor(routedTote);
                });
        RoutedPhysicalTote first = routedTote("tote-1", fixture.destination(), fixture.terminal());
        RoutedPhysicalTote second = routedTote("tote-2", fixture.destination(), fixture.terminal());
        holdAndEnqueue(fixture.sourceQueue(), first);
        holdAndEnqueue(fixture.sourceQueue(), second);

        controller.update(new SimulationContext(), 0.1d);

        assertSame(first, fixture.sourceQueue().peek().orElseThrow());
        assertEquals(0, fixture.target().snapshot().occupancy());
        assertEquals(0, payloadCreations.get());
        assertEquals(ToteMotionState.HELD, first.tote().getInteractionMode());
        assertEquals(first.physicalToteId(), evaluatedRequest.get().physicalToteId());
        assertEquals("104", evaluatedRequest.get().serviceCentreId());
        assertEquals(List.of("pharmacy-tote-1"), evaluatedRequest.get().pharmacyIds());
        P2pArrivalConsumerControllerSnapshot blocked = controller.snapshot();
        assertTrue(blocked.blocked());
        assertEquals(Optional.of(first.physicalToteId()), blocked.blockedPhysicalToteId());
        assertEquals(P2pArrivalConsumerController.ADMISSION_DEFERRED, blocked.blockedReason());
        assertEquals("line belongs to service centre 108", blocked.policyReason());

        decision.set(P2pArrivalAdmissionDecision.permit());
        controller.update(new SimulationContext(), 0.1d);

        assertSame(second, fixture.sourceQueue().peek().orElseThrow());
        assertSame(first.tote(), fixture.inputQueue().peekPayload().getTote());
        assertEquals(1, payloadCreations.get());
        assertFalse(controller.snapshot().blocked());
    }

    @Test
    void shouldRetainHeadUntilTipperInputCapacityReturns() {
        Fixture fixture = fixture(1, 1, new AllowAllP2pArrivalAdmissionPolicy());
        RoutedPhysicalTote occupying = routedTote(
                "occupying-tote", fixture.destination(), fixture.terminal());
        fixture.target().accept(occupying, payloadFor(occupying));
        RoutedPhysicalTote waiting = routedTote(
                "waiting-tote", fixture.destination(), fixture.terminal());
        holdAndEnqueue(fixture.sourceQueue(), waiting);

        fixture.controller().update(new SimulationContext(), 0.1d);

        assertSame(waiting, fixture.sourceQueue().peek().orElseThrow());
        assertEquals(ToteMotionState.HELD, waiting.tote().getInteractionMode());
        assertEquals(P2pArrivalConsumerController.TIPPER_INPUT_FULL,
                fixture.controller().snapshot().blockedReason());
        assertEquals("", fixture.controller().snapshot().policyReason());

        fixture.inputQueue().dequeuePayload();
        fixture.controller().update(new SimulationContext(), 0.1d);

        assertTrue(fixture.sourceQueue().peek().isEmpty());
        assertSame(waiting.tote(), fixture.inputQueue().peekPayload().getTote());
        assertFalse(fixture.controller().snapshot().blocked());
        assertEquals(1L, fixture.controller().snapshot().successfulAcceptanceCount());
    }

    @Test
    void shouldPreserveExactRoutedPayloadIdentityAtTarget() {
        Fixture fixture = fixture(1, 1, new AllowAllP2pArrivalAdmissionPolicy());
        RoutedPhysicalTote routedTote = routedTote(
                "tote-1", fixture.destination(), fixture.terminal());
        holdAndEnqueue(fixture.sourceQueue(), routedTote);

        fixture.controller().update(new SimulationContext(), 0.1d);

        TipperTotePayload accepted = fixture.inputQueue().peekPayload();
        assertSame(routedTote.tote(), accepted.getTote());
        assertSame(routedTote.renderable(), accepted.getToteRenderable());
        assertSame(routedTote.loadPlan(), fixture.target().getLoadPlanFor("tote-1"));
        assertFalse(fixture.sourceQueue().contains(routedTote.physicalToteId()));
    }

    @Test
    void shouldRejectInvalidConstructionWithoutConsumingAnything() {
        RouteSegment terminal = segment("terminal", 0f);
        RouteSegment tipperEntry = segment("tipper", 1f);
        terminal.connectTo(tipperEntry);
        OperationalRouteDestination p2p = destination(StationType.P2P, "p2p-1");
        OperationalRouteDestination otherP2p = destination(StationType.P2P, "p2p-2");
        StationRoutedToteArrivalQueue p2pSource = new StationRoutedToteArrivalQueue(p2p, 1);
        P2pTipperArrivalTarget target = new P2pTipperArrivalTarget(
                p2p, new TipperInputQueue("tipper-input", 1));
        P2pArrivalRouteBinding routeBinding = new P2pArrivalRouteBinding(terminal, tipperEntry);
        P2pTipperPayloadFactory payloadFactory = P2pArrivalConsumerControllerTest::payloadFor;
        P2pArrivalAdmissionPolicy policy = new AllowAllP2pArrivalAdmissionPolicy();

        assertThrows(IllegalArgumentException.class,
                () -> new P2pArrivalConsumerController(
                        null, policy, routeBinding, payloadFactory, target));
        assertThrows(IllegalArgumentException.class,
                () -> new P2pArrivalConsumerController(
                        p2pSource, null, routeBinding, payloadFactory, target));
        assertThrows(IllegalArgumentException.class,
                () -> new P2pArrivalConsumerController(
                        p2pSource, policy, null, payloadFactory, target));
        assertThrows(IllegalArgumentException.class,
                () -> new P2pArrivalConsumerController(
                        p2pSource, policy, routeBinding, null, target));
        assertThrows(IllegalArgumentException.class,
                () -> new P2pArrivalConsumerController(
                        p2pSource, policy, routeBinding, payloadFactory, null));
        assertThrows(IllegalArgumentException.class,
                () -> new P2pArrivalConsumerController(
                        new StationRoutedToteArrivalQueue(
                                destination(StationType.ADAPTING, "bench-1"), 1),
                        policy, routeBinding, payloadFactory, target));
        assertThrows(IllegalArgumentException.class,
                () -> new P2pArrivalConsumerController(
                        p2pSource, policy, routeBinding, payloadFactory,
                        new P2pTipperArrivalTarget(
                                otherP2p, new TipperInputQueue("other-input", 1))));
    }

    @Test
    void shouldRejectInvalidUpdateAndWrongTerminalPositionWithoutMutation() {
        Fixture fixture = fixture(1, 1, new AllowAllP2pArrivalAdmissionPolicy());
        RoutedPhysicalTote wrongRoute = routedTote(
                "tote-1", fixture.destination(), segment("wrong-terminal", 5f));
        holdAndEnqueue(fixture.sourceQueue(), wrongRoute);

        assertThrows(IllegalArgumentException.class,
                () -> fixture.controller().update(null, 0.1d));
        assertThrows(IllegalArgumentException.class,
                () -> fixture.controller().update(new SimulationContext(), -0.1d));
        assertThrows(IllegalArgumentException.class,
                () -> fixture.controller().update(new SimulationContext(), Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> fixture.controller().update(new SimulationContext(), Double.POSITIVE_INFINITY));
        assertThrows(IllegalStateException.class,
                () -> fixture.controller().update(new SimulationContext(), 0.1d));

        assertSame(wrongRoute, fixture.sourceQueue().peek().orElseThrow());
        assertEquals(0, fixture.target().snapshot().occupancy());
        assertEquals(0L, fixture.controller().snapshot().successfulAcceptanceCount());
    }

    @Test
    void shouldRejectNullPolicyDecisionAndPayloadWithoutSourceMutation() {
        Fixture nullDecisionFixture = fixture(1, 1, request -> null);
        RoutedPhysicalTote first = routedTote(
                "tote-1", nullDecisionFixture.destination(), nullDecisionFixture.terminal());
        holdAndEnqueue(nullDecisionFixture.sourceQueue(), first);

        assertThrows(IllegalStateException.class,
                () -> nullDecisionFixture.controller().update(new SimulationContext(), 0.1d));
        assertSame(first, nullDecisionFixture.sourceQueue().peek().orElseThrow());
        assertEquals(0, nullDecisionFixture.target().snapshot().occupancy());

        Fixture nullPayloadFixture = fixture(1, 1, new AllowAllP2pArrivalAdmissionPolicy());
        RoutedPhysicalTote second = routedTote(
                "tote-2", nullPayloadFixture.destination(), nullPayloadFixture.terminal());
        holdAndEnqueue(nullPayloadFixture.sourceQueue(), second);
        P2pArrivalConsumerController nullPayloadController = controller(
                nullPayloadFixture,
                routedTote -> null);

        assertThrows(IllegalStateException.class,
                () -> nullPayloadController.update(new SimulationContext(), 0.1d));
        assertSame(second, nullPayloadFixture.sourceQueue().peek().orElseThrow());
        assertEquals(0, nullPayloadFixture.target().snapshot().occupancy());
    }

    private static Fixture fixture(
            int sourceCapacity,
            int targetCapacity,
            P2pArrivalAdmissionPolicy policy) {
        OperationalRouteDestination destination = destination(StationType.P2P, "p2p-1");
        RouteSegment terminal = segment("p2p-terminal", 0f);
        RouteSegment tipperEntry = segment("p2p-tipper-entry", 1f);
        terminal.connectTo(tipperEntry);
        StationRoutedToteArrivalQueue sourceQueue =
                new StationRoutedToteArrivalQueue(destination, sourceCapacity);
        TipperInputQueue inputQueue = new TipperInputQueue("tipper-input", targetCapacity);
        P2pTipperArrivalTarget target = new P2pTipperArrivalTarget(destination, inputQueue);
        P2pArrivalRouteBinding routeBinding = new P2pArrivalRouteBinding(terminal, tipperEntry);
        P2pArrivalConsumerController controller = new P2pArrivalConsumerController(
                sourceQueue,
                policy,
                routeBinding,
                P2pArrivalConsumerControllerTest::payloadFor,
                target);
        return new Fixture(
                destination,
                terminal,
                routeBinding,
                sourceQueue,
                inputQueue,
                target,
                policy,
                controller);
    }

    private static P2pArrivalConsumerController controller(
            Fixture fixture,
            P2pTipperPayloadFactory payloadFactory) {
        return new P2pArrivalConsumerController(
                fixture.sourceQueue(),
                fixture.policy(),
                fixture.routeBinding(),
                payloadFactory,
                fixture.target());
    }

    private static void holdAndEnqueue(
            StationRoutedToteArrivalQueue sourceQueue,
            RoutedPhysicalTote routedTote) {
        routedTote.tote().setInteractionMode(ToteMotionState.HELD);
        sourceQueue.enqueue(routedTote);
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
            OperationalRouteDestination destination,
            RouteSegment currentSegment) {
        PhysicalToteId toteId = new PhysicalToteId(physicalToteId);
        InboundToteManifest manifest = new InboundToteManifest(
                toteId,
                new OrderSheetKey("order-" + physicalToteId, 1),
                OrderType.FULL_PACK,
                "104",
                List.of(new DspOrderItem(
                        "line-" + physicalToteId,
                        "product-1",
                        1,
                        "pharmacy-" + physicalToteId,
                        "patient-" + physicalToteId,
                        "prescription-" + physicalToteId,
                        DspOrderLineType.FULL_PACK,
                        "order-" + physicalToteId,
                        1,
                        1)),
                0L);
        OsrOutboundRouteLaunchRequest launchRequest = new OsrOutboundRouteLaunchRequest(
                new OsrProcessingReleaseRequest(manifest, Duration.ZERO),
                destination);
        RenderableObject renderable = renderable(physicalToteId);
        Tote tote = new Tote(
                physicalToteId,
                new RouteFollower(physicalToteId, currentSegment, 0f, 1d),
                renderable,
                new Vec3(),
                0f);
        return new RoutedPhysicalTote(
                launchRequest,
                new ToteLoadPlan(toteId, List.of()),
                tote,
                renderable);
    }

    private static OperationalRouteDestination destination(
            StationType stationType,
            String targetId) {
        return new OperationalRouteDestination(stationType, targetId);
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

    private record Fixture(
            OperationalRouteDestination destination,
            RouteSegment terminal,
            P2pArrivalRouteBinding routeBinding,
            StationRoutedToteArrivalQueue sourceQueue,
            TipperInputQueue inputQueue,
            P2pTipperArrivalTarget target,
            P2pArrivalAdmissionPolicy policy,
            P2pArrivalConsumerController controller) {
    }
}
