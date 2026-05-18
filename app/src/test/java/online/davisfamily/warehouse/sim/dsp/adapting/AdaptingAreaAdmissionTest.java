package online.davisfamily.warehouse.sim.dsp.adapting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;

class AdaptingAreaAdmissionTest {

    @Test
    void shouldSelectLowestBenchIdWhenMultipleBenchesCanAccept() {
        AdaptingArea area = new AdaptingArea(List.of(
                new AdaptingBench("bench-2", new AdaptedLineStore(), 1d),
                new AdaptingBench("bench-1", new AdaptedLineStore(), 1d)), 1);

        AdaptingBenchSelection selection = area.selectBenchFor(storeVisit("tote-1", "line-1", "target-1"));

        assertTrue(selection.accepted());
        assertEquals(new AdaptingBenchId("bench-1"), selection.benchId());
    }

    @Test
    void shouldQueueVisitAtSelectedBenchUntilBenchCanProcessIt() {
        AdaptingArea area = new AdaptingArea(List.of(
                new AdaptingBench("bench-1", new AdaptedLineStore(), 5d)), 1);
        AdaptingBenchId benchId = new AdaptingBenchId("bench-1");

        area.submitVisit(storeVisit("tote-1", "line-1", "target-1"));
        area.bench(benchId).startProcessing();

        AdaptingBenchSelection queuedSelection = area.submitVisit(storeVisit("tote-2", "line-2", "target-2"));
        assertTrue(queuedSelection.accepted());
        assertEquals(benchId, queuedSelection.benchId());
        assertTrue(area.peekQueuedVisit(benchId).isPresent());
        assertEquals("tote-2", area.peekQueuedVisit(benchId).orElseThrow().toteId());

        area.bench(benchId).tick(5d);
        area.bench(benchId).consumeCompletion().orElseThrow();

        assertTrue(area.dispatchNextQueuedVisit(benchId));
        assertEquals(AdaptingBenchState.QUEUED, area.bench(benchId).state());
        assertEquals("tote-2", area.bench(benchId).snapshot().activeToteId());
        assertFalse(area.peekQueuedVisit(benchId).isPresent());
    }

    @Test
    void shouldSelectLaterBenchWhenEarlierBenchHasNoCapacity() {
        AdaptingBench bench1 = new AdaptingBench("bench-1", new AdaptedLineStore(), 5d);
        AdaptingBench bench2 = new AdaptingBench("bench-2", new AdaptedLineStore(), 5d);
        AdaptingArea area = new AdaptingArea(List.of(bench1, bench2), 1);

        area.submitVisit(storeVisit("tote-1", "line-1", "target-1"));
        bench1.startProcessing();
        area.submitVisit(storeVisit("tote-2", "line-2", "target-2"));

        AdaptingBenchSelection selection = area.selectBenchFor(storeVisit("tote-3", "line-3", "target-3"));

        assertTrue(selection.accepted());
        assertEquals(new AdaptingBenchId("bench-2"), selection.benchId());
    }

    @Test
    void shouldBlockWhenAllBenchesAreFull() {
        AdaptingBench bench1 = new AdaptingBench("bench-1", new AdaptedLineStore(), 5d);
        AdaptingBench bench2 = new AdaptingBench("bench-2", new AdaptedLineStore(), 5d);
        AdaptingArea area = new AdaptingArea(List.of(bench1, bench2), 0);

        area.submitVisit(storeVisit("tote-1", "line-1", "target-1"));
        area.submitVisit(storeVisit("tote-2", "line-2", "target-2"));

        AdaptingAreaAdmissionSnapshot snapshot = area.admissionSnapshotFor(storeVisit("tote-3", "line-3", "target-3"));

        assertFalse(snapshot.admissionOpen());
        assertFalse(snapshot.selection().accepted());
        assertTrue(snapshot.selection().blockedReason().contains("No adapting bench"));
    }

    @Test
    void shouldExposeBenchAdmissionSnapshots() {
        AdaptingBench bench1 = new AdaptingBench("bench-1", new AdaptedLineStore(), 5d);
        AdaptingBench bench2 = new AdaptingBench("bench-2", new AdaptedLineStore(), 5d);
        AdaptingArea area = new AdaptingArea(List.of(bench1, bench2), 1);

        area.submitVisit(storeVisit("tote-1", "line-1", "target-1"));
        bench1.startProcessing();
        area.submitVisit(storeVisit("tote-2", "line-2", "target-2"));

        AdaptingAreaAdmissionSnapshot snapshot = area.admissionSnapshotFor(storeVisit("tote-3", "line-3", "target-3"));
        AdaptingBenchAdmissionSnapshot bench1Snapshot = snapshot.benchAdmissions().stream()
                .filter(admission -> admission.benchId().equals(new AdaptingBenchId("bench-1")))
                .findFirst()
                .orElseThrow();

        assertEquals(AdaptingBenchState.PROCESSING_STORE, bench1Snapshot.benchSnapshot().state());
        assertEquals(List.of("tote-2"), bench1Snapshot.queueSnapshot().toteIds());
        assertFalse(bench1Snapshot.admissionOpen());
        assertTrue(bench1Snapshot.blockedReason().contains("full"));
    }

    private static AdaptingVisit storeVisit(String toteId, String lineId, String targetOrderId) {
        return AdaptingVisit.store(toteId, List.of(new DspOrderItem(
                lineId,
                "product-" + lineId,
                1,
                "0000310",
                DspOrderLineType.ADAPTED,
                targetOrderId,
                1,
                0)));
    }
}
