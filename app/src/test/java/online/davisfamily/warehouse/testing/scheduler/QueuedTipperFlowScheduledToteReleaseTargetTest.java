package online.davisfamily.warehouse.testing.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
import online.davisfamily.warehouse.sim.machine.queue.MachineWaitQueueSnapshot;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperInputQueue;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperTotePayload;

class QueuedTipperFlowScheduledToteReleaseTargetTest {

    @Test
    void shouldDeferWhenQueueIsFull() {
        TestFixture fixture = new TestFixture(0);

        SchedulerCommandApplicationResult result = fixture.releaseTarget().release(releaseDecision("tote-b"), fixture.payload);

        assertFalse(result.applied());
        assertTrue(result.deferred());
        assertEquals("Tipper input queue is full", result.reason());
        assertEquals(0, fixture.objects.size());
        assertTrue(fixture.queue.snapshot().toteIds().isEmpty());
    }

    @Test
    void shouldAddRenderableAndTrackableObjectWhenReleased() {
        TestFixture fixture = new TestFixture(1);

        SchedulerCommandApplicationResult result = fixture.releaseTarget().release(releaseDecision("tote-b"), fixture.payload);

        assertTrue(result.applied());
        assertTrue(fixture.objects.contains(fixture.payload.getToteRenderable()));

        fixture.sim.update(0.05d);

        assertNotNull(fixture.payload.getTote().getLastSnapshot());
    }

    @Test
    void shouldNotAddRenderableTwice() {
        TestFixture fixture = new TestFixture(1);
        fixture.objects.add(fixture.payload.getToteRenderable());

        SchedulerCommandApplicationResult result = fixture.releaseTarget().release(releaseDecision("tote-b"), fixture.payload);

        assertTrue(result.applied());
        assertEquals(1, fixture.objects.size());
    }

    @Test
    void shouldEnqueuePayloadWithoutDirectTipperHandoff() {
        TestFixture fixture = new TestFixture(1);

        SchedulerCommandApplicationResult result = fixture.releaseTarget().release(releaseDecision("tote-b"), fixture.payload);

        assertTrue(result.applied());
        assertSame(fixture.payload, fixture.queue.peekPayload());
        MachineWaitQueueSnapshot snapshot = fixture.queue.snapshot();
        assertEquals(List.of("tote-b"), snapshot.toteIds());
        assertFalse(snapshot.canAccept());
    }

    private static final class TestFixture {
        private final SimulationWorld sim = new SimulationWorld();
        private final List<RenderableObject> objects = new ArrayList<>();
        private final TipperInputQueue queue;
        private final TipperTotePayload payload;

        private TestFixture(int capacity) {
            queue = new TipperInputQueue("tipper-input", capacity);
            payload = payload("tote-b");
        }

        private QueuedTipperFlowScheduledToteReleaseTarget releaseTarget() {
            return new QueuedTipperFlowScheduledToteReleaseTarget(sim, objects, queue);
        }
    }

    private static TipperTotePayload payload(String toteId) {
        Tote tote = new Tote(
                toteId,
                new RouteFollower(toteId, routeSegment(), 0f, 1.4d),
                RenderableObject.create(
                        toteId,
                        null,
                        anchorMesh(),
                        new Mat4.ObjectTransformation(0f, 0f, 0f, 0f, 0f, 0f, new Mat4()),
                        triangleIndex -> 0,
                        false),
                new Vec3(),
                0f);
        return new TipperTotePayload(tote, tote.getRenderable(), 0f, Map.of());
    }

    private static RouteSegment routeSegment() {
        return new RouteSegment(
                "infeed",
                new LinearSegment3(new Vec3(0f, 0f, 0f), new Vec3(2f, 0f, 0f), false));
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
