package online.davisfamily.warehouse.sim.dsp.runtime.operational;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.av02.Av02AllocatedTote;
import online.davisfamily.warehouse.sim.dsp.bagging.BagKey;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedBag;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifestCatalog;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleState;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineDefinition;
import online.davisfamily.warehouse.sim.dsp.runtime.operational.DspOperationalWorkSnapshot.CentreTotals;
import online.davisfamily.warehouse.sim.dsp.runtime.operational.DspOperationalWorkSnapshot.LineTotals;
import online.davisfamily.warehouse.sim.dsp.runtime.operational.DspOperationalWorkSnapshot.RemainingCounts;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;

/**
 * Simulation-thread-owned remaining obligations and aggregate accounting, not physical ownership.
 * Successful callers validate before their handoff and record after it. Ended entries are removed;
 * there is no historical identity guard or checkpoint/reseeding mechanism.
 */
public final class DspOperationalWorkLedger {
    private final Map<PhysicalToteId, InboundWork> inbound = new LinkedHashMap<>();
    private final Map<OrderSheetKey, DspSchedulerOrderState> emptyOrders = new LinkedHashMap<>();
    private final Map<PhysicalToteId, Av02AllocatedTote> av02ById = new LinkedHashMap<>();
    private final Map<OrderSheetKey, Av02AllocatedTote> av02BySheet = new LinkedHashMap<>();
    private final Map<BagKey, BagWork> bags = new LinkedHashMap<>();
    private final Map<OrderSheetKey, Integer> sourceBagCounts = new LinkedHashMap<>();
    private final Map<OrderSheetKey, Integer> sourceConsumerCounts = new LinkedHashMap<>();
    private final Map<String, Integer> targetConsumerCounts = new LinkedHashMap<>();
    private final Map<String, RemainingCounts> remainingCounts = new LinkedHashMap<>();
    private final Map<String, CentreTotals> centreTotals = new LinkedHashMap<>();
    private final Map<P2pLineId, LineTotals> lineTotals = new LinkedHashMap<>();

    private List<InboundToteManifest> inboundView;
    private List<DspSchedulerOrderState> emptyView;
    private List<Av02AllocatedTote> av02View;
    private Map<String, List<PhysicalToteId>> p2pIdsView;
    private DspOperationalWorkSnapshot cachedSnapshot;

    public DspOperationalWorkLedger(
            List<DspSchedulerOrderState> orders,
            InboundToteManifestCatalog manifests,
            List<PlannedBag> plannedBags,
            List<P2pLineDefinition> lines) {
        require(orders, "orders");
        require(manifests, "manifests");
        require(plannedBags, "plannedBags");
        require(lines, "lines");
        for (P2pLineDefinition line : lines) {
            require(line, "line");
            if (lineTotals.putIfAbsent(line.lineId(), new LineTotals(0, 0)) != null) {
                throw new IllegalArgumentException("Duplicate configured line: " + line.lineId());
            }
        }
        Map<OrderSheetKey, DspSchedulerOrderState> ordersBySheet = new LinkedHashMap<>();
        for (DspSchedulerOrderState state : orders) {
            require(state, "order state");
            OrderSheetKey sheet = state.order().orderSheetKey();
            if (ordersBySheet.putIfAbsent(sheet, state) != null) {
                throw new IllegalArgumentException("Duplicate executable sheet: " + sheet);
            }
            String centre = state.order().serviceCentreId();
            initializeCentre(centre);
            if (state.order().orderType() == OrderType.EMPTY) {
                emptyOrders.put(sheet, state);
                remainingCounts.put(centre, changedCounts(centre, 0, 1, 0, 0));
                increment(sourceConsumerCounts, sheet);
                increment(targetConsumerCounts, sheet.orderId());
            }
        }
        for (InboundToteManifest manifest : manifests.manifests()) {
            DspSchedulerOrderState state = ordersBySheet.get(manifest.orderSheetKey());
            if (state == null || state.order().orderType() != manifest.orderType()
                    || !state.order().serviceCentreId().equals(manifest.serviceCentreId())) {
                throw new IllegalArgumentException("Manifest does not match executable order: "
                        + manifest.physicalToteId());
            }
            boolean p2p = state.routeRequirements().requiresP2p();
            inbound.put(manifest.physicalToteId(), new InboundWork(manifest, p2p));
            if (p2p) {
                remainingCounts.put(manifest.serviceCentreId(),
                        changedCounts(manifest.serviceCentreId(), 1, 0, 0, 0));
            }
            increment(sourceConsumerCounts, manifest.orderSheetKey());
            if (manifest.orderType() == OrderType.ASSOCIATED) {
                increment(targetConsumerCounts, manifest.orderSheetKey().orderId());
            }
        }
        for (PlannedBag bag : plannedBags) {
            require(bag, "planned bag");
            if (bags.putIfAbsent(bag.bagKey(), new BagWork(bag)) != null) {
                throw new IllegalArgumentException("Duplicate planned bag: " + bag.bagKey());
            }
            initializeCentre(bag.serviceCentreId());
            remainingCounts.put(bag.serviceCentreId(), changedCounts(
                    bag.serviceCentreId(), 0, 0, bag.physicalPackIds().size(), 1));
            for (OrderSheetKey sheet : bag.owningOrderSheetKeys()) {
                increment(sourceBagCounts, sheet);
            }
        }
    }

    public boolean isUnallocatedEmpty(OrderSheetKey sheet) {
        require(sheet, "sheet");
        return emptyOrders.containsKey(sheet);
    }

    public Optional<Av02AllocatedTote> findLiveAv02(PhysicalToteId id) {
        require(id, "id");
        return Optional.ofNullable(av02ById.get(id));
    }

    public Optional<Av02AllocatedTote> findLiveAv02(OrderSheetKey sheet) {
        require(sheet, "sheet");
        return Optional.ofNullable(av02BySheet.get(sheet));
    }

    public Optional<PlannedBag> remainingBag(BagKey key) {
        require(key, "bag key");
        BagWork work = bags.get(key);
        return work == null ? Optional.empty() : Optional.of(work.bag);
    }

    public int remainingBagsForSource(OrderSheetKey sheet) {
        require(sheet, "sheet");
        return sourceBagCounts.getOrDefault(sheet, 0);
    }

    public int remainingInboundConsumersForSource(OrderSheetKey sheet) {
        require(sheet, "sheet");
        return sourceConsumerCounts.getOrDefault(sheet, 0);
    }

    public int remainingConsumersForOrder(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId must not be blank");
        }
        return targetConsumerCounts.getOrDefault(orderId, 0);
    }

    public void validateCanAllocateEmpty(OrderSheetKey sheet) {
        require(sheet, "sheet");
        DspSchedulerOrderState pending = emptyOrders.get(sheet);
        if (pending == null || !pending.routeRequirements().requiresP2p()
                || av02BySheet.containsKey(sheet)) {
            throw new IllegalArgumentException("No pending EMPTY work for sheet: " + sheet);
        }
        changedCounts(pending.order().serviceCentreId(), 1, -1, 0, 0);
        validateDecrement(sourceConsumerCounts, sheet);
        validateDecrement(targetConsumerCounts, sheet.orderId());
    }

    public void validateAv02Allocated(Av02AllocatedTote tote) {
        require(tote, "AV02 tote");
        validateCanAllocateEmpty(tote.orderSheetKey());
        DspSchedulerOrderState pending = emptyOrders.get(tote.orderSheetKey());
        if (!pending.order().serviceCentreId().equals(tote.serviceCentreId())
                || pending.order().sequenceNumber() != tote.sourceSequenceNumber()
                || tote.physicalTote().state() != PhysicalToteLifecycleState.ACTIVE_PRE_P2P
                || inbound.containsKey(tote.physicalToteId()) || av02ById.containsKey(tote.physicalToteId())) {
            throw new IllegalArgumentException("AV02 tote must match pending work and a free current identity");
        }
    }

    public void recordAv02Allocated(Av02AllocatedTote tote) {
        validateAv02Allocated(tote);
        remainingCounts.put(tote.serviceCentreId(), changedCounts(tote.serviceCentreId(), 1, -1, 0, 0));
        emptyOrders.remove(tote.orderSheetKey());
        av02ById.put(tote.physicalToteId(), tote);
        av02BySheet.put(tote.orderSheetKey(), tote);
        emptyView = null;
        av02View = null;
        p2pIdsView = null;
        cachedSnapshot = null;
    }

    public void validateInboundConsumed(PhysicalToteId id, StationType station, Optional<P2pLineId> line) {
        require(id, "id");
        require(station, "station");
        require(line, "line");
        InboundWork work = inbound.get(id);
        Av02AllocatedTote av02 = av02ById.get(id);
        if (work == null && av02 == null) {
            throw new IllegalArgumentException("No remaining inbound work for tote: " + id);
        }
        boolean p2p = av02 != null || work.p2p;
        if (p2p) {
            if (station != StationType.P2P || line.isEmpty()) {
                throw new IllegalArgumentException("P2P completion requires a configured line");
            }
            LineTotals totals = requireLine(line.orElseThrow());
            Math.incrementExact(totals.consumedTotes());
        } else if (station != StationType.ADAPTING || line.isPresent()
                || work.manifest.orderType() != OrderType.ADAPTED) {
            throw new IllegalArgumentException("STORE-only ADAPTED completion requires Adapting and no line");
        }
        String centre = av02 != null ? av02.serviceCentreId() : work.manifest.serviceCentreId();
        OrderSheetKey sheet = av02 != null ? av02.orderSheetKey() : work.manifest.orderSheetKey();
        changedCounts(centre, p2p ? -1 : 0, 0, 0, 0);
        CentreTotals totals = centreTotals.get(centre);
        Math.incrementExact(p2p ? totals.consumedAtP2p() : totals.consumedAtAdapting());
        validateDecrement(sourceConsumerCounts, sheet);
        if (av02 != null || work.manifest.orderType() == OrderType.ASSOCIATED) {
            validateDecrement(targetConsumerCounts, sheet.orderId());
        }
    }

    public void recordInboundConsumed(PhysicalToteId id, StationType station, Optional<P2pLineId> line) {
        validateInboundConsumed(id, station, line);
        InboundWork work = inbound.get(id);
        Av02AllocatedTote av02 = av02ById.get(id);
        boolean p2p = av02 != null || work.p2p;
        String centre = av02 != null ? av02.serviceCentreId() : work.manifest.serviceCentreId();
        OrderSheetKey sheet = av02 != null ? av02.orderSheetKey() : work.manifest.orderSheetKey();
        CentreTotals totals = centreTotals.get(centre);
        centreTotals.put(centre, new CentreTotals(
                totals.consumedAtAdapting() + (p2p ? 0 : 1),
                totals.consumedAtP2p() + (p2p ? 1 : 0), totals.allocatedBags(),
                totals.allocatedPacks(), totals.affectedAllocatedBags()));
        remainingCounts.put(centre, changedCounts(centre, p2p ? -1 : 0, 0, 0, 0));
        if (p2p) {
            P2pLineId lineId = line.orElseThrow();
            LineTotals old = lineTotals.get(lineId);
            lineTotals.put(lineId, new LineTotals(old.consumedTotes() + 1, old.allocatedBags()));
            p2pIdsView = null;
        }
        decrement(sourceConsumerCounts, sheet);
        if (av02 != null || work.manifest.orderType() == OrderType.ASSOCIATED) {
            decrement(targetConsumerCounts, sheet.orderId());
        }
        if (av02 != null) {
            av02ById.remove(id);
            av02BySheet.remove(sheet);
            av02View = null;
        } else {
            inbound.remove(id);
            inboundView = null;
        }
        cachedSnapshot = null;
    }

    public void validateMissingPacks(BagKey key, Set<String> missingIds) {
        BagWork work = requireBag(key);
        require(missingIds, "missing IDs");
        if (missingIds.stream().anyMatch(id -> id == null)
                || !work.bag.physicalPackIds().containsAll(missingIds)) {
            throw new IllegalArgumentException("Missing IDs must belong to the remaining planned bag");
        }
        changedCounts(work.bag.serviceCentreId(), 0, 0, work.missingIds.size() - missingIds.size(), 0);
    }

    public void recordMissingPacks(BagKey key, Set<String> missingIds) {
        validateMissingPacks(key, missingIds);
        BagWork work = bags.get(key);
        if (work.missingIds.equals(missingIds)) {
            return;
        }
        Set<String> replacement = Set.copyOf(missingIds);
        remainingCounts.put(work.bag.serviceCentreId(), changedCounts(
                work.bag.serviceCentreId(), 0, 0, work.missingIds.size() - replacement.size(), 0));
        work.missingIds = replacement;
        cachedSnapshot = null;
    }

    public void validateBagAllocated(P2pLineId line, PlannedBag bag, Set<String> missingIds) {
        require(bag, "bag");
        LineTotals lineTotal = requireLine(line);
        BagWork work = requireBag(bag.bagKey());
        validateMissingPacks(bag.bagKey(), missingIds);
        if (!work.bag.equals(bag) || !work.missingIds.equals(missingIds) || work.effectivePacks() == 0) {
            throw new IllegalArgumentException("Allocation must match classified remaining nonempty bag work");
        }
        validateBagRemoval(work);
        CentreTotals totals = centreTotals.get(bag.serviceCentreId());
        Math.incrementExact(totals.allocatedBags());
        Math.addExact(totals.allocatedPacks(), work.effectivePacks());
        if (!missingIds.isEmpty()) {
            Math.incrementExact(totals.affectedAllocatedBags());
        }
        Math.incrementExact(lineTotal.allocatedBags());
    }

    public void recordBagAllocated(P2pLineId line, PlannedBag bag, Set<String> missingIds) {
        validateBagAllocated(line, bag, missingIds);
        BagWork work = bags.get(bag.bagKey());
        CentreTotals totals = centreTotals.get(bag.serviceCentreId());
        centreTotals.put(bag.serviceCentreId(), new CentreTotals(
                totals.consumedAtAdapting(), totals.consumedAtP2p(), totals.allocatedBags() + 1,
                totals.allocatedPacks() + work.effectivePacks(),
                totals.affectedAllocatedBags() + (missingIds.isEmpty() ? 0 : 1)));
        LineTotals old = lineTotals.get(line);
        lineTotals.put(line, new LineTotals(old.consumedTotes(), old.allocatedBags() + 1));
        removeBag(work);
    }

    public void validatePendingEmptyBag(BagKey key) {
        BagWork work = requireBag(key);
        if (work.effectivePacks() != 0) {
            throw new IllegalArgumentException("Only fully missing bags leave as pending empty work");
        }
        validateBagRemoval(work);
    }

    public void recordPendingEmptyBag(BagKey key) {
        validatePendingEmptyBag(key);
        removeBag(bags.get(key));
    }

    public DspOperationalWorkSnapshot snapshot() {
        if (cachedSnapshot == null) {
            if (inboundView == null) {
                inboundView = inbound.values().stream().map(work -> work.manifest).toList();
            }
            if (emptyView == null) {
                emptyView = List.copyOf(emptyOrders.values());
            }
            if (av02View == null) {
                av02View = List.copyOf(av02ById.values());
            }
            if (p2pIdsView == null) {
                Map<String, List<PhysicalToteId>> ids = new LinkedHashMap<>();
                remainingCounts.keySet().forEach(centre -> ids.put(centre, new ArrayList<>()));
                for (InboundWork work : inbound.values()) {
                    if (work.p2p) {
                        ids.get(work.manifest.serviceCentreId()).add(work.manifest.physicalToteId());
                    }
                }
                for (Av02AllocatedTote tote : av02ById.values()) {
                    ids.get(tote.serviceCentreId()).add(tote.physicalToteId());
                }
                ids.replaceAll((centre, values) -> List.copyOf(values));
                p2pIdsView = Collections.unmodifiableMap(ids);
            }
            cachedSnapshot = new DspOperationalWorkSnapshot(inboundView, emptyView, av02View,
                    p2pIdsView, remainingCounts, centreTotals, lineTotals);
        }
        return cachedSnapshot;
    }

    // Counts only, for same-package bounded-retirement proofs; never a history/debug payload API.
    EntryCounts entryCounts() {
        int classifiedBags = 0;
        for (BagWork work : bags.values()) {
            if (!work.missingIds.isEmpty()) {
                classifiedBags++;
            }
        }
        return new EntryCounts(inbound.size(), emptyOrders.size(), av02ById.size(), av02BySheet.size(),
                bags.size(), sourceBagCounts.size(), sourceConsumerCounts.size(), targetConsumerCounts.size(),
                classifiedBags);
    }

    record EntryCounts(int inbound, int empty, int av02Ids, int av02Sheets, int bags,
            int sourceBags, int sourceConsumers, int targetConsumers, int classifiedBags) { }

    private void initializeCentre(String centre) {
        remainingCounts.putIfAbsent(centre, new RemainingCounts(0, 0, 0, 0));
        centreTotals.putIfAbsent(centre, new CentreTotals(0, 0, 0, 0, 0));
    }

    private RemainingCounts changedCounts(String centre, int totes, int empty, int packs, int bagCount) {
        RemainingCounts old = remainingCounts.get(centre);
        return new RemainingCounts(Math.addExact(old.totes(), totes),
                Math.addExact(old.unallocatedEmptyOrders(), empty),
                Math.addExact(old.packs(), packs), Math.addExact(old.bags(), bagCount));
    }

    private LineTotals requireLine(P2pLineId line) {
        require(line, "line");
        LineTotals totals = lineTotals.get(line);
        if (totals == null) {
            throw new IllegalArgumentException("Unknown configured P2P line: " + line);
        }
        return totals;
    }

    private BagWork requireBag(BagKey key) {
        require(key, "bag key");
        BagWork work = bags.get(key);
        if (work == null) {
            throw new IllegalArgumentException("No remaining bag work: " + key);
        }
        return work;
    }

    private void validateBagRemoval(BagWork work) {
        changedCounts(work.bag.serviceCentreId(), 0, 0, -work.effectivePacks(), -1);
        for (OrderSheetKey sheet : work.bag.owningOrderSheetKeys()) {
            validateDecrement(sourceBagCounts, sheet);
        }
    }

    private void removeBag(BagWork work) {
        remainingCounts.put(work.bag.serviceCentreId(), changedCounts(
                work.bag.serviceCentreId(), 0, 0, -work.effectivePacks(), -1));
        for (OrderSheetKey sheet : work.bag.owningOrderSheetKeys()) {
            decrement(sourceBagCounts, sheet);
        }
        bags.remove(work.bag.bagKey());
        cachedSnapshot = null;
    }

    private static <K> void increment(Map<K, Integer> counts, K key) {
        counts.put(key, Math.incrementExact(counts.getOrDefault(key, 0)));
    }

    private static <K> void validateDecrement(Map<K, Integer> counts, K key) {
        if (counts.getOrDefault(key, 0) <= 0) {
            throw new IllegalArgumentException("No remaining count for: " + key);
        }
    }

    private static <K> void decrement(Map<K, Integer> counts, K key) {
        int remaining = counts.get(key) - 1;
        if (remaining == 0) {
            counts.remove(key);
        } else {
            counts.put(key, remaining);
        }
    }

    private static void require(Object value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
    }

    private record InboundWork(InboundToteManifest manifest, boolean p2p) { }

    private static final class BagWork {
        private final PlannedBag bag;
        private Set<String> missingIds = Set.of();

        private BagWork(PlannedBag bag) {
            this.bag = bag;
        }

        private int effectivePacks() {
            return bag.physicalPackIds().size() - missingIds.size();
        }
    }
}
