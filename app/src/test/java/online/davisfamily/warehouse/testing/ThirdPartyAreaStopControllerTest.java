package online.davisfamily.warehouse.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

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
import online.davisfamily.warehouse.sim.dsp.adapting.MapBackedToteLoadPlanRegistry;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.ProductMasterRecord;
import online.davisfamily.warehouse.sim.dsp.routing.InMemoryProductMasterRepository;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyArea;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyAreaConfig;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyAreaController;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyVisitFactory;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.tote.Tote.ToteMotionState;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

class ThirdPartyAreaStopControllerTest {

    @Test
    void shouldHoldAndSubmitQualifyingToteExactlyOnceAtExplicitStop() {
        Fixture fixture = fixture(new ThirdPartyAreaConfig(0, 1, 2d));
        Tote tote = tote("tote-order-1", fixture.route(), 0f, 4d);
        fixture.register(tote, thirdPartyOrder("order-1", OrderType.FULL_PACK));

        fixture.sim().update(0.30d);
        fixture.sim().update(0.25d);

        assertEquals(ToteMotionState.HELD, tote.getInteractionMode());
        assertTrue(tote.areLidsOpen());
        assertEquals(1f, tote.getLastSnapshot().distanceAlongSegment(), 0.0001f);
        assertEquals(1, fixture.area().snapshot().activeCount());
        assertEquals(List.of("order-1"), fixture.area().snapshot().activeVisits().stream()
                .map(visit -> visit.orderId())
                .toList());
    }

    @Test
    void shouldApplyCompletionAndReleaseSameToteAlongExistingRoute() {
        Fixture fixture = fixture(new ThirdPartyAreaConfig(0, 1, 0.5d));
        Tote tote = tote("tote-order-2", fixture.route(), 0f, 4d);
        fixture.register(tote, thirdPartyOrder("order-2", OrderType.ADAPTED));

        fixture.sim().update(0.30d);
        fixture.sim().update(0.50d);

        assertEquals(ToteMotionState.MOVING, tote.getInteractionMode());
        assertFalse(tote.areLidsOpen());
        assertTrue(fixture.controller().completedToteIds().contains(tote.getId()));
        assertEquals(fixture.route(), tote.getLastSnapshot().currentSegment());
        assertEquals(List.of("pack-line-order-2-1"), fixture.registry().getLoadPlanFor(tote.getId())
                .getPackPlans().stream().map(PackPlan::packId).toList());

        fixture.sim().update(0.75d);
        assertEquals(ToteMotionState.MOVING, tote.getInteractionMode());
        assertEquals(1, fixture.registry().getLoadPlanFor(tote.getId()).getPackPlans().size());
    }

    @Test
    void shouldAllowToteWithoutThirdPartyWorkToPassWithoutStopping() {
        Fixture fixture = fixture(new ThirdPartyAreaConfig(0, 1, 1d));
        Tote tote = tote("tote-regular", fixture.route(), 0f, 4d);
        fixture.register(tote, regularOrder("regular"));

        fixture.sim().update(0.50d);

        assertEquals(ToteMotionState.MOVING, tote.getInteractionMode());
        assertFalse(tote.areLidsOpen());
        assertEquals(2f, tote.getLastSnapshot().distanceAlongSegment(), 0.0001f);
        assertEquals(0, fixture.area().snapshot().admittedCount());
    }

    @Test
    void shouldUseSeparateStopsForConcurrentAndWaitingVisits() {
        RouteSegment route = route();
        List<ThirdPartyAreaStop> stops = List.of(
                new ThirdPartyAreaStop("stop-1", route, 1f),
                new ThirdPartyAreaStop("stop-2", route, 3f));
        Fixture fixture = fixture(new ThirdPartyAreaConfig(1, 1, 2d), route, stops);
        Tote first = tote("tote-order-3", route, 0f, 4d);
        Tote second = tote("tote-order-4", route, 0f, 4d);
        fixture.register(first, thirdPartyOrder("order-3", OrderType.FULL_PACK));
        fixture.register(second, thirdPartyOrder("order-4", OrderType.ASSOCIATED));

        fixture.sim().update(0.30d);
        fixture.sim().update(0.50d);

        assertEquals(ToteMotionState.HELD, first.getInteractionMode());
        assertEquals(ToteMotionState.HELD, second.getInteractionMode());
        assertEquals(1, fixture.area().snapshot().activeCount());
        assertEquals(1, fixture.area().snapshot().waitingCount());
        assertEquals(List.of("order-4"), fixture.area().snapshot().waitingOrderIds());
    }

    private Fixture fixture(ThirdPartyAreaConfig config) {
        RouteSegment route = route();
        return fixture(config, route, List.of(new ThirdPartyAreaStop("stop-1", route, 1f)));
    }

    private Fixture fixture(
            ThirdPartyAreaConfig config,
            RouteSegment route,
            List<ThirdPartyAreaStop> stops) {
        InMemoryProductMasterRepository products = new InMemoryProductMasterRepository(List.of(
                product("third-party-product", "Y74"),
                product("regular-product", null)));
        ThirdPartyVisitFactory visitFactory = new ThirdPartyVisitFactory(products);
        ThirdPartyArea area = new ThirdPartyArea(config);
        MapBackedToteLoadPlanRegistry registry = new MapBackedToteLoadPlanRegistry();
        ThirdPartyAreaController areaController = new ThirdPartyAreaController(
                area,
                registry,
                (visit, lineWork, packOrdinal) -> new PackPlan(
                        "pack-" + lineWork.lineReference() + "-" + packOrdinal,
                        visit.orderId(),
                        dimensions()));
        ThirdPartyAreaStopController controller = new ThirdPartyAreaStopController(
                area,
                areaController,
                visitFactory,
                stops);
        SimulationWorld sim = new SimulationWorld();
        sim.addController(controller);
        return new Fixture(sim, route, area, registry, controller);
    }

    private NotionalToteOrder thirdPartyOrder(String orderId, OrderType orderType) {
        return order(orderId, orderType, "third-party-product");
    }

    private NotionalToteOrder regularOrder(String orderId) {
        return order(orderId, OrderType.FULL_PACK, "regular-product");
    }

    private NotionalToteOrder order(String orderId, OrderType orderType, String productId) {
        return new NotionalToteOrder(
                orderId,
                "tote-" + orderId,
                "sc-1",
                1,
                orderType,
                List.of(new DspOrderItem(
                        "line-" + orderId,
                        productId,
                        1,
                        "0000310",
                        DspOrderLineType.FULL_PACK,
                        orderId,
                        1,
                        0)),
                0L);
    }

    private ProductMasterRecord product(String productId, String thirdPartyLocation) {
        return new ProductMasterRecord(
                productId,
                productId,
                Optional.ofNullable(thirdPartyLocation),
                Optional.of(dimensions()));
    }

    private static RouteSegment route() {
        return new RouteSegment(
                "third-party-route",
                new LinearSegment3(new Vec3(0f, 0f, 0f), new Vec3(0f, 0f, 8f), false));
    }

    private Tote tote(String toteId, RouteSegment route, float distance, double speed) {
        RenderableObject renderable = RenderableObject.create(
                toteId,
                null,
                anchorMesh(),
                new Mat4.ObjectTransformation(0f, 0f, 0f, 0f, 0f, 0f, new Mat4()),
                triangleIndex -> 0,
                false);
        Tote tote = new Tote(
                toteId,
                new RouteFollower(toteId, route, distance, speed),
                renderable,
                new Vec3(),
                0f);
        tote.snapToRouteDistance(distance);
        tote.closeLids();
        return tote;
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

    private static PackDimensions dimensions() {
        return new PackDimensions(0.2f, 0.1f, 0.05f);
    }

    private record Fixture(
            SimulationWorld sim,
            RouteSegment route,
            ThirdPartyArea area,
            MapBackedToteLoadPlanRegistry registry,
            ThirdPartyAreaStopController controller) {

        private void register(Tote tote, NotionalToteOrder order) {
            registry.putLoadPlan(new ToteLoadPlan(tote.getId(), List.of()));
            sim.addTrackableObject(tote);
            assertTrue(controller.registerTote(tote, order));
        }
    }
}
