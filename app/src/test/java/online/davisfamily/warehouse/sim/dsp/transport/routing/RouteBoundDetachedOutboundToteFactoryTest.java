package online.davisfamily.warehouse.sim.dsp.transport.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.behaviour.routing.RouteFollower;
import online.davisfamily.threedee.behaviour.routing.RouteFollower.TravelDirection;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OsrOutboundRouteLaunchRequest;
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

class RouteBoundDetachedOutboundToteFactoryTest {
    private static final double ROUTE_SPEED = 1.75d;

    @Test
    void shouldBuildEveryDestinationOnExactCommonEntry() {
        RouteSegment commonEntry = routeSegment("common-entry", 4f);
        WarehouseRouteCatalog catalog = catalog(commonEntry);

        for (WarehouseRouteDefinition definition : catalog.definitions()) {
            RoutedPhysicalTote source = RoutedToteRoutingTestFixtures.routedTote(
                    "tote-" + definition.destination().targetId(),
                    definition.destination());
            AtomicReference<OsrOutboundRouteLaunchRequest> receivedRequest =
                    new AtomicReference<>();
            AtomicReference<ToteLoadPlan> receivedPlan = new AtomicReference<>();
            RouteBoundDetachedOutboundToteFactory factory = factory(
                    catalog,
                    (request, loadPlan) -> {
                        receivedRequest.set(request);
                        receivedPlan.set(loadPlan);
                        return RoutedToteRoutingTestFixtures.renderable(
                                request.physicalToteId().value());
                    });

            RoutedPhysicalTote result = factory.create(
                    source.launchRequest(), source.loadPlan());
            RouteFollower follower = result.tote().getRouteFollower();

            assertSame(source.launchRequest(), receivedRequest.get());
            assertSame(source.loadPlan(), receivedPlan.get());
            assertSame(source.launchRequest(), result.launchRequest());
            assertSame(source.loadPlan(), result.loadPlan());
            assertSame(commonEntry, follower.getCurrentSegment());
            assertEquals(0.5d, follower.getDistanceAlongSegment());
            assertEquals(TravelDirection.REVERSE, follower.getTravelDirection());
            assertEquals(ROUTE_SPEED, follower.getSpeedUnitsPerSecond());
            assertSame(result.renderable(), result.tote().getRenderable());
            assertFalse(result.tote().areLidsOpen());
        }
    }

    @Test
    void shouldUseCopiedConfiguredOffsets() {
        RouteSegment commonEntry = routeSegment("common-entry", 4f);
        WarehouseRouteCatalog catalog = catalog(commonEntry);
        OperationalRouteDestination destination = catalog.definitions().get(0).destination();
        RoutedPhysicalTote source =
                RoutedToteRoutingTestFixtures.routedTote("tote-1", destination);
        Vec3 offsets = new Vec3(2f, 3f, 4f);
        RouteBoundDetachedOutboundToteFactory factory = new RouteBoundDetachedOutboundToteFactory(
                catalog,
                (request, loadPlan) -> RoutedToteRoutingTestFixtures.renderable(
                        request.physicalToteId().value()),
                ROUTE_SPEED,
                offsets,
                0.25f);
        offsets.setXYZ(20f, 30f, 40f);

        RoutedPhysicalTote result = factory.create(source.launchRequest(), source.loadPlan());
        result.tote().update(null, 0d);

        assertEquals(2.5f, result.renderable().transformation.xTranslation);
        assertEquals(3f, result.renderable().transformation.yTranslation);
        assertEquals(4f, result.renderable().transformation.zTranslation);
    }

    @Test
    void shouldRejectUnknownRouteBeforeCreatingRenderable() {
        WarehouseRouteCatalog catalog = catalog(routeSegment("common-entry", 4f));
        OperationalRouteDestination unknown =
                new OperationalRouteDestination(StationType.P2P, "unknown");
        RoutedPhysicalTote source =
                RoutedToteRoutingTestFixtures.routedTote("tote-1", unknown);
        AtomicInteger renderableCalls = new AtomicInteger();
        RouteBoundDetachedOutboundToteFactory factory = factory(
                catalog,
                (request, loadPlan) -> {
                    renderableCalls.incrementAndGet();
                    return RoutedToteRoutingTestFixtures.renderable("tote-1");
                });

        assertThrows(
                IllegalArgumentException.class,
                () -> factory.create(source.launchRequest(), source.loadPlan()));
        assertEquals(0, renderableCalls.get());
    }

    @Test
    void shouldRejectInvalidRenderableAndPhysicalIdentity() {
        WarehouseRouteCatalog catalog = catalog(routeSegment("common-entry", 4f));
        RoutedPhysicalTote source = RoutedToteRoutingTestFixtures.routedTote(
                "tote-1", catalog.definitions().get(0).destination());

        assertThrows(
                IllegalArgumentException.class,
                () -> factory(catalog, (request, plan) -> null)
                        .create(source.launchRequest(), source.loadPlan()));
        assertThrows(
                IllegalArgumentException.class,
                () -> factory(
                        catalog,
                        (request, plan) ->
                                RoutedToteRoutingTestFixtures.renderable("other"))
                        .create(source.launchRequest(), source.loadPlan()));

        RenderableObject invalid = RoutedToteRoutingTestFixtures.renderable("tote-1");
        invalid.transformation = null;
        assertThrows(
                IllegalArgumentException.class,
                () -> factory(catalog, (request, plan) -> invalid)
                        .create(source.launchRequest(), source.loadPlan()));

        ToteLoadPlan wrongPlan = new ToteLoadPlan("other", List.of());
        assertThrows(
                IllegalArgumentException.class,
                () -> factory(catalog, (request, plan) -> invalid)
                        .create(source.launchRequest(), wrongPlan));
    }

    @Test
    void shouldValidateDependenciesAndArguments() {
        WarehouseRouteCatalog catalog = catalog(routeSegment("common-entry", 4f));
        DetachedToteRenderableFactory renderableFactory =
                (request, plan) -> RoutedToteRoutingTestFixtures.renderable(
                        request.physicalToteId().value());

        assertThrows(
                IllegalArgumentException.class,
                () -> new RouteBoundDetachedOutboundToteFactory(
                        null, renderableFactory, 1d, new Vec3(), 0f));
        assertThrows(
                IllegalArgumentException.class,
                () -> new RouteBoundDetachedOutboundToteFactory(
                        catalog, null, 1d, new Vec3(), 0f));
        for (double speed : new double[] {0d, -1d, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new RouteBoundDetachedOutboundToteFactory(
                            catalog, renderableFactory, speed, new Vec3(), 0f));
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> new RouteBoundDetachedOutboundToteFactory(
                        catalog, renderableFactory, 1d, null, 0f));
        assertThrows(
                IllegalArgumentException.class,
                () -> new RouteBoundDetachedOutboundToteFactory(
                        catalog, renderableFactory, 1d,
                        new Vec3(Float.NaN, 0f, 0f), 0f));
        assertThrows(
                IllegalArgumentException.class,
                () -> new RouteBoundDetachedOutboundToteFactory(
                        catalog, renderableFactory, 1d, new Vec3(), Float.NaN));

        RouteBoundDetachedOutboundToteFactory factory = factory(catalog, renderableFactory);
        assertThrows(IllegalArgumentException.class, () -> factory.create(null, null));
        RoutedPhysicalTote source = RoutedToteRoutingTestFixtures.routedTote(
                "tote-1", catalog.definitions().get(0).destination());
        assertThrows(
                IllegalArgumentException.class,
                () -> factory.create(source.launchRequest(), null));
    }

    private static RouteBoundDetachedOutboundToteFactory factory(
            WarehouseRouteCatalog catalog,
            DetachedToteRenderableFactory renderableFactory) {
        return new RouteBoundDetachedOutboundToteFactory(
                catalog,
                renderableFactory,
                ROUTE_SPEED,
                new Vec3(2f, 3f, 4f),
                0.25f);
    }

    private static WarehouseRouteCatalog catalog(RouteSegment commonEntry) {
        return new WarehouseRouteCatalog(List.of(
                definition(StationType.THIRD_PARTY, "third-party", commonEntry),
                definition(StationType.ADAPTING, "adapting", commonEntry),
                definition(StationType.P2P, "p2p", commonEntry)));
    }

    private static WarehouseRouteDefinition definition(
            StationType stationType,
            String targetId,
            RouteSegment commonEntry) {
        return new WarehouseRouteDefinition(
                new OperationalRouteDestination(stationType, targetId),
                commonEntry,
                0.5f,
                TravelDirection.REVERSE,
                targetId + "-sensor",
                routeSegment(targetId + "-terminal", 2f));
    }

    private static RouteSegment routeSegment(String label, float length) {
        return RoutedToteRoutingTestFixtures.routeSegment(label, length);
    }
}
