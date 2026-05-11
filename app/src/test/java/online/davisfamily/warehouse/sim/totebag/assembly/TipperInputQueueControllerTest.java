package online.davisfamily.warehouse.sim.totebag.assembly;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

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
import online.davisfamily.warehouse.sim.totebag.control.ToteTrackTipperFlowController;
import online.davisfamily.warehouse.sim.totebag.machine.SortingMachine;
import online.davisfamily.warehouse.sim.totebag.machine.TippingMachine;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlanProvider;

class TipperInputQueueControllerTest {
    private static final PackDimensions TEST_PACK = new PackDimensions(0.20f, 0.10f, 0.08f);

    @Test
    void shouldRejectNullInputs() {
        ToteTrackTipperFlowController controller = fixture().controller;
        TipperInputQueue queue = new TipperInputQueue("tipper-input", 1);

        assertThrows(IllegalArgumentException.class, () -> new TipperInputQueueController(null, controller));
        assertThrows(IllegalArgumentException.class, () -> new TipperInputQueueController(queue, null));
    }

    @Test
    void shouldLeavePayloadQueuedUntilTipperFlowCanAccept() {
        TestFixture fixture = fixture();
        fixture.queue.enqueue(fixture.secondPayload);

        for (int i = 0; i < 10; i++) {
            fixture.sim.update(0.05d);
        }

        assertEquals(List.of("tote-b"), fixture.queue.snapshot().toteIds());
    }

    @Test
    void shouldDrainQueueIntoTipperFlowWhenReady() {
        TestFixture fixture = fixture();
        fixture.queue.enqueue(fixture.secondPayload);

        boolean sawQueueDrain = false;
        boolean sawSecondCapture = false;
        for (int i = 0; i < 250; i++) {
            fixture.sim.update(0.05d);
            if (!sawQueueDrain && fixture.queue.snapshot().toteIds().isEmpty()) {
                sawQueueDrain = true;
            }
            if (fixture.controller.isToteCaptured() && "tote-b".equals(fixture.tippingMachine.getActiveToteId())) {
                sawSecondCapture = true;
                break;
            }
        }

        assertTrue(sawQueueDrain);
        assertTrue(sawSecondCapture);
        assertNull(fixture.queue.peekPayload());
        assertFalse(fixture.controller.canAcceptNextTote());
    }

    private static TestFixture fixture() {
        RouteSegment infeedSegment = new RouteSegment(
                "infeed",
                new LinearSegment3(new Vec3(0f, 0f, 0f), new Vec3(2f, 0f, 0f), false));
        RouteSegment tipperSegment = new RouteSegment(
                "tipper",
                new LinearSegment3(new Vec3(2f, 0f, 0f), new Vec3(3.25f, 0f, 0f), false));
        RouteSegment exitSegment = new RouteSegment(
                "exit",
                new LinearSegment3(new Vec3(3.25f, 0f, 0f), new Vec3(5f, 0f, 0f), false));
        infeedSegment.connectTo(tipperSegment);
        tipperSegment.connectTo(exitSegment);

        ToteLoadPlan firstPlan = new ToteLoadPlan(
                "tote-a",
                List.of(new PackPlan("pack-a1", "bag-a", TEST_PACK)));
        ToteLoadPlan secondPlan = new ToteLoadPlan(
                "tote-b",
                List.of(new PackPlan("pack-b1", "bag-b", TEST_PACK)));
        ToteLoadPlanProvider toteLoadPlanProvider = toteId -> {
            if (firstPlan.getToteId().equals(toteId)) {
                return firstPlan;
            }
            if (secondPlan.getToteId().equals(toteId)) {
                return secondPlan;
            }
            return null;
        };

        Tote firstTote = createTote("tote-a", infeedSegment);
        Tote secondTote = createTote("tote-b", infeedSegment);
        TippingMachine tippingMachine = new TippingMachine("tipper", 0.20d, 0.10d, 0.10d);
        SortingMachine sortingMachine = new SortingMachine("sorter", 0.10d);
        ToteTrackTipperFlowController controller = new ToteTrackTipperFlowController(
                firstTote,
                toteLoadPlanProvider,
                tipperSegment,
                0.625f,
                -1.02f,
                tippingMachine,
                sortingMachine,
                0.20d);

        TipperInputQueue queue = new TipperInputQueue("tipper-input", 1);
        TipperInputQueueController queueController = new TipperInputQueueController(queue, controller);

        SimulationWorld sim = new SimulationWorld();
        sim.addTrackableObject(firstTote);
        sim.addTrackableObject(secondTote);
        sim.addSimObject(tippingMachine);
        sim.addSimObject(sortingMachine);
        sim.addController(controller);
        sim.addController(queueController);

        TipperTotePayload secondPayload = new TipperTotePayload(
                secondTote,
                secondTote.getRenderable(),
                0f,
                Map.of());

        return new TestFixture(sim, controller, tippingMachine, queue, secondPayload);
    }

    private static Tote createTote(String toteId, RouteSegment infeedSegment) {
        RenderableObject toteRenderable = RenderableObject.create(
                toteId,
                null,
                anchorMesh(),
                new Mat4.ObjectTransformation(0f, 0f, 0f, 0f, 0f, 0f, new Mat4()),
                triangleIndex -> 0,
                false);
        return new Tote(
                toteId,
                new RouteFollower(toteId, infeedSegment, 0f, 1.4d),
                toteRenderable,
                new Vec3(0f, 0f, 0f),
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

    private record TestFixture(
            SimulationWorld sim,
            ToteTrackTipperFlowController controller,
            TippingMachine tippingMachine,
            TipperInputQueue queue,
            TipperTotePayload secondPayload) {
    }
}
