package online.davisfamily.warehouse.sim.dsp.p2p.arrival;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.behaviour.routing.RouteFollower.TravelDirection;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.path.LinearSegment3;
import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineActivitySnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineDefinition;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineLeaseRegistry;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pPhysicalToteAssignment;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.StickyP2pArrivalAdmissionPolicy;
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;
import online.davisfamily.warehouse.sim.dsp.transport.routing.StationRoutedToteArrivalQueue;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperInputQueue;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperInputQueueController;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperTotePayload;
import online.davisfamily.warehouse.sim.totebag.control.TipperDownstreamFlow;
import online.davisfamily.warehouse.sim.totebag.control.ToteTrackTipperFlowController;
import online.davisfamily.warehouse.sim.totebag.machine.TippingMachine;
import online.davisfamily.warehouse.sim.totebag.pack.Pack;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

class DspP2pArrivalConsumerScenarioTest {
    private static final double UPDATE_SECONDS = 0.25d;

    @Test
    void shouldPreserveToteAndPlanContinuityFromArrivalToTipper() {
        RouteSegment terminal = segment("p2p-1-terminal", 0f, 1f);
        RouteSegment approach = segment("p2p-1-approach", 1f, 2f);
        RouteSegment tipper = segment("p2p-1-tipper", 2f, 3.25f);
        RouteSegment exit = segment("p2p-1-exit", 3.25f, 4.25f);
        terminal.connectTo(approach);
        approach.connectTo(tipper);
        tipper.connectTo(exit);

        OperationalRouteDestination destination = p2pDestination("p2p-1");
        StationRoutedToteArrivalQueue source =
                new StationRoutedToteArrivalQueue(destination, 2);
        TipperInputQueue input = new TipperInputQueue("p2p-1-input", 1);
        AtomicReference<TipperTotePayload> acceptedPayload = new AtomicReference<>();
        P2pTipperArrivalTarget target = new P2pTipperArrivalTarget(
                destination,
                input,
                (payload, ignored) -> acceptedPayload.set(payload));

        RoutedPhysicalTote bootstrap = P2pArrivalRuntimeTestFixtures.routedTote(
                "bootstrap", destination, tipper);
        bootstrap.tote().getRouteFollower().setDistanceAlongSegment(0.625f);
        RoutedPhysicalTote arrival = P2pArrivalRuntimeTestFixtures.routedTote(
                "arrival-1", destination, terminal);
        arrival.tote().getRouteFollower().setSpeedUnitsPerSecond(2d);
        source.enqueue(arrival);

        AtomicReference<ToteLoadPlan> resolvedArrivalPlan = new AtomicReference<>();
        TippingMachine tippingMachine = new TippingMachine("p2p-1-tipper", 0d, 0d, 0d);
        ToteTrackTipperFlowController flowController = new ToteTrackTipperFlowController(
                bootstrap.tote(),
                toteId -> {
                    if (bootstrap.physicalToteId().value().equals(toteId)) {
                        return bootstrap.loadPlan();
                    }
                    ToteLoadPlan resolved = target.getLoadPlanFor(toteId);
                    resolvedArrivalPlan.set(resolved);
                    return resolved;
                },
                tipper,
                0.625f,
                -1.02f,
                tippingMachine,
                acceptingDownstreamFlow(),
                0.01d);
        TipperInputQueueController inputController =
                new TipperInputQueueController(input, flowController);
        P2pArrivalConsumerController arrivalController = new P2pArrivalConsumerController(
                source,
                new AllowAllP2pArrivalAdmissionPolicy(),
                new P2pArrivalRouteBinding(terminal, tipper),
                DspP2pArrivalConsumerScenarioTest::payload,
                target);

        SimulationWorld world = new SimulationWorld();
        world.addTrackableObject(bootstrap.tote());
        world.addTrackableObject(arrival.tote());
        world.addSimObject(tippingMachine);
        world.addController(flowController);
        world.addController(inputController);
        world.addController(arrivalController);

        List<RouteSegment> visitedSegments = new ArrayList<>();
        boolean capturedArrival = false;
        for (int update = 0; update < 30; update++) {
            world.update(UPDATE_SECONDS);
            RouteSegment current = arrival.tote().getRouteFollower().getCurrentSegment();
            if (!visitedSegments.contains(current)) {
                visitedSegments.add(current);
            }
            if (flowController.isToteCaptured()
                    && arrival.physicalToteId().value().equals(tippingMachine.getActiveToteId())) {
                capturedArrival = true;
                break;
            }
        }

        assertTrue(capturedArrival);
        assertTrue(visitedSegments.contains(approach));
        assertTrue(visitedSegments.contains(tipper));
        assertSame(arrival.tote(), acceptedPayload.get().getTote());
        assertSame(arrival.renderable(), acceptedPayload.get().getToteRenderable());
        assertSame(arrival.loadPlan(), resolvedArrivalPlan.get());
        assertSame(tipper, arrival.tote().getRouteFollower().getCurrentSegment());
        assertFalse(source.contains(arrival.physicalToteId()));
        assertFalse(input.contains(arrival.physicalToteId().value()));
    }

    @Test
    void shouldRetainBlockedArrivalStateAndRetryAfterAdmissionAndCapacityOpen() {
        RouteSegment terminal = segment("p2p-1-terminal", 0f, 1f);
        RouteSegment tipper = segment("p2p-1-tipper", 1f, 2f);
        terminal.connectTo(tipper);
        OperationalRouteDestination destination = p2pDestination("p2p-1");
        StationRoutedToteArrivalQueue source =
                new StationRoutedToteArrivalQueue(destination, 1);
        TipperInputQueue input = new TipperInputQueue("p2p-1-input", 1);
        P2pTipperArrivalTarget target = new P2pTipperArrivalTarget(destination, input);
        AtomicBoolean admissionOpen = new AtomicBoolean(false);
        P2pArrivalAdmissionPolicy policy = request -> admissionOpen.get()
                ? P2pArrivalAdmissionDecision.permit()
                : P2pArrivalAdmissionDecision.defer("LINE_CLOSED");
        P2pArrivalConsumerController controller = new P2pArrivalConsumerController(
                source,
                policy,
                new P2pArrivalRouteBinding(terminal, tipper),
                DspP2pArrivalConsumerScenarioTest::payload,
                target);

        RoutedPhysicalTote arrival = P2pArrivalRuntimeTestFixtures.routedTote(
                "arrival-1", destination, terminal);
        arrival.tote().getRouteFollower().setDistanceAlongSegment(0.4f);
        arrival.tote().getRouteFollower().setTravelDirection(TravelDirection.REVERSE);
        source.enqueue(arrival);
        RoutedPhysicalTote blocker = P2pArrivalRuntimeTestFixtures.routedTote(
                "blocker", destination, terminal);

        SimulationWorld world = new SimulationWorld();
        world.addTrackableObject(arrival.tote());
        world.addController(controller);
        world.update(UPDATE_SECONDS);

        assertHeldAtSource(
                source, input, arrival, terminal, 0.4d, TravelDirection.REVERSE);
        assertEquals(P2pArrivalConsumerController.ADMISSION_DEFERRED,
                controller.snapshot().blockedReason());

        target.accept(blocker, payload(blocker));
        admissionOpen.set(true);
        world.update(UPDATE_SECONDS);

        assertHeldAtSource(
                source, input, arrival, terminal, 0.4d, TravelDirection.REVERSE);
        assertEquals(P2pArrivalConsumerController.TIPPER_INPUT_FULL,
                controller.snapshot().blockedReason());

        assertSame(blocker.tote(), input.dequeuePayload().getTote());
        world.update(UPDATE_SECONDS);

        assertFalse(source.contains(arrival.physicalToteId()));
        assertTrue(input.contains(arrival.physicalToteId().value()));
        assertSame(arrival.tote(), input.peekPayload().getTote());
        assertEquals(1L, controller.snapshot().successfulAcceptanceCount());
    }

    @Test
    void shouldAllowIndependentP2pLineToAdvanceWhenAnotherLineIsBlocked() {
        LineFixture blocked = line("p2p-1", request ->
                P2pArrivalAdmissionDecision.defer("LINE_CLOSED"));
        LineFixture open = line("p2p-2", new AllowAllP2pArrivalAdmissionPolicy());
        RoutedPhysicalTote blockedTote = P2pArrivalRuntimeTestFixtures.routedTote(
                "arrival-1", blocked.destination(), blocked.terminal());
        RoutedPhysicalTote openTote = P2pArrivalRuntimeTestFixtures.routedTote(
                "arrival-2", open.destination(), open.terminal());
        blocked.source().enqueue(blockedTote);
        open.source().enqueue(openTote);

        SimulationWorld world = new SimulationWorld();
        world.addController(blocked.controller());
        world.addController(open.controller());
        world.update(UPDATE_SECONDS);

        assertTrue(blocked.source().contains(blockedTote.physicalToteId()));
        assertFalse(blocked.input().contains(blockedTote.physicalToteId().value()));
        assertFalse(open.source().contains(openTote.physicalToteId()));
        assertTrue(open.input().contains(openTote.physicalToteId().value()));
        assertEquals(P2pArrivalConsumerController.ADMISSION_DEFERRED,
                blocked.controller().snapshot().blockedReason());
        assertEquals(1L, open.controller().snapshot().successfulAcceptanceCount());
    }

    @Test
    void shouldHoldMismatchedLeaseHeadWhileIndependentLineAdvancesThenRetry() {
        P2pLineDefinition firstDefinition = new P2pLineDefinition(
                new P2pLineId("line-1"), p2pDestination("p2p-1"));
        P2pLineDefinition secondDefinition = new P2pLineDefinition(
                new P2pLineId("line-2"), p2pDestination("p2p-2"));
        P2pLineLeaseRegistry registry = new P2pLineLeaseRegistry(
                List.of(firstDefinition, secondDefinition));
        P2pPhysicalToteAssignment firstAssignment = assignment(
                "arrival-1", "104", firstDefinition);
        P2pPhysicalToteAssignment secondAssignment = assignment(
                "arrival-2", "108", secondDefinition);
        registry.acquireLease(
                firstDefinition.lineId(), "104", P2pLineActivitySnapshot.idle());
        registry.commitAssignment(firstAssignment);
        registry.releaseLease(
                firstDefinition.lineId(), "104", P2pLineActivitySnapshot.idle());
        registry.acquireLease(
                firstDefinition.lineId(), "108", P2pLineActivitySnapshot.idle());
        registry.acquireLease(
                secondDefinition.lineId(), "108", P2pLineActivitySnapshot.idle());
        registry.commitAssignment(secondAssignment);
        Map<P2pLineId, P2pLineActivitySnapshot> idleActivities = Map.of(
                firstDefinition.lineId(), P2pLineActivitySnapshot.idle(),
                secondDefinition.lineId(), P2pLineActivitySnapshot.idle());

        LineFixture first = line(
                "p2p-1",
                new StickyP2pArrivalAdmissionPolicy(
                        firstDefinition, () -> registry.snapshot(idleActivities)));
        LineFixture second = line(
                "p2p-2",
                new StickyP2pArrivalAdmissionPolicy(
                        secondDefinition, () -> registry.snapshot(idleActivities)));
        RoutedPhysicalTote blocked = P2pArrivalRuntimeTestFixtures.routedTote(
                "arrival-1",
                "104",
                first.destination(),
                first.terminal(),
                Optional.of(firstAssignment));
        RoutedPhysicalTote permitted = P2pArrivalRuntimeTestFixtures.routedTote(
                "arrival-2",
                "108",
                second.destination(),
                second.terminal(),
                Optional.of(secondAssignment));
        first.source().enqueue(blocked);
        second.source().enqueue(permitted);

        SimulationWorld world = new SimulationWorld();
        world.addController(first.controller());
        world.addController(second.controller());
        world.update(UPDATE_SECONDS);

        assertTrue(first.source().contains(blocked.physicalToteId()));
        assertFalse(first.input().contains(blocked.physicalToteId().value()));
        assertEquals(StickyP2pArrivalAdmissionPolicy.LEASE_OWNER_MISMATCH,
                first.controller().snapshot().policyReason());
        assertFalse(second.source().contains(permitted.physicalToteId()));
        assertTrue(second.input().contains(permitted.physicalToteId().value()));

        registry.releaseLease(
                firstDefinition.lineId(), "108", P2pLineActivitySnapshot.idle());
        registry.acquireLease(
                firstDefinition.lineId(), "104", P2pLineActivitySnapshot.idle());
        world.update(UPDATE_SECONDS);

        assertFalse(first.source().contains(blocked.physicalToteId()));
        assertTrue(first.input().contains(blocked.physicalToteId().value()));
        assertEquals(1L, first.controller().snapshot().successfulAcceptanceCount());
    }

    private static void assertHeldAtSource(
            StationRoutedToteArrivalQueue source,
            TipperInputQueue input,
            RoutedPhysicalTote expected,
            RouteSegment expectedSegment,
            double expectedDistance,
            TravelDirection expectedDirection) {
        assertSame(expected, source.peek().orElseThrow());
        assertTrue(source.contains(expected.physicalToteId()));
        assertFalse(input.contains(expected.physicalToteId().value()));
        assertSame(expectedSegment, expected.tote().getRouteFollower().getCurrentSegment());
        assertEquals(expectedDistance,
                expected.tote().getRouteFollower().getDistanceAlongSegment(), 0.0001d);
        assertEquals(expectedDirection,
                expected.tote().getRouteFollower().getTravelDirection());
    }

    private static LineFixture line(String targetId, P2pArrivalAdmissionPolicy policy) {
        OperationalRouteDestination destination = p2pDestination(targetId);
        RouteSegment terminal = segment(targetId + "-terminal", 0f, 1f);
        RouteSegment tipper = segment(targetId + "-tipper", 1f, 2f);
        terminal.connectTo(tipper);
        StationRoutedToteArrivalQueue source =
                new StationRoutedToteArrivalQueue(destination, 1);
        TipperInputQueue input = new TipperInputQueue(targetId + "-input", 1);
        P2pTipperArrivalTarget target = new P2pTipperArrivalTarget(destination, input);
        P2pArrivalConsumerController controller = new P2pArrivalConsumerController(
                source,
                policy,
                new P2pArrivalRouteBinding(terminal, tipper),
                DspP2pArrivalConsumerScenarioTest::payload,
                target);
        return new LineFixture(destination, terminal, source, input, controller);
    }

    private static OperationalRouteDestination p2pDestination(String targetId) {
        return new OperationalRouteDestination(StationType.P2P, targetId);
    }

    private static P2pPhysicalToteAssignment assignment(
            String physicalToteId,
            String serviceCentreId,
            P2pLineDefinition line) {
        return new P2pPhysicalToteAssignment(
                new PhysicalToteId(physicalToteId),
                serviceCentreId,
                line.lineId(),
                line.destination());
    }

    private static RouteSegment segment(String label, float startX, float endX) {
        return new RouteSegment(
                label,
                new LinearSegment3(
                        new Vec3(startX, 0f, 0f),
                        new Vec3(endX, 0f, 0f),
                        false));
    }

    private static TipperTotePayload payload(RoutedPhysicalTote routedTote) {
        return new TipperTotePayload(
                routedTote.tote(), routedTote.renderable(), 0f, Map.of());
    }

    private static TipperDownstreamFlow acceptingDownstreamFlow() {
        return new TipperDownstreamFlow() {
            @Override
            public boolean canAcceptDischargedPack(Pack pack) {
                return true;
            }

            @Override
            public void acceptDischargedPack(Pack pack) {
            }

            @Override
            public void update(double dtSeconds) {
            }

            @Override
            public boolean keepsTipperOccupied() {
                return false;
            }
        };
    }

    private record LineFixture(
            OperationalRouteDestination destination,
            RouteSegment terminal,
            StationRoutedToteArrivalQueue source,
            TipperInputQueue input,
            P2pArrivalConsumerController controller) {
    }
}
