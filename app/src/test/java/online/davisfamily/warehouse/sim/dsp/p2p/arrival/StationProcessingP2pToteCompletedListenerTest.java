package online.davisfamily.warehouse.sim.dsp.p2p.arrival;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingCoordinator;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingDispositionType;
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.totebag.control.TipperToteCompletedListener;

class StationProcessingP2pToteCompletedListenerTest {

    @Test
    void shouldRunLifecycleBeforePublishingOneConsumeDisposition() {
        P2pArrivalRuntimeTestFixtures.BindingFixture fixture =
                P2pArrivalRuntimeTestFixtures.binding("p2p-completion");
        RoutedPhysicalTote routed = P2pArrivalRuntimeTestFixtures.routedTote(
                "completion-tote", fixture.binding().destination(), fixture.terminal());
        StationProcessingCoordinator coordinator = new StationProcessingCoordinator();
        coordinator.claim(routed, Duration.ofSeconds(2));
        List<String> sequence = new ArrayList<>();
        TipperToteCompletedListener delegate = (tote, context) -> sequence.add("lifecycle");
        StationProcessingP2pToteCompletedListener listener =
                new StationProcessingP2pToteCompletedListener(delegate, coordinator);
        SimulationContext context = context(2.3456789016d);

        listener.onToteCompleted(routed.tote(), context);

        assertEquals(List.of("lifecycle"), sequence);
        assertTrue(coordinator.snapshot().activeClaims().isEmpty());
        assertEquals(1, coordinator.snapshot().pendingDispositions().size());
        var disposition = coordinator.snapshot().pendingDispositions().getFirst();
        assertEquals(routed.physicalToteId(), disposition.physicalToteId());
        assertEquals(StationProcessingDispositionType.CONSUME, disposition.type());
        assertEquals(Duration.ofNanos(Math.round(2.3456789016d * 1_000_000_000L)),
                disposition.completedAt());
    }

    @Test
    void shouldLeaveClaimActiveWhenLifecycleDelegateFails() {
        P2pArrivalRuntimeTestFixtures.BindingFixture fixture =
                P2pArrivalRuntimeTestFixtures.binding("p2p-failure");
        RoutedPhysicalTote routed = P2pArrivalRuntimeTestFixtures.routedTote(
                "failure-tote", fixture.binding().destination(), fixture.terminal());
        StationProcessingCoordinator coordinator = new StationProcessingCoordinator();
        var claim = coordinator.claim(routed, Duration.ZERO);
        AtomicBoolean called = new AtomicBoolean();
        TipperToteCompletedListener delegate = (tote, context) -> {
            called.set(true);
            throw new IllegalStateException("lifecycle rejected");
        };
        StationProcessingP2pToteCompletedListener listener =
                new StationProcessingP2pToteCompletedListener(delegate, coordinator);
        var before = coordinator.snapshot();

        assertThrows(IllegalStateException.class,
                () -> listener.onToteCompleted(routed.tote(), context(1d)));

        assertTrue(called.get());
        assertSame(claim, coordinator.requireActiveClaim(routed.physicalToteId()));
        assertEquals(before, coordinator.snapshot());
    }

    @Test
    void shouldRejectWrongInstanceWrongDestinationAndUnknownBeforeDelegate() {
        P2pArrivalRuntimeTestFixtures.BindingFixture fixture =
                P2pArrivalRuntimeTestFixtures.binding("p2p-rejections");
        RoutedPhysicalTote routed = P2pArrivalRuntimeTestFixtures.routedTote(
                "rejection-tote", fixture.binding().destination(), fixture.terminal());
        StationProcessingCoordinator coordinator = new StationProcessingCoordinator();
        coordinator.claim(routed, Duration.ZERO);
        AtomicBoolean called = new AtomicBoolean();
        StationProcessingP2pToteCompletedListener listener =
                new StationProcessingP2pToteCompletedListener(
                        (tote, context) -> called.set(true), coordinator);
        Tote wrongInstance = P2pArrivalRuntimeTestFixtures.routedTote(
                "rejection-tote", fixture.binding().destination(), fixture.terminal()).tote();
        var before = coordinator.snapshot();

        assertThrows(IllegalStateException.class,
                () -> listener.onToteCompleted(wrongInstance, context(1d)));
        assertEquals(before, coordinator.snapshot());
        assertTrue(!called.get());

        StationProcessingCoordinator wrongDestinationCoordinator = new StationProcessingCoordinator();
        OperationalRouteDestination thirdParty =
                new OperationalRouteDestination(StationType.THIRD_PARTY, "third-party");
        RoutedPhysicalTote wrongDestination = P2pArrivalRuntimeTestFixtures.routedTote(
                "third-party-tote", thirdParty, fixture.terminal());
        wrongDestinationCoordinator.claim(wrongDestination, Duration.ZERO);
        StationProcessingP2pToteCompletedListener wrongDestinationListener =
                new StationProcessingP2pToteCompletedListener(
                        (tote, context) -> { throw new AssertionError("delegate called"); },
                        wrongDestinationCoordinator);
        assertThrows(IllegalStateException.class,
                () -> wrongDestinationListener.onToteCompleted(
                        wrongDestination.tote(), context(1d)));
        assertEquals(1, wrongDestinationCoordinator.snapshot().activeClaims().size());

        StationProcessingCoordinator unknownCoordinator = new StationProcessingCoordinator();
        StationProcessingP2pToteCompletedListener unknownListener =
                new StationProcessingP2pToteCompletedListener(
                        (tote, context) -> { throw new AssertionError("delegate called"); },
                        unknownCoordinator);
        assertThrows(IllegalStateException.class,
                () -> unknownListener.onToteCompleted(routed.tote(), context(1d)));
        assertTrue(unknownCoordinator.snapshot().activeClaims().isEmpty());
    }

    private static SimulationContext context(double seconds) {
        SimulationContext context = new SimulationContext();
        context.setSimulationTimeSeconds(seconds);
        return context;
    }
}
