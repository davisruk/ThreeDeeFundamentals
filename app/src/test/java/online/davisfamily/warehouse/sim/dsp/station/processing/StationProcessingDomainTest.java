package online.davisfamily.warehouse.sim.dsp.station.processing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

class StationProcessingDomainTest {

    @Test
    void shouldRetainExactRoutedToteAndExposeItsIdentity() {
        var routedTote = StationProcessingTestFixtures.routedTote(
                "tote-1", StationProcessingTestFixtures.destination("third-party-1"));
        Duration claimTime = Duration.ofNanos(1_234_567_890L);

        StationProcessingClaim claim = new StationProcessingClaim(routedTote, claimTime);

        assertSame(routedTote, claim.routedTote());
        assertEquals(new PhysicalToteId("tote-1"), claim.physicalToteId());
        assertSame(routedTote.destination(), claim.destination());
        assertEquals(claimTime, claim.claimedAt());
    }

    @Test
    void shouldRejectInvalidClaimAndDispositionValues() {
        var routedTote = StationProcessingTestFixtures.routedTote(
                "tote-2", StationProcessingTestFixtures.destination("third-party-2"));
        StationProcessingClaim claim = new StationProcessingClaim(
                routedTote,
                Duration.ofSeconds(2));
        ToteLoadPlan matchingPlan = routedTote.loadPlan();

        assertThrows(IllegalArgumentException.class,
                () -> new StationProcessingClaim(null, Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new StationProcessingClaim(routedTote, Duration.ofSeconds(-1)));
        assertThrows(IllegalArgumentException.class,
                () -> new StationProcessingClaim(routedTote, null));
        assertThrows(IllegalArgumentException.class,
                () -> new StationProcessingDisposition(
                        claim,
                        StationProcessingDispositionType.CONTINUE,
                        matchingPlan,
                        Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new StationProcessingDisposition(
                        claim,
                        StationProcessingDispositionType.CONTINUE,
                        StationProcessingTestFixtures.replacementPlan("other-tote"),
                        Duration.ofSeconds(2)));
        assertThrows(IllegalArgumentException.class,
                () -> new StationProcessingDisposition(
                        claim,
                        null,
                        matchingPlan,
                        Duration.ofSeconds(2)));
        assertThrows(IllegalArgumentException.class,
                () -> new StationProcessingDisposition(
                        claim,
                        StationProcessingDispositionType.CONSUME,
                        matchingPlan,
                        null));
    }

    @Test
    void shouldRetainExactReplacementPlanInDisposition() {
        var routedTote = StationProcessingTestFixtures.routedTote(
                "tote-3", StationProcessingTestFixtures.destination("third-party-3"));
        StationProcessingClaim claim = new StationProcessingClaim(
                routedTote,
                Duration.ofSeconds(3));
        ToteLoadPlan replacementPlan = StationProcessingTestFixtures.replacementPlan("tote-3");

        StationProcessingDisposition disposition = new StationProcessingDisposition(
                claim,
                StationProcessingDispositionType.CONTINUE,
                replacementPlan,
                Duration.ofSeconds(4));

        assertSame(claim, disposition.claim());
        assertSame(replacementPlan, disposition.currentLoadPlan());
        assertEquals(new PhysicalToteId("tote-3"), disposition.physicalToteId());
        assertEquals(StationProcessingDispositionType.CONTINUE, disposition.type());
    }
}
