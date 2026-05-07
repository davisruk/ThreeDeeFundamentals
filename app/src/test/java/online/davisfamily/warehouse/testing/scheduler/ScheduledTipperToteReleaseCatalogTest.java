package online.davisfamily.warehouse.testing.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

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
            return null;
        });

        ScheduledTipperToteReleaseCatalog catalog = new ScheduledTipperToteReleaseCatalog(List.of(release));

        assertEquals(0, invocationCount.get());
        catalog.findByOrderId("order-a").orElseThrow().createPayload();
        assertEquals(1, invocationCount.get());
    }

    private static ScheduledTipperToteRelease release(String orderId, String toteId, TipperTotePayloadFactory payloadFactory) {
        return new ScheduledTipperToteRelease(orderId, toteLoadPlan(toteId), payloadFactory);
    }

    private static ToteLoadPlan toteLoadPlan(String toteId) {
        return new ToteLoadPlan(
                toteId,
                List.of(new PackPlan("pack-" + toteId, "bag-" + toteId, TEST_PACK)));
    }
}
