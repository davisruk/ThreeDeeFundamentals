package online.davisfamily.warehouse.testing.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.behaviour.routing.RouteFollower;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.matrices.Vec4;
import online.davisfamily.threedee.model.Mesh;
import online.davisfamily.threedee.path.LinearSegment3;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.p2p.P2pAdmissionResult;
import online.davisfamily.warehouse.sim.dsp.p2p.P2pAdmissionSnapshot;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperInputQueue;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperTotePayload;

class QueuedReleaseP2pAdmissionTest {

    @Test
    void shouldAcceptWhenInputQueueHasCapacity() {
        QueuedReleaseP2pAdmission admission = new QueuedReleaseP2pAdmission(new TipperInputQueue("tipper-input", 1));

        P2pAdmissionResult result = admission.canAdmit(order("order-1"), p2pSnapshot());

        assertTrue(result.accepted());
        assertEquals("", result.rejectionReason());
    }

    @Test
    void shouldRejectWhenInputQueueIsFull() {
        TipperInputQueue queue = new TipperInputQueue("tipper-input", 1);
        queue.enqueue(payload("tote-a"));
        QueuedReleaseP2pAdmission admission = new QueuedReleaseP2pAdmission(queue);

        P2pAdmissionResult result = admission.canAdmit(order("order-1"), p2pSnapshot());

        assertFalse(result.accepted());
        assertEquals("P2P input queue is full", result.rejectionReason());
    }

    @Test
    void shouldRejectNullInputs() {
        TipperInputQueue queue = new TipperInputQueue("tipper-input", 1);

        assertThrows(IllegalArgumentException.class, () -> new QueuedReleaseP2pAdmission(null));

        QueuedReleaseP2pAdmission admission = new QueuedReleaseP2pAdmission(queue);
        assertThrows(IllegalArgumentException.class, () -> admission.canAdmit(null, p2pSnapshot()));
        assertThrows(IllegalArgumentException.class, () -> admission.canAdmit(order("order-1"), null));
    }

    private static NotionalToteOrder order(String orderId) {
        return new NotionalToteOrder(
                orderId,
                "notional-" + orderId,
                "sc-1",
                1,
                OrderType.ASSOCIATED,
                List.of(new DspOrderItem("item-" + orderId, "product-1", 1)),
                0);
    }

    private static P2pAdmissionSnapshot p2pSnapshot() {
        return new P2pAdmissionSnapshot(
                "p2p-1",
                2,
                Set.of("bag-a"),
                Set.of("bag-a"),
                true);
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
