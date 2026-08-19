package online.davisfamily.warehouse.sim.dsp.adapting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;

class AdaptingBenchTest {
    private static final OrderSheetKey SOURCE_ORDER_SHEET = new OrderSheetKey("adapted-source", 7);
    private static final String SERVICE_CENTRE_ID = "104";

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
