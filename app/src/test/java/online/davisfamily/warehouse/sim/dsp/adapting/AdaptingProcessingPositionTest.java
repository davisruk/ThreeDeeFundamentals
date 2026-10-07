package online.davisfamily.warehouse.sim.dsp.adapting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;

class AdaptingProcessingPositionTest {

    @Test
    void runsThreeExactVisitsWithIndependentStoreAndCollectTimersAndNoEarlyStaging() {
        AdaptedLineStore store = store();
        DspOrderItem firstLine = line("first");
        DspOrderItem collectedLine = line("collected");
        DspOrderItem thirdLine = line("third");
        OrderSheetKey originalSource = new OrderSheetKey("original-source", 7);
        store.stage(collectedLine, originalSource, "104");
        AdaptingBench bench = new AdaptingBench("bench-1", store, 60d, 10d, 3);
        List<AdaptingProcessingPosition> owners = bench.positions();
        AdaptingVisit first = storeVisit(firstLine);
        AdaptingVisit second = collectVisit(collectedLine);
        AdaptingVisit third = storeVisit(thirdLine);
        List<AdaptingVisit> visits = List.of(first, second, third);

        for (int ordinal = 1; ordinal <= 3; ordinal++) {
            AdaptingProcessingPosition position = bench.firstIdlePosition().orElseThrow();
            assertSame(bench.position(ordinal), position);
            assertEquals(ordinal, position.ordinal());
            assertNull(position.activeVisit());
            assertNull(position.activeToteId());
            position.acceptVisit(visits.get(ordinal - 1));
            assertSame(visits.get(ordinal - 1), position.activeVisit());
            assertSame(visits.get(ordinal - 1).physicalToteId(), position.activeToteId());
            assertEquals(AdaptingBenchState.QUEUED, position.state());
            assertEquals(ordinal, bench.occupiedProcessingPositions());
            assertTrue(position.consumeCompletion().isEmpty());
        }
        assertFalse(bench.canAcceptVisit());
        assertTrue(bench.firstIdlePosition().isEmpty());
        assertThrows(IllegalStateException.class,
                () -> bench.position(1).acceptVisit(storeVisit(line("fourth"))));
        assertEquals(3, bench.occupiedProcessingPositions());
        assertFalse(store.contains(key(line("fourth"))));
        for (int index = 0; index < owners.size(); index++) {
            assertSame(visits.get(index), owners.get(index).activeVisit());
            owners.get(index).startProcessing();
        }

        bench.tick(9d);
        assertEquals(AdaptingBenchState.PROCESSING_STORE, owners.get(0).state());
        assertEquals(AdaptingBenchState.PROCESSING_COLLECT, owners.get(1).state());
        assertEquals(AdaptingBenchState.PROCESSING_STORE, owners.get(2).state());
        assertEquals(51d, owners.get(0).snapshot().remainingProcessingSeconds());
        assertEquals(1d, owners.get(1).snapshot().remainingProcessingSeconds());
        assertEquals(51d, owners.get(2).snapshot().remainingProcessingSeconds());
        assertTrue(store.contains(key(collectedLine)));
        assertFalse(store.contains(key(firstLine)));
        assertFalse(store.contains(key(thirdLine)));
        for (AdaptingProcessingPosition position : owners) {
            assertTrue(position.peekCompletion().isEmpty());
        }

        bench.tick(1d);
        AdaptingBenchCompletion collected = owners.get(1).peekCompletion().orElseThrow();
        assertSame(second, collected.visit());
        assertEquals(List.of(key(collectedLine)), collected.collectedLines().stream().map(AdaptedLineRecord::key).toList());
        assertEquals(originalSource, collected.collectedLines().getFirst().sourceOrderSheetKey());
        assertFalse(store.contains(key(collectedLine)));
        assertEquals(3, bench.occupiedProcessingPositions());
        assertFalse(bench.canAcceptVisit());
        assertSame(collected, owners.get(1).consumeCompletion().orElseThrow());
        assertTrue(owners.get(1).consumeCompletion().isEmpty());
        assertNull(owners.get(1).activeVisit());
        assertNull(owners.get(1).activeToteId());
        assertEquals(2, bench.occupiedProcessingPositions());
        assertSame(owners.get(1), bench.firstIdlePosition().orElseThrow());

        bench.tick(49d);
        assertTrue(owners.get(0).peekCompletion().isEmpty());
        assertTrue(owners.get(2).peekCompletion().isEmpty());
        assertFalse(store.contains(key(firstLine)));
        assertFalse(store.contains(key(thirdLine)));
        bench.tick(1d);
        assertTrue(store.contains(key(firstLine)));
        assertTrue(store.contains(key(thirdLine)));
        assertEquals(2, bench.occupiedProcessingPositions());
        assertSame(first, owners.get(0).consumeCompletion().orElseThrow().visit());
        assertEquals(1, bench.occupiedProcessingPositions());
        assertSame(third, owners.get(2).consumeCompletion().orElseThrow().visit());
        assertEquals(0, bench.occupiedProcessingPositions());
        assertSame(owners, bench.positions());
        assertSame(owners.getFirst(), bench.firstIdlePosition().orElseThrow());

        AdaptingVisit next = storeVisit(line("next"));
        owners.getFirst().acceptVisit(next);
        owners.getFirst().startProcessing();
        bench.tick(59d);
        assertTrue(owners.getFirst().peekCompletion().isEmpty());
        bench.tick(1d);
        assertSame(next, owners.getFirst().consumeCompletion().orElseThrow().visit());
        assertSame(owners.getFirst(), bench.position(1));
        assertSame(owners, bench.positions());
        assertEquals(0, bench.occupiedProcessingPositions());
    }

    @Test
    void retainsBlockedIdentityAndCapacityUntilExplicitClearWithoutPartialCollection() {
        AdaptedLineStore store = store();
        DspOrderItem staged = line("staged");
        DspOrderItem missing = line("missing");
        store.stage(staged, new OrderSheetKey("source", 7), "104");
        AdaptingBench bench = new AdaptingBench("bench-1", store, 60d, 10d, 1);
        AdaptingProcessingPosition position = bench.position(1);
        AdaptingVisit visit = AdaptingVisit.collect(new PhysicalToteId("blocked"),
                new OrderSheetKey("target", 1), "104", List.of(key(staged), key(missing)), List.of("store"));
        position.acceptVisit(visit);
        position.startProcessing();
        bench.tick(9d);
        assertEquals(AdaptingBenchState.PROCESSING_COLLECT, position.state());
        assertEquals(1, bench.occupiedProcessingPositions());
        bench.tick(1d);
        assertEquals(AdaptingBenchState.BLOCKED, position.state());
        assertSame(visit, position.activeVisit());
        assertSame(visit.physicalToteId(), position.activeToteId());
        assertEquals("bench-1", position.snapshot().benchId());
        assertEquals("blocked", position.snapshot().activeToteId());
        assertEquals(0d, position.snapshot().remainingProcessingSeconds());
        assertTrue(position.snapshot().blockedReason().contains("Missing staged adapted lines"));
        assertTrue(store.contains(key(staged)));
        assertEquals(1, bench.occupiedProcessingPositions());
        assertFalse(bench.canAcceptVisit());
        assertTrue(position.peekCompletion().isEmpty());
        assertTrue(position.consumeCompletion().isEmpty());
        assertThrows(IllegalStateException.class, position::startProcessing);
        assertThrows(IllegalStateException.class, () -> position.acceptVisit(storeVisit(missing)));
        bench.tick(100d);
        assertEquals(1, bench.occupiedProcessingPositions());
        assertSame(visit, position.activeVisit());
        position.clearBlocked();
        assertEquals(0, bench.occupiedProcessingPositions());
        assertEquals(AdaptingBenchState.IDLE, position.state());
        assertNull(position.activeVisit());
        assertNull(position.activeToteId());
        assertThrows(IllegalStateException.class, position::clearBlocked);
        assertTrue(position.consumeCompletion().isEmpty());
        assertEquals(0, bench.occupiedProcessingPositions());
        position.acceptVisit(storeVisit(missing));
        assertEquals(1, bench.occupiedProcessingPositions());
        assertSame(position, bench.position(1));
    }

    @Test
    void ticksOnceInOrdinalOrderWithoutBuildingBenchOrStoreSnapshots() {
        List<OrderSheetKey> completedSources = new ArrayList<>();
        AdaptedLineStore store = new AdaptedLineStore() {
            @Override
            public void stageAll(List<DspOrderItem> lines, OrderSheetKey source, String centre) {
                completedSources.add(source);
                super.stageAll(lines, source, centre);
            }

            @Override
            public AdaptedLineStoreSnapshot snapshot() {
                throw new AssertionError("Timer progression must not construct a store snapshot");
            }

            @Override
            public List<AdaptingBinSnapshot> binSnapshots() {
                throw new AssertionError("Timer progression must not construct bin snapshots");
            }
        };
        bindStorage(store);
        AdaptingBench bench = new AdaptingBench("bench-1", store, 1d, 10d, 3) {
            @Override
            public AdaptingBenchSnapshot snapshot() {
                throw new AssertionError("Timer progression must not construct a bench snapshot");
            }
        };
        List<AdaptingProcessingPosition> owners = bench.positions();
        // Acceptance order must not change the position traversal order.
        for (int ordinal : new int[] {3, 1, 2}) {
            bench.position(ordinal).acceptVisit(storeVisit(line("line-" + ordinal)));
            bench.position(ordinal).startProcessing();
        }
        bench.tick(0.5d);
        assertTrue(completedSources.isEmpty());
        assertEquals(3, bench.occupiedProcessingPositions());
        for (AdaptingProcessingPosition position : owners) {
            assertEquals(AdaptingBenchState.PROCESSING_STORE, position.state());
        }
        bench.tick(0.5d);
        assertEquals(List.of(new OrderSheetKey("source-line-1", 7),
                new OrderSheetKey("source-line-2", 7), new OrderSheetKey("source-line-3", 7)), completedSources);
        bench.tick(100d);
        assertEquals(3, completedSources.size());
        for (int ordinal = 1; ordinal <= 3; ordinal++) {
            assertSame(owners.get(ordinal - 1), bench.position(ordinal));
            assertSame(bench.position(ordinal).activeVisit(),
                    bench.position(ordinal).consumeCompletion().orElseThrow().visit());
        }
        assertSame(owners, bench.positions());
        assertEquals(0, bench.occupiedProcessingPositions());
    }

    @Test
    void rejectsInvalidOperationsBeforeChangingAnExactPosition() {
        AdaptingBench bench = new AdaptingBench("bench-1", store(), 60d, 10d, 3);
        AdaptingProcessingPosition position = bench.position(2);
        assertThrows(IllegalArgumentException.class, () -> position.acceptVisit(null));
        assertThrows(IllegalStateException.class, position::startProcessing);
        assertThrows(IllegalStateException.class, position::clearBlocked);
        assertThrows(IllegalArgumentException.class, () -> position.tick(-1d));
        assertThrows(IllegalArgumentException.class, () -> bench.tick(-1d));
        assertTrue(position.peekCompletion().isEmpty());
        assertTrue(position.consumeCompletion().isEmpty());
        assertNull(position.activeToteId());
        assertEquals(0, bench.occupiedProcessingPositions());
        AdaptingVisit visit = storeVisit(line("valid"));
        position.acceptVisit(visit);
        bench.tick(100d);
        assertEquals(AdaptingBenchState.QUEUED, position.state());
        assertSame(visit, position.activeVisit());
        assertEquals(1, bench.occupiedProcessingPositions());
        position.startProcessing();
        assertThrows(IllegalStateException.class, position::startProcessing);
        assertThrows(IllegalStateException.class, position::clearBlocked);
        assertThrows(IllegalArgumentException.class, () -> position.tick(-1d));
        assertSame(visit, position.activeVisit());
        assertEquals(60d, position.snapshot().remainingProcessingSeconds());
        assertEquals(1, bench.occupiedProcessingPositions());
    }

    private static AdaptedLineStore store() {
        AdaptedLineStore store = new AdaptedLineStore();
        bindStorage(store);
        return store;
    }

    private static void bindStorage(AdaptedLineStore store) {
        AdaptingStorageMap map = new AdaptingStorageMap();
        map.configureAvailableBenches(List.of(new AdaptingBenchId("bench-1")));
        store.bindStorageMap(map);
    }

    private static DspOrderItem line(String id) {
        return new DspOrderItem(id, "product-" + id, 1, "store", "patient-" + id,
                "rx-" + id, DspOrderLineType.ADAPTED, "target", 1, 1);
    }

    private static PreparedLineKey key(DspOrderItem line) {
        return PreparedLineKey.forPreparedLine(line);
    }

    private static AdaptingVisit storeVisit(DspOrderItem line) {
        return AdaptingVisit.store(new PhysicalToteId("store-" + line.lineReference()),
                new OrderSheetKey("source-" + line.lineReference(), 7), "104", List.of(line));
    }

    private static AdaptingVisit collectVisit(DspOrderItem line) {
        return AdaptingVisit.collect(new PhysicalToteId("collect-" + line.lineReference()),
                new OrderSheetKey("target", 1), "104", List.of(key(line)), List.of("store"));
    }
}
