package online.davisfamily.warehouse.sim.transfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.behaviour.routing.RouteFollower.TravelDirection;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.path.LinearSegment3;
import online.davisfamily.warehouse.sim.transfer.TransferZoneMachine.TransferDecision;

class TransferRoutingDecisionTest {

    @Test
    void shouldCreateContinueDecision() {
        TransferRoutingDecision decision = TransferRoutingDecision.continueOnCurrentRoute();

        assertTrue(decision.isContinueOnCurrentRoute());
        assertFalse(decision.isTransfer());
        assertNull(decision.transferTarget());
        assertEquals(TransferOrientationPolicy.PRESERVE_TOTE_ORIENTATION, decision.orientationPolicy());
    }

    @Test
    void shouldCreateTransferDecision() {
        TransferTarget target = target();

        TransferRoutingDecision decision = TransferRoutingDecision.transferTo(target);

        assertFalse(decision.isContinueOnCurrentRoute());
        assertTrue(decision.isTransfer());
        assertSame(target, decision.transferTarget());
        assertEquals(TransferOrientationPolicy.PRESERVE_TOTE_ORIENTATION, decision.orientationPolicy());
    }

    @Test
    void shouldCreateTransferDecisionWithExplicitOrientationPolicy() {
        TransferTarget target = target();

        TransferRoutingDecision decision = TransferRoutingDecision.transferTo(
                target,
                TransferOrientationPolicy.ALIGN_TO_TARGET_TRAVEL);

        assertFalse(decision.isContinueOnCurrentRoute());
        assertTrue(decision.isTransfer());
        assertSame(target, decision.transferTarget());
        assertEquals(TransferOrientationPolicy.ALIGN_TO_TARGET_TRAVEL, decision.orientationPolicy());
    }

    @Test
    void shouldRejectNullTransferTarget() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> TransferRoutingDecision.transferTo(null));

        assertEquals("target must not be null", ex.getMessage());
    }

    @Test
    void shouldRejectNullOrientationPolicy() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> TransferRoutingDecision.transferTo(target(), null));

        assertEquals("orientationPolicy must not be null", ex.getMessage());
    }

    @Test
    void shouldCreateContinueDecisionFromLegacyDecision() {
        TransferRoutingDecision decision = TransferRoutingDecision.fromDecision(
                TransferDecision.CONTINUE,
                target());

        assertTrue(decision.isContinueOnCurrentRoute());
        assertFalse(decision.isTransfer());
        assertNull(decision.transferTarget());
        assertEquals(TransferOrientationPolicy.PRESERVE_TOTE_ORIENTATION, decision.orientationPolicy());
    }

    @Test
    void shouldCreateTransferDecisionFromLegacyBranchDecision() {
        TransferTarget target = target();

        TransferRoutingDecision decision = TransferRoutingDecision.fromDecision(
                TransferDecision.BRANCH,
                target);

        assertFalse(decision.isContinueOnCurrentRoute());
        assertTrue(decision.isTransfer());
        assertSame(target, decision.transferTarget());
        assertEquals(TransferOrientationPolicy.PRESERVE_TOTE_ORIENTATION, decision.orientationPolicy());
    }

    @Test
    void shouldRejectNullLegacyDecision() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> TransferRoutingDecision.fromDecision(null, target()));

        assertEquals("decision must not be null", ex.getMessage());
    }

    private static TransferTarget target() {
        RouteSegment segment = new RouteSegment(
                "target",
                new LinearSegment3(
                        new Vec3(0f, 0f, 0f),
                        new Vec3(5f, 0f, 0f),
                        false));
        return new TransferTarget(segment, 1.5f, TravelDirection.FORWARD);
    }
}
