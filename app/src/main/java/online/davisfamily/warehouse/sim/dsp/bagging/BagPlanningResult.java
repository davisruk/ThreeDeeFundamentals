package online.davisfamily.warehouse.sim.dsp.bagging;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

/** Immutable authoritative bag, slot, trace, and P2P-load planning result. */
public final class BagPlanningResult {
    private final List<PlannedBag> plannedBags;
    private final List<ToteLoadPlan> p2pToteLoadPlans;
    private final List<PlannedPackTrace> packTraces;
    private final List<PlannedPackSlot> plannedPackSlots;
    private final List<BagSequencePosition> bagSequencePositions;

    private final Map<BagKey, PlannedBag> bagsByKey;
    private final Map<String, PlannedBag> bagsByCorrelationId;
    private final Map<String, PlannedPackTrace> tracesByPhysicalPackId;
    private final Map<PlannedPackSlotKey, PlannedPackSlot> slotsByKey;
    private final Map<String, PlannedPackSlot> slotsByReservedPhysicalPackId;
    private final Map<BagKey, BagSequencePosition> positionsByBagKey;

    public BagPlanningResult(
            List<PlannedBag> plannedBags,
            List<ToteLoadPlan> p2pToteLoadPlans,
            List<PlannedPackTrace> packTraces,
            List<PlannedPackSlot> plannedPackSlots,
            List<BagSequencePosition> bagSequencePositions) {
        this.plannedBags = copyAndRejectNull(plannedBags, "plannedBags");
        this.p2pToteLoadPlans = copyAndRejectNull(p2pToteLoadPlans, "p2pToteLoadPlans");
        this.packTraces = copyAndRejectNull(packTraces, "packTraces");
        this.plannedPackSlots = copyAndRejectNull(plannedPackSlots, "plannedPackSlots");
        this.bagSequencePositions = copyAndRejectNull(
                bagSequencePositions,
                "bagSequencePositions");

        Map<BagKey, PlannedBag> bagsByKey = new LinkedHashMap<>();
        Map<String, PlannedBag> bagsByCorrelationId = new LinkedHashMap<>();
        for (PlannedBag plannedBag : this.plannedBags) {
            if (bagsByKey.putIfAbsent(plannedBag.bagKey(), plannedBag) != null) {
                throw new IllegalArgumentException("Duplicate bag key: " + plannedBag.bagKey());
            }
            String correlationId = plannedBag.bagKey().correlationId();
            if (bagsByCorrelationId.putIfAbsent(correlationId, plannedBag) != null) {
                throw new IllegalArgumentException("Duplicate bag correlation ID: " + correlationId);
            }
        }

        Map<PlannedPackSlotKey, PlannedPackSlot> slotsByKey = new LinkedHashMap<>();
        Map<String, PlannedPackSlot> slotsByReservedPhysicalPackId = new LinkedHashMap<>();
        Map<BagKey, List<String>> slotPackIdsByBagKey = new LinkedHashMap<>();
        for (PlannedPackSlot slot : this.plannedPackSlots) {
            if (slotsByKey.putIfAbsent(slot.slotKey(), slot) != null) {
                throw new IllegalArgumentException("Duplicate planned pack slot key: " + slot.slotKey());
            }
            if (slotsByReservedPhysicalPackId.putIfAbsent(
                    slot.reservedPhysicalPackId(),
                    slot) != null) {
                throw new IllegalArgumentException(
                        "Duplicate reserved physical pack ID: " + slot.reservedPhysicalPackId());
            }
            if (!bagsByKey.containsKey(slot.bagKey())) {
                throw new IllegalArgumentException(
                        "Planned pack slot references unknown bag: " + slot.bagKey());
            }
            slotPackIdsByBagKey.computeIfAbsent(slot.bagKey(), ignored -> new ArrayList<>())
                    .add(slot.reservedPhysicalPackId());
        }
        for (PlannedBag plannedBag : this.plannedBags) {
            List<String> slotPackIds = slotPackIdsByBagKey.get(plannedBag.bagKey());
            if (slotPackIds == null || !plannedBag.physicalPackIds().equals(slotPackIds)) {
                throw new IllegalArgumentException(
                        "Planned bag physical pack IDs do not match planned slots: "
                                + plannedBag.bagKey());
            }
        }

        if (this.bagSequencePositions.size() != this.plannedBags.size()) {
            throw new IllegalArgumentException(
                    "bagSequencePositions must have one entry per planned bag");
        }
        Map<BagKey, BagSequencePosition> positionsByBagKey = new LinkedHashMap<>();
        Map<String, Integer> totalByPrescription = new LinkedHashMap<>();
        Map<String, Set<Integer>> ordinalsByPrescription = new LinkedHashMap<>();
        for (int index = 0; index < this.plannedBags.size(); index++) {
            PlannedBag bag = this.plannedBags.get(index);
            BagSequencePosition position = this.bagSequencePositions.get(index);
            if (position.bagOrdinal() != bag.bagKey().bagOrdinal()) {
                throw new IllegalArgumentException(
                        "Bag sequence ordinal does not match bag key: " + bag.bagKey());
            }
            Integer previousTotal = totalByPrescription.putIfAbsent(
                    bag.bagKey().prescriptionId(),
                    position.totalBagCount());
            if (previousTotal != null && previousTotal.intValue() != position.totalBagCount()) {
                throw new IllegalArgumentException(
                        "Inconsistent total bag count for prescription: "
                                + bag.bagKey().prescriptionId());
            }
            ordinalsByPrescription.computeIfAbsent(
                    bag.bagKey().prescriptionId(),
                    ignored -> new LinkedHashSet<>()).add(position.bagOrdinal());
            if (positionsByBagKey.putIfAbsent(bag.bagKey(), position) != null) {
                throw new IllegalArgumentException(
                        "Duplicate bag sequence position: " + bag.bagKey());
            }
        }
        for (Map.Entry<String, Integer> entry : totalByPrescription.entrySet()) {
            Set<Integer> ordinals = ordinalsByPrescription.get(entry.getKey());
            if (ordinals.size() != entry.getValue()) {
                throw new IllegalArgumentException(
                        "Bag ordinal count does not match total bag count for prescription: "
                                + entry.getKey());
            }
            for (int ordinal = 1; ordinal <= entry.getValue(); ordinal++) {
                if (!ordinals.contains(ordinal)) {
                    throw new IllegalArgumentException(
                            "Bag ordinals are not contiguous for prescription: " + entry.getKey());
                }
            }
        }

        Map<String, PlannedPackTrace> tracesByPhysicalPackId = new LinkedHashMap<>();
        for (PlannedPackTrace trace : this.packTraces) {
            if (tracesByPhysicalPackId.putIfAbsent(trace.physicalPackId(), trace) != null) {
                throw new IllegalArgumentException(
                        "Duplicate planned pack trace: " + trace.physicalPackId());
            }
            PlannedPackSlot slot = slotsByReservedPhysicalPackId.get(trace.physicalPackId());
            if (slot == null) {
                throw new IllegalArgumentException(
                        "Trace has no planned pack slot: " + trace.physicalPackId());
            }
            if (slot.initialPhysicalToteId().isEmpty()
                    || !slot.initialPhysicalToteId().orElseThrow().equals(trace.inputPhysicalToteId())
                    || !slot.sourceProvenance().equals(trace.sourceProvenance())
                    || !slot.fulfilmentOrderSheetKey().equals(trace.fulfilmentOrderSheetKey())
                    || !slot.bagKey().equals(trace.bagKey())) {
                throw new IllegalArgumentException(
                        "Planned pack trace does not match its slot: " + trace.physicalPackId());
            }
        }
        for (PlannedPackSlot slot : this.plannedPackSlots) {
            if (slot.initialPhysicalToteId().isPresent()
                    && !tracesByPhysicalPackId.containsKey(slot.reservedPhysicalPackId())) {
                throw new IllegalArgumentException(
                        "Initially physical slot has no planned pack trace: "
                                + slot.reservedPhysicalPackId());
            }
        }

        validateP2pToteLoadPlans(this.p2pToteLoadPlans, slotsByReservedPhysicalPackId,
                tracesByPhysicalPackId);
        if (tracesByPhysicalPackId.size() != countP2pPacks(this.p2pToteLoadPlans)) {
            throw new IllegalArgumentException(
                    "Every planned pack trace must appear in exactly one P2P tote load plan");
        }

        this.bagsByKey = immutableMap(bagsByKey);
        this.bagsByCorrelationId = immutableMap(bagsByCorrelationId);
        this.tracesByPhysicalPackId = immutableMap(tracesByPhysicalPackId);
        this.slotsByKey = immutableMap(slotsByKey);
        this.slotsByReservedPhysicalPackId = immutableMap(slotsByReservedPhysicalPackId);
        this.positionsByBagKey = immutableMap(positionsByBagKey);
    }

    public List<PlannedBag> plannedBags() {
        return plannedBags;
    }

    public List<ToteLoadPlan> p2pToteLoadPlans() {
        return p2pToteLoadPlans;
    }

    public List<PlannedPackTrace> packTraces() {
        return packTraces;
    }

    public List<PlannedPackSlot> plannedPackSlots() {
        return plannedPackSlots;
    }

    public List<BagSequencePosition> bagSequencePositions() {
        return bagSequencePositions;
    }

    public Optional<PlannedBag> findBag(BagKey bagKey) {
        if (bagKey == null) {
            throw new IllegalArgumentException("bagKey must not be null");
        }
        return Optional.ofNullable(bagsByKey.get(bagKey));
    }

    public PlannedBag requireBag(BagKey bagKey) {
        return findBag(bagKey).orElseThrow(() -> new IllegalArgumentException(
                "Unknown bag key: " + bagKey));
    }

    public Optional<PlannedBag> findBagByCorrelationId(String correlationId) {
        return Optional.ofNullable(bagsByCorrelationId.get(requireTrimmedValue(
                correlationId,
                "correlationId")));
    }

    public Optional<PlannedPackTrace> findPackTrace(String physicalPackId) {
        return Optional.ofNullable(tracesByPhysicalPackId.get(requireTrimmedValue(
                physicalPackId,
                "physicalPackId")));
    }

    public Optional<PlannedPackSlot> findPlannedPackSlot(PlannedPackSlotKey slotKey) {
        if (slotKey == null) {
            throw new IllegalArgumentException("slotKey must not be null");
        }
        return Optional.ofNullable(slotsByKey.get(slotKey));
    }

    public PlannedPackSlot requirePlannedPackSlot(PlannedPackSlotKey slotKey) {
        return findPlannedPackSlot(slotKey).orElseThrow(() -> new IllegalArgumentException(
                "Unknown planned pack slot: " + slotKey));
    }

    public Optional<PlannedPackSlot> findPlannedPackSlotByReservedPhysicalPackId(
            String reservedPhysicalPackId) {
        return Optional.ofNullable(slotsByReservedPhysicalPackId.get(requireTrimmedValue(
                reservedPhysicalPackId,
                "reservedPhysicalPackId")));
    }

    public Optional<BagSequencePosition> findBagSequencePosition(BagKey bagKey) {
        if (bagKey == null) {
            throw new IllegalArgumentException("bagKey must not be null");
        }
        return Optional.ofNullable(positionsByBagKey.get(bagKey));
    }

    public BagSequencePosition requireBagSequencePosition(BagKey bagKey) {
        return findBagSequencePosition(bagKey).orElseThrow(() -> new IllegalArgumentException(
                "Unknown bag key: " + bagKey));
    }

    private static void validateP2pToteLoadPlans(
            List<ToteLoadPlan> loadPlans,
            Map<String, PlannedPackSlot> slotsByReservedPhysicalPackId,
            Map<String, PlannedPackTrace> tracesByPhysicalPackId) {
        Set<String> p2pPackIds = new LinkedHashSet<>();
        for (ToteLoadPlan loadPlan : loadPlans) {
            for (PackPlan packPlan : loadPlan.getPackPlans()) {
                if (!p2pPackIds.add(packPlan.packId())) {
                    throw new IllegalArgumentException(
                            "Duplicate P2P physical pack ID: " + packPlan.packId());
                }
                PlannedPackSlot slot = slotsByReservedPhysicalPackId.get(packPlan.packId());
                PlannedPackTrace trace = tracesByPhysicalPackId.get(packPlan.packId());
                if (slot == null || trace == null) {
                    throw new IllegalArgumentException(
                            "P2P load plan contains an untraced physical pack: "
                                    + packPlan.packId());
                }
                if (!slot.bagKey().correlationId().equals(packPlan.correlationId())
                        || !slot.dimensions().equals(packPlan.dimensions())
                        || !trace.inputPhysicalToteId().equals(loadPlan.physicalToteId())) {
                    throw new IllegalArgumentException(
                            "P2P load plan pack does not match planned slot: " + packPlan.packId());
                }
            }
        }
    }

    private static int countP2pPacks(List<ToteLoadPlan> loadPlans) {
        int count = 0;
        for (ToteLoadPlan loadPlan : loadPlans) {
            count += loadPlan.getPackPlans().size();
        }
        return count;
    }

    private static <T> List<T> copyAndRejectNull(List<T> values, String fieldName) {
        if (values == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        for (T value : values) {
            if (value == null) {
                throw new IllegalArgumentException(fieldName + " must not contain null");
            }
        }
        return List.copyOf(values);
    }

    private static <K, V> Map<K, V> immutableMap(Map<K, V> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static String requireTrimmedValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BagPlanningResult that)) {
            return false;
        }
        return plannedBags.equals(that.plannedBags)
                && p2pToteLoadPlans.equals(that.p2pToteLoadPlans)
                && packTraces.equals(that.packTraces)
                && plannedPackSlots.equals(that.plannedPackSlots)
                && bagSequencePositions.equals(that.bagSequencePositions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                plannedBags,
                p2pToteLoadPlans,
                packTraces,
                plannedPackSlots,
                bagSequencePositions);
    }

    @Override
    public String toString() {
        return "BagPlanningResult[plannedBags=" + plannedBags
                + ", p2pToteLoadPlans=" + p2pToteLoadPlans
                + ", packTraces=" + packTraces
                + ", plannedPackSlots=" + plannedPackSlots
                + ", bagSequencePositions=" + bagSequencePositions + "]";
    }
}
