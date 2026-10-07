package online.davisfamily.warehouse.sim.dsp.adapting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.bagging.DspPackPlanFactory;
import online.davisfamily.warehouse.sim.dsp.bagging.PackProvenanceRegistry;
import online.davisfamily.warehouse.sim.dsp.bagging.PackSourceProvenance;
import online.davisfamily.warehouse.sim.dsp.io.LoadedDspData;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.runtime.DspSchedulerRuntimeState;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

class AdaptingCollectFlowTest {

    @Test
    void shouldRegisterCollectedAdaptedPackAgainstOriginalSourceLine() {
        AdaptedLineStore store = new AdaptedLineStore();
        PackProvenanceRegistry provenanceRegistry = new PackProvenanceRegistry();
        DspOrderItem collectedLine = adaptedPreparedLine("line-1", "dispatch-1", 3, 2);
        store.stage(collectedLine, new OrderSheetKey("adapted-source-1", 1), "SC-1");
        List<Integer> resolvedOrdinals = new ArrayList<>();
        AdaptingBench bench = new AdaptingBench("bench-1", store, 1d);
        AdaptingArea area = new AdaptingArea(List.of(bench), 0);
        MapBackedToteLoadPlanRegistry loadPlans = new MapBackedToteLoadPlanRegistry();
        loadPlans.putLoadPlan(new ToteLoadPlan(
                "collect-tote-1",
                List.of(new PackPlan("pack-existing-1", "bag-existing", testDimensions()))));
        AdaptingAreaController controller = new AdaptingAreaController(
                area,
                emptyRuntimeState(),
                loadPlans,
                new DefaultCollectedPackPlanFactory(
                        testDimensions(),
                        new DspPackPlanFactory(provenanceRegistry),
                        (line, ordinal) -> {
                            resolvedOrdinals.add(ordinal);
                            return line.line().lineReference();
                        }));
        AdaptingVisitFactory visitFactory = new AdaptingVisitFactory();

        NotionalToteOrder collectingOrder = dispatchOrder(
                "dispatch-1",
                OrderType.ASSOCIATED,
                new DspOrderItem(
                        "line-1",
                        "product-line-1",
                        2,
                        "0000310",
                        DspOrderLineType.ADAPTED,
                        "dispatch-1",
                        1,
                        1));

        area.submitVisit(visitFactory.create(new PhysicalToteId("collect-tote-1"), collectingOrder));
        bench.startProcessing();
        bench.tick(1d);

        AdaptingBenchCompletion completion = controller.applyBenchCompletion(new AdaptingBenchId("bench-1")).orElseThrow();
        assertEquals(AdaptingVisitType.COLLECT, completion.visit().visitType());
        assertEquals(1, completion.collectedLines().size());

        ToteLoadPlan updatedLoadPlan = loadPlans.getLoadPlanFor("collect-tote-1");
        assertEquals(2, updatedLoadPlan.getPackPlans().size());
        assertEquals(List.of("bag-existing", "line-1"),
                updatedLoadPlan.getPackPlans().stream().map(PackPlan::correlationId).toList());
        assertEquals(List.of("pack-existing-1", "pack-line-1-1"),
                updatedLoadPlan.getPackPlans().stream().map(PackPlan::packId).toList());
        assertEquals(List.of(1), resolvedOrdinals);
        var provenance = provenanceRegistry.find("pack-line-1-1").orElseThrow();
        assertEquals(new OrderSheetKey("adapted-source-1", 1), provenance.sourceOrderSheetKey());
        assertEquals("line-1", provenance.lineReference());
        assertEquals("SC-1", provenance.serviceCentreId());
        assertEquals(collectedLine.patientId(), provenance.patientId());
        assertEquals(collectedLine.prescriptionId(), provenance.prescriptionId());
        assertFalse(store.contains(PreparedLineKey.forPreparedLine(collectedLine)));
    }

    @Test
    void shouldCreateLoadPlanForEmptyCollectingTote() {
        AdaptedLineStore store = new AdaptedLineStore();
        DspOrderItem collectedLine = adaptedPreparedLine("line-2", "dispatch-2", 3, 2);
        store.stage(collectedLine, new OrderSheetKey("adapted-source-2", 1), "SC-1");
        AdaptingBench bench = new AdaptingBench("bench-1", store, 0d);
        AdaptingArea area = new AdaptingArea(List.of(bench), 0);
        MapBackedToteLoadPlanRegistry loadPlans = new MapBackedToteLoadPlanRegistry();
        AdaptingAreaController controller = new AdaptingAreaController(
                area,
                emptyRuntimeState(),
                loadPlans,
                new DefaultCollectedPackPlanFactory(
                        testDimensions(),
                        new DspPackPlanFactory(new PackProvenanceRegistry())));
        AdaptingVisitFactory visitFactory = new AdaptingVisitFactory();

        NotionalToteOrder collectingOrder = dispatchOrder(
                "dispatch-2",
                OrderType.EMPTY,
                new DspOrderItem(
                        "line-2",
                        "product-line-2",
                        2,
                        "0000310",
                        DspOrderLineType.ADAPTED,
                        "dispatch-2",
                        1,
                        1));

        area.submitVisit(visitFactory.create(new PhysicalToteId("empty-tote-1"), collectingOrder));
        bench.startProcessing();

        controller.applyBenchCompletion(new AdaptingBenchId("bench-1")).orElseThrow();

        ToteLoadPlan createdLoadPlan = loadPlans.getLoadPlanFor("empty-tote-1");
        assertEquals(1, createdLoadPlan.getPackPlans().size());
        assertEquals("line-2", createdLoadPlan.getPackPlans().getFirst().correlationId());
        assertEquals("pack-line-2-1", createdLoadPlan.getPackPlans().getFirst().packId());
        assertFalse(store.contains(PreparedLineKey.forPreparedLine(collectedLine)));
    }

    @Test
    void shouldRejectFullPackCollectVisitByContract() {
        AdaptingVisitFactory visitFactory = new AdaptingVisitFactory();
        NotionalToteOrder fullPackOrder = dispatchOrder(
                "dispatch-3",
                OrderType.FULL_PACK,
                new DspOrderItem(
                        "line-3",
                        "product-line-3",
                        1,
                        "0000310",
                        DspOrderLineType.FULL_PACK,
                        "dispatch-3",
                        1,
                        0));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> visitFactory.create(new PhysicalToteId("collect-tote-3"), fullPackOrder));
        assertTrue(ex.getMessage().contains("FULL_PACK"));
    }

    @Test
    void shouldPrepareStrictCollectWithoutDrainingAndCommitExactReplacement() {
        StrictFixture fixture = strictFixture(AdaptingCollectObserver.noOp(), List.of());
        AdaptingBenchId benchId = new AdaptingBenchId("bench-1");
        ToteLoadPlan current = fixture.registry().getLoadPlanFor("collect-physical");
        List<AdaptingBinSnapshot> beforeBins = fixture.store().binSnapshots();

        PreparedAdaptingCollect decision = fixture.controller().prepareBenchCollect(benchId, current);

        assertEquals(AdaptingBenchState.COMPLETED, fixture.bench().state());
        assertTrue(fixture.store().contains(new PreparedLineKey("dispatch", "line-1")));
        assertTrue(fixture.provenance().find("pack-line-1-1").isEmpty());
        assertTrue(fixture.registry().getLoadPlanFor("collect-physical") == current);
        assertTrue(fixture.store().binSnapshots() == beforeBins);

        Runnable observerCommit = fixture.controller().commitBenchCollect(decision);
        observerCommit.run();
        assertEquals(AdaptingBenchState.IDLE, fixture.bench().state());
        assertFalse(fixture.store().contains(new PreparedLineKey("dispatch", "line-1")));
        assertEquals(List.of("pack-line-1-1"), fixture.registry()
                .getLoadPlanFor("collect-physical").getPackPlans().stream().map(PackPlan::packId).toList());
        assertTrue(fixture.provenance().find("pack-line-1-1").isPresent());
    }

    @Test
    void shouldRejectProspectivePlanAndObserverBeforeStrictDrain() {
        StrictFixture duplicate = strictFixture(AdaptingCollectObserver.noOp(),
                List.of(new PackPlan("pack-line-1-1", "old", testDimensions())));
        assertStrictPreparationFailsWithoutMutation(duplicate);

        StrictFixture observerFailure = strictFixture((sheet, tote, packs) -> {
            throw new IllegalStateException("observer rejected");
        }, List.of());
        assertStrictPreparationFailsWithoutMutation(observerFailure);

        StrictFixture provenanceConflict = strictFixture(AdaptingCollectObserver.noOp(), List.of());
        provenanceConflict.provenance().register("pack-line-1-1", new PackSourceProvenance(
                new OrderSheetKey("other-source", 1), "line-1", "product-line-1", "SC-1",
                "0000310", "patient", "prescription"));
        assertStrictPreparationFailsWithoutMutation(provenanceConflict);
    }

    private static void assertStrictPreparationFailsWithoutMutation(StrictFixture fixture) {
        ToteLoadPlan current = fixture.registry().getLoadPlanFor("collect-physical");
        List<AdaptingBinSnapshot> bins = fixture.store().binSnapshots();
        var provenanceBefore = fixture.provenance().snapshot();
        assertThrows(RuntimeException.class, () -> fixture.controller().prepareBenchCollect(
                new AdaptingBenchId("bench-1"), current));
        assertTrue(fixture.store().binSnapshots() == bins);
        assertTrue(fixture.registry().getLoadPlanFor("collect-physical") == current);
        assertEquals(provenanceBefore, fixture.provenance().snapshot());
        assertEquals(AdaptingBenchState.COMPLETED, fixture.bench().state());
        assertTrue(fixture.bench().peekCompletion().isPresent());
    }

    @Test
    void shouldRefreshOnlyAtPreparationAndCommitExactConcurrentPosition() {
        ConcurrentStrictFixture fixture = concurrentStrictFixture();
        var firstCompletion = fixture.bench().position(1).peekCompletion().orElseThrow();
        var secondCompletion = fixture.bench().position(2).peekCompletion().orElseThrow();
        PreparedAdaptingCollect first = fixture.controller().prepareBenchCollect(
                fixture.id(), 1, fixture.registry().getLoadPlanFor("collect-first"));
        assertEquals(first, new PreparedAdaptingCollect(first.benchId(), first.completion(),
                first.currentLoadPlan(), first.replacementLoadPlan(), first.preparedPacks(),
                first.observerCommit()));
        PreparedAdaptingCollect staleSecond = fixture.controller().prepareBenchCollect(
                fixture.id(), 2, fixture.registry().getLoadPlanFor("collect-second"));
        fixture.controller().commitBenchCollect(first).run();
        assertEquals(1, fixture.bench().occupiedProcessingPositions());
        var bins = fixture.store().binSnapshots();
        var provenance = fixture.provenance().snapshot();
        assertThrows(IllegalStateException.class, () -> fixture.controller().commitBenchCollect(staleSecond));
        assertSame(bins, fixture.store().binSnapshots());
        assertEquals(provenance, fixture.provenance().snapshot());
        assertSame(staleSecond.currentLoadPlan(), fixture.registry().getLoadPlanFor("collect-second"));
        assertSame(secondCompletion, fixture.bench().position(2).peekCompletion().orElseThrow());
        assertEquals(List.of("first"), fixture.publishedOrders());

        PreparedAdaptingCollect fresh = fixture.controller().prepareBenchCollect(
                fixture.id(), 2, staleSecond.currentLoadPlan());
        assertSame(secondCompletion, fresh.completion());
        assertEquals(secondCompletion.collectedLines(), fresh.currentOrderGroup().records());
        assertEquals(staleSecond.preparedPacks().packPlans(), fresh.preparedPacks().packPlans());
        fixture.controller().commitBenchCollect(fresh).run();
        assertSame(fresh.replacementLoadPlan(), fixture.registry().getLoadPlanFor("collect-second"));
        assertEquals(List.of("first", "second"), fixture.publishedOrders());
        assertEquals(0, fixture.bench().occupiedProcessingPositions());
        assertTrue(fixture.bench().position(1).peekCompletion().isEmpty());
        assertTrue(fixture.bench().position(2).peekCompletion().isEmpty());
        assertSame(firstCompletion, first.completion());
    }

    @Test
    void shouldRefreshPendingPreviewAfterUnrelatedStoreButRejectAlreadyPreparedToken() {
        ConcurrentStrictFixture fixture = concurrentStrictFixture();
        ToteLoadPlan current = fixture.registry().getLoadPlanFor("collect-first");
        var token = fixture.controller().prepareBenchCollect(fixture.id(), 1, current);
        fixture.store().stage(fixture.otherLine(), new OrderSheetKey("source-other", 1), "SC-1");
        var bins = fixture.store().binSnapshots();
        assertThrows(IllegalStateException.class, () -> fixture.controller().commitBenchCollect(token));
        assertSame(bins, fixture.store().binSnapshots());
        assertTrue(fixture.provenance().snapshot().provenanceByPackId().isEmpty());
        assertTrue(fixture.publishedOrders().isEmpty());
        var fresh = fixture.controller().prepareBenchCollect(fixture.id(), 1, current);
        assertSame(token.completion(), fresh.completion());
        assertEquals(token.currentOrderGroup().records(), fresh.currentOrderGroup().records());
        fixture.controller().commitBenchCollect(fresh).run();
        assertTrue(fixture.store().contains(PreparedLineKey.forPreparedLine(fixture.otherLine())));
        assertEquals(List.of("first"), fixture.publishedOrders());
    }

    @Test
    void shouldRejectStaleCompletionAndPlanReferencesBeforeDrainAndRejectSingularMultiPositionApis() {
        ConcurrentStrictFixture fixture = concurrentStrictFixture();
        ToteLoadPlan current = fixture.registry().getLoadPlanFor("collect-first");
        var token = fixture.controller().prepareBenchCollect(fixture.id(), 1, current);
        var copiedCompletion = new AdaptingBenchCompletion(token.completion().visit(),
                token.completion().collectedLines(), token.completion().preparedOrderGroup());
        var copiedToken = new PreparedAdaptingCollect(token.benchId(), copiedCompletion,
                current, token.replacementLoadPlan(), token.preparedPacks(), token.observerCommit(),
                1, token.currentOrderGroup());
        var bins = fixture.store().binSnapshots();
        assertThrows(IllegalStateException.class, () -> fixture.controller().commitBenchCollect(copiedToken));
        var wrongPosition = new PreparedAdaptingCollect(token.benchId(), token.completion(),
                current, token.replacementLoadPlan(), token.preparedPacks(), token.observerCommit(),
                2, token.currentOrderGroup());
        assertThrows(IllegalStateException.class, () -> fixture.controller().commitBenchCollect(wrongPosition));
        assertThrows(IllegalStateException.class, () -> fixture.controller().applyBenchCompletion(fixture.id()));
        assertThrows(IllegalStateException.class, () -> fixture.controller().prepareBenchCollect(fixture.id(), current));
        ToteLoadPlan replacementCurrent = new ToteLoadPlan(current.physicalToteId(), current.getPackPlans());
        fixture.registry().putLoadPlan(replacementCurrent);
        assertThrows(IllegalStateException.class, () -> fixture.controller().commitBenchCollect(token));
        assertSame(bins, fixture.store().binSnapshots());
        assertSame(replacementCurrent, fixture.registry().getLoadPlanFor(current.physicalToteId()));
        assertSame(token.completion(), fixture.bench().position(1).peekCompletion().orElseThrow());
        assertEquals(2, fixture.bench().occupiedProcessingPositions());
        assertTrue(fixture.provenance().snapshot().provenanceByPackId().isEmpty());
        assertTrue(fixture.publishedOrders().isEmpty());
    }

    @Test
    void shouldApplyLaterSheetEmptyStrictCollectOnlyAfterDesignatedFirstDrain() {
        ConcurrentStrictFixture fixture = concurrentStrictFixture();
        fixture.controller().commitBenchCollect(fixture.controller().prepareBenchCollect(
                fixture.id(), 1, fixture.registry().getLoadPlanFor("collect-first"))).run();
        DspOrderItem laterLine = adaptedPreparedLine("line-first-later", "first", 1, 1);
        NotionalToteOrder later = new NotionalToteOrder("first", "first-2", "SC-1", 2,
                OrderType.ASSOCIATED, List.of(laterLine), 1, 2);
        ToteLoadPlan current = new ToteLoadPlan("collect-later", List.of(
                new PackPlan("direct-pack", "direct-bag", testDimensions())));
        fixture.registry().putLoadPlan(current);
        fixture.controller().applyBenchCompletion(fixture.id(), 3); // Idle position stays idle.
        AdaptingVisit visit = new AdaptingVisitFactory().create(current.physicalToteId(), later);
        fixture.bench().position(1).acceptVisit(visit);
        fixture.bench().position(1).startProcessing();
        var bins = fixture.store().binSnapshots();
        var provenance = fixture.provenance().snapshot();
        var secondCompletion = fixture.bench().position(2).peekCompletion().orElseThrow();
        var token = fixture.controller().prepareBenchCollect(fixture.id(), 1, current);
        assertFalse(token.currentOrderGroup().firstCollection());
        assertTrue(token.preparedPacks().packPlans().isEmpty());
        fixture.controller().commitBenchCollect(token).run();
        assertSame(bins, fixture.store().binSnapshots());
        assertEquals(provenance, fixture.provenance().snapshot());
        assertSame(token.replacementLoadPlan(), fixture.registry().getLoadPlanFor(current.physicalToteId()));
        assertEquals(current.getPackPlans(), token.replacementLoadPlan().getPackPlans());
        assertSame(secondCompletion, fixture.bench().position(2).peekCompletion().orElseThrow());
        assertEquals(1, fixture.bench().occupiedProcessingPositions());
        assertEquals(List.of("first", "first"), fixture.publishedOrders());
    }

    private static ConcurrentStrictFixture concurrentStrictFixture() {
        List<NotionalToteOrder> orders = new ArrayList<>();
        Map<PreparedLineKey, OrderSheetKey> targets = new java.util.LinkedHashMap<>();
        List<DspOrderItem> lines = new ArrayList<>();
        for (String id : List.of("first", "second", "other")) {
            DspOrderItem line = adaptedPreparedLine("line-" + id, id, 1, 1);
            lines.add(line);
            orders.add(new NotionalToteOrder("source-" + id, "source-" + id, "SC-1", 1,
                    OrderType.ADAPTED, List.of(line), 1, 0));
            orders.add(dispatchOrder(id, OrderType.ASSOCIATED, line));
            targets.put(PreparedLineKey.forPreparedLine(line), new OrderSheetKey(id, 1));
        }
        DspOrderItem laterLine = adaptedPreparedLine("line-first-later", "first", 1, 1);
        NotionalToteOrder extraSource = new NotionalToteOrder("source-first-later", "source-first-later",
                "SC-1", 1, OrderType.ADAPTED, List.of(laterLine), 1, 0);
        orders.add(extraSource);
        orders.add(new NotionalToteOrder("first", "first-2", "SC-1", 2,
                OrderType.ASSOCIATED, List.of(laterLine), 1, 2));
        targets.put(PreparedLineKey.forPreparedLine(laterLine), new OrderSheetKey("first", 2));
        AdaptingTargetSheetCatalog targetCatalog = new AdaptingTargetSheetCatalog(targets);
        AdaptingOrderPreparationCatalog orderCatalog = new AdaptingOrderPreparationCatalog(
                new LoadedDspData(List.of(), orders, List.of(), Set.of()), targetCatalog);
        AdaptingStorageMap map = new AdaptingStorageMap();
        AdaptingBenchId id = new AdaptingBenchId("bench-1");
        map.configureAvailableBenches(List.of(id));
        AdaptedLineStore store = new AdaptedLineStore(new AdaptingStorageLayout(
                AdaptingStorageConfig.defaults(), map, targetCatalog, orderCatalog));
        for (int i = 0; i < 2; i++) {
            store.stage(lines.get(i), orders.get(i * 2).orderSheetKey(), "SC-1");
        }
        store.stage(laterLine, extraSource.orderSheetKey(), "SC-1");
        AdaptingBench bench = new AdaptingBench("bench-1", store, 0d, 0d, 3);
        AdaptingArea area = new AdaptingArea(List.of(bench), 3, map);
        MapBackedToteLoadPlanRegistry registry = new MapBackedToteLoadPlanRegistry();
        PackProvenanceRegistry provenance = new PackProvenanceRegistry();
        List<String> published = new ArrayList<>();
        AdaptingAreaController controller = new AdaptingAreaController(area, emptyRuntimeState(), registry,
                new DefaultCollectedPackPlanFactory(testDimensions(), new DspPackPlanFactory(provenance)),
                provenance, (sheet, tote, packs) -> () -> published.add(sheet.orderId()));
        for (int i = 0; i < 2; i++) {
            String toteId = "collect-" + orders.get(i * 2 + 1).orderId();
            registry.putLoadPlan(new ToteLoadPlan(toteId, List.of()));
            area.submitVisitTo(id, new AdaptingVisitFactory().create(
                    new PhysicalToteId(toteId), orders.get(i * 2 + 1)));
        }
        area.startQueuedPositions(id);
        return new ConcurrentStrictFixture(id, store, bench, registry, provenance, controller,
                published, lines.get(2));
    }

    private record ConcurrentStrictFixture(AdaptingBenchId id, AdaptedLineStore store,
            AdaptingBench bench, MapBackedToteLoadPlanRegistry registry, PackProvenanceRegistry provenance,
            AdaptingAreaController controller, List<String> publishedOrders, DspOrderItem otherLine) { }

    private static StrictFixture strictFixture(AdaptingCollectObserver observer,
            List<PackPlan> initialPacks) {
        DspOrderItem sourceLine = adaptedPreparedLine("line-1", "dispatch", 1, 1);
        DspOrderItem dispatchLine = adaptedPreparedLine("line-1", "dispatch", 1, 1);
        NotionalToteOrder source = new NotionalToteOrder("source", "source", "SC-1", 1,
                OrderType.ADAPTED, List.of(sourceLine), 1, 0);
        NotionalToteOrder dispatch = new NotionalToteOrder("dispatch", "dispatch", "SC-1", 1,
                OrderType.ASSOCIATED, List.of(dispatchLine), 1, 1);
        PreparedLineKey key = PreparedLineKey.forPreparedLine(sourceLine);
        AdaptingTargetSheetCatalog targets = new AdaptingTargetSheetCatalog(
                Map.of(key, dispatch.orderSheetKey()));
        AdaptingOrderPreparationCatalog orders = new AdaptingOrderPreparationCatalog(
                new LoadedDspData(List.of(), List.of(source, dispatch), List.of(), Set.of()), targets);
        AdaptingStorageMap storageMap = new AdaptingStorageMap();
        storageMap.configureAvailableBenches(List.of(new AdaptingBenchId("bench-1")));
        AdaptedLineStore store = new AdaptedLineStore(new AdaptingStorageLayout(
                AdaptingStorageConfig.defaults(), storageMap, targets, orders));
        store.stageAll(List.of(sourceLine), source.orderSheetKey(), "SC-1");
        AdaptingBench bench = new AdaptingBench("bench-1", store, 0d);
        AdaptingArea area = new AdaptingArea(List.of(bench), 0, storageMap);
        MapBackedToteLoadPlanRegistry registry = new MapBackedToteLoadPlanRegistry();
        registry.putLoadPlan(new ToteLoadPlan("collect-physical", initialPacks));
        PackProvenanceRegistry provenance = new PackProvenanceRegistry();
        AdaptingAreaController controller = new AdaptingAreaController(area, emptyRuntimeState(),
                registry, new DefaultCollectedPackPlanFactory(testDimensions(),
                        new DspPackPlanFactory(provenance)), provenance, observer);
        bench.acceptVisit(new AdaptingVisit(new PhysicalToteId("collect-physical"),
                new AdaptingVisitFactory().profileFor(dispatch)));
        bench.startProcessing();
        return new StrictFixture(store, bench, registry, provenance, controller);
    }

    private record StrictFixture(AdaptedLineStore store, AdaptingBench bench,
            MapBackedToteLoadPlanRegistry registry, PackProvenanceRegistry provenance,
            AdaptingAreaController controller) { }

    private static DspSchedulerRuntimeState emptyRuntimeState() {
        return new DspSchedulerRuntimeState(new WarehouseSchedulerSnapshot(
                List.of(),
                Map.of(),
                Set.of(),
                Optional.empty()));
    }

    private static NotionalToteOrder dispatchOrder(String orderId, OrderType orderType, DspOrderItem... items) {
        return new NotionalToteOrder(
                orderId,
                "notional-" + orderId,
                "SC-1",
                1,
                orderType,
                List.of(items),
                0L);
    }

    private static DspOrderItem adaptedPreparedLine(
            String lineId,
            String targetOrderId,
            int quantity,
            int numberOfPacksPicked) {
        return new DspOrderItem(
                lineId,
                "product-" + lineId,
                quantity,
                "0000310",
                DspOrderLineType.ADAPTED,
                targetOrderId,
                1,
                numberOfPacksPicked);
    }

    private static PackDimensions testDimensions() {
        return new PackDimensions(0.20f, 0.10f, 0.08f);
    }
}
