package online.davisfamily.warehouse.sim.dsp.station.processing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.behaviour.routing.RouteFollower;
import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;
import online.davisfamily.warehouse.sim.dsp.transport.routing.StationRoutedToteArrivalQueue;
import online.davisfamily.warehouse.sim.tote.Tote.ToteMotionState;

class StationArrivalClaimControllerTest {

    @Test
    void shouldTreatEmptyUpdateAsNoOp() {
        Fixture fixture = fixture(2);
        SimulationContext context = context(9.5d);

        fixture.controller.update(context, 0.25d);

        assertTrue(fixture.sourceQueue.peek().isEmpty());
        assertTrue(fixture.target.evaluatedTotes().isEmpty());
        assertEquals(0L, fixture.controller.snapshot().successfulClaimCount());
        assertFalse(fixture.controller.snapshot().blocked());
    }

    @Test
    void shouldPassExactHeadAndRoundedTimeBeforeDequeuingIt() {
        Fixture fixture = fixture(2);
        RoutedPhysicalTote head = routedTote(fixture.destination, "head");
        head.tote().setInteractionMode(ToteMotionState.HELD);
        fixture.sourceQueue.enqueue(head);
        SimulationContext context = context(1.2345678906d);

        fixture.controller.update(context, 0d);

        Duration expected = Duration.ofNanos(Math.round(1.2345678906d * 1_000_000_000L));
        assertEquals(List.of(head), fixture.target.evaluatedTotes());
        assertEquals(List.of(head), fixture.target.acceptedTotes());
        assertEquals(List.of(expected), fixture.target.acceptedTimes());
        assertTrue(fixture.sourceQueue.peek().isEmpty());
        assertSame(head,
                fixture.coordinator.requireActiveClaim(head.physicalToteId()).routedTote());
        assertEquals(head.physicalToteId(),
                fixture.controller.snapshot().lastClaimedPhysicalToteId().orElseThrow());
        assertEquals(1L, fixture.controller.snapshot().successfulClaimCount());
        assertEquals(ToteMotionState.HELD, head.tote().getInteractionMode());
    }

    @Test
    void shouldClaimOnlyOneHeadPerUpdateAndThenAdvanceFifo() {
        Fixture fixture = fixture(2);
        RoutedPhysicalTote first = routedTote(fixture.destination, "first");
        RoutedPhysicalTote second = routedTote(fixture.destination, "second");
        fixture.sourceQueue.enqueue(first);
        fixture.sourceQueue.enqueue(second);
        SimulationContext context = context(1d);

        fixture.controller.update(context, 0.1d);

        assertTrue(fixture.sourceQueue.peek().isPresent());
        assertSame(second, fixture.sourceQueue.peek().orElseThrow());
        assertEquals(1L, fixture.controller.snapshot().successfulClaimCount());
        assertEquals(List.of(first), fixture.target.acceptedTotes());

        context.setSimulationTimeSeconds(2d);
        fixture.controller.update(context, 0.1d);

        assertTrue(fixture.sourceQueue.peek().isEmpty());
        assertEquals(2L, fixture.controller.snapshot().successfulClaimCount());
        assertEquals(List.of(first, second), fixture.target.acceptedTotes());
    }

    @Test
    void shouldLeaveFifoAndToteStateUntouchedWhenAdmissionDefers() {
        Fixture fixture = fixture(2);
        RoutedPhysicalTote first = routedTote(fixture.destination, "first");
        RoutedPhysicalTote second = routedTote(fixture.destination, "second");
        first.tote().setInteractionMode(ToteMotionState.HELD);
        fixture.sourceQueue.enqueue(first);
        fixture.sourceQueue.enqueue(second);
        RouteFollower firstFollower = first.tote().getRouteFollower();
        var firstSegment = firstFollower.getCurrentSegment();
        var firstRenderable = first.renderable();
        var firstPlan = first.loadPlan();
        fixture.target.decision(StationProcessingAdmissionDecision.defer("station full"));

        fixture.controller.update(context(3d), 0.1d);

        assertEquals(List.of(first.physicalToteId(), second.physicalToteId()),
                fixture.sourceQueue.snapshot().entries().stream()
                        .map(entry -> entry.physicalToteId())
                        .toList());
        assertSame(first, fixture.sourceQueue.peek().orElseThrow());
        assertEquals(ToteMotionState.HELD, first.tote().getInteractionMode());
        assertSame(firstSegment, firstFollower.getCurrentSegment());
        assertSame(firstRenderable, first.renderable());
        assertSame(firstPlan, first.loadPlan());
        assertTrue(fixture.target.acceptedTotes().isEmpty());
        assertEquals(first.physicalToteId(),
                fixture.controller.snapshot().blockedPhysicalToteId().orElseThrow());
        assertEquals("station full", fixture.controller.snapshot().blockedReason());
        assertEquals(0L, fixture.controller.snapshot().successfulClaimCount());
    }

    @Test
    void shouldRejectNullDecisionWithoutDequeuing() {
        Fixture fixture = fixture(1);
        RoutedPhysicalTote head = routedTote(fixture.destination, "null-decision");
        fixture.sourceQueue.enqueue(head);
        fixture.target.evaluator(routedTote -> null);

        assertThrows(IllegalStateException.class,
                () -> fixture.controller.update(context(1d), 0.1d));
        assertSame(head, fixture.sourceQueue.peek().orElseThrow());
        assertTrue(fixture.coordinator.snapshot().activeClaims().isEmpty());
    }

    @Test
    void shouldRejectWrongReturnedClaimWithoutDequeuing() {
        Fixture fixture = fixture(1);
        RoutedPhysicalTote head = routedTote(fixture.destination, "head");
        RoutedPhysicalTote wrong = routedTote(fixture.destination, "wrong");
        fixture.sourceQueue.enqueue(head);
        fixture.target.returnedClaim(new StationProcessingClaim(wrong, Duration.ofSeconds(1)));

        assertThrows(IllegalStateException.class,
                () -> fixture.controller.update(context(1d), 0.1d));
        assertSame(head, fixture.sourceQueue.peek().orElseThrow());
        assertSame(head,
                fixture.coordinator.requireActiveClaim(head.physicalToteId()).routedTote());
        assertEquals(0L, fixture.controller.snapshot().successfulClaimCount());
    }

    @Test
    void shouldRejectTargetThatFailsToRegisterReturnedClaim() {
        Fixture fixture = fixture(1);
        RoutedPhysicalTote head = routedTote(fixture.destination, "unregistered");
        fixture.sourceQueue.enqueue(head);
        fixture.target.registerClaim(false);

        assertThrows(IllegalStateException.class,
                () -> fixture.controller.update(context(1d), 0.1d));
        assertSame(head, fixture.sourceQueue.peek().orElseThrow());
        assertTrue(fixture.coordinator.snapshot().activeClaims().isEmpty());
    }

    @Test
    void shouldRejectTargetThatMutatesCoordinatorDuringEvaluation() {
        Fixture fixture = fixture(1);
        RoutedPhysicalTote head = routedTote(fixture.destination, "mutated");
        fixture.sourceQueue.enqueue(head);
        fixture.target.evaluationMutation(
                routedTote -> fixture.coordinator.claim(routedTote, Duration.ZERO));

        assertThrows(IllegalStateException.class,
                () -> fixture.controller.update(context(1d), 0.1d));
        assertSame(head, fixture.sourceQueue.peek().orElseThrow());
        assertEquals(1, fixture.coordinator.snapshot().activeClaims().size());
    }

    @Test
    void shouldRejectDisappearingOrDifferentDequeueAfterAcceptance() {
        Fixture disappearing = fixture(1);
        RoutedPhysicalTote only = routedTote(disappearing.destination, "only");
        disappearing.sourceQueue.enqueue(only);
        disappearing.target.acceptanceMutation(
                routedTote -> disappearing.sourceQueue.dequeue());

        assertThrows(IllegalStateException.class,
                () -> disappearing.controller.update(context(1d), 0.1d));
        assertEquals(0L, disappearing.controller.snapshot().successfulClaimCount());

        Fixture different = fixture(2);
        RoutedPhysicalTote first = routedTote(different.destination, "first");
        RoutedPhysicalTote second = routedTote(different.destination, "second");
        different.sourceQueue.enqueue(first);
        different.sourceQueue.enqueue(second);
        different.target.acceptanceMutation(
                routedTote -> different.sourceQueue.dequeue());

        assertThrows(IllegalStateException.class,
                () -> different.controller.update(context(1d), 0.1d));
        assertEquals(0L, different.controller.snapshot().successfulClaimCount());
        assertTrue(different.sourceQueue.peek().isEmpty());
    }

    private static Fixture fixture(int capacity) {
        OperationalRouteDestination destination =
                new OperationalRouteDestination(StationType.THIRD_PARTY, "third-party-1");
        StationRoutedToteArrivalQueue sourceQueue =
                new StationRoutedToteArrivalQueue(destination, capacity);
        StationProcessingCoordinator coordinator = new StationProcessingCoordinator();
        StationProcessingTestTarget target =
                new StationProcessingTestTarget(destination, coordinator);
        StationArrivalClaimController controller = new StationArrivalClaimController(
                new StationProcessingBinding(sourceQueue, target));
        return new Fixture(destination, sourceQueue, coordinator, target, controller);
    }

    private static RoutedPhysicalTote routedTote(
            OperationalRouteDestination destination,
            String physicalToteId) {
        return StationProcessingTestFixtures.routedTote(physicalToteId, destination);
    }

    private static SimulationContext context(double seconds) {
        SimulationContext context = new SimulationContext();
        context.setSimulationTimeSeconds(seconds);
        return context;
    }

    private record Fixture(
            OperationalRouteDestination destination,
            StationRoutedToteArrivalQueue sourceQueue,
            StationProcessingCoordinator coordinator,
            StationProcessingTestTarget target,
            StationArrivalClaimController controller) {
    }
}
