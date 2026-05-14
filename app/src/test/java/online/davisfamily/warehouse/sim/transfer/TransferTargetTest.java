package online.davisfamily.warehouse.sim.transfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.behaviour.routing.RouteFollower.TravelDirection;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.path.LinearSegment3;

class TransferTargetTest {

    @Test
    void shouldCreateValidTransferTarget() {
        RouteSegment segment = segment(5f);

        TransferTarget target = new TransferTarget(segment, 2.5f, TravelDirection.REVERSE);

        assertSame(segment, target.segment());
        assertEquals(2.5f, target.entryDistance());
        assertEquals(TravelDirection.REVERSE, target.travelDirection());
    }

    @Test
    void shouldRejectNullSegment() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new TransferTarget(null, 0f, TravelDirection.FORWARD));

        assertEquals("segment must not be null", ex.getMessage());
    }

    @Test
    void shouldRejectNegativeEntryDistance() {
        RouteSegment segment = segment(5f);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new TransferTarget(segment, -0.01f, TravelDirection.FORWARD));

        assertEquals("entryDistance must be within the target segment length", ex.getMessage());
    }

    @Test
    void shouldRejectEntryDistanceBeyondSegmentLength() {
        RouteSegment segment = segment(5f);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new TransferTarget(segment, 5.01f, TravelDirection.FORWARD));

        assertEquals("entryDistance must be within the target segment length", ex.getMessage());
    }

    @Test
    void shouldRejectNullTravelDirection() {
        RouteSegment segment = segment(5f);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new TransferTarget(segment, 0f, null));

        assertEquals("travelDirection must not be null", ex.getMessage());
    }

    private static RouteSegment segment(float length) {
        return new RouteSegment(
                "target",
                new LinearSegment3(
                        new Vec3(0f, 0f, 0f),
                        new Vec3(length, 0f, 0f),
                        false));
    }
}
