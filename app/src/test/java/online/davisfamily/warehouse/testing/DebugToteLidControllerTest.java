package online.davisfamily.warehouse.testing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.behaviour.routing.RouteFollower;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.matrices.Vec4;
import online.davisfamily.threedee.model.Mesh;
import online.davisfamily.threedee.path.LinearSegment3;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.tote.Tote.ToteMotionState;

class DebugToteLidControllerTest {

    @Test
    void shouldRejectNullInputs() {
        Tote tote = createTote("tote-a");

        assertThrows(IllegalArgumentException.class, () -> new DebugToteLidController(null));
        assertThrows(IllegalArgumentException.class, () -> new DebugToteLidController(Arrays.asList(tote, null)));
    }

    @Test
    void shouldOpenLidsAfterPrimaryToteStartsMoving() {
        Tote tote = createTote("tote-a");
        SimulationWorld sim = new SimulationWorld();
        sim.addTrackableObject(tote);
        sim.addController(new DebugToteLidController(List.of(tote)));

        assertFalse(tote.areLidsOpen());

        sim.update(0.10d);

        assertTrue(tote.areLidsOpen());
    }

    @Test
    void shouldKeepLidsClosedWhileToteIsHeld() {
        Tote tote = createTote("tote-a");
        tote.setInteractionMode(ToteMotionState.HELD);
        SimulationWorld sim = new SimulationWorld();
        sim.addTrackableObject(tote);
        sim.addController(new DebugToteLidController(List.of(tote)));

        sim.update(0.10d);
        sim.update(0.10d);

        assertFalse(tote.areLidsOpen());
    }

    @Test
    void shouldOpenLidsWhenHeldToteBeginsMoving() {
        Tote tote = createTote("tote-a");
        tote.setInteractionMode(ToteMotionState.HELD);
        SimulationWorld sim = new SimulationWorld();
        sim.addTrackableObject(tote);
        sim.addController(new DebugToteLidController(List.of(tote)));

        sim.update(0.10d);
        assertFalse(tote.areLidsOpen());

        tote.setInteractionMode(ToteMotionState.MOVING);
        sim.update(0.10d);

        assertTrue(tote.areLidsOpen());
    }

    private static Tote createTote(String toteId) {
        RouteSegment segment = new RouteSegment(
                "route",
                new LinearSegment3(new Vec3(0f, 0f, 0f), new Vec3(2f, 0f, 0f), false));
        RenderableObject renderable = RenderableObject.create(
                toteId,
                null,
                anchorMesh(),
                new Mat4.ObjectTransformation(0f, 0f, 0f, 0f, 0f, 0f, new Mat4()),
                triangleIndex -> 0,
                false);
        return new Tote(
                toteId,
                new RouteFollower(toteId, segment, 0f, 1.0d),
                renderable,
                new Vec3(),
                0f);
    }

    private static Mesh anchorMesh() {
        return new Mesh(
                new Vec4[] {
                        new Vec4(0f, 0f, 0f, 1f),
                        new Vec4(0f, 0f, 0f, 1f),
                        new Vec4(0f, 0f, 0f, 1f)
                },
                new int[][] { {0, 1, 2} },
                "anchor");
    }
}
