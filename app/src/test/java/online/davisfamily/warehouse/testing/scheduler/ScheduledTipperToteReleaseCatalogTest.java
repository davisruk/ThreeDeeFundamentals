package online.davisfamily.warehouse.testing.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

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
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

class ScheduledTipperToteReleaseCatalogTest {
    private static final PackDimensions TEST_PACK = new PackDimensions(0.1f, 0.05f, 0.04f);

    @Test
    void shouldFindReleaseByOrderId() {
        ScheduledTipperToteRelease first = release("order-a", "tote-a", () -> null);
        ScheduledTipperToteRelease second = release("order-b", "tote-b", () -> null);

        ScheduledTipperToteReleaseCatalog catalog = new ScheduledTipperToteReleaseCatalog(List.of(first, second));

        assertTrue(catalog.findByOrderId("order-a").isPresent());
        assertSame(first, catalog.findByOrderId("order-a").get());
        assertSame(second, catalog.findByOrderId("order-b").get());
        assertFalse(catalog.findByOrderId("missing").isPresent());
    }

    @Test
    void shouldProvideToteLoadPlansByToteId() {
        ToteLoadPlan toteA = toteLoadPlan("tote-a");
        ToteLoadPlan toteB = toteLoadPlan("tote-b");
        ScheduledTipperToteReleaseCatalog catalog = new ScheduledTipperToteReleaseCatalog(List.of(
                new ScheduledTipperToteRelease("order-a", toteA, () -> null),
                new ScheduledTipperToteRelease("order-b", toteB, () -> null)));

        assertEquals(List.of(toteA, toteB), catalog.toteLoadPlans());
        assertSame(toteA, catalog.toteLoadPlanProvider().getLoadPlanFor("tote-a"));
        assertSame(toteB, catalog.toteLoadPlanProvider().getLoadPlanFor("tote-b"));
        assertEquals(null, catalog.toteLoadPlanProvider().getLoadPlanFor("missing"));
    }

    @Test
    void shouldRejectDuplicateOrderIdsAndToteIds() {
        ToteLoadPlan sharedTote = toteLoadPlan("tote-a");

        assertThrows(IllegalArgumentException.class, () -> new ScheduledTipperToteReleaseCatalog(List.of(
                new ScheduledTipperToteRelease("order-a", sharedTote, () -> null),
                new ScheduledTipperToteRelease("order-a", toteLoadPlan("tote-b"), () -> null))));

        assertThrows(IllegalArgumentException.class, () -> new ScheduledTipperToteReleaseCatalog(List.of(
                new ScheduledTipperToteRelease("order-a", sharedTote, () -> null),
                new ScheduledTipperToteRelease("order-b", sharedTote, () -> null))));
    }

    @Test
    void shouldCreatePayloadLazilyOnlyWhenReleaseIsUsed() {
        AtomicInteger invocationCount = new AtomicInteger();
        ScheduledTipperToteRelease release = release("order-a", "tote-a", () -> {
            invocationCount.incrementAndGet();
            return payload("tote-a");
        });

        ScheduledTipperToteReleaseCatalog catalog = new ScheduledTipperToteReleaseCatalog(List.of(release));

        assertEquals(0, invocationCount.get());
        catalog.findByOrderId("order-a").orElseThrow().createPayload();
        assertEquals(1, invocationCount.get());
    }

    @Test
    void shouldRejectScheduledReleaseWhenPayloadAndPlanToteIdsDiffer() {
        AtomicInteger invocationCount = new AtomicInteger();
        ScheduledTipperToteRelease release = release("order-a", "planned-tote", () -> {
            invocationCount.incrementAndGet();
            return payload("different-tote");
        });

        assertEquals(0, invocationCount.get());
        IllegalStateException exception = assertThrows(IllegalStateException.class, release::createPayload);

        assertEquals(1, invocationCount.get());
        assertTrue(exception.getMessage().contains("planned-tote"));
        assertTrue(exception.getMessage().contains("different-tote"));
    }

    private static ScheduledTipperToteRelease release(String orderId, String toteId, TipperTotePayloadFactory payloadFactory) {
        return new ScheduledTipperToteRelease(orderId, toteLoadPlan(toteId), payloadFactory);
    }

    private static ToteLoadPlan toteLoadPlan(String toteId) {
        return new ToteLoadPlan(
                toteId,
                List.of(new PackPlan("pack-" + toteId, "bag-" + toteId, TEST_PACK)));
    }

    private static TipperTotePayload payload(String toteId) {
        RenderableObject renderable = RenderableObject.create(
                toteId,
                null,
                anchorMesh(),
                new Mat4.ObjectTransformation(0f, 0f, 0f, 0f, 0f, 0f, new Mat4()),
                triangleIndex -> 0,
                false);
        Tote tote = new Tote(
                toteId,
                new RouteFollower(toteId, routeSegment(), 0f, 1d),
                renderable,
                new Vec3(),
                0f);
        return new TipperTotePayload(tote, renderable, 0f, Map.of());
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
}
