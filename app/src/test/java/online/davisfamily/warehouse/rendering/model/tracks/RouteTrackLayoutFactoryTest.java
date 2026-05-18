package online.davisfamily.warehouse.rendering.model.tracks;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.behaviour.routing.RouteFollower.TravelDirection;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.path.LinearSegment3;
import online.davisfamily.warehouse.rendering.model.tote.ToteEnvelope;
import online.davisfamily.warehouse.sim.transfer.TransferMotionConfig;
import online.davisfamily.warehouse.sim.transfer.TransferRoutingDecision;
import online.davisfamily.warehouse.sim.transfer.TransferTarget;

class RouteTrackLayoutFactoryTest {

    @Test
    void shouldSuppressGuideSpansAcrossTransferIntervalsWhenRequested() {
        WarehouseRouteBuilder builder = new WarehouseRouteBuilder();
        RouteSegment source = segment("source", new Vec3(0f, 0f, 0f), new Vec3(10f, 0f, 0f));
        RouteSegment target = segment("target", new Vec3(5f, 0f, 1f), new Vec3(10f, 0f, 1f));

        builder.addInlineTransfer(
                "inline",
                source,
                5f,
                2f,
                GuideSide.RIGHT,
                List.of(new TransferTarget(target, 0f, TravelDirection.FORWARD)),
                (tote, machine) -> Optional.of(TransferRoutingDecision.transferTo(
                        new TransferTarget(target, 0f, TravelDirection.FORWARD))),
                new TransferMotionConfig(0.35, 0f, 0f));

        TrackSpec spec = new TrackSpec(
                new ToteEnvelope(0.6f, 0.8f, 0.5f),
                0.03f,
                0.04f,
                0f,
                true,
                0.05f,
                0.01f,
                0.005f,
                0.5f,
                1f,
                TrackDriveType.ROLLER,
                true,
                0.08f,
                0.01f,
                0.025f,
                0.018f,
                0.01f,
                0.012f,
                0.08f,
                0.22f,
                0.05f,
                0.004f,
                true,
                true,
                0.08f);

        RouteTrackLayout layout = RouteTrackLayoutFactory.create(source, builder.getMetadata(source));
        List<TrackSpan> rightSpans = RouteTrackLayoutFactory.createGuideSpans(layout, spec, GuideSide.RIGHT);

        assertEquals(2, rightSpans.size());
        assertEquals(0f, rightSpans.get(0).getStartDistance(), 0.0001f);
        assertEquals(4f, rightSpans.get(0).getEndDistance(), 0.0001f);
        assertEquals(6f, rightSpans.get(1).getStartDistance(), 0.0001f);
        assertEquals(10f, rightSpans.get(1).getEndDistance(), 0.0001f);
    }

    private static RouteSegment segment(String label, Vec3 start, Vec3 end) {
        return new RouteSegment(label, new LinearSegment3(start, end, false));
    }
}
