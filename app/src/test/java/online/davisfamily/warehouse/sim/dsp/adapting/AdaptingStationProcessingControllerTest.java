package online.davisfamily.warehouse.sim.dsp.adapting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.warehouse.sim.dsp.bagging.DspPackPlanFactory;
import online.davisfamily.warehouse.sim.dsp.bagging.PackProvenanceRegistry;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteLifecycleController;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifestCatalog;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteAssignmentEndReason;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleLedger;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleSnapshot;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleState;
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
import online.davisfamily.warehouse.sim.dsp.runtime.DspSchedulerRuntimeState;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingDisposition;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingDispositionType;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingOrderCatalog;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingCoordinator;
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

class AdaptingStationProcessingControllerTest {

    @Test
    void shouldContinueAssociatedAndEmptyCollectWithExactReplacementPlans() {
        Fixture associated = collectFixture(
                order("associated-controller", OrderType.ASSOCIATED),
                OperationalPhysicalToteSource.OSR,
                true,
                0d);
        ToteLoadPlan associatedBefore = associated.registry().getLoadPlanFor(
                associated.routedTote().physicalToteId());
        associated.target().accept(associated.routedTote(), Duration.ZERO);
        associated.controller().update(context(1.125d), 0d);

        StationProcessingDisposition associatedDisposition =
                associated.coordinator().peekDisposition().orElseThrow();
        assertEquals(StationProcessingDispositionType.CONTINUE, associatedDisposition.type());
        assertSame(associated.routedTote(), associatedDisposition.claim().routedTote());
        assertEquals(associated.routedTote().physicalToteId(), associatedDisposition.physicalToteId());
        assertTrue(associatedDisposition.currentLoadPlan().getPackPlans().size() > 0);
        assertTrue(associatedDisposition.currentLoadPlan() != associatedBefore);
        assertEquals(AdaptingBenchState.IDLE,
                associated.area().bench(new AdaptingBenchId("bench-1")).state());

        Fixture empty = collectFixture(
                order("empty-controller", OrderType.EMPTY),
                OperationalPhysicalToteSource.AV02,
                false,
                0d);
        ToteLoadPlan emptyBefore = empty.registry().getLoadPlanFor(
                empty.routedTote().physicalToteId());
        empty.target().accept(empty.routedTote(), Duration.ZERO);
        empty.controller().update(context(2.25d), 0d);

        StationProcessingDisposition emptyDisposition =
                empty.coordinator().peekDisposition().orElseThrow();
        assertEquals(StationProcessingDispositionType.CONTINUE, emptyDisposition.type());
        assertEquals(OperationalPhysicalToteSource.AV02,
                empty.routedTote().launchRequest().source());
        assertTrue(emptyDisposition.currentLoadPlan() != emptyBefore);
        assertEquals(empty.routedTote().physicalToteId(), emptyDisposition.currentLoadPlan().physicalToteId());
    }

    @Test
    void shouldConsumeAdaptedStoreAndPublishReadinessAtRoundedTime() {
        NotionalToteOrder order = order("store-controller", OrderType.ADAPTED);
        Fixture fixture = storeFixture(order, 0d);
        fixture.lifecycle().activate(fixture.routedTote().physicalToteId(), Duration.ofSeconds(1));
        fixture.target().accept(fixture.routedTote(), Duration.ofSeconds(1));

        fixture.controller().update(context(2.0000000004d), 0d);

        StationProcessingDisposition disposition = fixture.coordinator().peekDisposition().orElseThrow();
        assertEquals(StationProcessingDispositionType.CONSUME, disposition.type());
        assertEquals(Duration.ofSeconds(2), disposition.completedAt());
        assertEquals(PhysicalToteLifecycleState.CONSUMED_AT_ADAPTING,
                fixture.lifecycle().snapshot().totes()
                        .get(fixture.routedTote().physicalToteId()).state());
        assertEquals(PhysicalToteAssignmentEndReason.CONSUMED_AT_ADAPTING,
                fixture.lifecycle().snapshot()
                        .assignmentHistoryFor(order.orderSheetKey()).getFirst()
                        .endReason().orElseThrow());
        assertTrue(fixture.runtimeState().snapshot().preparedLineKeys().contains(
                PreparedLineKey.forPreparedLine(order.items().getFirst())));
        assertSame(fixture.routedTote().loadPlan(), disposition.currentLoadPlan());
    }

    @Test
    void shouldCompleteBenchesInSortedOrderAndPublishAtMostOneDispositionPerUpdate() {
        NotionalToteOrder firstOrder = order("bench-1-order", OrderType.EMPTY);
        NotionalToteOrder secondOrder = order("bench-2-order", OrderType.ASSOCIATED);
        MultiBenchFixture fixture = multiBenchFixture(firstOrder, secondOrder);

        fixture.targetFor("bench-2").accept(fixture.toteFor("bench-2"), Duration.ZERO);
        fixture.targetFor("bench-1").accept(fixture.toteFor("bench-1"), Duration.ZERO);

        fixture.controller().update(context(1d), 0d);
        assertEquals(List.of(new PhysicalToteId("bench-1-order-physical")),
                fixture.coordinator().pendingDispositions().stream()
                        .map(StationProcessingDisposition::physicalToteId).toList());
        assertEquals(AdaptingBenchState.COMPLETED,
                fixture.area().bench(new AdaptingBenchId("bench-2")).state());

        fixture.controller().update(context(2d), 0d);
        assertEquals(List.of(
                        new PhysicalToteId("bench-1-order-physical"),
                        new PhysicalToteId("bench-2-order-physical")),
                fixture.coordinator().pendingDispositions().stream()
                        .map(StationProcessingDisposition::physicalToteId).toList());
    }

    @Test
    void shouldDispatchAndStartQueuedVisitOnlyAfterCompletion() {
        Fixture first = collectFixture(
                order("queue-first", OrderType.ASSOCIATED),
                OperationalPhysicalToteSource.OSR,
                true,
                0d);
        NotionalToteOrder secondOrder = order("queue-second", OrderType.EMPTY);
        RoutedPhysicalTote second = routedTote(
                secondOrder,
                new PhysicalToteId("queue-second-physical"),
                first.destination(),
                new ToteLoadPlan("queue-second-physical", List.of()),
                OperationalPhysicalToteSource.AV02);
        first.registry().putLoadPlan(second.loadPlan());
        first.store().stage(secondOrder.items().getFirst(),
                new OrderSheetKey("source-queue-second", 1), "SC-1");
        AdaptingStationProcessingTarget secondTarget = new AdaptingStationProcessingTarget(
                first.destination(),
                new StationProcessingOrderCatalog(List.of(first.order(), secondOrder)),
                first.registry(),
                new AdaptingVisitFactory(),
                first.area(),
                first.coordinator());

        first.target().accept(first.routedTote(), Duration.ZERO);
        secondTarget.accept(second, Duration.ZERO);
        assertEquals(List.of(second.physicalToteId().value()),
                first.area().admissionSnapshotFor(new AdaptingVisitFactory()
                        .profileFor(secondOrder)).benchAdmissions().getFirst()
                        .queueSnapshot().toteIds());

        first.controller().update(context(1d), 0d);
        assertEquals(AdaptingBenchState.COMPLETED,
                first.area().bench(new AdaptingBenchId("bench-1")).state());
        assertEquals(1, first.coordinator().pendingDispositions().size());

        first.controller().update(context(2d), 0d);
        assertEquals(2, first.coordinator().pendingDispositions().size());
        assertEquals(new PhysicalToteId("queue-second-physical"),
                first.coordinator().pendingDispositions().get(1).physicalToteId());
    }

    @Test
    void shouldRejectStaleCollectPlanBeforeConsumingCompletion() {
        Fixture fixture = collectFixture(
                order("stale-controller", OrderType.ASSOCIATED),
                OperationalPhysicalToteSource.OSR,
                true,
                0d);
        fixture.target().accept(fixture.routedTote(), Duration.ZERO);
        ToteLoadPlan stale = new ToteLoadPlan(fixture.routedTote().physicalToteId(), List.of());
        fixture.registry().putLoadPlan(stale);
        var coordinatorBefore = fixture.coordinator().snapshot();
        var areaBefore = fixture.area().bench(new AdaptingBenchId("bench-1")).snapshot();

        assertThrows(IllegalStateException.class,
                () -> fixture.controller().update(context(1d), 0d));

        assertEquals(coordinatorBefore, fixture.coordinator().snapshot());
        assertEquals(areaBefore, fixture.area().bench(new AdaptingBenchId("bench-1")).snapshot());
        assertSame(stale, fixture.registry().getLoadPlanFor(fixture.routedTote().physicalToteId()));
    }

    @Test
    void shouldRejectCompletionPhysicalSheetServiceAndVisitTypeMismatchesBeforeMutation() {
        NotionalToteOrder order = order("mismatch-controller", OrderType.ASSOCIATED);
        PhysicalToteId physicalToteId = new PhysicalToteId("mismatch-controller-physical");

        assertCompletionMismatch(
                order,
                AdaptingVisit.collect(
                        new PhysicalToteId("different-physical"),
                        order.orderSheetKey(),
                        order.serviceCentreId(),
                        List.of(PreparedLineKey.forDispatchLine(order, order.items().getFirst())),
                        List.of("pharmacy-1")));
        assertCompletionMismatch(
                order,
                AdaptingVisit.collect(
                        physicalToteId,
                        new OrderSheetKey("different-sheet", 1),
                        order.serviceCentreId(),
                        List.of(PreparedLineKey.forDispatchLine(order, order.items().getFirst())),
                        List.of("pharmacy-1")));
        assertCompletionMismatch(
                order,
                AdaptingVisit.collect(
                        physicalToteId,
                        order.orderSheetKey(),
                        "SC-OTHER",
                        List.of(PreparedLineKey.forDispatchLine(order, order.items().getFirst())),
                        List.of("pharmacy-1")));
        assertCompletionMismatch(
                order,
                AdaptingVisit.store(
                        physicalToteId,
                        order.orderSheetKey(),
                        order.serviceCentreId(),
                        order.items()));
    }

    @Test
    void shouldRejectStoreCompletionBeforeLifecycleValidationAndLeaveBoundaryUnchanged() {
        Fixture fixture = storeFixture(order("invalid-store-controller", OrderType.ADAPTED), 0d);
        fixture.target().accept(fixture.routedTote(), Duration.ZERO);
        var coordinatorBefore = fixture.coordinator().snapshot();
        var lifecycleBefore = fixture.lifecycle().snapshot();
        var runtimeBefore = fixture.runtimeState().snapshot();

        assertThrows(IllegalStateException.class,
                () -> fixture.controller().update(context(1d), 0d));

        assertEquals(coordinatorBefore, fixture.coordinator().snapshot());
        assertEquals(lifecycleBefore, fixture.lifecycle().snapshot());
        assertEquals(runtimeBefore, fixture.runtimeState().snapshot());
        assertEquals(AdaptingBenchState.COMPLETED,
                fixture.area().bench(new AdaptingBenchId("bench-1")).state());
    }

    @Test
    void shouldRejectCompletionBeforeClaimTimeWithoutApplyingDomainCompletion() {
        Fixture fixture = storeFixture(order("early-store-controller", OrderType.ADAPTED), 0d);
        fixture.lifecycle().activate(fixture.routedTote().physicalToteId(), Duration.ofSeconds(5));
        fixture.target().accept(fixture.routedTote(), Duration.ofSeconds(5));
        var coordinatorBefore = fixture.coordinator().snapshot();
        var lifecycleBefore = fixture.lifecycle().snapshot();

        assertThrows(IllegalArgumentException.class,
                () -> fixture.controller().update(context(4d), 0d));

        assertEquals(coordinatorBefore, fixture.coordinator().snapshot());
        assertEquals(lifecycleBefore, fixture.lifecycle().snapshot());
        assertEquals(AdaptingBenchState.COMPLETED,
                fixture.area().bench(new AdaptingBenchId("bench-1")).state());
    }

    @Test
    void shouldNotPublishASecondDispositionAfterCompletion() {
        Fixture fixture = collectFixture(
                order("repeat-controller", OrderType.ASSOCIATED),
                OperationalPhysicalToteSource.OSR,
                true,
                0d);
        fixture.target().accept(fixture.routedTote(), Duration.ZERO);
        fixture.controller().update(context(1d), 0d);
        StationProcessingDisposition first = fixture.coordinator().peekDisposition().orElseThrow();

        fixture.controller().update(context(2d), 0d);

        assertSame(first, fixture.coordinator().peekDisposition().orElseThrow());
        assertEquals(1, fixture.coordinator().pendingDispositions().size());
        assertTrue(fixture.coordinator().snapshot().activeClaims().isEmpty());
    }

    private static Fixture collectFixture(
            NotionalToteOrder order,
            OperationalPhysicalToteSource source,
            boolean existingPlan,
            double processingDurationSeconds) {
        AdaptedLineStore store = new AdaptedLineStore();
        store.stage(order.items().getFirst(), new OrderSheetKey("source-" + order.orderId(), 1), "SC-1");
        AdaptingArea area = area(store, processingDurationSeconds, 1, "bench-1");
        MapBackedToteLoadPlanRegistry registry = new MapBackedToteLoadPlanRegistry();
        PhysicalToteId physicalToteId = new PhysicalToteId(order.orderId() + "-physical");
        ToteLoadPlan loadPlan = new ToteLoadPlan(physicalToteId,
                existingPlan
                        ? List.of(new PackPlan("existing-" + order.orderId(), "existing", dimensions()))
                        : List.of());
        registry.putLoadPlan(loadPlan);
        RoutedPhysicalTote routedTote = routedTote(
                order,
                physicalToteId,
                destination("bench-1"),
                loadPlan,
                source);
        return compose(order, routedTote, registry, store, area, lifecycleController(),
                false, processingDurationSeconds);
    }

    private static Fixture storeFixture(NotionalToteOrder order, double processingDurationSeconds) {
        AdaptedLineStore store = new AdaptedLineStore();
        AdaptingArea area = area(store, processingDurationSeconds, 1, "bench-1");
        MapBackedToteLoadPlanRegistry registry = new MapBackedToteLoadPlanRegistry();
        PhysicalToteId physicalToteId = new PhysicalToteId(order.orderId() + "-physical");
        ToteLoadPlan loadPlan = new ToteLoadPlan(physicalToteId, List.of());
        registry.putLoadPlan(loadPlan);
        RoutedPhysicalTote routedTote = routedTote(
                order,
                physicalToteId,
                destination("bench-1"),
                loadPlan,
                OperationalPhysicalToteSource.OSR);
        InboundToteManifest manifest = new InboundToteManifest(
                physicalToteId,
                order.orderSheetKey(),
                OrderType.ADAPTED,
                order.serviceCentreId(),
                order.items(),
                0);
        InboundToteLifecycleController lifecycle = new InboundToteLifecycleController(
                new PhysicalToteLifecycleLedger(),
                new InboundToteManifestCatalog(List.of(manifest)));
        return compose(order, routedTote, registry, store, area, lifecycle,
                true, processingDurationSeconds);
    }

    private static void assertCompletionMismatch(
            NotionalToteOrder order,
            AdaptingVisit mismatchedVisit) {
        AdaptedLineStore store = new AdaptedLineStore();
        store.stage(order.items().getFirst(), new OrderSheetKey("source-" + order.orderId(), 1), "SC-1");
        MismatchingPeekBench bench = new MismatchingPeekBench(
                "bench-1", store, 0d);
        AdaptingStorageMap storageMap = new AdaptingStorageMap();
        storageMap.configureAvailableBenches(List.of(new AdaptingBenchId("bench-1")));
        storageMap.assignPharmacyToBench("pharmacy-1", new AdaptingBenchId("bench-1"));
        AdaptingArea area = new AdaptingArea(List.of(bench), 1, storageMap);
        MapBackedToteLoadPlanRegistry registry = new MapBackedToteLoadPlanRegistry();
        PhysicalToteId physicalToteId = new PhysicalToteId(order.orderId() + "-physical");
        ToteLoadPlan plan = new ToteLoadPlan(physicalToteId, List.of());
        registry.putLoadPlan(plan);
        RoutedPhysicalTote routed = routedTote(order, physicalToteId,
                destination("bench-1"), plan, OperationalPhysicalToteSource.OSR);
        DspSchedulerRuntimeState runtimeState = new DspSchedulerRuntimeState(
                new WarehouseSchedulerSnapshot(List.of(), Map.of(), Set.of(), Optional.empty()));
        AdaptingAreaController areaController = new AdaptingAreaController(
                area, runtimeState, registry,
                new DefaultCollectedPackPlanFactory(
                        dimensions(), new DspPackPlanFactory(new PackProvenanceRegistry())));
        StationProcessingCoordinator coordinator = new StationProcessingCoordinator();
        AdaptingStationProcessingTarget target = new AdaptingStationProcessingTarget(
                destination("bench-1"), new StationProcessingOrderCatalog(List.of(order)),
                registry, new AdaptingVisitFactory(), area, coordinator);
        AdaptingStationProcessingController controller = new AdaptingStationProcessingController(
                "mismatch-controller", Set.of(destination("bench-1")), registry, area,
                areaController, lifecycleController(), coordinator);

        target.accept(routed, Duration.ZERO);
        bench.overrideCompletion(new AdaptingBenchCompletion(mismatchedVisit, List.of()));
        var coordinatorBefore = coordinator.snapshot();
        var benchBefore = bench.snapshot();
        var runtimeBefore = runtimeState.snapshot();
        var storeBefore = store.snapshot();

        assertThrows(IllegalStateException.class,
                () -> controller.update(context(1d), 0d));

        assertEquals(coordinatorBefore, coordinator.snapshot());
        assertEquals(benchBefore, bench.snapshot());
        assertEquals(runtimeBefore, runtimeState.snapshot());
        assertEquals(storeBefore, store.snapshot());
        assertSame(plan, registry.getLoadPlanFor(physicalToteId));
    }

    private static Fixture compose(
            NotionalToteOrder order,
            RoutedPhysicalTote routedTote,
            MapBackedToteLoadPlanRegistry registry,
            AdaptedLineStore store,
            AdaptingArea area,
            InboundToteLifecycleController lifecycle,
            boolean storeVisit,
            double processingDurationSeconds) {
        DspSchedulerRuntimeState runtimeState = new DspSchedulerRuntimeState(
                new WarehouseSchedulerSnapshot(List.of(), Map.of(), Set.of(), Optional.empty()));
        AdaptingAreaController areaController = new AdaptingAreaController(
                area,
                runtimeState,
                registry,
                new DefaultCollectedPackPlanFactory(
                        dimensions(), new DspPackPlanFactory(new PackProvenanceRegistry())));
        StationProcessingCoordinator coordinator = new StationProcessingCoordinator();
        AdaptingStationProcessingTarget target = new AdaptingStationProcessingTarget(
                routedTote.destination(),
                new StationProcessingOrderCatalog(List.of(order)),
                registry,
                new AdaptingVisitFactory(),
                area,
                coordinator);
        AdaptingStationProcessingController controller = new AdaptingStationProcessingController(
                "adapting-controller",
                Set.of(routedTote.destination()),
                registry,
                area,
                areaController,
                lifecycle,
                coordinator);
        return new Fixture(order, routedTote, registry, store, area, target, controller,
                coordinator, lifecycle, runtimeState, routedTote.destination());
    }

    private static MultiBenchFixture multiBenchFixture(
            NotionalToteOrder firstOrder,
            NotionalToteOrder secondOrder) {
        AdaptedLineStore store = new AdaptedLineStore();
        store.stage(firstOrder.items().getFirst(), new OrderSheetKey("source-first", 1), "SC-1");
        store.stage(secondOrder.items().getFirst(), new OrderSheetKey("source-second", 1), "SC-1");
        AdaptingArea area = area(store, 0d, 2, "bench-2", "bench-1");
        MapBackedToteLoadPlanRegistry registry = new MapBackedToteLoadPlanRegistry();
        StationProcessingCoordinator coordinator = new StationProcessingCoordinator();
        java.util.Map<String, RoutedPhysicalTote> totes = new java.util.LinkedHashMap<>();
        java.util.Map<String, AdaptingStationProcessingTarget> targets = new java.util.LinkedHashMap<>();
        for (String benchId : List.of("bench-2", "bench-1")) {
            NotionalToteOrder order = benchId.equals("bench-1") ? firstOrder : secondOrder;
            OperationalPhysicalToteSource source = order.orderType() == OrderType.EMPTY
                    ? OperationalPhysicalToteSource.AV02 : OperationalPhysicalToteSource.OSR;
            PhysicalToteId physicalToteId = new PhysicalToteId(order.orderId() + "-physical");
            ToteLoadPlan plan = new ToteLoadPlan(physicalToteId, List.of());
            registry.putLoadPlan(plan);
            RoutedPhysicalTote routed = routedTote(order, physicalToteId,
                    destination(benchId), plan, source);
            totes.put(benchId, routed);
            targets.put(benchId, new AdaptingStationProcessingTarget(
                    destination(benchId),
                    new StationProcessingOrderCatalog(List.of(firstOrder, secondOrder)),
                    registry,
                    new AdaptingVisitFactory(),
                    area,
                    coordinator));
        }
        DspSchedulerRuntimeState runtimeState = new DspSchedulerRuntimeState(
                new WarehouseSchedulerSnapshot(List.of(), Map.of(), Set.of(), Optional.empty()));
        AdaptingAreaController areaController = new AdaptingAreaController(
                area, runtimeState, registry,
                new DefaultCollectedPackPlanFactory(
                        dimensions(), new DspPackPlanFactory(new PackProvenanceRegistry())));
        InboundToteLifecycleController lifecycle = lifecycleController();
        AdaptingStationProcessingController controller = new AdaptingStationProcessingController(
                "multi-adapting-controller",
                new LinkedHashSet<>(List.of(destination("bench-2"), destination("bench-1"))),
                registry, area, areaController, lifecycle, coordinator);
        return new MultiBenchFixture(area, registry, coordinator, controller, totes, targets);
    }

    private static AdaptingArea area(
            AdaptedLineStore store,
            double duration,
            int queueCapacity,
            String... benchIds) {
        AdaptingStorageMap storageMap = new AdaptingStorageMap();
        List<AdaptingBenchId> ids = java.util.Arrays.stream(benchIds)
                .map(AdaptingBenchId::new).toList();
        storageMap.configureAvailableBenches(ids);
        storageMap.assignPharmacyToBench("pharmacy-1", ids.getFirst());
        return new AdaptingArea(
                java.util.Arrays.stream(benchIds)
                        .map(id -> new AdaptingBench(id, store, duration)).toList(),
                queueCapacity,
                storageMap);
    }

    private static InboundToteLifecycleController lifecycleController() {
        return new InboundToteLifecycleController(
                new PhysicalToteLifecycleLedger(),
                new InboundToteManifestCatalog(List.of()));
    }

    private static NotionalToteOrder order(String orderId, OrderType orderType) {
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
                        DspOrderLineType.ADAPTED,
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
                ? PhysicalToteRole.PRE_P2P : PhysicalToteRole.INBOUND_PACK;
        OperationalPhysicalToteIdentity identity = new OperationalPhysicalToteIdentity(
                source, physicalToteId, order.orderSheetKey(), order.orderType(),
                order.serviceCentreId(), role, 0);
        OperationalPhysicalToteReleaseRequest releaseRequest =
                new OperationalPhysicalToteReleaseRequest(identity, List.of("pharmacy-1"),
                        Duration.ZERO, Optional.empty());
        OperationalRouteLaunchRequest launchRequest = new OperationalRouteLaunchRequest(
                releaseRequest, destination);
        Tote tote = testTote(physicalToteId.value());
        return new RoutedPhysicalTote(launchRequest, loadPlan, tote, tote.getRenderable());
    }

    private static Tote testTote(String id) {
        online.davisfamily.threedee.rendering.RenderableObject renderable =
                online.davisfamily.threedee.rendering.RenderableObject.create(
                        id, null,
                        new online.davisfamily.threedee.model.Mesh(
                                new online.davisfamily.threedee.matrices.Vec4[] {
                                        new online.davisfamily.threedee.matrices.Vec4(0f, 0f, 0f, 1f),
                                        new online.davisfamily.threedee.matrices.Vec4(0f, 0f, 0f, 1f),
                                        new online.davisfamily.threedee.matrices.Vec4(0f, 0f, 0f, 1f)},
                                new int[][] {{0, 1, 2}}, "anchor"),
                        new online.davisfamily.threedee.matrices.Mat4.ObjectTransformation(
                                0f, 0f, 0f, 0f, 0f, 0f, new online.davisfamily.threedee.matrices.Mat4()),
                        triangleIndex -> 0, false);
        online.davisfamily.threedee.behaviour.routing.RouteSegment segment =
                new online.davisfamily.threedee.behaviour.routing.RouteSegment(
                        "segment-" + id,
                        new online.davisfamily.threedee.path.LinearSegment3(
                                new online.davisfamily.threedee.matrices.Vec3(0f, 0f, 0f),
                                new online.davisfamily.threedee.matrices.Vec3(1f, 0f, 0f), false));
        return new Tote(id,
                new online.davisfamily.threedee.behaviour.routing.RouteFollower(id, segment, 0f, 1d),
                renderable,
                new online.davisfamily.threedee.matrices.Vec3(), 0f);
    }

    private static PackDimensions dimensions() {
        return new PackDimensions(0.2f, 0.1f, 0.08f);
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
            AdaptingStationProcessingController controller,
            StationProcessingCoordinator coordinator,
            InboundToteLifecycleController lifecycle,
            DspSchedulerRuntimeState runtimeState,
            OperationalRouteDestination destination) {
    }

    private record MultiBenchFixture(
            AdaptingArea area,
            MapBackedToteLoadPlanRegistry registry,
            StationProcessingCoordinator coordinator,
            AdaptingStationProcessingController controller,
            Map<String, RoutedPhysicalTote> totes,
            Map<String, AdaptingStationProcessingTarget> targets) {
        RoutedPhysicalTote toteFor(String benchId) {
            return totes.get(benchId);
        }

        AdaptingStationProcessingTarget targetFor(String benchId) {
            return targets.get(benchId);
        }
    }

    private static final class MismatchingPeekBench extends AdaptingBench {
        private AdaptingBenchCompletion overrideCompletion;

        private MismatchingPeekBench(String id, AdaptedLineStore store, double duration) {
            super(id, store, duration);
        }

        private void overrideCompletion(AdaptingBenchCompletion completion) {
            this.overrideCompletion = completion;
        }

        @Override
        public java.util.Optional<AdaptingBenchCompletion> peekCompletion() {
            return overrideCompletion == null
                    ? super.peekCompletion()
                    : java.util.Optional.of(overrideCompletion);
        }
    }
}
