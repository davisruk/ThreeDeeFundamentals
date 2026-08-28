package online.davisfamily.warehouse.sim.dsp.station.continuation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingDispositionType;

class StationRouteContinuationControllerSnapshotTest {

    @Test
    void shouldExposeAnImmutableEmptySnapshot() {
        StationRouteContinuationControllerSnapshot snapshot = empty();

        assertTrue(snapshot.headPhysicalToteId().isEmpty());
        assertTrue(snapshot.headDispositionType().isEmpty());
        assertTrue(snapshot.selectedNextStation().isEmpty());
        assertTrue(snapshot.selectedNextDestination().isEmpty());
        assertFalse(snapshot.blocked());
        assertEquals(0, snapshot.continuedCount());
        assertEquals(0, snapshot.consumedAcknowledgementCount());
        assertTrue(snapshot.lastHandledPhysicalToteId().isEmpty());
        assertTrue(snapshot.lastHandledDispositionType().isEmpty());
        assertTrue(snapshot.lastHandledNextDestination().isEmpty());

        StationRouteContinuationControllerSnapshot changed = new StationRouteContinuationControllerSnapshot(
                Optional.of(new PhysicalToteId("head")),
                Optional.of(StationProcessingDispositionType.CONTINUE),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                "",
                1,
                0,
                Optional.of(new PhysicalToteId("head")),
                Optional.of(StationProcessingDispositionType.CONTINUE),
                Optional.of(destination(StationType.P2P, "line-1")));
        assertTrue(snapshot.headPhysicalToteId().isEmpty());
        assertEquals(0, snapshot.continuedCount());
        assertEquals(1, changed.continuedCount());
        assertEquals(destination(StationType.P2P, "line-1"),
                changed.lastHandledNextDestination().orElseThrow());
    }

    @Test
    void shouldValidateOptionalAndCountInvariants() {
        assertThrows(IllegalArgumentException.class, () -> new StationRouteContinuationControllerSnapshot(
                Optional.of(new PhysicalToteId("head")), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), "", 0, 0, Optional.empty(), Optional.empty(), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new StationRouteContinuationControllerSnapshot(
                Optional.empty(), Optional.empty(), Optional.of(StationType.P2P), Optional.empty(),
                Optional.empty(), "", 0, 0, Optional.empty(), Optional.empty(), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new StationRouteContinuationControllerSnapshot(
                Optional.of(new PhysicalToteId("head")), Optional.of(StationProcessingDispositionType.CONTINUE),
                Optional.of(StationType.ADAPTING), Optional.of(destination(StationType.P2P, "line-1")),
                Optional.empty(), "", 0, 0, Optional.empty(), Optional.empty(), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new StationRouteContinuationControllerSnapshot(
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(new PhysicalToteId("blocked")), "", 0, 0,
                Optional.empty(), Optional.empty(), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new StationRouteContinuationControllerSnapshot(
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), "",
                -1, 0, Optional.empty(), Optional.empty(), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new StationRouteContinuationControllerSnapshot(
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), "",
                0, 0, Optional.of(new PhysicalToteId("last")),
                Optional.of(StationProcessingDispositionType.CONSUME), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new StationRouteContinuationControllerSnapshot(
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), "",
                1, 0, Optional.empty(), Optional.empty(), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new StationRouteContinuationControllerSnapshot(
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), "",
                1, 0, Optional.of(new PhysicalToteId("last")),
                Optional.of(StationProcessingDispositionType.CONTINUE), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new StationRouteContinuationControllerSnapshot(
                Optional.of(new PhysicalToteId("consume-head")),
                Optional.of(StationProcessingDispositionType.CONSUME),
                Optional.of(StationType.P2P), Optional.empty(), Optional.empty(), "",
                0, 0, Optional.empty(), Optional.empty(), Optional.empty()));
    }

    @Test
    void shouldRepresentBlockedAndTerminalOrContinuationHistoryWithoutLiveObjects() {
        StationRouteContinuationControllerSnapshot blocked = new StationRouteContinuationControllerSnapshot(
                Optional.of(new PhysicalToteId("head")), Optional.of(StationProcessingDispositionType.CONTINUE),
                Optional.empty(), Optional.empty(), Optional.of(new PhysicalToteId("head")),
                "queue is full", 0, 0, Optional.empty(), Optional.empty(), Optional.empty());
        assertTrue(blocked.blocked());
        assertEquals("queue is full", blocked.blockedReason());

        StationRouteContinuationControllerSnapshot terminal = new StationRouteContinuationControllerSnapshot(
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), "",
                0, 1, Optional.of(new PhysicalToteId("consume")),
                Optional.of(StationProcessingDispositionType.CONSUME), Optional.empty());
        assertTrue(terminal.lastHandledNextDestination().isEmpty());
        assertEquals(1, terminal.consumedAcknowledgementCount());

        StationRouteContinuationControllerSnapshot continued = new StationRouteContinuationControllerSnapshot(
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), "",
                1, 0, Optional.of(new PhysicalToteId("continue")),
                Optional.of(StationProcessingDispositionType.CONTINUE),
                Optional.of(destination(StationType.ADAPTING, "bench-2")));
        assertEquals(StationType.ADAPTING,
                continued.lastHandledNextDestination().orElseThrow().stationType());
        assertEquals("bench-2", continued.lastHandledNextDestination().orElseThrow().targetId());
    }

    private static StationRouteContinuationControllerSnapshot empty() {
        return new StationRouteContinuationControllerSnapshot(
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), "", 0, 0, Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static OperationalRouteDestination destination(StationType stationType, String targetId) {
        return new OperationalRouteDestination(stationType, targetId);
    }
}
