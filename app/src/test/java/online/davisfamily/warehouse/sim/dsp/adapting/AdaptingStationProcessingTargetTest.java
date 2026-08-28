package online.davisfamily.warehouse.sim.dsp.adapting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRole;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.OperationalPhysicalToteReleaseRequest;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteIdentity;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteSource;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteLaunchRequest;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationArrivalClaimController;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingAdmissionDecision;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingBinding;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingCoordinator;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingDispositionType;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingOrderCatalog;
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;
import online.davisfamily.warehouse.sim.dsp.transport.routing.StationRoutedToteArrivalQueue;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.tote.Tote.ToteMotionState;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

class AdaptingStationProcessingTargetTest {

    @Test
    void shouldAcceptOsrAdaptedStoreAndBothCollectSourcesAtExactBench() {
        Fixture adapted = fixture(order("adapted", OrderType.ADAPTED),
                OperationalPhysicalToteSource.OSR, "adapted", 2d, true);
        assertTrue(adapted.target().evaluate(adapted.routedTote()).permitted());
        var adaptedClaim = adapted.target().accept(adapted.routedTote(), Duration.ofSeconds(1));
        assertSame(adapted.routedTote(), adaptedClaim.routedTote());
        assertEquals(AdaptingBenchState.PROCESSING_STORE,
                adapted.area().bench(new AdaptingBenchId("adapted")).state());

        Fixture associated = fixture(order("associated", OrderType.ASSOCIATED),
                OperationalPhysicalToteSource.OSR, "associated", 2d, true);
        assertTrue(associated.target().evaluate(associated.routedTote()).permitted());
        associated.target().accept(associated.routedTote(), Duration.ofSeconds(1));
        assertEquals(AdaptingBenchState.PROCESSING_COLLECT,
                associated.area().bench(new AdaptingBenchId("associated")).state());

        Fixture empty = fixture(order("empty", OrderType.EMPTY),
                OperationalPhysicalToteSource.AV02, "empty", 2d, true);
        assertEquals(OperationalPhysicalToteSource.AV02,
                empty.routedTote().launchRequest().source());
        assertTrue(empty.target().evaluate(empty.routedTote()).permitted());
        empty.target().accept(empty.routedTote(), Duration.ofSeconds(1));
        assertEquals(AdaptingBenchState.PROCESSING_COLLECT,
                empty.area().bench(new AdaptingBenchId("empty")).state());
    }

    @Test
    void shouldEvaluateContinuedToteWithoutTimeSentinelAndValidateRealClaimTimeBeforeMutation() {
        Fixture fixture = fixture(order("continued", OrderType.ASSOCIATED),
                OperationalPhysicalToteSource.OSR, "continued", 2d, true);
        fixture.coordinator().claim(fixture.routedTote(), Duration.ofSeconds(1));
        var disposition = fixture.coordinator().complete(
                fixture.routedTote().physicalToteId(),
                StationProcessingDispositionType.CONTINUE,
                fixture.routedTote().loadPlan(),
                Duration.ofSeconds(2));
        fixture.coordinator().acknowledgeDisposition(disposition);
        var coordinatorBefore = fixture.coordinator().snapshot();
        var areaBefore = fixture.area().stationSnapshot();

        assertTrue(fixture.target().evaluate(fixture.routedTote()).permitted());
        assertEquals(coordinatorBefore, fixture.coordinator().snapshot());
        assertEquals(areaBefore, fixture.area().stationSnapshot());

        assertThrows(IllegalArgumentException.class,
                () -> fixture.target().accept(fixture.routedTote(), Duration.ofSeconds(1)));
        assertEquals(coordinatorBefore, fixture.coordinator().snapshot());
        assertEquals(areaBefore, fixture.area().stationSnapshot());

        var claim = fixture.target().accept(fixture.routedTote(), Duration.ofSeconds(3));
        assertSame(fixture.routedTote(), claim.routedTote());
        assertEquals(AdaptingBenchState.PROCESSING_COLLECT,
                fixture.area().bench(new AdaptingBenchId("continued")).state());
    }

    @Test
    void shouldClaimExactStationFifoHeadAndKeepAreaFifoQueued() {
        NotionalToteOrder firstOrder = order("fifo-first", OrderType.ASSOCIATED);
        NotionalToteOrder secondOrder = order("fifo-second", OrderType.EMPTY);
        Fixture fixture = fixture(List.of(firstOrder, secondOrder),
                OperationalPhysicalToteSource.OSR, "bench-1", 5d, 1, true);
        RoutedPhysicalTote first = fixture.routedTote();
        RoutedPhysicalTote second = routedTote(
                secondOrder,
                new PhysicalToteId("fifo-second-physical"),
                fixture.destination(),
                new ToteLoadPlan("fifo-second-physical", List.of()),
                OperationalPhysicalToteSource.AV02);
        fixture.registry().putLoadPlan(second.loadPlan());

        StationRoutedToteArrivalQueue queue = new StationRoutedToteArrivalQueue(
                fixture.destination(), 2);
        queue.enqueue(first);
        queue.enqueue(second);
        StationArrivalClaimController claimant = new StationArrivalClaimController(
                new StationProcessingBinding(queue, fixture.target()));

        claimant.update(context(0d), 0d);
        assertEquals(List.of(first.physicalToteId()),
                fixture.coordinator().snapshot().activeClaims().stream()
                        .map(active -> active.physicalToteId()).toList());
        assertSame(second, queue.peek().orElseThrow());

        claimant.update(context(0.5d), 0d);
        assertEquals(List.of(),
                queue.snapshot().entries().stream().map(entry -> entry.physicalToteId()).toList());
        assertEquals(List.of(first.physicalToteId(), second.physicalToteId()),
                fixture.coordinator().snapshot().activeClaims().stream()
                        .map(active -> active.physicalToteId()).toList());
        assertEquals(AdaptingBenchState.PROCESSING_COLLECT,
                fixture.area().bench(new AdaptingBenchId("bench-1")).state());
        assertEquals(List.of(second.physicalToteId().value()),
                fixture.area().admissionSnapshotFor(new AdaptingVisitFactory().profileFor(secondOrder))
                        .benchAdmissions().stream()
                        .filter(admission -> admission.benchId().equals(new AdaptingBenchId("bench-1")))
                        .findFirst().orElseThrow().queueSnapshot().toteIds());
    }

    @Test
    void shouldDeferExactHeadWhenSelectedBenchCapacityClosesWithoutMutation() {
        NotionalToteOrder firstOrder = order("capacity-first", OrderType.ASSOCIATED);
        NotionalToteOrder secondOrder = order("capacity-second", OrderType.EMPTY);
        NotionalToteOrder blockedOrder = order("capacity-blocked", OrderType.EMPTY);
        NotionalToteOrder trailingOrder = order("capacity-trailing", OrderType.ASSOCIATED);
        Fixture fixture = fixture(List.of(firstOrder, secondOrder, blockedOrder, trailingOrder),
                OperationalPhysicalToteSource.OSR, "bench-1", 5d, 1, true);

        fixture.target().accept(fixture.routedTote(), Duration.ZERO);
        RoutedPhysicalTote queued = routedTote(
                secondOrder,
                new PhysicalToteId("capacity-second-physical"),
                fixture.destination(),
                new ToteLoadPlan("capacity-second-physical", List.of()),
                OperationalPhysicalToteSource.AV02);
        fixture.registry().putLoadPlan(queued.loadPlan());
        fixture.target().accept(queued, Duration.ZERO);

        RoutedPhysicalTote blocked = routedTote(
                blockedOrder,
                new PhysicalToteId("capacity-blocked-physical"),
                fixture.destination(),
                new ToteLoadPlan("capacity-blocked-physical", List.of()),
                OperationalPhysicalToteSource.AV02);
        RoutedPhysicalTote trailing = routedTote(
                trailingOrder,
                new PhysicalToteId("capacity-trailing-physical"),
                fixture.destination(),
                new ToteLoadPlan("capacity-trailing-physical", List.of()),
                OperationalPhysicalToteSource.OSR);
        fixture.registry().putLoadPlan(blocked.loadPlan());
        fixture.registry().putLoadPlan(trailing.loadPlan());
        blocked.tote().setInteractionMode(ToteMotionState.HELD);
        RouteFollower blockedFollower = blocked.tote().getRouteFollower();
        RouteSegment blockedSegment = blockedFollower.getCurrentSegment();
        RenderableObject blockedRenderable = blocked.renderable();
        ToteLoadPlan blockedPlan = blocked.loadPlan();
        var areaBefore = fixture.area().admissionSnapshotFor(
                new AdaptingVisitFactory().profileFor(blockedOrder));
        var coordinatorBefore = fixture.coordinator().snapshot();
        var storeBefore = fixture.store().snapshot();

        StationRoutedToteArrivalQueue queue = new StationRoutedToteArrivalQueue(
                fixture.destination(), 2);
        queue.enqueue(blocked);
        queue.enqueue(trailing);
        StationArrivalClaimController claimant = new StationArrivalClaimController(
                new StationProcessingBinding(queue, fixture.target()));

        claimant.update(context(1d), 0d);

        assertSame(blocked, queue.peek().orElseThrow());
        assertTrue(claimant.snapshot().blocked());
        assertEquals(List.of(blocked.physicalToteId(), trailing.physicalToteId()),
                queue.snapshot().entries().stream().map(entry -> entry.physicalToteId()).toList());
        assertEquals(areaBefore, fixture.area().admissionSnapshotFor(
                new AdaptingVisitFactory().profileFor(blockedOrder)));
        assertEquals(coordinatorBefore, fixture.coordinator().snapshot());
        assertEquals(storeBefore, fixture.store().snapshot());
        assertEquals(ToteMotionState.HELD, blocked.tote().getInteractionMode());
        assertSame(blockedSegment, blockedFollower.getCurrentSegment());
        assertSame(blockedRenderable, blocked.renderable());
        assertSame(blockedPlan, fixture.registry().getLoadPlanFor(blocked.physicalToteId()));
    }

    @Test
    void shouldRevalidatePlanAndSelectedCapacityAtAcceptance() {
        Fixture stale = fixture(order("stale", OrderType.ASSOCIATED),
                OperationalPhysicalToteSource.OSR, "stale", 2d, true);
        assertTrue(stale.target().evaluate(stale.routedTote()).permitted());
        stale.registry().putLoadPlan(new ToteLoadPlan(stale.routedTote().physicalToteId(), List.of()));
        var staleArea = stale.area().stationSnapshot();
        var staleCoordinator = stale.coordinator().snapshot();
        assertThrows(IllegalStateException.class,
                () -> stale.target().accept(stale.routedTote(), Duration.ZERO));
        assertEquals(staleArea, stale.area().stationSnapshot());
        assertEquals(staleCoordinator, stale.coordinator().snapshot());

        Fixture capacity = fixture(order("revalidate", OrderType.ASSOCIATED),
                OperationalPhysicalToteSource.OSR, "revalidate", 2d, true);
        assertTrue(capacity.target().evaluate(capacity.routedTote()).permitted());
        capacity.target().accept(capacity.routedTote(), Duration.ZERO);
        var capacityArea = capacity.area().stationSnapshot();
        var capacityCoordinator = capacity.coordinator().snapshot();
        assertThrows(IllegalStateException.class,
                () -> capacity.target().accept(
                        routedTote(order("revalidate-2", OrderType.EMPTY),
                                new PhysicalToteId("revalidate-2-physical"),
                                capacity.destination(),
                                new ToteLoadPlan("revalidate-2-physical", List.of()),
                                OperationalPhysicalToteSource.AV02),
                        Duration.ZERO));
        assertEquals(capacityArea, capacity.area().stationSnapshot());
        assertEquals(capacityCoordinator, capacity.coordinator().snapshot());
    }

    @Test
    void shouldRejectInvalidArrivalsWithoutAreaOrClaimMutation() {
        Fixture fixture = fixture(order("valid", OrderType.ASSOCIATED),
                OperationalPhysicalToteSource.OSR, "bench-1", 1d, true);

        assertRejected(fixture, routedTote(
                fixture.order(), new PhysicalToteId("wrong-destination"),
                new OperationalRouteDestination(StationType.ADAPTING, "bench-other"),
                new ToteLoadPlan("wrong-destination", List.of()),
                OperationalPhysicalToteSource.OSR));

        NotionalToteOrder unknown = order("unknown", OrderType.ASSOCIATED);
        assertRejected(fixture, routedTote(unknown, new PhysicalToteId("unknown-physical"),
                fixture.destination(), new ToteLoadPlan("unknown-physical", List.of()),
                OperationalPhysicalToteSource.OSR));

        NotionalToteOrder retainedFullPack = order("valid", OrderType.FULL_PACK);
        assertRejected(fixture, routedTote(retainedFullPack,
                fixture.routedTote().physicalToteId(), fixture.destination(),
                new ToteLoadPlan(fixture.routedTote().physicalToteId(), List.of()),
                OperationalPhysicalToteSource.OSR));

        NotionalToteOrder serviceMismatch = order("valid", "SC-OTHER", OrderType.ASSOCIATED);
        assertRejected(fixture, routedTote(serviceMismatch,
                new PhysicalToteId("service-mismatch"), fixture.destination(),
                new ToteLoadPlan("service-mismatch", List.of()),
                OperationalPhysicalToteSource.OSR));

        NotionalToteOrder absentVisit = orderWithoutAdaptedLine("absent-visit", OrderType.ASSOCIATED);
        assertRejected(fixture, routedTote(absentVisit, new PhysicalToteId("absent-visit-physical"),
                fixture.destination(), new ToteLoadPlan("absent-visit-physical", List.of()),
                OperationalPhysicalToteSource.OSR));

        RoutedPhysicalTote missingPlan = routedTote(
                fixture.order(), new PhysicalToteId("missing-plan"), fixture.destination(),
                new ToteLoadPlan("missing-plan", List.of()), OperationalPhysicalToteSource.OSR);
        assertRejected(fixture, missingPlan);

        RoutedPhysicalTote changedPlan = routedTote(
                fixture.order(), new PhysicalToteId("changed-plan"), fixture.destination(),
                new ToteLoadPlan("changed-plan", List.of()), OperationalPhysicalToteSource.OSR);
        fixture.registry().putLoadPlan(new ToteLoadPlan("changed-plan", List.of()));
        assertRejected(fixture, changedPlan);
    }

    @Test
    void shouldRejectDuplicatePhysicalClaimWithoutSecondVisit() {
        Fixture fixture = fixture(order("duplicate", OrderType.ASSOCIATED),
                OperationalPhysicalToteSource.OSR, "duplicate", 1d, true);
        fixture.target().accept(fixture.routedTote(), Duration.ZERO);
        var areaBefore = fixture.area().stationSnapshot();
        var coordinatorBefore = fixture.coordinator().snapshot();

        assertThrows(IllegalStateException.class,
                () -> fixture.target().evaluate(fixture.routedTote()));
        assertThrows(IllegalStateException.class,
                () -> fixture.target().accept(fixture.routedTote(), Duration.ofSeconds(1)));
        assertEquals(areaBefore, fixture.area().stationSnapshot());
        assertEquals(coordinatorBefore, fixture.coordinator().snapshot());
    }

    private static void assertRejected(Fixture fixture, RoutedPhysicalTote routedTote) {
        var areaBefore = fixture.area().stationSnapshot();
        var coordinatorBefore = fixture.coordinator().snapshot();
        ToteLoadPlan registeredBefore = fixture.registry().getLoadPlanFor(
                routedTote.physicalToteId());

        assertThrows(RuntimeException.class,
                () -> fixture.target().evaluate(routedTote));
        assertThrows(RuntimeException.class,
                () -> fixture.target().accept(routedTote, Duration.ZERO));

        assertEquals(areaBefore, fixture.area().stationSnapshot());
        assertEquals(coordinatorBefore, fixture.coordinator().snapshot());
        assertSame(registeredBefore,
                fixture.registry().getLoadPlanFor(routedTote.physicalToteId()));
    }

    private static Fixture fixture(
            NotionalToteOrder order,
            OperationalPhysicalToteSource source,
            String destinationId,
            double processingDurationSeconds,
            boolean installPlan) {
        return fixture(List.of(order), source, destinationId, processingDurationSeconds, 1, installPlan);
    }

    private static Fixture fixture(
            List<NotionalToteOrder> orders,
            OperationalPhysicalToteSource source,
            String destinationId,
            double processingDurationSeconds,
            int queueCapacity,
            boolean installPlan) {
        OperationalRouteDestination destination = destination(destinationId);
        AdaptedLineStore store = new AdaptedLineStore();
        AdaptingStorageMap storageMap = new AdaptingStorageMap();
        storageMap.configureAvailableBenches(List.of(new AdaptingBenchId(destinationId)));
        storageMap.assignPharmacyToBench("pharmacy-1", new AdaptingBenchId(destinationId));
        AdaptingArea area = new AdaptingArea(
                List.of(new AdaptingBench(destinationId, store, processingDurationSeconds)),
                queueCapacity,
                storageMap);
        MapBackedToteLoadPlanRegistry registry = new MapBackedToteLoadPlanRegistry();
        NotionalToteOrder order = orders.getFirst();
        PhysicalToteId physicalToteId = new PhysicalToteId(order.orderId() + "-physical");
        ToteLoadPlan loadPlan = new ToteLoadPlan(physicalToteId, List.of());
        RoutedPhysicalTote routedTote = routedTote(
                order, physicalToteId, destination, loadPlan, source);
        if (installPlan) {
            registry.putLoadPlan(loadPlan);
        }
        AdaptingStationProcessingTarget target = new AdaptingStationProcessingTarget(
                destination,
                new StationProcessingOrderCatalog(orders),
                registry,
                new AdaptingVisitFactory(),
                area,
                new StationProcessingCoordinator());
        return new Fixture(order, routedTote, registry, store, area,
                target, target.coordinator(), destination);
    }

    private static NotionalToteOrder order(String orderId, OrderType orderType) {
        return order(orderId, "SC-1", orderType);
    }

    private static NotionalToteOrder order(
            String orderId,
            String serviceCentreId,
            OrderType orderType) {
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
                        DspOrderLineType.ADAPTED,
                        orderId,
                        1,
                        0)),
                0,
                1);
    }

    private static NotionalToteOrder orderWithoutAdaptedLine(String orderId, OrderType orderType) {
        return new NotionalToteOrder(
                orderId,
                "notional-" + orderId,
                "SC-1",
                1,
                orderType,
                List.of(new DspOrderItem(
                        "line-" + orderId,
                        "product-1",
                        1,
                        "pharmacy-1",
                        "patient-" + orderId,
                        "prescription-" + orderId,
                        DspOrderLineType.FULL_PACK,
                        orderId,
                        1,
                        0)),
                0,
                1);
    }

    private static OperationalRouteDestination destination(String targetId) {
        return new OperationalRouteDestination(StationType.ADAPTING, targetId);
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
                releaseRequest, destination);
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
            MapBackedToteLoadPlanRegistry registry,
            AdaptedLineStore store,
            AdaptingArea area,
            AdaptingStationProcessingTarget target,
            StationProcessingCoordinator coordinator,
            OperationalRouteDestination destination) {
    }
}
