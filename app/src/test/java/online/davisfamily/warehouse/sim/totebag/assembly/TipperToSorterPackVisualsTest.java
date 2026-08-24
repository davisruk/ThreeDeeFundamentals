package online.davisfamily.warehouse.sim.totebag.assembly;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.behaviour.routing.RouteFollower;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.debug.SelectionInspectionRegistry;
import online.davisfamily.threedee.input.keyboard.InputState;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.matrices.Vec4;
import online.davisfamily.threedee.model.Mesh;
import online.davisfamily.threedee.path.LinearSegment3;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.rendering.TriangleRenderer;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.totebag.control.ToteTrackTipperFlowController;
import online.davisfamily.warehouse.sim.totebag.machine.SortingMachine;
import online.davisfamily.warehouse.sim.totebag.machine.TippingMachine;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

class TipperToSorterPackVisualsTest {

    @Test
    void shouldRegisterSameToteSourceIdempotently() {
        TriangleRenderer renderer = renderer();
        RouteSegment tipperSegment = new RouteSegment(
                "tipper",
                new LinearSegment3(
                        new Vec3(0f, 0f, 0f),
                        new Vec3(1f, 0f, 0f),
                        false));
        RenderableObject toteRenderable = renderable("tote-1");
        Tote tote = new Tote(
                "tote-1",
                new RouteFollower("tote-1", tipperSegment, 0f, 1d),
                toteRenderable,
                new Vec3(),
                0f);
        ToteLoadPlan loadPlan = new ToteLoadPlan(
                "tote-1",
                List.of(new PackPlan(
                        "pack-1",
                        "bag-1",
                        new PackDimensions(0.20f, 0.10f, 0.05f))));
        TipperTotePayload payload = new TipperTotePayload(
                tote,
                toteRenderable,
                0.10f,
                Map.of("pack-1", new Vec3(0f, 0.125f, 0f)));
        TippingMachine tippingMachine = new TippingMachine("tipper", 0d, 0d, 0d);
        SortingMachine sortingMachine = new SortingMachine("sorter", 0d);
        ToteTrackTipperFlowController flowController = new ToteTrackTipperFlowController(
                tote,
                loadPlan,
                tipperSegment,
                0.5f,
                -1.02f,
                tippingMachine,
                sortingMachine,
                0.01d);
        TipperModule tipperModule = new TipperModule(
                tote,
                tippingMachine,
                toteRenderable,
                renderable("tipper-assembly"),
                new Vec3(),
                new Vec3(),
                new Vec3(),
                new Vec3(),
                new Vec3(),
                -1.02f,
                0f);
        SortingModule sortingModule = new SortingModule(
                renderer,
                sortingMachine,
                new Vec3(),
                0f,
                new Vec3());
        List<RenderableObject> objects = new ArrayList<>();
        TipperToSorterPackVisuals visuals = new TipperToSorterPackVisuals(
                renderer,
                objects,
                new SelectionInspectionRegistry(),
                payload,
                loadPlan,
                0f,
                new TipperToSorterDischargeSeam());

        visuals.registerToteSource(payload, loadPlan);
        visuals.registerToteSource(payload, loadPlan);
        visuals.sync(flowController, tipperModule, sortingModule);
        RenderableObject firstPackRenderable = visuals.getPackRenderable("pack-1");
        visuals.sync(flowController, tipperModule, sortingModule);

        assertEquals(0, objects.size());
        assertSame(firstPackRenderable, visuals.getPackRenderable("pack-1"));
        assertTrue(toteRenderable.children.contains(firstPackRenderable));
        assertEquals(1L, toteRenderable.children.stream()
                .filter(child -> child == firstPackRenderable)
                .count());
    }

    private static TriangleRenderer renderer() {
        return new TriangleRenderer(
                new int[100 * 100],
                100,
                0,
                0,
                99,
                99,
                null,
                new InputState(),
                null);
    }

    private static RenderableObject renderable(String id) {
        return RenderableObject.create(
                id,
                null,
                new Mesh(
                        new Vec4[] {
                                new Vec4(0f, 0f, 0f, 1f),
                                new Vec4(0f, 0f, 0f, 1f),
                                new Vec4(0f, 0f, 0f, 1f)
                        },
                        new int[][] { {0, 1, 2} },
                        "anchor"),
                new Mat4.ObjectTransformation(0f, 0f, 0f, 0f, 0f, 0f, new Mat4()),
                triangleIndex -> 0,
                false);
    }
}
