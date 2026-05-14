package online.davisfamily.warehouse.sim.tote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.behaviour.transformation.ClampedRotationBehaviour;
import online.davisfamily.threedee.behaviour.routing.RouteFollower;
import online.davisfamily.threedee.behaviour.routing.RouteFollower.TravelDirection;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.path.LinearSegment3;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.warehouse.rendering.model.tote.RenderableToteFactory;
import online.davisfamily.warehouse.rendering.model.tote.ToteGeometry;
import online.davisfamily.warehouse.sim.transfer.TransferMotionConfig;
import online.davisfamily.warehouse.sim.transfer.TransferZoneMachine;

class ToteTest {

    @Test
    void shouldReportLidsClosedByDefault() {
        Tote tote = tote();

        assertFalse(tote.areLidsOpen());
    }

    @Test
    void shouldReportLidsOpenAfterOpening() {
        Tote tote = tote();

        tote.openLids();

        assertTrue(tote.areLidsOpen());
    }

    @Test
    void shouldReportLidsClosedAfterClosing() {
        Tote tote = tote();
        tote.openLids();

        tote.closeLids();

        assertFalse(tote.areLidsOpen());
    }

    @Test
    void shouldOpenAndCloseRenderableLidsFromTote() {
        Tote tote = tote();
        RenderableObject toteRenderable = tote.getRenderable();

        RenderableObject leftLid = findChild(toteRenderable, "tote_LeftLid");
        RenderableObject rightLid = findChild(toteRenderable, "tote_RightLid");

        tote.openLids();

        assertTrue(leftLid.behaviours.stream().anyMatch(ClampedRotationBehaviour.class::isInstance));
        assertTrue(rightLid.behaviours.stream().anyMatch(ClampedRotationBehaviour.class::isInstance));

        tote.closeLids();

        assertTrue(leftLid.behaviours.stream().anyMatch(ClampedRotationBehaviour.class::isInstance));
        assertTrue(rightLid.behaviours.stream().anyMatch(ClampedRotationBehaviour.class::isInstance));
    }

    @Test
    void shouldCompleteTransferUsingExplicitTargetTravelDirection() {
        Tote tote = tote();
        RouteSegment targetSegment = new RouteSegment(
                "target",
                new LinearSegment3(new Vec3(2f, 0f, 0f), new Vec3(1f, 0f, 0f), false));
        TransferMotionConfig motionConfig = new TransferMotionConfig(0.35, 0f, 0f);
        TransferZoneMachine machine = new TransferZoneMachine("machine", "approach", "window", null);
        SimulationContext context = new SimulationContext();

        tote.update(context, 0d);
        tote.reserveForTransfer(machine);
        tote.beginTransfer(
                "machine",
                targetSegment,
                tote.getRouteFollower().getCurrentSegment(),
                0f,
                0f,
                TravelDirection.REVERSE,
                motionConfig);

        tote.update(context, 0d);
        for (int i = 0; i < 10 && tote.getInteractionMode() == Tote.ToteMotionState.TRANSFERRING; i++) {
            tote.update(context, 0.5d);
        }

        assertEquals(Tote.ToteMotionState.MOVING, tote.getInteractionMode());
        assertEquals(targetSegment, tote.getRouteFollower().getCurrentSegment());
        assertEquals(TravelDirection.REVERSE, tote.getRouteFollower().getTravelDirection());
    }

    private RouteSegment routeSegment() {
        return new RouteSegment(
                "route",
                new LinearSegment3(new Vec3(0f, 0f, 0f), new Vec3(1f, 0f, 0f), false));
    }

    private Tote tote() {
        RenderableObject toteRenderable = RenderableToteFactory.createRenderableTote(
                "tote",
                null,
                new ToteGeometry(),
                true);
        return new Tote(
                "tote",
                new RouteFollower("tote", routeSegment(), 0f, 1.0d),
                toteRenderable,
                new Vec3(),
                0f);
    }

    private RenderableObject findChild(RenderableObject parent, String id) {
        return parent.children.stream()
                .filter(child -> child.id.equals(id))
                .findFirst()
                .orElseThrow();
    }
}
