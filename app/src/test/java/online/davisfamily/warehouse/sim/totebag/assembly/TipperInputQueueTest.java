package online.davisfamily.warehouse.sim.totebag.assembly;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import online.davisfamily.warehouse.sim.machine.queue.MachineWaitQueueSnapshot;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.tote.Tote.ToteMotionState;

class TipperInputQueueTest {

    @Test
    void shouldEnqueueAndDequeuePayloadsInFifoOrder() {
        TipperInputQueue queue = new TipperInputQueue("tipper-input", 2);
        TipperTotePayload first = payload("tote-a");
        TipperTotePayload second = payload("tote-b");

        queue.enqueue(first);
        queue.enqueue(second);

        assertSame(first, queue.peekPayload());
        assertSame(first, queue.dequeuePayload());
        assertSame(second, queue.peekPayload());
        assertSame(second, queue.dequeuePayload());
        assertNull(queue.peekPayload());
        assertNull(queue.dequeuePayload());
    }

    @Test
    void shouldReflectUnderlyingCapacity() {
        TipperInputQueue queue = new TipperInputQueue("tipper-input", 1);

        assertTrue(queue.canAccept());
        queue.enqueue(payload("tote-a"));
        assertFalse(queue.canAccept());
    }

    @Test
    void shouldExposeSnapshot() {
        TipperInputQueue queue = new TipperInputQueue("tipper-input", 2);
        queue.enqueue(payload("tote-a"));

        MachineWaitQueueSnapshot snapshot = queue.snapshot();

        assertEquals("tipper-input", snapshot.id());
        assertEquals(2, snapshot.capacity());
        assertEquals(java.util.List.of("tote-a"), snapshot.toteIds());
        assertTrue(snapshot.canAccept());
    }

    @Test
    void shouldHoldToteWhenQueued() {
        TipperInputQueue queue = new TipperInputQueue("tipper-input", 1);
        TipperTotePayload payload = payload("tote-a");

        queue.enqueue(payload);

        assertEquals(ToteMotionState.HELD, payload.getTote().getInteractionMode());
    }

    @Test
    void shouldRejectNullPayload() {
        TipperInputQueue queue = new TipperInputQueue("tipper-input", 1);

        assertThrows(IllegalArgumentException.class, () -> queue.enqueue(null));
    }

    @Test
    void shouldRejectEnqueueWhenFull() {
        TipperInputQueue queue = new TipperInputQueue("tipper-input", 1);
        queue.enqueue(payload("tote-a"));

        assertThrows(IllegalStateException.class, () -> queue.enqueue(payload("tote-b")));
    }

    private static TipperTotePayload payload(String toteId) {
        Tote tote = new Tote(
                toteId,
                new RouteFollower(toteId, routeSegment(), 0f, 1.0d),
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
                "route",
                new LinearSegment3(new Vec3(0f, 0f, 0f), new Vec3(1f, 0f, 0f), false));
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
