package online.davisfamily.warehouse.sim.dsp.adapting;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import online.davisfamily.warehouse.sim.machine.queue.MachineWaitQueue;

public class AdaptingArea {
    private final Map<AdaptingBenchId, BenchSlot> benchSlots = new LinkedHashMap<>();
    private final List<AdaptingBenchId> sortedBenchIds;

    public AdaptingArea(List<AdaptingBench> benches, int queueCapacityPerBench) {
        if (benches == null) {
            throw new IllegalArgumentException("benches must not be null");
        }
        if (queueCapacityPerBench < 0) {
            throw new IllegalArgumentException("queueCapacityPerBench must be >= 0");
        }

        for (AdaptingBench bench : List.copyOf(benches)) {
            if (bench == null) {
                throw new IllegalArgumentException("benches must not contain null");
            }
            AdaptingBenchId benchId = new AdaptingBenchId(bench.id());
            if (benchSlots.containsKey(benchId)) {
                throw new IllegalArgumentException("Duplicate bench id: " + benchId);
            }
            benchSlots.put(benchId, new BenchSlot(
                    benchId,
                    bench,
                    new MachineWaitQueue(bench.id() + "-queue", queueCapacityPerBench)));
        }

        if (benchSlots.isEmpty()) {
            throw new IllegalArgumentException("benches must not be empty");
        }

        sortedBenchIds = benchSlots.keySet().stream()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    public AdaptingBenchSelection selectBenchFor(AdaptingVisit visit) {
        if (visit == null) {
            throw new IllegalArgumentException("visit must not be null");
        }

        for (AdaptingBenchId benchId : sortedBenchIds) {
            BenchSlot slot = benchSlots.get(benchId);
            if (slot.canAcceptVisit()) {
                return AdaptingBenchSelection.accepted(benchId);
            }
        }

        return AdaptingBenchSelection.blocked("No adapting bench has queue or processing capacity");
    }

    public AdaptingAreaAdmissionSnapshot admissionSnapshotFor(AdaptingVisit visit) {
        if (visit == null) {
            throw new IllegalArgumentException("visit must not be null");
        }

        List<AdaptingBenchAdmissionSnapshot> admissions = new ArrayList<>();
        for (AdaptingBenchId benchId : sortedBenchIds) {
            BenchSlot slot = benchSlots.get(benchId);
            boolean open = slot.canAcceptVisit();
            admissions.add(new AdaptingBenchAdmissionSnapshot(
                    benchId,
                    slot.bench.snapshot(),
                    slot.queue.snapshot(),
                    open,
                    open ? "" : "Bench queue and processing slot are full"));
        }
        return new AdaptingAreaAdmissionSnapshot(admissions, selectBenchFor(visit));
    }

    public AdaptingBenchSelection submitVisit(AdaptingVisit visit) {
        if (visit == null) {
            throw new IllegalArgumentException("visit must not be null");
        }

        AdaptingBenchSelection selection = selectBenchFor(visit);
        if (!selection.accepted()) {
            return selection;
        }

        BenchSlot slot = slot(selection.benchId());
        if (slot.bench.canAcceptVisit() && slot.pendingVisits.isEmpty()) {
            slot.bench.acceptVisit(visit);
        } else {
            slot.queue.enqueue(visit.toteId());
            slot.pendingVisits.addLast(visit);
        }
        return selection;
    }

    public boolean dispatchNextQueuedVisit(AdaptingBenchId benchId) {
        BenchSlot slot = slot(benchId);
        if (!slot.bench.canAcceptVisit() || slot.pendingVisits.isEmpty()) {
            return false;
        }
        slot.queue.dequeue();
        slot.bench.acceptVisit(slot.pendingVisits.removeFirst());
        return true;
    }

    public Optional<AdaptingVisit> peekQueuedVisit(AdaptingBenchId benchId) {
        return Optional.ofNullable(slot(benchId).pendingVisits.peekFirst());
    }

    public AdaptingBench bench(AdaptingBenchId benchId) {
        return slot(benchId).bench;
    }

    private BenchSlot slot(AdaptingBenchId benchId) {
        if (benchId == null) {
            throw new IllegalArgumentException("benchId must not be null");
        }
        BenchSlot slot = benchSlots.get(benchId);
        if (slot == null) {
            throw new IllegalArgumentException("Unknown bench id: " + benchId);
        }
        return slot;
    }

    private static final class BenchSlot {
        final AdaptingBenchId benchId;
        final AdaptingBench bench;
        final MachineWaitQueue queue;
        final Deque<AdaptingVisit> pendingVisits = new ArrayDeque<>();

        BenchSlot(AdaptingBenchId benchId, AdaptingBench bench, MachineWaitQueue queue) {
            this.benchId = benchId;
            this.bench = bench;
            this.queue = queue;
        }

        boolean canAcceptVisit() {
            return bench.canAcceptVisit() || queue.canAccept();
        }
    }
}
