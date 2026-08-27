package online.davisfamily.warehouse.sim.dsp.station.processing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

class StationProcessingCoordinatorTest {

    @Test
    void shouldKeepActiveClaimsAndDispositionsInDeterministicOrder() {
        StationProcessingCoordinator coordinator = new StationProcessingCoordinator();
        var first = StationProcessingTestFixtures.routedTote(
                "tote-first", StationProcessingTestFixtures.destination("third-party-1"));
        var second = StationProcessingTestFixtures.routedTote(
                "tote-second", new OperationalRouteDestination(StationType.ADAPTING, "bench-1"));

        StationProcessingClaim firstClaim = coordinator.claim(first, Duration.ofSeconds(1));
        StationProcessingClaim secondClaim = coordinator.claim(second, Duration.ofSeconds(2));

        var activeClaims = coordinator.snapshot().activeClaims();
        assertEquals(List.of(first.physicalToteId(), second.physicalToteId()),
                activeClaims.stream()
                        .map(StationProcessingSnapshot.ActiveClaim::physicalToteId)
                        .toList());
        assertEquals(List.of(first.destination(), second.destination()),
                activeClaims.stream()
                        .map(StationProcessingSnapshot.ActiveClaim::destination)
                        .toList());

        ToteLoadPlan replacement = StationProcessingTestFixtures.replacementPlan("tote-first");
        StationProcessingDisposition firstDisposition = coordinator.complete(
                first.physicalToteId(),
                StationProcessingDispositionType.CONTINUE,
                replacement,
                Duration.ofSeconds(3));
        StationProcessingDisposition secondDisposition = coordinator.complete(
                second.physicalToteId(),
                StationProcessingDispositionType.CONSUME,
                second.loadPlan(),
                Duration.ofSeconds(4));

        assertSame(firstDisposition, coordinator.peekDisposition().orElseThrow());
        assertEquals(List.of(firstDisposition, secondDisposition), coordinator.pendingDispositions());
        assertEquals(List.of(firstDisposition, secondDisposition),
                List.of(coordinator.dequeueDisposition().orElseThrow(),
                        coordinator.dequeueDisposition().orElseThrow()));
        assertTrue(coordinator.dequeueDisposition().isEmpty());
        assertThrows(IllegalStateException.class,
                () -> coordinator.claim(first, Duration.ofSeconds(5)));
    }

    @Test
    void shouldRejectInvalidOperationsWithoutChangingOwnership() {
        StationProcessingCoordinator coordinator = new StationProcessingCoordinator();
        var routedTote = StationProcessingTestFixtures.routedTote(
                "tote-invalid", StationProcessingTestFixtures.destination("third-party-invalid"));
        Duration claimTime = Duration.ofSeconds(5);
        coordinator.claim(routedTote, claimTime);
        var before = coordinator.snapshot();

        assertThrows(IllegalStateException.class,
                () -> coordinator.claim(routedTote, Duration.ofSeconds(6)));
        assertThrows(IllegalArgumentException.class,
                () -> coordinator.complete(
                        routedTote.physicalToteId(),
                        StationProcessingDispositionType.CONTINUE,
                        StationProcessingTestFixtures.replacementPlan("other"),
                        Duration.ofSeconds(6)));
        assertThrows(IllegalArgumentException.class,
                () -> coordinator.complete(
                        routedTote.physicalToteId(),
                        StationProcessingDispositionType.CONTINUE,
                        routedTote.loadPlan(),
                        Duration.ofSeconds(4)));
        assertThrows(IllegalArgumentException.class,
                () -> coordinator.claim(routedTote, Duration.ofSeconds(-1)));
        assertThrows(IllegalStateException.class,
                () -> coordinator.requireActiveClaim(new PhysicalToteId("unknown")));

        assertEquals(before, coordinator.snapshot());
        assertTrue(coordinator.pendingDispositions().isEmpty());
    }

    @Test
    void shouldRejectRepeatedCompletionWithoutRemovingActiveClaim() {
        StationProcessingCoordinator coordinator = new StationProcessingCoordinator();
        var routedTote = StationProcessingTestFixtures.routedTote(
                "tote-repeat", StationProcessingTestFixtures.destination("third-party-repeat"));
        coordinator.claim(routedTote, Duration.ZERO);

        coordinator.complete(
                routedTote.physicalToteId(),
                StationProcessingDispositionType.CONSUME,
                routedTote.loadPlan(),
                Duration.ZERO);
        var afterFirstCompletion = coordinator.snapshot();

        assertThrows(IllegalStateException.class,
                () -> coordinator.complete(
                        routedTote.physicalToteId(),
                        StationProcessingDispositionType.CONSUME,
                        routedTote.loadPlan(),
                        Duration.ofSeconds(1)));
        assertEquals(afterFirstCompletion, coordinator.snapshot());
    }
}
