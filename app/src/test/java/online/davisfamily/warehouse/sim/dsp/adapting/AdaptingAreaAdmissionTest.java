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

class AdaptingAreaAdmissionTest {

    @Test
    void shouldInspectSortedLocalCapacityWithoutSelectingOrMutatingVisits() {
        AdaptedLineStore store = new AdaptedLineStore();
        AdaptingBench busy = new AdaptingBench("bench-2", store, 5d);
        AdaptingBench idle = new AdaptingBench("bench-1", store, 5d);
        AdaptingArea area = new AdaptingArea(List.of(busy, idle), 1, storageMap());
        AdaptingBenchId busyId = new AdaptingBenchId("bench-2");
        area.submitVisitTo(busyId, storeVisit("active", "active-line", "active-order"));
        busy.startProcessing();
        area.submitVisitTo(busyId, storeVisit("queued", "queued-line", "queued-order"));
        var selectionProfile = storeVisit("next", "next-line", "next-order").profile();
        var candidateBefore = area.admissionSnapshotFor(selectionProfile);

        var first = area.benchAdmissionSnapshots();
        assertEquals(List.of(new AdaptingBenchId("bench-1"), busyId),
                first.stream().map(AdaptingBenchAdmissionSnapshot::benchId).toList());
        assertEquals(AdaptingBenchState.IDLE, first.getFirst().benchSnapshot().state());
        assertTrue(first.getFirst().admissionOpen());
        assertEquals("", first.getFirst().blockedReason());
        assertEquals(AdaptingBenchState.PROCESSING_STORE, first.get(1).benchSnapshot().state());
        assertEquals("active", first.get(1).benchSnapshot().activeToteId());
        assertEquals(AdaptingVisitType.STORE, first.get(1).benchSnapshot().activeVisitType());
        assertEquals(5d, first.get(1).benchSnapshot().remainingProcessingSeconds());
        assertEquals(1, first.get(1).queueSnapshot().capacity());
        assertEquals(List.of("queued"), first.get(1).queueSnapshot().toteIds());
        assertFalse(first.get(1).admissionOpen());
        assertEquals("Bench queue and processing slot are full", first.get(1).blockedReason());
        assertThrows(UnsupportedOperationException.class, first::clear);
        assertThrows(UnsupportedOperationException.class,
                () -> first.get(1).queueSnapshot().toteIds().clear());
        assertEquals(first, area.benchAdmissionSnapshots());
        assertEquals(candidateBefore, area.admissionSnapshotFor(selectionProfile));

        busy.tick(5d);
        busy.consumeCompletion().orElseThrow();
        assertTrue(area.dispatchNextQueuedVisit(busyId));
        var second = area.benchAdmissionSnapshots();
        assertEquals("queued", second.get(1).benchSnapshot().activeToteId());
        assertTrue(second.get(1).queueSnapshot().toteIds().isEmpty());
        assertTrue(second.get(1).admissionOpen());
        assertEquals("active", first.get(1).benchSnapshot().activeToteId());
        assertEquals(List.of("queued"), first.get(1).queueSnapshot().toteIds());
    }

    @Test
    void shouldSubmitDirectAndQueuedVisitsToTheExactSelectedBench() {
        AdaptedLineStore sharedStore = new AdaptedLineStore();
        AdaptingArea area = new AdaptingArea(List.of(
                new AdaptingBench("bench-1", sharedStore, 5d),
                new AdaptingBench("bench-2", sharedStore, 5d)), 1, storageMap());
        AdaptingBenchId selectedBench = new AdaptingBenchId("bench-2");
        AdaptingVisit first = storeVisit("exact-first", "line-first", "target-first");
        AdaptingVisit second = storeVisit("exact-second", "line-second", "target-second");

        AdaptingBenchSelection direct = area.submitVisitTo(selectedBench, first);
        assertTrue(direct.accepted());
        assertEquals(selectedBench, direct.benchId());
        assertEquals("exact-first", area.bench(selectedBench).snapshot().activeToteId());
        assertEquals(AdaptingBenchState.IDLE,
                area.bench(new AdaptingBenchId("bench-1")).state());

        area.bench(selectedBench).startProcessing();
        AdaptingBenchSelection queued = area.submitVisitTo(selectedBench, second);
        assertTrue(queued.accepted());
        assertEquals(selectedBench, queued.benchId());
        assertEquals(List.of("exact-second"),
                area.admissionSnapshotFor(first.profile()).benchAdmissions().stream()
                        .filter(admission -> admission.benchId().equals(selectedBench))
                        .findFirst().orElseThrow().queueSnapshot().toteIds());
        assertEquals("exact-first", area.bench(selectedBench).snapshot().activeToteId());
    }

    @Test
    void shouldBlockExactBenchWithoutRedirectingToAnotherAvailableBench() {
        AdaptedLineStore sharedStore = new AdaptedLineStore();
        AdaptingBench bench1 = new AdaptingBench("bench-1", sharedStore, 5d);
        AdaptingBench bench2 = new AdaptingBench("bench-2", sharedStore, 5d);
        AdaptingArea area = new AdaptingArea(List.of(bench1, bench2), 1, storageMap());
        AdaptingBenchId selectedBench = new AdaptingBenchId("bench-1");

        area.submitVisitTo(selectedBench, storeVisit("full-active", "line-active", "target-active"));
        bench1.startProcessing();
        area.submitVisitTo(selectedBench, storeVisit("full-queued", "line-queued", "target-queued"));

        var before = area.admissionSnapshotFor(
                storeVisit("exact-blocked", "line-blocked", "target-blocked").profile());
        AdaptingVisit blockedVisit = storeVisit("exact-blocked", "line-blocked", "target-blocked");

        assertFalse(area.canAcceptVisitAt(selectedBench));
        AdaptingBenchSelection selection = area.submitVisitTo(selectedBench, blockedVisit);

        assertFalse(selection.accepted());
        assertTrue(selection.blockedReason().contains("full"));
        assertEquals(before, area.admissionSnapshotFor(blockedVisit.profile()));
        assertEquals(AdaptingBenchState.IDLE, bench2.state());
        assertEquals("full-active", bench1.snapshot().activeToteId());
    }

    @Test
    void shouldRejectNullAndUnknownExactBenchBeforeMutation() {
        AdaptedLineStore sharedStore = new AdaptedLineStore();
        AdaptingArea area = new AdaptingArea(List.of(
                new AdaptingBench("bench-1", sharedStore, 1d)), 1, storageMap());
        AdaptingVisit visit = storeVisit("exact-invalid", "line-invalid", "target-invalid");
        var before = area.admissionSnapshotFor(visit.profile());

        assertThrows(IllegalArgumentException.class,
                () -> area.canAcceptVisitAt(null));
        assertThrows(IllegalArgumentException.class,
                () -> area.canAcceptVisitAt(new AdaptingBenchId("unknown")));
        assertThrows(IllegalArgumentException.class,
                () -> area.submitVisitTo(new AdaptingBenchId("unknown"), visit));
        assertThrows(IllegalArgumentException.class,
                () -> area.submitVisitTo(new AdaptingBenchId("bench-1"), null));

        assertEquals(before, area.admissionSnapshotFor(visit.profile()));
        assertEquals(AdaptingBenchState.IDLE,
                area.bench(new AdaptingBenchId("bench-1")).state());
    }

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

    @Test
    void shouldCountThreePositionsAndThreeWaitingAndNeverBypassFullFifo() {
        AdaptingBenchId id = new AdaptingBenchId("bench-1");
        AdaptingBench bench = new AdaptingBench("bench-1", new AdaptedLineStore(), 0d, 0d, 3);
        AdaptingArea area = new AdaptingArea(List.of(bench), 3);
        List<AdaptingVisit> visits = java.util.stream.IntStream.rangeClosed(1, 7)
                .mapToObj(i -> storeVisit("tote-" + i, "line-" + i, "target-" + i)).toList();
        for (int i = 0; i < 6; i++) {
            assertTrue(area.submitVisitTo(id, visits.get(i)).accepted());
        }
        area.startQueuedPositions(id);
        var before = area.benchAdmissionSnapshots();
        assertEquals(3, before.getFirst().processingCapacity());
        assertEquals(3, before.getFirst().occupiedProcessingPositions());
        assertEquals(new online.davisfamily.warehouse.sim.dsp.scheduler.StationSnapshot(
                online.davisfamily.warehouse.sim.dsp.model.StationType.ADAPTING, 3, 3),
                area.stationSnapshot());
        assertFalse(area.submitVisitTo(id, visits.get(6)).accepted());
        assertEquals(before, area.benchAdmissionSnapshots());
        assertThrows(IllegalStateException.class, () -> area.dispatchNextQueuedVisit(id));

        bench.position(2).consumeCompletion().orElseThrow();
        // An idle owner is not permission to overtake the older full queue.
        assertFalse(area.canAcceptVisitAt(id));
        assertFalse(area.submitVisitTo(id, visits.get(6)).accepted());
        assertEquals(AdaptingBenchState.IDLE, bench.position(2).state());
        assertTrue(area.benchAdmissionSnapshots().getFirst().canStartQueuedVisit());
        area.dispatchQueuedVisits(id);
        assertEquals(visits.get(3), bench.position(2).activeVisit());
        assertEquals(AdaptingBenchState.QUEUED, bench.position(2).state());
        assertTrue(area.submitVisitTo(id, visits.get(6)).accepted());
        area.startQueuedPositions(id);
        bench.position(1).consumeCompletion().orElseThrow();
        bench.position(3).consumeCompletion().orElseThrow();
        area.dispatchQueuedVisits(id);
        assertEquals(visits.get(4), bench.position(1).activeVisit());
        assertEquals(visits.get(5), bench.position(3).activeVisit());
        assertEquals(visits.get(6), area.peekQueuedVisit(id).orElseThrow());
    }

    @Test
    void shouldAppendBehindWaitingVisitEvenWhenIdleCapacityExists() {
        AdaptingBenchId id = new AdaptingBenchId("bench-1");
        AdaptingBench bench = new AdaptingBench("bench-1", new AdaptedLineStore(), 0d, 0d, 3);
        AdaptingArea area = new AdaptingArea(List.of(bench), 3);
        for (int i = 1; i <= 4; i++) {
            area.submitVisitTo(id, storeVisit("tote-" + i, "line-" + i, "target-" + i));
        }
        area.startQueuedPositions(id);
        bench.position(1).consumeCompletion().orElseThrow();
        AdaptingVisit newest = storeVisit("newest", "newest-line", "newest-target");
        assertTrue(area.submitVisitTo(id, newest).accepted());
        assertEquals(AdaptingBenchState.IDLE, bench.position(1).state());
        assertEquals(List.of("tote-4", "newest"), area.benchAdmissionSnapshots().getFirst()
                .queueSnapshot().toteIds());
        area.dispatchQueuedVisits(id);
        assertEquals("tote-4", bench.position(1).activeToteId().value());
        assertEquals(newest, area.peekQueuedVisit(id).orElseThrow());
    }

    @Test
    void shouldPreserveLegacyAdmissionConstructorAndValidatePositionCounts() {
        AdaptingArea area = new AdaptingArea(List.of(
                new AdaptingBench("bench-1", new AdaptedLineStore(), 1d)), 1);
        var view = area.benchAdmissionSnapshots().getFirst();
        assertEquals(view, new AdaptingBenchAdmissionSnapshot(view.benchId(), view.benchSnapshot(),
                view.queueSnapshot(), view.admissionOpen(), view.blockedReason()));
        for (int[] counts : List.of(new int[] {0, 0}, new int[] {3, -1}, new int[] {3, 4})) {
            assertThrows(IllegalArgumentException.class, () -> new AdaptingBenchAdmissionSnapshot(
                    view.benchId(), view.benchSnapshot(), view.queueSnapshot(), true, "",
                    counts[0], counts[1]));
        }
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
