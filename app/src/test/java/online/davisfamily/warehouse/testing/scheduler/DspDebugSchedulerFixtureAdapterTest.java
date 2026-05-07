package online.davisfamily.warehouse.testing.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
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
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.StartLocation;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.runtime.DspSchedulerRuntimeState;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspOrderStatus;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationCapacity;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationSnapshot;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperTotePayload;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;
import online.davisfamily.warehouse.testing.TipperDemoFixtures;

class DspDebugSchedulerFixtureAdapterTest {

    @Test
    void shouldCreateSchedulerOrderStateForEachFeed() {
        DspDebugSchedulerFixtureAdapter adapter = new DspDebugSchedulerFixtureAdapter();
        List<TipperDemoFixtures.DemoTipperFeed> feeds = List.of(feed("tote-1"), feed("tote-2"));

        DspSchedulerRuntimeState runtimeState = adapter.createRuntimeState(feeds, stationAdmissions(), "SC-DEBUG");

        assertEquals(2, runtimeState.snapshot().orderStates().size());
        assertEquals("tote-1", runtimeState.snapshot().orderStates().get(0).order().orderId());
        assertEquals("tote-2", runtimeState.snapshot().orderStates().get(1).order().orderId());
        assertEquals("notional-tote-1", runtimeState.snapshot().orderStates().get(0).order().notionalToteId());
        assertEquals("SC-DEBUG", runtimeState.snapshot().orderStates().get(0).order().serviceCentreId());
        assertEquals(0L, runtimeState.snapshot().orderStates().get(0).order().sequenceNumber());
        assertEquals(1L, runtimeState.snapshot().orderStates().get(1).order().sequenceNumber());
        assertEquals(DspOrderStatus.WAITING, runtimeState.snapshot().orderStates().get(0).status());
        assertTrue(runtimeState.snapshot().activeServiceCentreId().isEmpty());
    }

    @Test
    void shouldCreateImmediatelyReleasableDebugDispatchOrders() {
        DspDebugSchedulerFixtureAdapter adapter = new DspDebugSchedulerFixtureAdapter();
        List<TipperDemoFixtures.DemoTipperFeed> feeds = List.of(feed("tote-1"));

        DspSchedulerRuntimeState runtimeState = adapter.createRuntimeState(feeds, stationAdmissions(), "SC-DEBUG");

        var order = runtimeState.snapshot().orderStates().getFirst().order();
        assertEquals(OrderType.ASSOCIATED, order.orderType());
        assertTrue(order.items().stream().allMatch(item -> item.lineType() == DspOrderLineType.FULL_PACK));
        assertTrue(runtimeState.snapshot().preparedLineKeys().isEmpty());
    }

    @Test
    void shouldCreateP2pRouteRequirementsForDebugOrders() {
        DspDebugSchedulerFixtureAdapter adapter = new DspDebugSchedulerFixtureAdapter();
        List<TipperDemoFixtures.DemoTipperFeed> feeds = List.of(feed("tote-1"));

        DspSchedulerRuntimeState runtimeState = adapter.createRuntimeState(feeds, stationAdmissions(), "SC-DEBUG");

        var routeRequirements = runtimeState.snapshot().orderStates().getFirst().routeRequirements();
        assertFalse(routeRequirements.requiresThirdParty());
        assertFalse(routeRequirements.requiresSortable());
        assertFalse(routeRequirements.requiresManual());
        assertTrue(routeRequirements.requiresP2p());
        assertFalse(routeRequirements.requiresManualMerge());
        assertEquals(StartLocation.OSR, routeRequirements.startLocation());
    }

    @Test
    void shouldCreateReleaseCatalogForFeedsWithoutCreatingAdditionalPayloads() {
        DspDebugSchedulerFixtureAdapter adapter = new DspDebugSchedulerFixtureAdapter();
        TipperDemoFixtures.DemoTipperFeed feed = feed("tote-1");

        ScheduledTipperToteReleaseCatalog catalog = adapter.createReleaseCatalog(List.of(feed));

        ScheduledTipperToteRelease release = catalog.findByOrderId("tote-1").orElseThrow();
        assertSame(feed.toteLoadPlan(), release.toteLoadPlan());
        assertSame(feed.totePayload(), release.createPayload());
    }

    private static Map<StationType, StationAdmissionSnapshot> stationAdmissions() {
        return Map.of(
                StationType.P2P,
                new StationAdmissionSnapshot(
                        StationType.P2P,
                        new StationCapacity(1, 100),
                        new StationSnapshot(StationType.P2P, 0, 0),
                        true,
                        ""));
    }

    private static TipperDemoFixtures.DemoTipperFeed feed(String toteId) {
        PackDimensions packDimensions = new PackDimensions(0.1f, 0.05f, 0.04f);
        ToteLoadPlan toteLoadPlan = new ToteLoadPlan(
                toteId,
                List.of(new PackPlan("pack-" + toteId, "bag-" + toteId, packDimensions)));
        RenderableObject toteRenderable = renderable(toteId);
        TipperTotePayload totePayload = new TipperTotePayload(
                new Tote(
                        toteId,
                        new RouteFollower(
                                toteId,
                                new RouteSegment(
                                        "infeed",
                                        new LinearSegment3(
                                                new Vec3(0f, 0f, 0f),
                                                new Vec3(1f, 0f, 0f),
                                                false)),
                                0f,
                                1.0d),
                        toteRenderable,
                        new Vec3(0f, 0f, 0f),
                        0f),
                toteRenderable,
                0f,
                Map.of());

        return new TipperDemoFixtures.DemoTipperFeed(
                toteLoadPlan,
                totePayload,
                candidateToteId -> toteId.equals(candidateToteId) ? toteLoadPlan : null);
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
