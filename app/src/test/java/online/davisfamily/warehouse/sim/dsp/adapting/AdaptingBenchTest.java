package online.davisfamily.warehouse.sim.dsp.adapting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.io.LoadedDspData;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;

class AdaptingBenchTest {
    private static final OrderSheetKey SOURCE_ORDER_SHEET = new OrderSheetKey("adapted-source", 7);
    private static final String SERVICE_CENTRE_ID = "104";

    @Test
    void shouldRetainStablePositionsAndChooseTheLowestIdleOrdinal() {
        AdaptingBench bench = new AdaptingBench("bench-1", new AdaptedLineStore(), 60d, 10d, 3);
        List<AdaptingProcessingPosition> positions = bench.positions();
        assertSame(positions, bench.positions());
        assertEquals(3, bench.processingCapacity());
        assertEquals(0, bench.occupiedProcessingPositions());
        assertEquals(AdaptingBenchState.IDLE, bench.state());
        assertSame(positions.getFirst(), bench.firstIdlePosition().orElseThrow());
        for (int ordinal = 1; ordinal <= 3; ordinal++) {
            assertSame(positions.get(ordinal - 1), bench.position(ordinal));
            assertEquals(ordinal, bench.position(ordinal).ordinal());
        }
        assertThrows(UnsupportedOperationException.class, positions::clear);
        for (int ordinal : new int[] {-1, 0, 4, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> bench.position(ordinal));
        }
        assertEquals(0, bench.occupiedProcessingPositions());
        bench.position(2).acceptVisit(AdaptingVisit.store(new PhysicalToteId("second"),
                SOURCE_ORDER_SHEET, SERVICE_CENTRE_ID,
                List.of(adaptedLine("second", "target", "0000310"))));
        assertSame(positions.getFirst(), bench.firstIdlePosition().orElseThrow());
        bench.position(1).acceptVisit(AdaptingVisit.store(new PhysicalToteId("first"),
                SOURCE_ORDER_SHEET, SERVICE_CENTRE_ID,
                List.of(adaptedLine("first", "target", "0000310"))));
        assertSame(positions.get(2), bench.firstIdlePosition().orElseThrow());
        assertEquals(2, bench.occupiedProcessingPositions());
        assertSame(positions, bench.positions());
    }

    @Test
    void shouldUseTheLowestOccupiedPositionAsTheRepresentativeView() {
        AdaptedLineStore store = new AdaptedLineStore();
        AdaptingBench bench = new AdaptingBench("bench-1", store, 0d, 0d, 3);
        bench.bindStorageMap(storageMap("0000310", "bench-1"));
        AdaptingVisit third = AdaptingVisit.store(new PhysicalToteId("third"),
                SOURCE_ORDER_SHEET, SERVICE_CENTRE_ID,
                List.of(adaptedLine("third", "target", "0000310")));
        bench.position(3).acceptVisit(third);
        bench.position(3).startProcessing();
        assertEquals(AdaptingBenchState.COMPLETED, bench.state());
        assertEquals("third", bench.snapshot().activeToteId());

        bench.position(2).acceptVisit(AdaptingVisit.collect(new PhysicalToteId("second"),
                new OrderSheetKey("target", 1), SERVICE_CENTRE_ID,
                List.of(new PreparedLineKey("target", "missing")), List.of("0000310")));
        bench.position(2).startProcessing();
        assertEquals(AdaptingBenchState.BLOCKED, bench.state());
        assertEquals("second", bench.snapshot().activeToteId());
        assertEquals("bench-1", bench.snapshot().benchId());
        assertEquals(AdaptingVisitType.COLLECT, bench.snapshot().activeVisitType());
        assertTrue(bench.canAcceptVisit());
        assertEquals(2, bench.occupiedProcessingPositions());

        bench.position(1).acceptVisit(AdaptingVisit.store(new PhysicalToteId("first"),
                SOURCE_ORDER_SHEET, SERVICE_CENTRE_ID,
                List.of(adaptedLine("first", "target", "0000310"))));
        assertEquals(AdaptingBenchState.QUEUED, bench.state());
        assertEquals("first", bench.snapshot().activeToteId());
        assertFalse(bench.canAcceptVisit());
        assertEquals(3, bench.occupiedProcessingPositions());
        assertSame(third, bench.position(3).consumeCompletion().orElseThrow().visit());
        assertEquals("first", bench.snapshot().activeToteId());
        assertEquals(2, bench.occupiedProcessingPositions());
        bench.position(1).startProcessing();
        bench.position(1).consumeCompletion().orElseThrow();
        assertEquals("second", bench.snapshot().activeToteId());
        assertEquals(AdaptingBenchState.BLOCKED, bench.state());
        bench.position(2).clearBlocked();
        assertEquals(0, bench.occupiedProcessingPositions());
        assertEquals(AdaptingBenchState.IDLE, bench.state());
        assertEquals("", bench.snapshot().activeToteId());
        assertEquals(bench.position(1).snapshot(), bench.snapshot());
    }

    @Test
    void shouldRejectSingularOperationsOnMultiPositionBenchesWithoutMutation() {
        AdaptedLineStore store = new AdaptedLineStore();
        AdaptingBench bench = new AdaptingBench("bench-1", store, 0d, 0d, 3);
        bench.bindStorageMap(storageMap("0000310", "bench-1"));
        AdaptingVisit visit = AdaptingVisit.store(new PhysicalToteId("pending"),
                SOURCE_ORDER_SHEET, SERVICE_CENTRE_ID,
                List.of(adaptedLine("pending", "target", "0000310")));
        assertSingularOperationsRejected(bench, visit);
        assertEquals(0, bench.occupiedProcessingPositions());

        bench.position(2).acceptVisit(AdaptingVisit.collect(new PhysicalToteId("blocked"),
                new OrderSheetKey("target", 1), SERVICE_CENTRE_ID,
                List.of(new PreparedLineKey("target", "missing")), List.of("0000310")));
        bench.position(2).startProcessing();
        bench.position(3).acceptVisit(visit);
        bench.position(3).startProcessing();
        AdaptingBenchCompletion completion = bench.position(3).peekCompletion().orElseThrow();
        AdaptingBenchSnapshot before = bench.snapshot();
        assertSingularOperationsRejected(bench, visit);
        assertEquals(before, bench.snapshot());
        assertEquals(2, bench.occupiedProcessingPositions());
        assertSame(completion, bench.position(3).peekCompletion().orElseThrow());
        assertEquals(AdaptingBenchState.BLOCKED, bench.position(2).state());
        assertTrue(store.contains(PreparedLineKey.forPreparedLine(visit.preparedLines().getFirst())));
    }

    private static void assertSingularOperationsRejected(AdaptingBench bench, AdaptingVisit visit) {
        assertThrows(IllegalStateException.class, () -> bench.acceptVisit(visit));
        assertThrows(IllegalStateException.class, bench::startProcessing);
        assertThrows(IllegalStateException.class, bench::peekCompletion);
        assertThrows(IllegalStateException.class, bench::consumeCompletion);
        assertThrows(IllegalStateException.class, bench::clearBlocked);
        assertThrows(IllegalStateException.class, () -> bench.commitOrderGroup(null));
    }

    @Test
    void shouldKeepTheLegacyOnePositionEqualDurationStoreAndCollectCycle() {
        AdaptedLineStore store = new AdaptedLineStore();
        AdaptingBench bench = new AdaptingBench("bench-1", store, 5d);
        bench.bindStorageMap(storageMap("0000310", "bench-1"));
        DspOrderItem line = adaptedLine("line", "target", "0000310");
        bench.acceptVisit(AdaptingVisit.store(new PhysicalToteId("source"),
                SOURCE_ORDER_SHEET, SERVICE_CENTRE_ID, List.of(line)));
        assertEquals(1, bench.processingCapacity());
        assertEquals(1, bench.occupiedProcessingPositions());
        bench.startProcessing();
        bench.tick(4d);
        assertTrue(bench.peekCompletion().isEmpty());
        assertFalse(store.contains(PreparedLineKey.forPreparedLine(line)));
        bench.tick(1d);
        assertEquals(1, bench.occupiedProcessingPositions());
        assertFalse(bench.canAcceptVisit());
        bench.consumeCompletion().orElseThrow();
        assertEquals(0, bench.occupiedProcessingPositions());
        assertTrue(bench.consumeCompletion().isEmpty());

        bench.acceptVisit(AdaptingVisit.collect(new PhysicalToteId("collect"),
                new OrderSheetKey("target", 1), SERVICE_CENTRE_ID,
                List.of(PreparedLineKey.forPreparedLine(line)), List.of("0000310")));
        bench.startProcessing();
        bench.tick(4d);
        assertTrue(bench.peekCompletion().isEmpty());
        assertTrue(store.contains(PreparedLineKey.forPreparedLine(line)));
        bench.tick(1d);
        assertEquals(1, bench.occupiedProcessingPositions());
        assertEquals(line, bench.peekCompletion().orElseThrow().collectedLines().getFirst().line());
        bench.consumeCompletion().orElseThrow();
        assertEquals(0, bench.occupiedProcessingPositions());
        assertEquals(AdaptingBenchState.IDLE, bench.state());
    }

    @Test
    void shouldRejectInvalidBenchConfiguration() {
        AdaptedLineStore store = new AdaptedLineStore();
        assertThrows(IllegalArgumentException.class, () -> new AdaptingBench(" ", store, 60d, 10d, 3));
        assertThrows(IllegalArgumentException.class, () -> new AdaptingBench("bench-1", null, 60d, 10d, 3));
        assertThrows(IllegalArgumentException.class, () -> new AdaptingBench("bench-1", store, -1d, 10d, 3));
        assertThrows(IllegalArgumentException.class, () -> new AdaptingBench("bench-1", store, 60d, -1d, 3));
        assertThrows(IllegalArgumentException.class, () -> new AdaptingBench("bench-1", store, 60d, 10d, 0));
        assertThrows(IllegalArgumentException.class, () -> new AdaptingBench("bench-1", store, 60d, 10d, -1));
    }

    @Test
    void shouldStageAdaptedLinesAfterStoreVisitCompletes() {
        AdaptedLineStore store = new AdaptedLineStore(new AdaptingStorageLayout(
                AdaptingStorageConfig.defaults(),
                storageMap("0000310", "bench-1", "0000388", "bench-2")));
        AdaptingBench bench = new AdaptingBench("bench-1", store, 5d);
        DspOrderItem line1 = adaptedLine("line-1", "target-1", "0000310");
        DspOrderItem line2 = adaptedLine("line-2", "target-2", "0000388");

        bench.acceptVisit(AdaptingVisit.store(
                new PhysicalToteId("tote-store"),
                SOURCE_ORDER_SHEET,
                SERVICE_CENTRE_ID,
                List.of(line1, line2)));
        assertEquals(AdaptingBenchState.QUEUED, bench.state());
        assertEquals("tote-store", bench.snapshot().activeToteId());
        assertEquals(AdaptingVisitType.STORE, bench.snapshot().activeVisitType());

        bench.startProcessing();
        assertEquals(AdaptingBenchState.PROCESSING_STORE, bench.state());
        bench.tick(4d);
        assertEquals(AdaptingBenchState.PROCESSING_STORE, bench.state());
        assertEquals(1d, bench.snapshot().remainingProcessingSeconds(), 0.0001d);

        bench.tick(1d);
        assertEquals(AdaptingBenchState.COMPLETED, bench.state());
        assertTrue(store.contains(PreparedLineKey.forPreparedLine(line1)));
        assertTrue(store.contains(PreparedLineKey.forPreparedLine(line2)));
        assertEquals(2, store.snapshot().stagedLineCount());
        assertEquals(1, store.snapshot().stagedLineCountByBench().getOrDefault(new AdaptingBenchId("bench-1"), 0));
        assertEquals(1, store.snapshot().stagedLineCountByBench().getOrDefault(new AdaptingBenchId("bench-2"), 0));

        AdaptingBenchCompletion completion = bench.consumeCompletion().orElseThrow();
        assertEquals(AdaptingVisitType.STORE, completion.visit().visitType());
        assertTrue(completion.collectedLines().isEmpty());
        assertEquals(AdaptingBenchState.IDLE, bench.state());
    }

    @Test
    void shouldReturnOriginalSourceSheetWhenAdaptedLineIsCollected() {
        AdaptedLineStore store = new AdaptedLineStore(new AdaptingStorageLayout(
                AdaptingStorageConfig.defaults(),
                storageMap("0000310", "bench-1", "0000388", "bench-2")));
        AdaptingBench bench = new AdaptingBench("bench-1", store, 0d);
        DspOrderItem line1 = adaptedLine("line-1", "target-1", "0000310");
        DspOrderItem line2 = adaptedLine("line-2", "target-2", "0000388");
        stage(store, line1);
        stage(store, line2);

        bench.acceptVisit(AdaptingVisit.collect(
                new PhysicalToteId("tote-collect"),
                new OrderSheetKey("associated-collect", 3),
                SERVICE_CENTRE_ID,
                List.of(PreparedLineKey.forPreparedLine(line2), PreparedLineKey.forPreparedLine(line1)),
                List.of("0000388", "0000310")));
        bench.startProcessing();

        assertEquals(AdaptingBenchState.COMPLETED, bench.state());
        AdaptingBenchCompletion completion = bench.consumeCompletion().orElseThrow();
        assertEquals(AdaptingVisitType.COLLECT, completion.visit().visitType());
        assertEquals(List.of(
                PreparedLineKey.forPreparedLine(line2),
                PreparedLineKey.forPreparedLine(line1)),
                completion.collectedLines().stream().map(AdaptedLineRecord::key).toList());
        assertEquals(List.of(SOURCE_ORDER_SHEET, SOURCE_ORDER_SHEET),
                completion.collectedLines().stream().map(AdaptedLineRecord::sourceOrderSheetKey).toList());
        assertEquals(0, store.snapshot().stagedLineCount());
        assertEquals(AdaptingBenchState.IDLE, bench.state());
    }

    @Test
    void shouldEnterBlockedStateWhenCollectVisitCannotFindAllRequestedLines() {
        AdaptedLineStore store = new AdaptedLineStore(new AdaptingStorageLayout(
                AdaptingStorageConfig.defaults(),
                storageMap("0000310", "bench-1", "0000388", "bench-2")));
        AdaptingBench bench = new AdaptingBench("bench-1", store, 0d);
        DspOrderItem line1 = adaptedLine("line-1", "target-1", "0000310");
        DspOrderItem missingLine = adaptedLine("line-2", "target-2", "0000388");
        stage(store, line1);

        bench.acceptVisit(AdaptingVisit.collect(
                new PhysicalToteId("tote-collect"),
                new OrderSheetKey("associated-collect", 3),
                SERVICE_CENTRE_ID,
                List.of(PreparedLineKey.forPreparedLine(line1), PreparedLineKey.forPreparedLine(missingLine)),
                List.of("0000310", "0000388")));
        bench.startProcessing();

        assertEquals(AdaptingBenchState.BLOCKED, bench.state());
        assertTrue(bench.snapshot().blockedReason().contains("Missing staged adapted lines"));
        assertFalse(bench.consumeCompletion().isPresent());

        bench.clearBlocked();
        assertEquals(AdaptingBenchState.IDLE, bench.state());
    }

    @Test
    void shouldStageWholeStoreVisitBeforeCompletionAndBlockWithoutPartialStaging() {
        DspOrderItem first = adaptedLine("first", "target", "0000310");
        DspOrderItem second = adaptedLine("second", "target", "0000310");
        DspOrderItem missing = adaptedLine("missing", "target", "0000310");
        OrderSheetKey sheet = new OrderSheetKey("target", 1);
        AdaptingTargetSheetCatalog targetCatalog = new AdaptingTargetSheetCatalog(Map.of(
                PreparedLineKey.forPreparedLine(first), sheet,
                PreparedLineKey.forPreparedLine(second), sheet));
        AdaptingOrderPreparationCatalog orderCatalog = new AdaptingOrderPreparationCatalog(
                new LoadedDspData(List.of(), List.of(
                        new NotionalToteOrder("adapted-source", "adapted-source", SERVICE_CENTRE_ID, 7,
                                OrderType.ADAPTED, List.of(first, second), 999, 7),
                        new NotionalToteOrder("target", "target", SERVICE_CENTRE_ID, 1,
                                OrderType.ASSOCIATED, List.of(
                                        adaptedLine("first", "adapted-source", "0000310"),
                                        adaptedLine("second", "adapted-source", "0000310")), 999, 1)),
                        List.of(), Set.of()), targetCatalog);
        AdaptedLineStore store = new AdaptedLineStore(new AdaptingStorageLayout(
                new AdaptingStorageConfig(2, 2, 2),
                storageMap("0000310", "bench-1"),
                targetCatalog, orderCatalog));
        AdaptingBench bench = new AdaptingBench("bench-1", store, 0d);

        bench.acceptVisit(AdaptingVisit.store(new PhysicalToteId("bad-store"),
                SOURCE_ORDER_SHEET, SERVICE_CENTRE_ID, List.of(first, missing)));
        bench.startProcessing();
        assertEquals(AdaptingBenchState.BLOCKED, bench.state());
        assertFalse(bench.consumeCompletion().isPresent());
        assertEquals(0, store.snapshot().stagedLineCount());
        assertTrue(store.binSnapshots().isEmpty());

        bench.clearBlocked();
        bench.acceptVisit(AdaptingVisit.store(new PhysicalToteId("good-store"),
                SOURCE_ORDER_SHEET, SERVICE_CENTRE_ID, List.of(first, second)));
        bench.startProcessing();
        assertEquals(AdaptingBenchState.COMPLETED, bench.state());
        assertEquals(2, store.snapshot().stagedLineCount());
        assertEquals(List.of(PreparedLineKey.forPreparedLine(first), PreparedLineKey.forPreparedLine(second)),
                store.binSnapshots().getFirst().stagedRecords().stream()
                        .map(AdaptedLineRecord::key).toList());
        assertEquals(AdaptingVisitType.STORE, bench.consumeCompletion().orElseThrow().visit().visitType());
    }

    @Test
    void shouldRejectVisitWhileBenchIsNotIdle() {
        AdaptedLineStore store = new AdaptedLineStore();
        AdaptingBench bench = new AdaptingBench("bench-1", store, 1d);
        bench.acceptVisit(AdaptingVisit.store(
                new PhysicalToteId("tote-store"),
                SOURCE_ORDER_SHEET,
                SERVICE_CENTRE_ID,
                List.of(adaptedLine("line-1", "target-1", "0000310"))));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> bench.acceptVisit(AdaptingVisit.store(
                        new PhysicalToteId("tote-store-2"),
                        new OrderSheetKey("adapted-source-2", 1),
                        SERVICE_CENTRE_ID,
                        List.of(adaptedLine("line-2", "target-2", "0000388")))));

        assertTrue(exception.getMessage().contains("Bench is not idle"));
    }

    private static void stage(AdaptedLineStore store, DspOrderItem line) {
        store.stage(line, SOURCE_ORDER_SHEET, SERVICE_CENTRE_ID);
    }

    private static DspOrderItem adaptedLine(String lineId, String targetOrderId, String pharmacyId) {
        return new DspOrderItem(
                lineId,
                "product-" + lineId,
                1,
                pharmacyId,
                DspOrderLineType.ADAPTED,
                targetOrderId,
                1,
                0);
    }

    private static AdaptingStorageMap storageMap(String... values) {
        AdaptingStorageMap storageMap = new AdaptingStorageMap();
        storageMap.configureAvailableBenches(List.of(new AdaptingBenchId("bench-1"), new AdaptingBenchId("bench-2")));
        for (int i = 0; i < values.length; i += 2) {
            storageMap.assignPharmacyToBench(values[i], new AdaptingBenchId(values[i + 1]));
        }
        return storageMap;
    }
}
