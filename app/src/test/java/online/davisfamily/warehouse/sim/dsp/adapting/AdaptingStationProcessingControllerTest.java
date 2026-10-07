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
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.warehouse.sim.dsp.bagging.DspPackPlanFactory;
import online.davisfamily.warehouse.sim.dsp.bagging.PackProvenanceRegistry;
import online.davisfamily.warehouse.sim.dsp.io.LoadedDspData;
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
    void shouldPublishStrictCollectObserverOnlyAfterExactStationContinuation() {
        NotionalToteOrder collecting = order("strict-collect", OrderType.ASSOCIATED);
        DspOrderItem sourceLine = collecting.items().getFirst();
        NotionalToteOrder source = new NotionalToteOrder(
                "strict-source", "strict-source", "SC-1", 1, OrderType.ADAPTED,
                List.of(sourceLine), 1, 0);
        PreparedLineKey key = PreparedLineKey.forPreparedLine(sourceLine);
        AdaptingTargetSheetCatalog targets = new AdaptingTargetSheetCatalog(
                Map.of(key, collecting.orderSheetKey()));
        AdaptingOrderPreparationCatalog orders = new AdaptingOrderPreparationCatalog(
                new LoadedDspData(List.of(), List.of(source, collecting), List.of(), Set.of()), targets);
        AdaptingStorageMap storageMap = new AdaptingStorageMap();
        storageMap.configureAvailableBenches(List.of(new AdaptingBenchId("bench-1")));
        AdaptedLineStore store = new AdaptedLineStore(new AdaptingStorageLayout(
                AdaptingStorageConfig.defaults(), storageMap, targets, orders));
        store.stageAll(List.of(sourceLine), source.orderSheetKey(), "SC-1");
        AdaptingArea area = new AdaptingArea(
                List.of(new AdaptingBench("bench-1", store, 0d)), 1, storageMap);
        MapBackedToteLoadPlanRegistry registry = new MapBackedToteLoadPlanRegistry();
        PhysicalToteId physical = new PhysicalToteId("strict-physical");
        ToteLoadPlan original = new ToteLoadPlan(physical, List.of());
        registry.putLoadPlan(original);
        StationProcessingCoordinator coordinator = new StationProcessingCoordinator();
        PackProvenanceRegistry provenance = new PackProvenanceRegistry();
        AtomicBoolean observerCommitted = new AtomicBoolean();
        AdaptingAreaController areaController = new AdaptingAreaController(
                area, new DspSchedulerRuntimeState(new WarehouseSchedulerSnapshot(
                        List.of(), Map.of(), Set.of(), Optional.empty())), registry,
                new DefaultCollectedPackPlanFactory(dimensions(), new DspPackPlanFactory(provenance)),
                provenance, (sheet, tote, packs) -> () -> {
                    assertEquals(physical, tote);
                    assertSame(registry.getLoadPlanFor(physical),
                            coordinator.peekDisposition().orElseThrow().currentLoadPlan());
                    assertTrue(store.binSnapshots().isEmpty());
                    assertTrue(provenance.find(packs.getFirst().packId()).isPresent());
                    observerCommitted.set(true);
                });
        OperationalRouteDestination destination = destination("bench-1");
        RoutedPhysicalTote routed = routedTote(collecting, physical, destination,
                original, OperationalPhysicalToteSource.OSR);
        AdaptingStationProcessingTarget target = new AdaptingStationProcessingTarget(
                destination, new StationProcessingOrderCatalog(List.of(collecting)),
                registry, new AdaptingVisitFactory(), area, coordinator);
        AdaptingStationProcessingController controller = new AdaptingStationProcessingController(
                "strict-controller", Set.of(destination), registry, area, areaController,
                lifecycleController(), coordinator);

        target.accept(routed, Duration.ZERO);
        controller.update(context(1d), 0d);

        assertTrue(observerCommitted.get());
        assertEquals(StationProcessingDispositionType.CONTINUE,
                coordinator.peekDisposition().orElseThrow().type());
        assertTrue(registry.getLoadPlanFor(physical) != original);
    }

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
        assertEquals(2, associatedDisposition.currentLoadPlan().getPackPlans().size());
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
        assertEquals(1, emptyDisposition.currentLoadPlan().getPackPlans().size());
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
    void shouldRejectCompletionSheetServiceAndVisitTypeMismatchesBeforeMutation() {
        NotionalToteOrder order = order("mismatch-controller", OrderType.ASSOCIATED);
        PhysicalToteId physicalToteId = new PhysicalToteId("mismatch-controller-physical");

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

    @Test
    void shouldCompleteConcurrentStoreAndCollectIndependentlyAndStartQueueWithoutReticking() {
        List<NotionalToteOrder> orders = List.of(order("store", OrderType.ADAPTED),
                order("collect-2", OrderType.ASSOCIATED), order("collect-3", OrderType.ASSOCIATED),
                order("queued", OrderType.ASSOCIATED));
        ConcurrentFixture fixture = concurrentFixture(orders, 60d, 10d, false, false,
                AdaptingCollectObserver.noOp());
        fixture.acceptAll();
        assertEquals(3, fixture.bench().occupiedProcessingPositions());
        assertEquals(4, fixture.coordinator().snapshot().activeClaims().size());
        fixture.controller().update(context(10d), 10d);
        assertEquals(List.of("collect-2-physical"), fixture.completedIds());
        assertSame(fixture.totes().get(3).physicalToteId(), fixture.bench().position(2).activeToteId());
        assertEquals(10d, fixture.bench().position(2).snapshot().remainingProcessingSeconds());
        assertEquals(50d, fixture.bench().position(1).snapshot().remainingProcessingSeconds());
        assertEquals(AdaptingBenchState.COMPLETED, fixture.bench().position(3).state());
        assertTrue(fixture.coordinator().findActiveClaim(fixture.totes().get(2).physicalToteId()).isPresent());
        assertTrue(fixture.coordinator().findActiveClaim(fixture.totes().get(1).physicalToteId()).isEmpty());
        assertSame(fixture.registry().getLoadPlanFor(fixture.totes().get(1).physicalToteId()),
                fixture.coordinator().peekDisposition().orElseThrow().currentLoadPlan());
        assertSame(fixture.totes().getFirst().loadPlan(),
                fixture.registry().getLoadPlanFor(fixture.totes().getFirst().physicalToteId()));
        fixture.controller().update(context(10d), 0d);
        assertEquals(List.of("collect-2-physical", "collect-3-physical"), fixture.completedIds());
        assertEquals(2, fixture.bench().occupiedProcessingPositions());
        fixture.controller().update(context(20d), 10d);
        assertEquals(1, fixture.bench().occupiedProcessingPositions());
        fixture.controller().update(context(60d), 40d);
        assertEquals(List.of("collect-2-physical", "collect-3-physical", "queued-physical", "store-physical"),
                fixture.completedIds());
        var storeDisposition = fixture.coordinator().pendingDispositions().getLast();
        assertEquals(StationProcessingDispositionType.CONSUME, storeDisposition.type());
        assertSame(fixture.totes().getFirst().loadPlan(), storeDisposition.currentLoadPlan());
        assertEquals(PhysicalToteLifecycleState.CONSUMED_AT_ADAPTING,
                fixture.lifecycle().snapshot().totes().get(fixture.totes().getFirst().physicalToteId()).state());
        assertTrue(fixture.runtimeState().snapshot().preparedLineKeys().contains(
                PreparedLineKey.forPreparedLine(orders.getFirst().items().getFirst())));
        assertEquals(0, fixture.bench().occupiedProcessingPositions());
        assertTrue(fixture.coordinator().snapshot().activeClaims().isEmpty());
        assertTrue(fixture.area().peekQueuedVisit(fixture.id()).isEmpty());
    }

    @Test
    void shouldCommitSimultaneousStrictPreviewsByOrdinalWithReadOnlyVersionRefresh() {
        ConcurrentFixture fixture = concurrentFixture(List.of(
                order("first", OrderType.ASSOCIATED), order("second", OrderType.ASSOCIATED),
                order("third", OrderType.ASSOCIATED)), 60d, 10d, true, false,
                AdaptingCollectObserver.noOp());
        fixture.acceptAll();
        fixture.bench().tick(10d);
        var previews = fixture.bench().positions().stream()
                .map(position -> position.peekCompletion().orElseThrow()).toList();
        assertEquals(previews.getFirst().preparedOrderGroup().orElseThrow().mutationVersion(),
                previews.get(1).preparedOrderGroup().orElseThrow().mutationVersion());
        for (int index = 0; index < 3; index++) {
            var before = fixture.bench().position(index + 1).peekCompletion().orElseThrow();
            fixture.controller().update(context(10d), 0d);
            assertEquals(index + 1, fixture.coordinator().pendingDispositions().size());
            assertEquals(2 - index, fixture.bench().occupiedProcessingPositions());
            var disposition = fixture.coordinator().pendingDispositions().get(index);
            assertSame(fixture.totes().get(index), disposition.claim().routedTote());
            assertSame(fixture.registry().getLoadPlanFor(disposition.physicalToteId()),
                    disposition.currentLoadPlan());
            assertEquals(List.of("pack-line-" + fixture.orders().get(index).orderId() + "-1"),
                    disposition.currentLoadPlan().getPackPlans().stream().map(PackPlan::packId).toList());
            assertSame(previews.get(index), before);
        }
        assertEquals(List.of("first-physical", "second-physical", "third-physical"), fixture.completedIds());
        assertTrue(fixture.store().binSnapshots().isEmpty());
    }

    @Test
    void shouldSkipUnclaimedDomainCompletionWithoutConsumingItsPosition() {
        ConcurrentFixture fixture = concurrentFixture(List.of(
                order("unclaimed", OrderType.ASSOCIATED), order("claimed", OrderType.ASSOCIATED)),
                0d, 0d, false, false, AdaptingCollectObserver.noOp());
        fixture.area().submitVisitTo(fixture.id(), new AdaptingVisitFactory().create(
                fixture.totes().getFirst().physicalToteId(), fixture.orders().getFirst()));
        fixture.area().startQueuedPositions(fixture.id());
        var retained = fixture.bench().position(1).peekCompletion().orElseThrow();
        fixture.target().accept(fixture.totes().get(1), Duration.ZERO);
        fixture.controller().update(context(1d), 0d);
        assertEquals(List.of("claimed-physical"), fixture.completedIds());
        assertSame(retained, fixture.bench().position(1).peekCompletion().orElseThrow());
        assertEquals(1, fixture.bench().occupiedProcessingPositions());
        assertSame(fixture.totes().getFirst().loadPlan(), fixture.registry()
                .getLoadPlanFor(fixture.totes().getFirst().physicalToteId()));
    }

    @Test
    void shouldPreserveStrictPositionPlansStorageAndClaimsForAllExpectedPreparationFailures() {
        for (String failure : List.of("provenance", "observer", "prospective", "stale-plan")) {
            AtomicBoolean observerCommitted = new AtomicBoolean();
            AdaptingCollectObserver observer = (sheet, tote, packs) -> {
                if (failure.equals("observer")) {
                    throw new IllegalStateException("observer refused preparation");
                }
                return () -> observerCommitted.set(true);
            };
            ConcurrentFixture fixture = concurrentFixture(List.of(order("failure", OrderType.ASSOCIATED),
                    order("other", OrderType.ASSOCIATED)), 0d, 0d, true,
                    failure.equals("prospective"), observer);
            fixture.acceptAll();
            if (failure.equals("provenance")) {
                fixture.provenance().register("pack-line-failure-1",
                        new online.davisfamily.warehouse.sim.dsp.bagging.PackSourceProvenance(
                                new OrderSheetKey("wrong-source", 1), "line-failure", "product-1", "SC-1",
                                "pharmacy-1", "patient-failure", "prescription-failure"));
            } else if (failure.equals("stale-plan")) {
                fixture.registry().putLoadPlan(new ToteLoadPlan("failure-physical", List.of()));
            }
            var bins = fixture.store().binSnapshots();
            var provenanceBefore = fixture.provenance().snapshot();
            var coordinatorBefore = fixture.coordinator().snapshot();
            var completion = fixture.bench().position(1).peekCompletion().orElseThrow();
            var current = fixture.registry().getLoadPlanFor("failure-physical");
            var group = fixture.store().prepareOrderGroup("pharmacy-1", "failure");
            assertThrows(RuntimeException.class, () -> fixture.controller().update(context(1d), 0d), failure);
            assertSame(bins, fixture.store().binSnapshots(), failure);
            assertEquals(group, fixture.store().prepareOrderGroup("pharmacy-1", "failure"), failure);
            assertSame(current, fixture.registry().getLoadPlanFor("failure-physical"), failure);
            assertSame(completion, fixture.bench().position(1).peekCompletion().orElseThrow(), failure);
            assertEquals(2, fixture.bench().occupiedProcessingPositions(), failure);
            assertEquals(provenanceBefore, fixture.provenance().snapshot(), failure);
            assertEquals(coordinatorBefore, fixture.coordinator().snapshot(), failure);
            assertFalse(observerCommitted.get(), failure);
        }
    }

    private static ConcurrentFixture concurrentFixture(List<NotionalToteOrder> orders,
            double storeDuration, double collectDuration, boolean strict, boolean wrongProspectivePlan,
            AdaptingCollectObserver observer) {
        AdaptingBenchId id = new AdaptingBenchId("bench-1");
        AdaptingStorageMap map = new AdaptingStorageMap();
        map.configureAvailableBenches(List.of(id));
        map.assignPharmacyToBench("pharmacy-1", id);
        List<NotionalToteOrder> sources = new java.util.ArrayList<>();
        Map<PreparedLineKey, OrderSheetKey> targets = new java.util.LinkedHashMap<>();
        for (NotionalToteOrder order : orders) {
            sources.add(new NotionalToteOrder("source-" + order.orderId(), "source-" + order.orderId(),
                    order.serviceCentreId(), 1, OrderType.ADAPTED, order.items(), 1, 0));
            targets.put(PreparedLineKey.forPreparedLine(order.items().getFirst()), order.orderSheetKey());
        }
        AdaptedLineStore store;
        if (strict) {
            AdaptingTargetSheetCatalog catalog = new AdaptingTargetSheetCatalog(targets);
            List<NotionalToteOrder> allOrders = new java.util.ArrayList<>(sources);
            allOrders.addAll(orders);
            store = new AdaptedLineStore(new AdaptingStorageLayout(AdaptingStorageConfig.defaults(), map,
                    catalog, new AdaptingOrderPreparationCatalog(
                            new LoadedDspData(List.of(), allOrders, List.of(), Set.of()), catalog)));
        } else {
            store = new AdaptedLineStore();
        }
        for (int index = 0; index < orders.size(); index++) {
            if (orders.get(index).orderType() != OrderType.ADAPTED) {
                store.stageAll(orders.get(index).items(), sources.get(index).orderSheetKey(), "SC-1");
            }
        }
        AdaptingBench bench = new SnapshotThrowingBench("bench-1", store, storeDuration, collectDuration);
        AdaptingArea area = new AdaptingArea(List.of(bench), 3, map);
        MapBackedToteLoadPlanRegistry registry = new MapBackedToteLoadPlanRegistry();
        StationProcessingCoordinator coordinator = new StationProcessingCoordinator();
        PackProvenanceRegistry provenance = new PackProvenanceRegistry();
        DspSchedulerRuntimeState runtimeState = new DspSchedulerRuntimeState(new WarehouseSchedulerSnapshot(
                List.of(), Map.of(), Set.of(), Optional.empty()));
        DefaultCollectedPackPlanFactory packs = new DefaultCollectedPackPlanFactory(
                dimensions(), new DspPackPlanFactory(provenance));
        AdaptingAreaController areaController = strict
                ? new AdaptingAreaController(area, runtimeState, registry, packs, provenance, observer)
                : new AdaptingAreaController(area, runtimeState, registry, packs);
        List<RoutedPhysicalTote> totes = new java.util.ArrayList<>();
        List<InboundToteManifest> manifests = new java.util.ArrayList<>();
        for (NotionalToteOrder order : orders) {
            PhysicalToteId physical = new PhysicalToteId(order.orderId() + "-physical");
            ToteLoadPlan plan = wrongProspectivePlan && totes.isEmpty()
                    ? new WrongProspectiveTotePlan(physical) : new ToteLoadPlan(physical, List.of());
            registry.putLoadPlan(plan);
            totes.add(routedTote(order, physical, destination("bench-1"), plan, OperationalPhysicalToteSource.OSR));
            if (order.orderType() == OrderType.ADAPTED) {
                manifests.add(new InboundToteManifest(physical, order.orderSheetKey(), order.orderType(),
                        order.serviceCentreId(), order.items(), 0));
            }
        }
        InboundToteLifecycleController lifecycle = new InboundToteLifecycleController(
                new PhysicalToteLifecycleLedger(), new InboundToteManifestCatalog(manifests));
        for (InboundToteManifest manifest : manifests) {
            lifecycle.activate(manifest.physicalToteId(), Duration.ZERO);
        }
        AdaptingStationProcessingTarget target = new AdaptingStationProcessingTarget(destination("bench-1"),
                new StationProcessingOrderCatalog(orders), registry, new AdaptingVisitFactory(), area, coordinator);
        AdaptingStationProcessingController controller = new AdaptingStationProcessingController("concurrent",
                Set.of(destination("bench-1")), registry, area, areaController, lifecycle, coordinator);
        return new ConcurrentFixture(id, orders, totes, store, bench, area, registry, provenance, coordinator,
                target, controller, lifecycle, runtimeState);
    }

    private record ConcurrentFixture(AdaptingBenchId id, List<NotionalToteOrder> orders,
            List<RoutedPhysicalTote> totes, AdaptedLineStore store, AdaptingBench bench, AdaptingArea area,
            MapBackedToteLoadPlanRegistry registry, PackProvenanceRegistry provenance,
            StationProcessingCoordinator coordinator, AdaptingStationProcessingTarget target,
            AdaptingStationProcessingController controller, InboundToteLifecycleController lifecycle,
            DspSchedulerRuntimeState runtimeState) {
        void acceptAll() {
            for (RoutedPhysicalTote tote : totes) {
                target.accept(tote, Duration.ZERO);
            }
        }

        List<String> completedIds() {
            return coordinator.pendingDispositions().stream()
                    .map(disposition -> disposition.physicalToteId().value()).toList();
        }
    }

    private static final class WrongProspectiveTotePlan extends ToteLoadPlan {
        private WrongProspectiveTotePlan(PhysicalToteId physical) {
            super(physical, List.of());
        }

        @Override
        public ToteLoadPlan withAdditionalPackPlans(List<PackPlan> packs) {
            return new ToteLoadPlan("wrong-physical", packs);
        }
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
        if (mismatchedVisit.visitType() == AdaptingVisitType.COLLECT) {
            store.stage(order.items().getFirst(), new OrderSheetKey("source-" + order.orderId(), 1), "SC-1");
        }
        AdaptingBench bench = new AdaptingBench(
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
        AdaptingStationProcessingController controller = new AdaptingStationProcessingController(
                "mismatch-controller", Set.of(destination("bench-1")), registry, area,
                areaController, lifecycleController(), coordinator);

        area.submitVisitTo(new AdaptingBenchId("bench-1"), mismatchedVisit);
        coordinator.claim(routed, Duration.ZERO);
        area.startQueuedPositions(new AdaptingBenchId("bench-1"));
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
                        3,
                        "pharmacy-1",
                        "patient-" + orderId,
                        "prescription-" + orderId,
                        DspOrderLineType.ADAPTED,
                        orderId,
                        1,
                        1)),
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

    private static final class SnapshotThrowingBench extends AdaptingBench {
        private SnapshotThrowingBench(String id, AdaptedLineStore store, double storeDuration,
                double collectDuration) {
            super(id, store, storeDuration, collectDuration, 3);
        }
        @Override
        public AdaptingBenchSnapshot snapshot() {
            throw new AssertionError("Processing must not construct bench snapshots");
        }
    }
}
