package online.davisfamily.warehouse.sim.dsp.adapting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;

class AdaptingAreaAdmissionTest {

    @Test
    void shouldSelectLowestBenchIdWhenMultipleBenchesCanAccept() {
        AdaptedLineStore sharedStore = new AdaptedLineStore();
        AdaptingStorageMap storageMap = storageMap();
        AdaptingArea area = new AdaptingArea(List.of(
                new AdaptingBench("bench-2", sharedStore, 1d),
                new AdaptingBench("bench-1", sharedStore, 1d)), 1, storageMap);

        AdaptingBenchSelection selection = area.selectBenchFor(
                storeVisit("tote-1", "line-1", "target-1").profile());

        assertTrue(selection.accepted());
        assertEquals(new AdaptingBenchId("bench-1"), selection.benchId());
    }

    @Test
    void shouldQueueVisitAtSelectedBenchUntilBenchCanProcessIt() {
        AdaptedLineStore sharedStore = new AdaptedLineStore();
        AdaptingArea area = new AdaptingArea(List.of(
                new AdaptingBench("bench-1", sharedStore, 5d)), 1, storageMap());
        AdaptingBenchId benchId = new AdaptingBenchId("bench-1");

        area.submitVisit(storeVisit("tote-1", "line-1", "target-1"));
        area.bench(benchId).startProcessing();

        AdaptingBenchSelection queuedSelection = area.submitVisit(storeVisit("tote-2", "line-2", "target-2"));
        assertTrue(queuedSelection.accepted());
        assertEquals(benchId, queuedSelection.benchId());
        assertTrue(area.peekQueuedVisit(benchId).isPresent());
        assertEquals(new PhysicalToteId("tote-2"),
                area.peekQueuedVisit(benchId).orElseThrow().physicalToteId());

        area.bench(benchId).tick(5d);
        area.bench(benchId).consumeCompletion().orElseThrow();

        assertTrue(area.dispatchNextQueuedVisit(benchId));
        assertEquals(AdaptingBenchState.QUEUED, area.bench(benchId).state());
        assertEquals("tote-2", area.bench(benchId).snapshot().activeToteId());
        assertFalse(area.peekQueuedVisit(benchId).isPresent());
    }

    @Test
    void shouldSelectLaterBenchWhenEarlierBenchHasNoCapacity() {
        AdaptedLineStore sharedStore = new AdaptedLineStore();
        AdaptingBench bench1 = new AdaptingBench("bench-1", sharedStore, 5d);
        AdaptingBench bench2 = new AdaptingBench("bench-2", sharedStore, 5d);
        AdaptingStorageMap storageMap = storageMap();
        storageMap.assignPharmacyToBench("0000310", new AdaptingBenchId("bench-1"));
        AdaptingArea area = new AdaptingArea(List.of(bench1, bench2), 1, storageMap);

        area.submitVisit(storeVisit("tote-1", "line-1", "target-1"));
        bench1.startProcessing();
        area.submitVisit(storeVisit("tote-2", "line-2", "target-2"));

        AdaptingBenchSelection selection = area.selectBenchFor(
                storeVisit("tote-3", "line-3", "target-3").profile());

        assertTrue(selection.accepted());
        assertEquals(new AdaptingBenchId("bench-2"), selection.benchId());
    }

    @Test
    void shouldBlockWhenAllBenchesAreFull() {
        AdaptedLineStore sharedStore = new AdaptedLineStore();
        AdaptingBench bench1 = new AdaptingBench("bench-1", sharedStore, 5d);
        AdaptingBench bench2 = new AdaptingBench("bench-2", sharedStore, 5d);
        AdaptingArea area = new AdaptingArea(List.of(bench1, bench2), 0, storageMap());

        area.submitVisit(storeVisit("tote-1", "line-1", "target-1"));
        area.submitVisit(storeVisit("tote-2", "line-2", "target-2"));

        AdaptingAreaAdmissionSnapshot snapshot = area.admissionSnapshotFor(
                storeVisit("tote-3", "line-3", "target-3").profile());

        assertFalse(snapshot.admissionOpen());
        assertFalse(snapshot.selection().accepted());
        assertTrue(snapshot.selection().blockedReason().contains("No adapting bench"));
    }

    @Test
    void shouldExposeBenchAdmissionSnapshots() {
        AdaptedLineStore sharedStore = new AdaptedLineStore();
        AdaptingBench bench1 = new AdaptingBench("bench-1", sharedStore, 5d);
        AdaptingBench bench2 = new AdaptingBench("bench-2", sharedStore, 5d);
        AdaptingArea area = new AdaptingArea(List.of(bench1, bench2), 1, storageMap());

        area.submitVisit(storeVisit("tote-1", "line-1", "target-1"));
        bench1.startProcessing();
        area.submitVisit(storeVisit("tote-2", "line-2", "target-2"));

        AdaptingAreaAdmissionSnapshot snapshot = area.admissionSnapshotFor(
                storeVisit("tote-3", "line-3", "target-3").profile());
        AdaptingBenchAdmissionSnapshot bench1Snapshot = snapshot.benchAdmissions().stream()
                .filter(admission -> admission.benchId().equals(new AdaptingBenchId("bench-1")))
                .findFirst()
                .orElseThrow();

        assertEquals(AdaptingBenchState.PROCESSING_STORE, bench1Snapshot.benchSnapshot().state());
        assertEquals(List.of("tote-2"), bench1Snapshot.queueSnapshot().toteIds());
        assertFalse(bench1Snapshot.admissionOpen());
        assertTrue(bench1Snapshot.blockedReason().contains("full"));
    }

    @Test
    void shouldPreferMappedBenchForCollectVisit() {
        AdaptedLineStore sharedStore = new AdaptedLineStore();
        AdaptingStorageMap storageMap = storageMap();
        storageMap.assignPharmacyToBench("0000388", new AdaptingBenchId("bench-2"));
        AdaptingArea area = new AdaptingArea(List.of(
                new AdaptingBench("bench-1", sharedStore, 1d),
                new AdaptingBench("bench-2", sharedStore, 1d)), 1, storageMap);

        AdaptingBenchSelection selection = area.selectBenchFor(AdaptingVisitProfile.collect(
                new OrderSheetKey("associated-1", 1),
                "104",
                List.of(new PreparedLineKey("dispatch-1", "line-1")),
                List.of("0000388")));

        assertTrue(selection.accepted());
        assertEquals(new AdaptingBenchId("bench-2"), selection.benchId());
    }

    @Test
    void shouldPreferDominantBenchForMixedStoreVisit() {
        AdaptedLineStore sharedStore = new AdaptedLineStore();
        AdaptingStorageMap storageMap = storageMap();
        storageMap.assignPharmacyToBench("0000310", new AdaptingBenchId("bench-1"));
        storageMap.assignPharmacyToBench("0000388", new AdaptingBenchId("bench-2"));
        AdaptingArea area = new AdaptingArea(List.of(
                new AdaptingBench("bench-1", sharedStore, 1d),
                new AdaptingBench("bench-2", sharedStore, 1d)), 1, storageMap);

        AdaptingBenchSelection selection = area.selectBenchFor(AdaptingVisitProfile.store(
                new OrderSheetKey("adapted-source", 1),
                "104",
                List.of(
                        adaptedLine("line-1", "target-1", "0000388"),
                        adaptedLine("line-2", "target-2", "0000388"),
                        adaptedLine("line-3", "target-3", "0000310"))));

        assertTrue(selection.accepted());
        assertEquals(new AdaptingBenchId("bench-2"), selection.benchId());
    }

    private static AdaptingVisit storeVisit(String toteId, String lineId, String targetOrderId) {
        return AdaptingVisit.store(
                new PhysicalToteId(toteId),
                new OrderSheetKey("source-" + toteId, 1),
                "104",
                List.of(adaptedLine(lineId, targetOrderId, "0000310")));
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

    private static AdaptingStorageMap storageMap() {
        AdaptingStorageMap storageMap = new AdaptingStorageMap();
        storageMap.configureAvailableBenches(List.of(new AdaptingBenchId("bench-1"), new AdaptingBenchId("bench-2")));
        storageMap.assignPharmacyToBench("0000310", new AdaptingBenchId("bench-1"));
        return storageMap;
    }
}
