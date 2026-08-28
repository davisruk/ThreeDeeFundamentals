package online.davisfamily.warehouse.sim.dsp.thirdparty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.behaviour.routing.RouteFollower;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.matrices.Vec4;
import online.davisfamily.threedee.model.Mesh;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.warehouse.sim.dsp.adapting.MapBackedToteLoadPlanRegistry;
import online.davisfamily.warehouse.sim.dsp.adapting.MutableToteLoadPlanRegistry;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRole;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.ProductMasterRecord;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.OperationalPhysicalToteReleaseRequest;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteIdentity;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteSource;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteLaunchRequest;
import online.davisfamily.warehouse.sim.dsp.routing.InMemoryProductMasterRepository;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingAdmissionDecision;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationArrivalClaimController;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingClaim;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingCoordinator;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingOrderCatalog;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingBinding;
import online.davisfamily.warehouse.sim.dsp.transport.routing.StationRoutedToteArrivalQueue;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.tote.Tote.ToteMotionState;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;

class ThirdPartyStationProcessingTargetTest {

    @Test
    void shouldValidateAndClaimAnExactDirectThirdPartyArrival() {
        Fixture fixture = fixture(OrderType.FULL_PACK, OperationalPhysicalToteSource.OSR,
                DspOrderLineType.FULL_PACK, true, "target-direct");

        assertEquals(OperationalPhysicalToteSource.OSR, fixture.source());
        assertEquals(OperationalPhysicalToteSource.OSR, fixture.routedTote().launchRequest().source());
        assertEquals(StationProcessingAdmissionDecision.permit(),
                fixture.target().evaluate(fixture.routedTote()));
        assertTrue(fixture.coordinator().snapshot().activeClaims().isEmpty());
        assertEquals(0, fixture.area().snapshot().admittedCount());

        StationProcessingClaim claim = fixture.target().accept(
                fixture.routedTote(),
                Duration.ofSeconds(2));

        assertSame(fixture.routedTote(), claim.routedTote());
        assertEquals(List.of(fixture.routedTote().physicalToteId()),
                fixture.coordinator().snapshot().activeClaims().stream()
                        .map(active -> active.physicalToteId())
                        .toList());
        assertEquals(1, fixture.area().snapshot().admittedCount());
    }

    @Test
    void shouldDeferOnlyAfterValidatingWhenAreaCapacityIsClosed() {
        NotionalToteOrder firstOrder = order("capacity-first", OrderType.FULL_PACK,
                DspOrderLineType.FULL_PACK);
        NotionalToteOrder secondOrder = order("capacity-second", OrderType.FULL_PACK,
                DspOrderLineType.FULL_PACK);
        OperationalRouteDestination destination = destination("third-party-capacity");
        MapBackedToteLoadPlanRegistry registry = new MapBackedToteLoadPlanRegistry();
        ThirdPartyArea area = new ThirdPartyArea(new ThirdPartyAreaConfig(0, 1, 5d));
        StationProcessingCoordinator coordinator = new StationProcessingCoordinator();
        ThirdPartyStationProcessingTarget target = target(
                List.of(firstOrder, secondOrder),
                destination,
                registry,
                area,
                coordinator);
        RoutedPhysicalTote first = routedTote(
                firstOrder,
                new PhysicalToteId("capacity-first-physical"),
                destination,
                new ToteLoadPlan(new PhysicalToteId("capacity-first-physical"), List.of()),
                OperationalPhysicalToteSource.OSR);
        RoutedPhysicalTote second = routedTote(
                secondOrder,
                new PhysicalToteId("capacity-second-physical"),
                destination,
                new ToteLoadPlan(new PhysicalToteId("capacity-second-physical"), List.of()),
                OperationalPhysicalToteSource.OSR);
        registry.putLoadPlan(first.loadPlan());
        registry.putLoadPlan(second.loadPlan());
        second.tote().setInteractionMode(ToteMotionState.HELD);
        RouteFollower secondFollower = second.tote().getRouteFollower();
        RouteSegment secondSegment = secondFollower.getCurrentSegment();
        RenderableObject secondRenderable = second.renderable();
        ToteLoadPlan secondPlan = second.loadPlan();

        StationRoutedToteArrivalQueue arrivalQueue = new StationRoutedToteArrivalQueue(destination, 2);
        arrivalQueue.enqueue(first);
        arrivalQueue.enqueue(second);
        StationArrivalClaimController claimant = new StationArrivalClaimController(
                new StationProcessingBinding(arrivalQueue, target));

        claimant.update(context(0d), 0d);
        assertSame(second, arrivalQueue.peek().orElseThrow());
        assertEquals(List.of(first.physicalToteId()),
                coordinator.snapshot().activeClaims().stream()
                        .map(active -> active.physicalToteId())
                        .toList());

        claimant.update(context(1d), 0d);

        assertEquals(List.of(second.physicalToteId()),
                arrivalQueue.snapshot().entries().stream()
                        .map(entry -> entry.physicalToteId())
                        .toList());
        assertSame(second, arrivalQueue.peek().orElseThrow());
        assertTrue(claimant.snapshot().blocked());
        assertEquals("Third Party area has no capacity", claimant.snapshot().blockedReason());
        assertEquals(1, area.snapshot().admittedCount());
        assertEquals(List.of(first.physicalToteId()),
                coordinator.snapshot().activeClaims().stream()
                        .map(active -> active.physicalToteId())
                        .toList());
        assertEquals(ToteMotionState.HELD, second.tote().getInteractionMode());
        assertSame(secondSegment, secondFollower.getCurrentSegment());
        assertSame(secondRenderable, second.renderable());
        assertSame(secondPlan, registry.getLoadPlanFor(second.physicalToteId()));
    }

    @Test
    void shouldAcceptAdaptedPreparationAndAv02EmptyVisits() {
        Fixture adapted = fixture(OrderType.ADAPTED, OperationalPhysicalToteSource.OSR,
                DspOrderLineType.ADAPTED, true, "target-adapted");
        Fixture empty = fixture(OrderType.EMPTY, OperationalPhysicalToteSource.AV02,
                DspOrderLineType.FULL_PACK, true, "target-empty");

        assertEquals(OperationalPhysicalToteSource.OSR,
                adapted.routedTote().launchRequest().source());
        assertEquals(OperationalPhysicalToteSource.AV02,
                empty.routedTote().launchRequest().source());
        assertTrue(adapted.target().evaluate(adapted.routedTote()).permitted());
        assertTrue(empty.target().evaluate(empty.routedTote()).permitted());
        adapted.target().accept(adapted.routedTote(), Duration.ZERO);
        empty.target().accept(empty.routedTote(), Duration.ZERO);

        assertEquals(1, adapted.area().snapshot().activeCount());
        assertEquals(1, empty.area().snapshot().activeCount());
    }

    @Test
    void shouldRejectAStaleRouteWithoutThirdPartyWork() {
        Fixture fixture = fixture(OrderType.ASSOCIATED, OperationalPhysicalToteSource.OSR,
                DspOrderLineType.ADAPTED, true, "target-stale");

        assertThrows(IllegalStateException.class,
                () -> fixture.target().evaluate(fixture.routedTote()));
        assertEquals(0, fixture.area().snapshot().admittedCount());
        assertTrue(fixture.coordinator().snapshot().activeClaims().isEmpty());
    }

    @Test
    void shouldRejectUnknownSheetTypeServiceCentreAndMissingPlanBeforeAreaMutation() {
        Fixture unknownSheet = fixture(OrderType.FULL_PACK, OperationalPhysicalToteSource.OSR,
                DspOrderLineType.FULL_PACK, true, "target-unknown-sheet");
        NotionalToteOrder unknownOrder = order(
                "unknown-sheet-order", OrderType.FULL_PACK, DspOrderLineType.FULL_PACK);
        RoutedPhysicalTote unknownSheetTote = routedTote(
                unknownOrder,
                new PhysicalToteId("unknown-sheet-physical"),
                unknownSheet.routedTote().destination(),
                new ToteLoadPlan(new PhysicalToteId("unknown-sheet-physical"), List.of()),
                OperationalPhysicalToteSource.OSR);
        unknownSheet.registry().putLoadPlan(unknownSheetTote.loadPlan());
        assertTargetRejected(unknownSheet, unknownSheetTote);

        Fixture wrongType = fixture(OrderType.FULL_PACK, OperationalPhysicalToteSource.OSR,
                DspOrderLineType.FULL_PACK, true, "target-type");
        NotionalToteOrder wrongTypeOrder = order(
                "order-target-type", OrderType.ADAPTED, DspOrderLineType.ADAPTED);
        RoutedPhysicalTote wrongTypeTote = routedTote(
                wrongTypeOrder,
                new PhysicalToteId("wrong-type-physical"),
                wrongType.routedTote().destination(),
                new ToteLoadPlan(new PhysicalToteId("wrong-type-physical"), List.of()),
                OperationalPhysicalToteSource.OSR);
        wrongType.registry().putLoadPlan(wrongTypeTote.loadPlan());
        assertTargetRejected(wrongType, wrongTypeTote);

        Fixture wrongServiceCentre = fixture(OrderType.FULL_PACK, OperationalPhysicalToteSource.OSR,
                DspOrderLineType.FULL_PACK, true, "target-service-centre");
        NotionalToteOrder wrongServiceCentreOrder = order(
                "order-target-service-centre", "SC-OTHER", OrderType.FULL_PACK,
                DspOrderLineType.FULL_PACK);
        RoutedPhysicalTote wrongServiceCentreTote = routedTote(
                wrongServiceCentreOrder,
                new PhysicalToteId("wrong-service-centre-physical"),
                wrongServiceCentre.routedTote().destination(),
                new ToteLoadPlan(new PhysicalToteId("wrong-service-centre-physical"), List.of()),
                OperationalPhysicalToteSource.OSR);
        wrongServiceCentre.registry().putLoadPlan(wrongServiceCentreTote.loadPlan());
        assertTargetRejected(wrongServiceCentre, wrongServiceCentreTote);

        Fixture missingPlan = fixture(OrderType.FULL_PACK, OperationalPhysicalToteSource.OSR,
                DspOrderLineType.FULL_PACK, true, "target-missing-plan");
        RoutedPhysicalTote missingPlanTote = routedTote(
                missingPlan.order(),
                new PhysicalToteId("missing-plan-physical"),
                missingPlan.routedTote().destination(),
                new ToteLoadPlan(new PhysicalToteId("missing-plan-physical"), List.of()),
                OperationalPhysicalToteSource.OSR);
        assertTargetRejected(missingPlan, missingPlanTote);
    }

    @Test
    void shouldRejectAChangedCurrentPlanBeforeAreaMutation() {
        Fixture fixture = fixture(OrderType.FULL_PACK, OperationalPhysicalToteSource.OSR,
                DspOrderLineType.FULL_PACK, true, "target-plan");
        fixture.registry().putLoadPlan(new ToteLoadPlan(fixture.routedTote().physicalToteId(), List.of()));

        assertTargetRejected(fixture, fixture.routedTote());
    }

    @Test
    void shouldRejectDuplicatePhysicalClaimAndWrongDestination() {
        Fixture fixture = fixture(OrderType.FULL_PACK, OperationalPhysicalToteSource.OSR,
                DspOrderLineType.FULL_PACK, true, "target-duplicate");
        fixture.target().accept(fixture.routedTote(), Duration.ZERO);

        assertThrows(IllegalStateException.class,
                () -> fixture.target().evaluate(fixture.routedTote()));
        assertThrows(IllegalStateException.class,
                () -> fixture.target().accept(fixture.routedTote(), Duration.ofSeconds(1)));
        assertEquals(1, fixture.area().snapshot().admittedCount());

        RoutedPhysicalTote wrongDestination = routedTote(
                fixture.order(),
                new PhysicalToteId("wrong-destination-physical"),
                destination("another-third-party"),
                new ToteLoadPlan(new PhysicalToteId("wrong-destination-physical"), List.of()),
                OperationalPhysicalToteSource.OSR);
        fixture.registry().putLoadPlan(wrongDestination.loadPlan());
        assertTargetRejected(fixture, wrongDestination);
    }

    @Test
    void shouldRejectNonThirdPartyTargetDestination() {
        assertThrows(IllegalArgumentException.class,
                () -> new ThirdPartyStationProcessingTarget(
                        new OperationalRouteDestination(StationType.ADAPTING, "bench-1"),
                        new StationProcessingOrderCatalog(List.of()),
                        new MapBackedToteLoadPlanRegistry(),
                        new ThirdPartyVisitFactory(new InMemoryProductMasterRepository(List.of())),
                        new ThirdPartyArea(new ThirdPartyAreaConfig(0, 1, 0d)),
                        new StationProcessingCoordinator()));
    }

    private static Fixture fixture(
            OrderType orderType,
            OperationalPhysicalToteSource source,
            DspOrderLineType lineType,
            boolean thirdParty,
            String targetId) {
        NotionalToteOrder order = order(
                "order-" + targetId,
                orderType,
                lineType);
        return fixture(
                order,
                "physical-" + targetId,
                destination(targetId),
                new MapBackedToteLoadPlanRegistry(),
                new ThirdPartyArea(new ThirdPartyAreaConfig(1, 1, 0d)),
                new StationProcessingCoordinator(),
                source,
                thirdParty);
    }

    private static Fixture fixture(
            NotionalToteOrder order,
            String physicalToteId,
            OperationalRouteDestination destination,
            MapBackedToteLoadPlanRegistry registry,
            ThirdPartyArea area,
            StationProcessingCoordinator coordinator) {
        OperationalPhysicalToteSource source = order.orderType() == OrderType.EMPTY
                ? OperationalPhysicalToteSource.AV02
                : OperationalPhysicalToteSource.OSR;
        return fixture(order, physicalToteId, destination, registry, area, coordinator, source, true);
    }

    private static Fixture fixture(
            NotionalToteOrder order,
            String physicalToteId,
            OperationalRouteDestination destination,
            MapBackedToteLoadPlanRegistry registry,
            ThirdPartyArea area,
            StationProcessingCoordinator coordinator,
            OperationalPhysicalToteSource source,
            boolean thirdParty) {
        PhysicalToteId id = new PhysicalToteId(physicalToteId);
        RoutedPhysicalTote routedTote = routedTote(
                order,
                id,
                destination,
                new ToteLoadPlan(id, List.of()),
                source);
        registry.putLoadPlan(routedTote.loadPlan());
        ProductMasterRecord product = new ProductMasterRecord(
                "product-1",
                "Third Party Product",
                thirdParty ? Optional.of("Y74") : Optional.empty(),
                Optional.of(new PackDimensions(0.2f, 0.1f, 0.05f)));
        ThirdPartyVisitFactory visitFactory = new ThirdPartyVisitFactory(
                new InMemoryProductMasterRepository(List.of(product)));
        ThirdPartyStationProcessingTarget target = new ThirdPartyStationProcessingTarget(
                destination,
                new StationProcessingOrderCatalog(List.of(order)),
                registry,
                visitFactory,
                area,
                coordinator);
        return new Fixture(order, routedTote, registry, area, coordinator, target, source);
    }

    private static void assertTargetRejected(
            Fixture fixture,
            RoutedPhysicalTote routedTote) {
        var areaBefore = fixture.area().snapshot();
        var coordinatorBefore = fixture.coordinator().snapshot();

        assertThrows(IllegalStateException.class,
                () -> fixture.target().evaluate(routedTote));

        assertEquals(areaBefore, fixture.area().snapshot());
        assertEquals(coordinatorBefore, fixture.coordinator().snapshot());
    }

    private static ThirdPartyStationProcessingTarget target(
            List<NotionalToteOrder> orders,
            OperationalRouteDestination destination,
            MapBackedToteLoadPlanRegistry registry,
            ThirdPartyArea area,
            StationProcessingCoordinator coordinator) {
        ThirdPartyVisitFactory visitFactory = new ThirdPartyVisitFactory(
                new InMemoryProductMasterRepository(List.of(new ProductMasterRecord(
                        "product-1",
                        "Third Party Product",
                        Optional.of("Y74"),
                        Optional.of(new PackDimensions(0.2f, 0.1f, 0.05f))))));
        return new ThirdPartyStationProcessingTarget(
                destination,
                new StationProcessingOrderCatalog(orders),
                registry,
                visitFactory,
                area,
                coordinator);
    }

    private static NotionalToteOrder order(
            String orderId,
            OrderType orderType,
            DspOrderLineType lineType) {
        return order(orderId, "SC-1", orderType, lineType);
    }

    private static NotionalToteOrder order(
            String orderId,
            String serviceCentreId,
            OrderType orderType,
            DspOrderLineType lineType) {
        return new NotionalToteOrder(
                orderId,
                "notional-" + orderId,
                serviceCentreId,
                1,
                orderType,
                List.of(new DspOrderItem(
                        "line-" + orderId,
                        "product-1",
                        1,
                        "pharmacy-1",
                        "patient-" + orderId,
                        "prescription-" + orderId,
                        lineType,
                        orderId,
                        1,
                        0)),
                0,
                1);
    }

    private static OperationalRouteDestination destination(String targetId) {
        return new OperationalRouteDestination(StationType.THIRD_PARTY, targetId);
    }

    private static RoutedPhysicalTote routedTote(
            NotionalToteOrder order,
            PhysicalToteId physicalToteId,
            OperationalRouteDestination destination,
            ToteLoadPlan loadPlan,
            OperationalPhysicalToteSource source) {
        PhysicalToteRole role = source == OperationalPhysicalToteSource.AV02
                ? PhysicalToteRole.PRE_P2P
                : PhysicalToteRole.INBOUND_PACK;
        OperationalPhysicalToteIdentity identity = new OperationalPhysicalToteIdentity(
                source,
                physicalToteId,
                order.orderSheetKey(),
                order.orderType(),
                order.serviceCentreId(),
                role,
                0);
        OperationalPhysicalToteReleaseRequest releaseRequest =
                new OperationalPhysicalToteReleaseRequest(
                        identity,
                        List.of("pharmacy-1"),
                        Duration.ZERO,
                        Optional.empty());
        OperationalRouteLaunchRequest launchRequest = new OperationalRouteLaunchRequest(
                releaseRequest,
                destination);
        RenderableObject renderable = renderable(physicalToteId.value());
        RouteSegment segment = new RouteSegment(
                "segment-" + physicalToteId.value(),
                new online.davisfamily.threedee.path.LinearSegment3(
                        new Vec3(0f, 0f, 0f),
                        new Vec3(1f, 0f, 0f),
                        false));
        Tote tote = new Tote(
                physicalToteId.value(),
                new RouteFollower(physicalToteId.value(), segment, 0f, 1d),
                renderable,
                new Vec3(),
                0f);
        return new RoutedPhysicalTote(launchRequest, loadPlan, tote, renderable);
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
                        new int[][] {{0, 1, 2}},
                        "anchor"),
                new Mat4.ObjectTransformation(
                        0f, 0f, 0f, 0f, 0f, 0f, new Mat4()),
                triangleIndex -> 0,
                false);
    }

    private static SimulationContext context(double seconds) {
        SimulationContext context = new SimulationContext();
        context.setSimulationTimeSeconds(seconds);
        return context;
    }

    private record Fixture(
            NotionalToteOrder order,
            RoutedPhysicalTote routedTote,
            MutableToteLoadPlanRegistry registry,
            ThirdPartyArea area,
            StationProcessingCoordinator coordinator,
            ThirdPartyStationProcessingTarget target,
            OperationalPhysicalToteSource source) {
    }
}
