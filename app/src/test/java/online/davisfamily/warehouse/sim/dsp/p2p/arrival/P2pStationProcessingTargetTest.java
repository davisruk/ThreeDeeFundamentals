package online.davisfamily.warehouse.sim.dsp.p2p.arrival;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingAdmissionDecision;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingCoordinator;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingDispositionType;
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;
import online.davisfamily.warehouse.sim.tote.Tote.ToteMotionState;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperInputQueue;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperTotePayload;

class P2pStationProcessingTargetTest {

    @Test
    void shouldAdmitExactArrivalAndRegisterClaimAfterTipperAcceptance() {
        P2pArrivalRuntimeTestFixtures.BindingFixture fixture =
                P2pArrivalRuntimeTestFixtures.binding("p2p-target");
        StationProcessingCoordinator coordinator = new StationProcessingCoordinator();
        P2pStationProcessingTarget target = target(fixture, new AllowAllP2pArrivalAdmissionPolicy(),
                fixture.binding().payloadFactory(), coordinator);
        RoutedPhysicalTote routed = P2pArrivalRuntimeTestFixtures.routedTote(
                "target-tote", fixture.binding().destination(), fixture.terminal());

        assertEquals(StationProcessingAdmissionDecision.permit(), target.evaluate(routed));
        assertTrue(coordinator.snapshot().activeClaims().isEmpty());

        target.accept(routed, Duration.ofSeconds(2));

        assertEquals(1, fixture.inputQueue().snapshot().toteIds().size());
        TipperTotePayload payload = fixture.inputQueue().peekPayload();
        assertSame(routed.tote(), payload.getTote());
        assertSame(routed.renderable(), payload.getToteRenderable());
        assertSame(routed.loadPlan(), fixture.target().getLoadPlanFor(routed.physicalToteId().value()));
        assertEquals(1, coordinator.snapshot().activeClaims().size());
        assertEquals(routed.physicalToteId(),
                coordinator.snapshot().activeClaims().getFirst().physicalToteId());
        assertTrue(coordinator.snapshot().pendingDispositions().isEmpty());
    }

    @Test
    void shouldEvaluateContinuedToteWithoutTimeSentinelAndValidateRealClaimTimeBeforeMutation() {
        P2pArrivalRuntimeTestFixtures.BindingFixture fixture =
                P2pArrivalRuntimeTestFixtures.binding("p2p-continued");
        StationProcessingCoordinator coordinator = new StationProcessingCoordinator();
        P2pStationProcessingTarget target = target(fixture,
                new AllowAllP2pArrivalAdmissionPolicy(),
                fixture.binding().payloadFactory(),
                coordinator);
        RoutedPhysicalTote routed = P2pArrivalRuntimeTestFixtures.routedTote(
                "continued-tote", fixture.binding().destination(), fixture.terminal());
        coordinator.claim(routed, Duration.ofSeconds(1));
        var disposition = coordinator.complete(
                routed.physicalToteId(),
                StationProcessingDispositionType.CONTINUE,
                routed.loadPlan(),
                Duration.ofSeconds(2));
        coordinator.acknowledgeDisposition(disposition);
        var coordinatorBefore = coordinator.snapshot();
        var inputBefore = fixture.inputQueue().snapshot();

        assertTrue(target.evaluate(routed).permitted());
        assertEquals(coordinatorBefore, coordinator.snapshot());
        assertEquals(inputBefore, fixture.inputQueue().snapshot());

        assertThrows(IllegalArgumentException.class,
                () -> target.accept(routed, Duration.ofSeconds(1)));
        assertEquals(coordinatorBefore, coordinator.snapshot());
        assertEquals(inputBefore, fixture.inputQueue().snapshot());
        assertFalse(fixture.target().hasAccepted(routed.physicalToteId()));

        var claim = target.accept(routed, Duration.ofSeconds(3));
        assertSame(routed, claim.routedTote());
        assertTrue(fixture.target().hasAccepted(routed.physicalToteId()));
    }

    @Test
    void shouldDeferPolicyAndCapacityWithoutBoundaryMutation() {
        P2pArrivalRuntimeTestFixtures.BindingFixture fixture =
                P2pArrivalRuntimeTestFixtures.binding("p2p-deferred");
        StationProcessingCoordinator coordinator = new StationProcessingCoordinator();
        AtomicInteger payloadCalls = new AtomicInteger();
        P2pStationProcessingTarget policyTarget = target(
                fixture,
                request -> P2pArrivalAdmissionDecision.defer("LINE_CLOSED"),
                routed -> {
                    payloadCalls.incrementAndGet();
                    return payload(routed);
                },
                coordinator);
        RoutedPhysicalTote candidate = P2pArrivalRuntimeTestFixtures.routedTote(
                "deferred-tote", fixture.binding().destination(), fixture.terminal());
        candidate.tote().setInteractionMode(ToteMotionState.HELD);
        var beforeInput = fixture.inputQueue().snapshot();
        var beforeCoordinator = coordinator.snapshot();
        var beforeSegment = candidate.tote().getRouteFollower().getCurrentSegment();

        assertEquals(StationProcessingAdmissionDecision.defer("LINE_CLOSED"),
                policyTarget.evaluate(candidate));
        assertEquals(0, payloadCalls.get());
        assertEquals(beforeInput, fixture.inputQueue().snapshot());
        assertEquals(beforeCoordinator, coordinator.snapshot());
        assertSame(beforeSegment, candidate.tote().getRouteFollower().getCurrentSegment());
        assertEquals(ToteMotionState.HELD, candidate.tote().getInteractionMode());

        P2pArrivalRuntimeTestFixtures.BindingFixture fullFixture =
                P2pArrivalRuntimeTestFixtures.binding(
                        "p2p-full",
                        new AllowAllP2pArrivalAdmissionPolicy(),
                        new TipperInputQueue("full-input", 1));
        StationProcessingCoordinator fullCoordinator = new StationProcessingCoordinator();
        P2pStationProcessingTarget fullTarget = target(
                fullFixture,
                new AllowAllP2pArrivalAdmissionPolicy(),
                fullFixture.binding().payloadFactory(),
                fullCoordinator);
        RoutedPhysicalTote blocker = P2pArrivalRuntimeTestFixtures.routedTote(
                "full-blocker", fullFixture.binding().destination(), fullFixture.terminal());
        fullFixture.target().accept(blocker, payload(blocker));
        RoutedPhysicalTote waiting = P2pArrivalRuntimeTestFixtures.routedTote(
                "full-waiting", fullFixture.binding().destination(), fullFixture.terminal());
        var fullBefore = fullCoordinator.snapshot();

        assertEquals(StationProcessingAdmissionDecision.defer(
                        P2pArrivalConsumerController.TIPPER_INPUT_FULL),
                fullTarget.evaluate(waiting));
        assertEquals(fullBefore, fullCoordinator.snapshot());
        assertFalse(fullFixture.inputQueue().canAccept());
        assertFalse(fullFixture.target().hasAccepted(waiting.physicalToteId()));
    }

    @Test
    void shouldRejectInvalidRouteDestinationAndDuplicateClaimWithoutMutation() {
        P2pArrivalRuntimeTestFixtures.BindingFixture fixture =
                P2pArrivalRuntimeTestFixtures.binding("p2p-invalid");
        StationProcessingCoordinator coordinator = new StationProcessingCoordinator();
        P2pStationProcessingTarget target = target(fixture, new AllowAllP2pArrivalAdmissionPolicy(),
                fixture.binding().payloadFactory(), coordinator);
        RoutedPhysicalTote candidate = P2pArrivalRuntimeTestFixtures.routedTote(
                "invalid-tote", fixture.binding().destination(), fixture.terminal());
        var before = coordinator.snapshot();
        RouteSegment wrongSegment = new RouteSegment(
                "wrong", new online.davisfamily.threedee.path.LinearSegment3(
                        new online.davisfamily.threedee.matrices.Vec3(),
                        new online.davisfamily.threedee.matrices.Vec3(1f, 0f, 0f),
                        false));
        RoutedPhysicalTote wrongRoute = P2pArrivalRuntimeTestFixtures.routedTote(
                "wrong-route", fixture.binding().destination(), wrongSegment);

        assertThrows(IllegalStateException.class, () -> target.evaluate(wrongRoute));
        assertEquals(before, coordinator.snapshot());

        OperationalRouteDestination otherDestination =
                new OperationalRouteDestination(StationType.P2P, "other-p2p");
        RoutedPhysicalTote wrongDestination = P2pArrivalRuntimeTestFixtures.routedTote(
                "wrong-destination", otherDestination, fixture.terminal());
        assertThrows(IllegalStateException.class, () -> target.evaluate(wrongDestination));
        assertEquals(before, coordinator.snapshot());

        target.accept(candidate, Duration.ZERO);
        var afterClaim = coordinator.snapshot();
        assertThrows(IllegalStateException.class, () -> target.evaluate(candidate));
        assertEquals(afterClaim, coordinator.snapshot());
    }

    @Test
    void shouldRepeatAdmissionBeforeAcceptingAfterStateChanges() {
        P2pArrivalRuntimeTestFixtures.BindingFixture fixture =
                P2pArrivalRuntimeTestFixtures.binding(
                        "p2p-revalidate",
                        new AllowAllP2pArrivalAdmissionPolicy(),
                        new TipperInputQueue("revalidate-input", 1));
        AtomicBoolean open = new AtomicBoolean(true);
        StationProcessingCoordinator coordinator = new StationProcessingCoordinator();
        P2pStationProcessingTarget target = target(
                fixture,
                request -> open.get()
                        ? P2pArrivalAdmissionDecision.permit()
                        : P2pArrivalAdmissionDecision.defer("LINE_CLOSED"),
                fixture.binding().payloadFactory(),
                coordinator);
        RoutedPhysicalTote candidate = P2pArrivalRuntimeTestFixtures.routedTote(
                "revalidate-tote", fixture.binding().destination(), fixture.terminal());

        assertTrue(target.evaluate(candidate).permitted());
        open.set(false);
        var beforeClosed = coordinator.snapshot();
        assertThrows(IllegalStateException.class,
                () -> target.accept(candidate, Duration.ofSeconds(1)));
        assertEquals(beforeClosed, coordinator.snapshot());
        assertFalse(fixture.target().hasAccepted(candidate.physicalToteId()));

        open.set(true);
        assertTrue(target.evaluate(candidate).permitted());
        RoutedPhysicalTote blocker = P2pArrivalRuntimeTestFixtures.routedTote(
                "revalidate-blocker", fixture.binding().destination(), fixture.terminal());
        fixture.target().accept(blocker, payload(blocker));
        var beforeFull = coordinator.snapshot();
        assertThrows(IllegalStateException.class,
                () -> target.accept(candidate, Duration.ofSeconds(2)));
        assertEquals(beforeFull, coordinator.snapshot());
        assertFalse(fixture.target().hasAccepted(candidate.physicalToteId()));
    }

    private static P2pStationProcessingTarget target(
            P2pArrivalRuntimeTestFixtures.BindingFixture fixture,
            P2pArrivalAdmissionPolicy policy,
            P2pTipperPayloadFactory payloadFactory,
            StationProcessingCoordinator coordinator) {
        return new P2pStationProcessingTarget(
                policy,
                fixture.binding().routeBinding(),
                payloadFactory,
                fixture.target(),
                coordinator);
    }

    private static TipperTotePayload payload(RoutedPhysicalTote routed) {
        return new TipperTotePayload(routed.tote(), routed.renderable(), 0f, Map.of());
    }
}
