package online.davisfamily.warehouse.sim.dsp.analysis.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingOrderPreparationCatalog;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingTargetSheetCatalog;
import online.davisfamily.warehouse.sim.dsp.bagging.BagKey;
import online.davisfamily.warehouse.sim.dsp.bagging.BagPlanningResult;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedBag;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedPackSlot;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.p2p.allocation.P2pMissingPackSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;

/** Simulation-thread owner of misplaced prepared packs and their PDC collection state. */
public final class DspPreparedPackExceptionLedger {
    private final Map<String, PlannedPackSlot> slotsByPackId;
    private final Map<String, PlannedBag> bagsByCorrelationId;
    private final Map<String, Set<String>> expectedPreparedPackIdsByOrderId;
    private final Map<OrderSheetKey, Set<String>> plannedPackIdsBySheet;
    private final AdaptingOrderPreparationCatalog orderCatalog;

    private Map<String, MisplacedPack> misplacedByPackId = Map.of();
    private Map<String, OrderSheetKey> visitedSheetByToteId = Map.of();
    private Set<String> collectedAtPdc = Set.of();
    private P2pMissingPackSnapshot snapshot = P2pMissingPackSnapshot.empty();

    public DspPreparedPackExceptionLedger(
            BagPlanningResult bagPlan,
            AdaptingTargetSheetCatalog targetCatalog,
            AdaptingOrderPreparationCatalog orderCatalog) {
        if (bagPlan == null || targetCatalog == null || orderCatalog == null) {
            throw new IllegalArgumentException("Bag plan and catalogs must not be null");
        }
        this.orderCatalog = orderCatalog;
        Map<String, PlannedBag> bags = new LinkedHashMap<>();
        for (PlannedBag bag : bagPlan.plannedBags()) {
            bags.put(bag.bagKey().correlationId(), bag);
        }
        bagsByCorrelationId = Map.copyOf(bags);
        Map<String, PlannedPackSlot> slots = new LinkedHashMap<>();
        Map<String, Set<String>> expected = new LinkedHashMap<>();
        Map<OrderSheetKey, Set<String>> bySheet = new LinkedHashMap<>();
        Map<String, Set<PreparedLineKey>> preparedKeysByOrderId = new LinkedHashMap<>();
        for (PlannedPackSlot slot : bagPlan.plannedPackSlots()) {
            slots.put(slot.reservedPhysicalPackId(), slot);
            bySheet.computeIfAbsent(slot.fulfilmentOrderSheetKey(), ignored -> new LinkedHashSet<>())
                    .add(slot.reservedPhysicalPackId());
            String orderId = slot.fulfilmentOrderSheetKey().orderId();
            PreparedLineKey key = new PreparedLineKey(orderId, slot.sourceProvenance().lineReference());
            Set<PreparedLineKey> preparedKeys = preparedKeysByOrderId.computeIfAbsent(
                    orderId, id -> Set.copyOf(orderCatalog.requiredKeysFor(id)));
            if (preparedKeys.contains(key)) {
                if (!targetCatalog.requireTargetSheet(key).equals(slot.fulfilmentOrderSheetKey())
                        || !orderCatalog.requireStoreId(orderId).equals(slot.sourceProvenance().pharmacyId())) {
                    throw new IllegalStateException("Prepared slot disagrees with catalog: " + slot.reservedPhysicalPackId());
                }
                expected.computeIfAbsent(orderId, ignored -> new LinkedHashSet<>())
                        .add(slot.reservedPhysicalPackId());
            }
        }
        slotsByPackId = Map.copyOf(slots);
        expectedPreparedPackIdsByOrderId = immutableSets(expected);
        plannedPackIdsBySheet = immutableSets(bySheet);
    }

    /** Read-only validation; the returned decision contains the entire prospective publication. */
    public CollectDecision prepareCollect(
            OrderSheetKey collectingSheet,
            PhysicalToteId receivingTote,
            List<PackPlan> collectedPacks) {
        if (collectingSheet == null || receivingTote == null || collectedPacks == null) {
            throw new IllegalArgumentException("Collect inputs must not be null");
        }
        String orderId = collectingSheet.orderId();
        OrderSheetKey designated = orderCatalog.firstCollectSheetFor(orderId)
                .orElseThrow(() -> new IllegalStateException("No designated COLLECT sheet for " + orderId));
        if (visitedSheetByToteId.containsKey(receivingTote.value())) {
            throw new IllegalStateException("Tote already recorded at COLLECT: " + receivingTote.value());
        }
        boolean first = !snapshot.firstCollectedSheetByOrderId().containsKey(orderId);
        if (first && !designated.equals(collectingSheet)) {
            throw new IllegalStateException("First COLLECT must use designated sheet " + designated);
        }
        if (!first && !collectedPacks.isEmpty()) {
            throw new IllegalStateException("Later COLLECT must not transfer prepared packs");
        }

        Map<String, MisplacedPack> nextMisplaced = new LinkedHashMap<>(misplacedByPackId);
        Map<String, OrderSheetKey> nextVisited = new LinkedHashMap<>(visitedSheetByToteId);
        Map<BagKey, Set<String>> nextMissing = mutableSets(snapshot.missingPhysicalPackIdsByBagKey());
        Set<BagKey> nextPending = new LinkedHashSet<>(snapshot.pendingEmptyBagKeys());
        Set<BagKey> affectedBags = new LinkedHashSet<>();
        Map<String, Integer> nextMissingCounts = new LinkedHashMap<>(snapshot.missingPackCountByServiceCentreId());
        Map<String, OrderSheetKey> nextFirst = new LinkedHashMap<>(snapshot.firstCollectedSheetByOrderId());
        if (first) {
            Set<String> seen = new LinkedHashSet<>();
            for (PackPlan pack : collectedPacks) {
                if (pack == null || !seen.add(pack.packId())) {
                    throw new IllegalStateException("Duplicate or null collected pack");
                }
                PlannedPackSlot slot = slotsByPackId.get(pack.packId());
                if (slot == null || !expectedPreparedPackIdsByOrderId
                        .getOrDefault(orderId, Set.of()).contains(pack.packId())) {
                    throw new IllegalStateException("Unknown prepared pack for order: " + pack.packId());
                }
                PlannedBag bag = bagsByCorrelationId.get(pack.correlationId());
                if (bag == null || !bag.bagKey().equals(slot.bagKey())
                        || !bag.physicalPackIds().contains(pack.packId())
                        || !slot.dimensions().equals(pack.dimensions())
                        || !slot.sourceProvenance().serviceCentreId().equals(bag.serviceCentreId())
                        || !slot.sourceProvenance().pharmacyId().equals(bag.pharmacyId())
                        || !slot.fulfilmentOrderSheetKey().orderId().equals(orderId)) {
                    throw new IllegalStateException("Inconsistent planned bag identity: " + pack.packId());
                }
                if (!slot.fulfilmentOrderSheetKey().equals(collectingSheet)) {
                    if (nextMisplaced.containsKey(pack.packId())) {
                        throw new IllegalStateException("Pack already recorded: " + pack.packId());
                    }
                    nextMisplaced.put(pack.packId(), new MisplacedPack(
                            pack.packId(), slot.fulfilmentOrderSheetKey(), receivingTote,
                            pack.correlationId(), bag.bagKey(), bag.serviceCentreId(), bag.pharmacyId()));
                    nextMissing.computeIfAbsent(bag.bagKey(), ignored -> new LinkedHashSet<>())
                            .add(pack.packId());
                    affectedBags.add(bag.bagKey());
                    nextMissingCounts.merge(bag.serviceCentreId(), 1, Integer::sum);
                }
            }
            if (!seen.equals(expectedPreparedPackIdsByOrderId.getOrDefault(orderId, Set.of()))) {
                throw new IllegalStateException("COLLECT preview does not contain the complete prepared order");
            }
            nextFirst.put(orderId, collectingSheet);
        }
        for (BagKey bagKey : affectedBags) {
            PlannedBag bag = bagsByCorrelationId.get(bagKey.correlationId());
            if (nextMissing.get(bagKey).size() == bag.physicalPackIds().size()) {
                nextPending.add(bagKey);
            }
        }
        nextVisited.put(receivingTote.value(), collectingSheet);
        P2pMissingPackSnapshot nextSnapshot = new P2pMissingPackSnapshot(
                snapshot.version() + 1, nextMissing, nextPending, nextMissingCounts,
                snapshot.pdcCollectedPackCountByServiceCentreId(), nextFirst);
        return new CollectDecision(this, snapshot.version(), Map.copyOf(nextMisplaced),
                Map.copyOf(nextVisited), nextSnapshot);
    }

    public void commitCollect(CollectDecision decision) {
        if (decision == null || decision.owner != this || decision.version != snapshot.version()) {
            throw new IllegalStateException("Stale or foreign COLLECT decision");
        }
        misplacedByPackId = decision.misplacedByPackId;
        visitedSheetByToteId = decision.visitedSheetByToteId;
        snapshot = decision.nextSnapshot;
    }

    public boolean isMisplaced(String packId) {
        return misplacedByPackId.containsKey(requireId(packId, "packId"));
    }

    public Optional<MisplacedPack> misplacedPack(String packId) {
        return Optional.ofNullable(misplacedByPackId.get(requireId(packId, "packId")));
    }

    public Set<String> missingPackIdsFor(String correlationId) {
        PlannedBag bag = requireBag(correlationId);
        return snapshot.missingPhysicalPackIdsByBagKey().getOrDefault(bag.bagKey(), Set.of());
    }

    public int effectivePackCount(String correlationId, int plannedCount) {
        PlannedBag bag = requireBag(correlationId);
        if (plannedCount != bag.physicalPackIds().size()) {
            throw new IllegalArgumentException("Planned count disagrees with bag " + correlationId);
        }
        return plannedCount - missingPackIdsFor(correlationId).size();
    }

    public boolean allowEmptyTote(String physicalToteId) {
        OrderSheetKey sheet = visitedSheetByToteId.get(requireId(physicalToteId, "physicalToteId"));
        if (sheet == null) {
            return false;
        }
        Set<String> planned = plannedPackIdsBySheet.getOrDefault(sheet, Set.of());
        boolean isEmpty = planned.isEmpty();
        boolean misplacedContainsAllPlannedPacks = misplacedByPackId.keySet().containsAll(planned);
        boolean bothTrue = !isEmpty && misplacedContainsAllPlannedPacks;
        return bothTrue;
    }

    public void confirmPdcCollection(String packId) {
        MisplacedPack pack = misplacedByPackId.get(requireId(packId, "packId"));
        if (pack == null || collectedAtPdc.contains(packId)) {
            throw new IllegalStateException("Unknown or already collected misplaced pack: " + packId);
        }
        Set<String> nextCollected = new LinkedHashSet<>(collectedAtPdc);
        nextCollected.add(packId);
        Map<String, Integer> counts = new LinkedHashMap<>(snapshot.pdcCollectedPackCountByServiceCentreId());
        counts.merge(pack.serviceCentreId(), 1, Integer::sum);
        P2pMissingPackSnapshot nextSnapshot = new P2pMissingPackSnapshot(
                snapshot.version() + 1, snapshot.missingPhysicalPackIdsByBagKey(),
                snapshot.pendingEmptyBagKeys(), snapshot.missingPackCountByServiceCentreId(),
                counts, snapshot.firstCollectedSheetByOrderId());
        collectedAtPdc = Collections.unmodifiableSet(nextCollected);
        snapshot = nextSnapshot;
    }

    public Set<BagKey> pendingEmptyBagKeys() {
        return snapshot.pendingEmptyBagKeys();
    }

    public P2pMissingPackSnapshot snapshot() {
        return snapshot;
    }

    private PlannedBag requireBag(String correlationId) {
        PlannedBag bag = bagsByCorrelationId.get(requireId(correlationId, "correlationId"));
        if (bag == null) {
            throw new IllegalArgumentException("Unknown bag correlation: " + correlationId);
        }
        return bag;
    }

    private static String requireId(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static <K> Map<K, Set<String>> immutableSets(Map<K, Set<String>> source) {
        Map<K, Set<String>> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, Collections.unmodifiableSet(new LinkedHashSet<>(value))));
        return Collections.unmodifiableMap(copy);
    }

    private static <K> Map<K, Set<String>> mutableSets(Map<K, Set<String>> source) {
        Map<K, Set<String>> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, new LinkedHashSet<>(value)));
        return copy;
    }

    public record MisplacedPack(
            String physicalPackId, OrderSheetKey intendedSheet, PhysicalToteId receivingTote,
            String correlationId, BagKey bagKey, String serviceCentreId, String storeId) {
    }

    public static final class CollectDecision {
        private final DspPreparedPackExceptionLedger owner;
        private final long version;
        private final Map<String, MisplacedPack> misplacedByPackId;
        private final Map<String, OrderSheetKey> visitedSheetByToteId;
        private final P2pMissingPackSnapshot nextSnapshot;

        private CollectDecision(DspPreparedPackExceptionLedger owner, long version,
                Map<String, MisplacedPack> misplacedByPackId,
                Map<String, OrderSheetKey> visitedSheetByToteId,
                P2pMissingPackSnapshot nextSnapshot) {
            this.owner = owner;
            this.version = version;
            this.misplacedByPackId = misplacedByPackId;
            this.visitedSheetByToteId = visitedSheetByToteId;
            this.nextSnapshot = nextSnapshot;
        }
    }
}
