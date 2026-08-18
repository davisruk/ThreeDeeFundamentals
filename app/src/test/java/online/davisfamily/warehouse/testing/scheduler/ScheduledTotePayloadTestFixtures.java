package online.davisfamily.warehouse.testing.scheduler;

import java.util.Map;

import online.davisfamily.threedee.behaviour.routing.RouteFollower;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.matrices.Vec4;
import online.davisfamily.threedee.model.Mesh;
import online.davisfamily.threedee.path.LinearSegment3;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperTotePayload;

final class ScheduledTotePayloadTestFixtures {

    private ScheduledTotePayloadTestFixtures() {
    }

    static TipperTotePayload payload(String toteId) {
        RenderableObject renderable = RenderableObject.create(
                toteId,
                null,
                anchorMesh(),
                new Mat4.ObjectTransformation(0f, 0f, 0f, 0f, 0f, 0f, new Mat4()),
                triangleIndex -> 0,
                false);
        Tote tote = new Tote(
                toteId,
                new RouteFollower(
                        toteId,
                        new RouteSegment(
                                "infeed",
                                new LinearSegment3(
                                        new Vec3(0f, 0f, 0f),
                                        new Vec3(2f, 0f, 0f),
                                        false)),
                        0f,
                        1d),
                renderable,
                new Vec3(),
                0f);
        return new TipperTotePayload(tote, renderable, 0f, Map.of());
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
