package online.davisfamily.warehouse.sim.dsp.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

class PhysicalToteAssignmentTest {

    @Test
    void shouldCreateActiveAssignment() {
        PhysicalToteAssignment assignment = activeAssignment();

        assertTrue(assignment.active());
        assertTrue(assignment.terminatedAt().isEmpty());
        assertTrue(assignment.endReason().isEmpty());
    }

    @Test
    void shouldTerminateAssignmentWithoutChangingItsIdentityOrSequence() {
        PhysicalToteAssignment active = activeAssignment();

        PhysicalToteAssignment terminated = active.terminate(
                Duration.ofSeconds(15),
                PhysicalToteAssignmentEndReason.CONSUMED_AT_P2P);

        assertTrue(active.active());
        assertFalse(terminated.active());
        assertEquals(active.sequenceNumber(), terminated.sequenceNumber());
        assertEquals(active.orderSheetKey(), terminated.orderSheetKey());
        assertEquals(active.physicalToteId(), terminated.physicalToteId());
        assertEquals(active.stage(), terminated.stage());
        assertEquals(Optional.of(Duration.ofSeconds(15)), terminated.terminatedAt());
        assertEquals(Optional.of(PhysicalToteAssignmentEndReason.CONSUMED_AT_P2P), terminated.endReason());
    }

    @Test
    void shouldRejectNegativeOrReversedSimulationTimes() {
        assertThrows(IllegalArgumentException.class,
                () -> PhysicalToteAssignment.active(
                        0,
                        sheetKey(),
                        toteId(),
                        PhysicalToteAssignmentStage.INBOUND_PACK,
                        Duration.ofSeconds(-1)));
        assertThrows(IllegalArgumentException.class,
                () -> activeAssignment().terminate(
                        Duration.ofSeconds(4),
                        PhysicalToteAssignmentEndReason.CONSUMED_AT_P2P));
        assertThrows(IllegalArgumentException.class,
                () -> new PhysicalToteAssignment(
                        0,
                        sheetKey(),
                        toteId(),
                        PhysicalToteAssignmentStage.INBOUND_PACK,
                        Duration.ofSeconds(5),
                        Optional.of(Duration.ofSeconds(-1)),
                        Optional.of(PhysicalToteAssignmentEndReason.CONSUMED_AT_P2P)));
    }

    @Test
    void shouldRejectPartialTerminationData() {
        assertThrows(IllegalArgumentException.class,
                () -> new PhysicalToteAssignment(
                        0,
                        sheetKey(),
                        toteId(),
                        PhysicalToteAssignmentStage.INBOUND_PACK,
                        Duration.ZERO,
                        Optional.of(Duration.ofSeconds(1)),
                        Optional.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> new PhysicalToteAssignment(
                        0,
                        sheetKey(),
                        toteId(),
                        PhysicalToteAssignmentStage.INBOUND_PACK,
                        Duration.ZERO,
                        Optional.empty(),
                        Optional.of(PhysicalToteAssignmentEndReason.ADVANCED_TO_NEXT_STAGE)));
    }

    @Test
    void shouldRejectTerminatingAssignmentTwice() {
        PhysicalToteAssignment terminated = activeAssignment().terminate(
                Duration.ofSeconds(10),
                PhysicalToteAssignmentEndReason.CONSUMED_AT_P2P);

        assertThrows(IllegalStateException.class,
                () -> terminated.terminate(
                        Duration.ofSeconds(11),
                        PhysicalToteAssignmentEndReason.OUTBOUND_TOTE_CLOSED));
    }

    private static PhysicalToteAssignment activeAssignment() {
        return PhysicalToteAssignment.active(
                7,
                sheetKey(),
                toteId(),
                PhysicalToteAssignmentStage.INBOUND_PACK,
                Duration.ofSeconds(5));
    }

    private static OrderSheetKey sheetKey() {
        return new OrderSheetKey("ORDER-A", 1);
    }

    private static PhysicalToteId toteId() {
        return new PhysicalToteId("TOTE-100");
    }
}
