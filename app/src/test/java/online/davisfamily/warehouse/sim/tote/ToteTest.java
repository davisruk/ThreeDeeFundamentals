package online.davisfamily.warehouse.sim.tote;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.behaviour.transformation.ClampedRotationBehaviour;
import online.davisfamily.threedee.behaviour.routing.RouteFollower;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.path.LinearSegment3;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.warehouse.rendering.model.tote.RenderableToteFactory;
import online.davisfamily.warehouse.rendering.model.tote.ToteGeometry;

class ToteTest {

    @Test
    void shouldOpenAndCloseRenderableLidsFromTote() {
        RenderableObject toteRenderable = RenderableToteFactory.createRenderableTote(
                "tote",
                null,
                new ToteGeometry(),
                true);
        Tote tote = new Tote(
                "tote",
                new RouteFollower("tote", routeSegment(), 0f, 1.0d),
                toteRenderable,
                new Vec3(),
                0f);

        RenderableObject leftLid = findChild(toteRenderable, "tote_LeftLid");
        RenderableObject rightLid = findChild(toteRenderable, "tote_RightLid");

        tote.openLids();

        assertTrue(leftLid.behaviours.stream().anyMatch(ClampedRotationBehaviour.class::isInstance));
        assertTrue(rightLid.behaviours.stream().anyMatch(ClampedRotationBehaviour.class::isInstance));

        tote.closeLids();

        assertTrue(leftLid.behaviours.stream().anyMatch(ClampedRotationBehaviour.class::isInstance));
        assertTrue(rightLid.behaviours.stream().anyMatch(ClampedRotationBehaviour.class::isInstance));
    }

    private RouteSegment routeSegment() {
        return new RouteSegment(
                "route",
                new LinearSegment3(new Vec3(0f, 0f, 0f), new Vec3(1f, 0f, 0f), false));
    }

    private RenderableObject findChild(RenderableObject parent, String id) {
        return parent.children.stream()
                .filter(child -> child.id.equals(id))
                .findFirst()
                .orElseThrow();
    }
}
