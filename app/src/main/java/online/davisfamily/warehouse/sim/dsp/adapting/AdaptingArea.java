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
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationSnapshot;

public class AdaptingArea {
    private final Map<AdaptingBenchId, BenchSlot> benchSlots = new LinkedHashMap<>();
    private final List<AdaptingBenchId> sortedBenchIds;
    private final AdaptingStorageMap storageMap;

    public AdaptingArea(List<AdaptingBench> benches, int queueCapacityPerBench) {
        this(benches, queueCapacityPerBench, new AdaptingStorageMap());
    }

    public AdaptingArea(List<AdaptingBench> benches, int queueCapacityPerBench, AdaptingStorageMap storageMap) {
        if (benches == null) {
            throw new IllegalArgumentException("benches must not be null");
        }
        if (queueCapacityPerBench < 0) {
            throw new IllegalArgumentException("queueCapacityPerBench must be >= 0");
        }
        if (storageMap == null) {
            throw new IllegalArgumentException("storageMap must not be null");
        }
        this.storageMap = storageMap;

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
        this.storageMap.configureAvailableBenches(sortedBenchIds);
        for (BenchSlot slot : benchSlots.values()) {
            slot.bench.bindStorageMap(this.storageMap);
        }
    }

    public AdaptingBenchSelection selectBenchFor(AdaptingVisitProfile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("profile must not be null");
        }

        for (AdaptingBenchId benchId : preferredBenchOrderFor(profile)) {
            BenchSlot slot = benchSlots.get(benchId);
            if (slot.canAcceptVisit()) {
                return AdaptingBenchSelection.accepted(benchId);
            }
        }

        return AdaptingBenchSelection.blocked("No adapting bench has queue or processing capacity");
    }

    public AdaptingAreaAdmissionSnapshot admissionSnapshotFor(AdaptingVisitProfile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("profile must not be null");
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
        return new AdaptingAreaAdmissionSnapshot(admissions, selectBenchFor(profile));
    }

    public AdaptingBenchSelection submitVisit(AdaptingVisit visit) {
        if (visit == null) {
            throw new IllegalArgumentException("visit must not be null");
        }

        AdaptingBenchSelection selection = selectBenchFor(visit.profile());
        if (!selection.accepted()) {
            return selection;
        }

        BenchSlot slot = slot(selection.benchId());
        if (slot.bench.canAcceptVisit() && slot.pendingVisits.isEmpty()) {
            slot.bench.acceptVisit(visit);
        } else {
            slot.queue.enqueue(visit.physicalToteId().value());
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

    public StationSnapshot stationSnapshot() {
        int inProgress = 0;
        int queued = 0;
        for (BenchSlot slot : benchSlots.values()) {
            if (slot.bench.state() != AdaptingBenchState.IDLE) {
                inProgress++;
            }
            queued += slot.pendingVisits.size();
        }
        return new StationSnapshot(StationType.ADAPTING, inProgress, queued);
    }

    private List<AdaptingBenchId> preferredBenchOrderFor(AdaptingVisitProfile profile) {
        Map<AdaptingBenchId, Integer> scores = new LinkedHashMap<>();
        for (String pharmacyId : profile.pharmacyIds()) {
            AdaptingBenchId preferredBench = storageMap.preferredBenchFor(pharmacyId);
            scores.merge(preferredBench, 1, Integer::sum);
        }

        return sortedBenchIds.stream()
                .sorted(Comparator
                        .comparingInt((AdaptingBenchId benchId) -> scores.getOrDefault(benchId, 0))
                        .reversed()
                        .thenComparing(Comparator.naturalOrder()))
                .toList();
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
