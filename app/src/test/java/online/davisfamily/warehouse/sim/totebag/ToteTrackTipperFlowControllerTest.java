package online.davisfamily.warehouse.sim.totebag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.behaviour.routing.RouteFollower;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.matrices.Vec4;
import online.davisfamily.threedee.model.Mesh;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.path.LinearSegment3;
import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.tote.Tote.ToteMotionState;
import online.davisfamily.warehouse.sim.totebag.control.ToteTrackTipperFlowController;
import online.davisfamily.warehouse.sim.totebag.machine.SortingMachine;
import online.davisfamily.warehouse.sim.totebag.machine.TippingMachine;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlanProvider;

class ToteTrackTipperFlowControllerTest {

    @Test
    void shouldAcceptNextToteAfterReleasingPreviousOne() {
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

        PackDimensions packDimensions = new PackDimensions(0.20f, 0.10f, 0.08f);
        ToteLoadPlan toteAPlan = new ToteLoadPlan(
                "tote-a",
                List.of(new PackPlan("pack-a1", "bag-a", packDimensions)));
        ToteLoadPlan toteBPlan = new ToteLoadPlan(
                "tote-b",
                List.of(new PackPlan("pack-b1", "bag-b", packDimensions)));
        ToteLoadPlanProvider toteLoadPlanProvider = toteId -> {
            if (toteAPlan.getToteId().equals(toteId)) {
                return toteAPlan;
            }
            if (toteBPlan.getToteId().equals(toteId)) {
                return toteBPlan;
            }
            return null;
        };

        Tote toteA = createTote("tote-a", infeedSegment);
        Tote toteB = createTote("tote-b", infeedSegment);
        TippingMachine tippingMachine = new TippingMachine("tipper", 0.20d, 0.10d, 0.10d);
        SortingMachine sortingMachine = new SortingMachine("sorter", 0.10d);
        ToteTrackTipperFlowController controller = new ToteTrackTipperFlowController(
                toteA,
                toteLoadPlanProvider,
                tipperSegment,
                0.625f,
                -1.02f,
                tippingMachine,
                sortingMachine,
                0.20d);

        SimulationWorld sim = new SimulationWorld();
        sim.addTrackableObject(toteA);
        sim.addSimObject(tippingMachine);
        sim.addSimObject(sortingMachine);
        sim.addController(controller);

        boolean sawFirstCapture = false;
        boolean sawReadyForSecond = false;
        for (int i = 0; i < 250; i++) {
            sim.update(0.05d);
            if (controller.isToteCaptured()) {
                sawFirstCapture = true;
            }
            if (sawFirstCapture && controller.canAcceptNextTote()) {
                sawReadyForSecond = true;
                break;
            }
        }

        assertTrue(sawFirstCapture);
        assertTrue(sawReadyForSecond);

        sim.addTrackableObject(toteB);
        controller.acceptNextTote(toteB);

        assertFalse(controller.canAcceptNextTote());

        boolean sawSecondCapture = false;
        for (int i = 0; i < 120; i++) {
            sim.update(0.05d);
            if (controller.isToteCaptured()) {
                sawSecondCapture = true;
                assertEquals("tote-b", tippingMachine.getActiveToteId());
                break;
            }
        }

        assertTrue(sawSecondCapture);
    }

    @Test
    void shouldHoldToteUntilAdmissionAllowsTipping() {
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

        PackDimensions packDimensions = new PackDimensions(0.20f, 0.10f, 0.08f);
        ToteLoadPlan toteLoadPlan = new ToteLoadPlan(
                "tote-a",
                List.of(new PackPlan("pack-a1", "bag-a", packDimensions)));
        ToteLoadPlanProvider toteLoadPlanProvider = toteId -> toteLoadPlan.getToteId().equals(toteId) ? toteLoadPlan : null;

        Tote toteA = createTote("tote-a", infeedSegment);
        TippingMachine tippingMachine = new TippingMachine("tipper", 0.20d, 0.10d, 0.10d);
        SortingMachine sortingMachine = new SortingMachine("sorter", 0.10d);
        ToteTrackTipperFlowController controller = new ToteTrackTipperFlowController(
                toteA,
                toteLoadPlanProvider,
                tipperSegment,
                0.625f,
                -1.02f,
                tippingMachine,
                sortingMachine,
                0.20d);
        controller.setToteAdmissionPredicate(ignored -> false);

        SimulationWorld sim = new SimulationWorld();
        sim.addTrackableObject(toteA);
        sim.addSimObject(tippingMachine);
        sim.addSimObject(sortingMachine);
        sim.addController(controller);

        boolean sawHeldWhileBlocked = false;
        for (int i = 0; i < 80; i++) {
            sim.update(0.05d);
            if (controller.isToteCaptured()) {
                sawHeldWhileBlocked = true;
                break;
            }
            if (toteA.getInteractionMode() == ToteMotionState.HELD
                    && tippingMachine.getActiveToteId() == null
                    && toteA.getLastSnapshot() != null
                    && toteA.getLastSnapshot().currentSegment() == tipperSegment) {
                sawHeldWhileBlocked = true;
                break;
            }
        }

        assertTrue(sawHeldWhileBlocked);
        assertFalse(controller.isToteCaptured());
        assertEquals(null, tippingMachine.getActiveToteId());

        controller.setToteAdmissionPredicate(ignored -> true);

        boolean sawCaptureAfterAdmission = false;
        for (int i = 0; i < 120; i++) {
            sim.update(0.05d);
            if (controller.isToteCaptured()) {
                sawCaptureAfterAdmission = true;
                assertEquals("tote-a", tippingMachine.getActiveToteId());
                break;
            }
        }

        assertTrue(sawCaptureAfterAdmission);
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
}
