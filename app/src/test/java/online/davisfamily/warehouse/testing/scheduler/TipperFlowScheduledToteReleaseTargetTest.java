package online.davisfamily.warehouse.testing.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
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
import online.davisfamily.warehouse.sim.dsp.runtime.SchedulerCommandApplicationResult;
import online.davisfamily.warehouse.sim.dsp.scheduler.ReleaseDecision;
import online.davisfamily.warehouse.sim.dsp.scheduler.ReleaseOrderCommand;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperTotePayload;
import online.davisfamily.warehouse.sim.totebag.control.ToteTrackTipperFlowController;
import online.davisfamily.warehouse.sim.totebag.machine.SortingMachine;
import online.davisfamily.warehouse.sim.totebag.machine.TippingMachine;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlanProvider;

class TipperFlowScheduledToteReleaseTargetTest {
    private static final PackDimensions TEST_PACK = new PackDimensions(0.20f, 0.10f, 0.08f);

    @Test
    void shouldDeferWhenTipperCannotAcceptRelease() {
        TestFixture fixture = new TestFixture();
        TipperFlowScheduledToteReleaseTarget releaseTarget = fixture.releaseTarget();

        SchedulerCommandApplicationResult result = releaseTarget.release(releaseDecision("order-2"), fixture.secondPayload);

        assertFalse(result.applied());
        assertTrue(result.deferred());
        assertEquals(0, fixture.objects.size());
        assertTrue(fixture.controller.isToteCaptured() || !fixture.controller.canAcceptNextTote());
    }

    @Test
    void shouldAddRenderableAndTrackableObjectWhenReleased() {
        TestFixture fixture = new TestFixture();
        fixture.advanceUntilReadyForNextTote();
        TipperFlowScheduledToteReleaseTarget releaseTarget = fixture.releaseTarget();

        SchedulerCommandApplicationResult result = releaseTarget.release(releaseDecision("order-2"), fixture.secondPayload);

        assertTrue(result.applied());
        assertFalse(result.deferred());
        assertTrue(fixture.objects.contains(fixture.secondPayload.getToteRenderable()));

        fixture.sim.update(0.05d);

        assertNotNull(fixture.secondPayload.getTote().getLastSnapshot());
    }

    @Test
    void shouldNotAddRenderableTwice() {
        TestFixture fixture = new TestFixture();
        fixture.advanceUntilReadyForNextTote();
        fixture.objects.add(fixture.secondPayload.getToteRenderable());
        TipperFlowScheduledToteReleaseTarget releaseTarget = fixture.releaseTarget();

        SchedulerCommandApplicationResult result = releaseTarget.release(releaseDecision("order-2"), fixture.secondPayload);

        assertTrue(result.applied());
        assertEquals(1, fixture.objects.size());
    }

    @Test
    void shouldPassToteToTipperFlowController() {
        TestFixture fixture = new TestFixture();
        fixture.advanceUntilReadyForNextTote();
        TipperFlowScheduledToteReleaseTarget releaseTarget = fixture.releaseTarget();

        SchedulerCommandApplicationResult result = releaseTarget.release(releaseDecision("order-2"), fixture.secondPayload);

        assertTrue(result.applied());
        assertFalse(fixture.controller.canAcceptNextTote());

        boolean sawSecondCapture = false;
        for (int i = 0; i < 120; i++) {
            fixture.sim.update(0.05d);
            if (fixture.controller.isToteCaptured()) {
                sawSecondCapture = true;
                assertEquals("tote-b", fixture.tippingMachine.getActiveToteId());
                break;
            }
        }

        assertTrue(sawSecondCapture);
    }

    private static final class TestFixture {
        private final SimulationWorld sim = new SimulationWorld();
        private final List<RenderableObject> objects = new ArrayList<>();
        private final ToteTrackTipperFlowController controller;
        private final TippingMachine tippingMachine;
        private final TipperTotePayload secondPayload;

        private TestFixture() {
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
            tippingMachine = new TippingMachine("tipper", 0.20d, 0.10d, 0.10d);
            SortingMachine sortingMachine = new SortingMachine("sorter", 0.10d);
            controller = new ToteTrackTipperFlowController(
                    firstTote,
                    toteLoadPlanProvider,
                    tipperSegment,
                    0.625f,
                    -1.02f,
                    tippingMachine,
                    sortingMachine,
                    0.20d);

            sim.addTrackableObject(firstTote);
            sim.addSimObject(tippingMachine);
            sim.addSimObject(sortingMachine);
            sim.addController(controller);

            secondPayload = new TipperTotePayload(
                    secondTote,
                    secondTote.getRenderable(),
                    0f,
                    Map.of());
        }

        private TipperFlowScheduledToteReleaseTarget releaseTarget() {
            return new TipperFlowScheduledToteReleaseTarget(sim, objects, controller);
        }

        private void advanceUntilReadyForNextTote() {
            for (int i = 0; i < 250; i++) {
                sim.update(0.05d);
                if (controller.canAcceptNextTote()) {
                    return;
                }
            }
            throw new AssertionError("Tipper flow did not become ready for another tote");
        }
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

    private static ReleaseDecision releaseDecision(String orderId) {
        return new ReleaseDecision(
                orderId,
                "sc-1",
                online.davisfamily.warehouse.sim.dsp.model.StartLocation.OSR,
                new online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements(false, false, false, true, false,
                        online.davisfamily.warehouse.sim.dsp.model.StartLocation.OSR),
                new ReleaseOrderCommand(orderId, "sc-1", online.davisfamily.warehouse.sim.dsp.model.StartLocation.OSR));
    }
}
