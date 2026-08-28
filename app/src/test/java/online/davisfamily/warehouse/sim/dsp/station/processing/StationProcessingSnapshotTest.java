package online.davisfamily.warehouse.sim.dsp.station.processing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class StationProcessingSnapshotTest {

    @Test
    void shouldExposeOnlyOrderedImmutableInspectionValues() {
        StationProcessingCoordinator coordinator = new StationProcessingCoordinator();
        var first = StationProcessingTestFixtures.routedTote(
                "tote-snapshot-1", StationProcessingTestFixtures.destination("third-party-1"));
        var second = StationProcessingTestFixtures.routedTote(
                "tote-snapshot-2", StationProcessingTestFixtures.destination("third-party-2"));
        coordinator.claim(first, Duration.ofSeconds(1));
        coordinator.claim(second, Duration.ofSeconds(2));
        coordinator.complete(
                first.physicalToteId(),
                StationProcessingDispositionType.CONTINUE,
                first.loadPlan(),
                Duration.ofSeconds(3));

        StationProcessingSnapshot snapshot = coordinator.snapshot();

        assertEquals(1, snapshot.activeClaims().size());
        assertEquals(second.physicalToteId(), snapshot.activeClaims().getFirst().physicalToteId());
        assertEquals(second.destination(), snapshot.activeClaims().getFirst().destination());
        assertEquals(Duration.ofSeconds(2), snapshot.activeClaims().getFirst().claimedAt());
        assertEquals(1, snapshot.pendingDispositions().size());
        assertEquals(first.physicalToteId(), snapshot.pendingDispositions().getFirst().physicalToteId());
        assertEquals(StationProcessingDispositionType.CONTINUE,
                snapshot.pendingDispositions().getFirst().type());
        assertEquals(Duration.ofSeconds(3), snapshot.pendingDispositions().getFirst().completedAt());
        assertEquals(1, snapshot.completedCount());
        assertTrue(snapshot.lastCompletedPhysicalToteId().isPresent());
        assertEquals(first.physicalToteId(), snapshot.lastCompletedPhysicalToteId().orElseThrow());
        assertEquals(StationProcessingDispositionType.CONTINUE,
                snapshot.lastCompletedType().orElseThrow());
        assertEquals(0, snapshot.acknowledgedContinuationCount());
        assertEquals(0, snapshot.acknowledgedConsumeCount());
        assertTrue(snapshot.lastAcknowledgedPhysicalToteId().isEmpty());
        assertTrue(snapshot.lastAcknowledgedType().isEmpty());

        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.activeClaims().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.pendingDispositions().clear());

        coordinator.dequeueDisposition();
        StationProcessingSnapshot afterDequeue = coordinator.snapshot();
        assertTrue(afterDequeue.pendingDispositions().isEmpty());
        assertEquals(1, afterDequeue.completedCount());
        assertEquals(first.physicalToteId(), afterDequeue.lastCompletedPhysicalToteId().orElseThrow());
        assertEquals(1, afterDequeue.acknowledgedContinuationCount());
        assertEquals(0, afterDequeue.acknowledgedConsumeCount());
        assertEquals(first.physicalToteId(),
                afterDequeue.lastAcknowledgedPhysicalToteId().orElseThrow());
        assertEquals(StationProcessingDispositionType.CONTINUE,
                afterDequeue.lastAcknowledgedType().orElseThrow());
    }

    @Test
    void shouldValidateSnapshotOptionalCompletionFields() {
        var destination = StationProcessingTestFixtures.destination("third-party-snapshot");
        var id = StationProcessingTestFixtures.routedTote("tote-snapshot", destination).physicalToteId();
        var active = new StationProcessingSnapshot.ActiveClaim(id, destination, Duration.ZERO);
        var pending = new StationProcessingSnapshot.PendingDisposition(
                id,
                destination,
                StationProcessingDispositionType.CONSUME,
                Duration.ZERO,
                Duration.ZERO);

        assertTrue(new StationProcessingSnapshot(
                java.util.List.of(),
                java.util.List.of(),
                0,
                java.util.Optional.empty(),
                java.util.Optional.empty()).activeClaims().isEmpty());
        assertFalse(new StationProcessingSnapshot(
                java.util.List.of(active),
                java.util.List.of(pending),
                1,
                java.util.Optional.of(id),
                java.util.Optional.of(StationProcessingDispositionType.CONSUME))
                .pendingDispositions().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new StationProcessingSnapshot(
                java.util.List.of(),
                java.util.List.of(),
                1,
                java.util.Optional.of(id),
                java.util.Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new StationProcessingSnapshot(
                java.util.List.of(),
                java.util.List.of(),
                -1,
                java.util.Optional.empty(),
                java.util.Optional.empty()));
    }
}
