package online.davisfamily.warehouse.rendering.model.tracks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.behaviour.routing.RouteFollower.TravelDirection;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.path.LinearSegment3;
import online.davisfamily.warehouse.sim.transfer.TransferMotionConfig;
import online.davisfamily.warehouse.sim.transfer.TransferRoutingDecision;
import online.davisfamily.warehouse.sim.transfer.TransferTarget;

class WarehouseRouteBuilderTest {

    @Test
    void shouldAddInlineTransferWithOneSourceZoneAndMultipleTargetOpenings() {
        WarehouseRouteBuilder builder = new WarehouseRouteBuilder();
        RouteSegment source = segment("source", new Vec3(0f, 0f, 0f), new Vec3(10f, 0f, 0f));
        RouteSegment left = segment("left", new Vec3(5f, 0f, 1f), new Vec3(0f, 0f, 1f));
        RouteSegment right = segment("right", new Vec3(5f, 0f, -1f), new Vec3(10f, 0f, -1f));
        TransferTarget leftTarget = new TransferTarget(left, 1f, TravelDirection.FORWARD);
        TransferTarget rightTarget = new TransferTarget(right, 2f, TravelDirection.REVERSE);

        builder.addInlineTransfer(
                "inline",
                source,
                5f,
                1f,
                GuideSide.RIGHT,
                List.of(leftTarget, rightTarget),
                (tote, machine) -> Optional.of(TransferRoutingDecision.transferTo(rightTarget)),
                new TransferMotionConfig(0.35, 0f, 0f));

        WarehouseSegmentMetadata sourceMetadata = builder.getMetadata(source);
        WarehouseSegmentMetadata leftMetadata = builder.getMetadata(left);
        WarehouseSegmentMetadata rightMetadata = builder.getMetadata(right);

        assertEquals(1, sourceMetadata.getTransferZones().size());
        assertEquals(1, sourceMetadata.getGuideOpenings().size());
        assertEquals(1, leftMetadata.getGuideOpenings().size());
        assertEquals(1, rightMetadata.getGuideOpenings().size());
        assertSame(left, sourceMetadata.getTransferZones().getFirst().getTargetSegment());
        assertSame(leftTarget.segment(), sourceMetadata.getTransferZones().getFirst().getTargetSegment());
        assertNotNull(sourceMetadata.getTransferZones().getFirst().getTargetDecisionStrategy());
    }

    @Test
    void shouldRejectEmptyInlineTransferTargets() {
        WarehouseRouteBuilder builder = new WarehouseRouteBuilder();
        RouteSegment source = segment("source", new Vec3(0f, 0f, 0f), new Vec3(10f, 0f, 0f));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> builder.addInlineTransfer(
                        "inline",
                        source,
                        5f,
                        1f,
                        GuideSide.RIGHT,
                        List.of(),
                        (tote, machine) -> Optional.of(TransferRoutingDecision.continueOnCurrentRoute()),
                        new TransferMotionConfig(0.35, 0f, 0f)));

        assertEquals("targets must not be empty", ex.getMessage());
    }

    private static RouteSegment segment(String label, Vec3 start, Vec3 end) {
        return new RouteSegment(label, new LinearSegment3(start, end, false));
    }
}
