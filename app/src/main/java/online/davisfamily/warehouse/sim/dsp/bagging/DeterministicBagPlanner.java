package online.davisfamily.warehouse.sim.dsp.bagging;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

/** Assigns the complete ordered logical demand to immutable bags and slots. */
public final class DeterministicBagPlanner {
    private final BagCapacityPolicy capacityPolicy;

    public DeterministicBagPlanner(BagCapacityPolicy capacityPolicy) {
        if (capacityPolicy == null) {
            throw new IllegalArgumentException("capacityPolicy must not be null");
        }
        this.capacityPolicy = capacityPolicy;
    }

    public BagPlanningResult plan(BagPlanningRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }

        Map<String, PhysicalPackObservation> physicalPacks = indexPhysicalPacks(request);
        Map<String, BagPackDemand> demandsByReservedPackId = indexAndValidateDemand(
                request.packDemands(),
                physicalPacks);
        List<PrescriptionGroup> groups = groupDemands(request.packDemands());

        List<PlannedBag> plannedBags = new ArrayList<>();
        List<BagSequencePosition> bagSequencePositions = new ArrayList<>();
        List<PlannedPackSlot> plannedPackSlots = new ArrayList<>();
        List<PlannedPackTrace> packTraces = new ArrayList<>();
        Map<String, BagKey> bagKeysByPackId = new LinkedHashMap<>();

        for (PrescriptionGroup group : groups) {
            List<List<BagPackDemand>> demandBags = partition(group.demands());
            int totalBagCount = demandBags.size();
            for (int index = 0; index < demandBags.size(); index++) {
                int bagOrdinal = index + 1;
                BagKey bagKey = new BagKey(group.prescriptionId(), bagOrdinal);
                List<BagPackDemand> bagDemands = demandBags.get(index);
                List<String> reservedPackIds = new ArrayList<>();
                LinkedHashSet<OrderSheetKey> owningSheets = new LinkedHashSet<>();
                for (BagPackDemand demand : bagDemands) {
                    reservedPackIds.add(demand.reservedPhysicalPackId());
                    owningSheets.add(demand.fulfilmentOrderSheetKey());
                }

                plannedBags.add(new PlannedBag(
                        bagKey,
                        group.serviceCentreId(),
                        group.pharmacyId(),
                        group.patientId(),
                        group.prescriptionId(),
                        reservedPackIds,
                        List.copyOf(owningSheets)));
                bagSequencePositions.add(new BagSequencePosition(bagOrdinal, totalBagCount));

                for (BagPackDemand demand : bagDemands) {
                    PlannedPackSlot slot = new PlannedPackSlot(demand, bagKey);
                    plannedPackSlots.add(slot);
                    if (bagKeysByPackId.put(slot.reservedPhysicalPackId(), bagKey) != null) {
                        throw new IllegalStateException(
                                "Duplicate reserved physical pack ID during bag planning: "
                                        + slot.reservedPhysicalPackId());
                    }
                    demand.initialPhysicalToteId().ifPresent(inputToteId -> packTraces.add(
                            new PlannedPackTrace(
                                    slot.reservedPhysicalPackId(),
                                    slot.sourceProvenance(),
                                    inputToteId,
                                    slot.fulfilmentOrderSheetKey(),
                                    bagKey)));
                }
            }
        }

        List<ToteLoadPlan> p2pToteLoadPlans = createP2pToteLoadPlans(
                request.planningTotes(),
                bagKeysByPackId,
                demandsByReservedPackId);
        return new BagPlanningResult(
                plannedBags,
                p2pToteLoadPlans,
                packTraces,
                plannedPackSlots,
                bagSequencePositions);
    }

    private Map<String, PhysicalPackObservation> indexPhysicalPacks(BagPlanningRequest request) {
        Map<String, PhysicalPackObservation> physicalPacks = new LinkedHashMap<>();
        for (BagPlanningTote planningTote : request.planningTotes()) {
            for (PackPlan packPlan : planningTote.toteLoadPlan().getPackPlans()) {
                PhysicalPackObservation previous = physicalPacks.putIfAbsent(
                        packPlan.packId(),
                        new PhysicalPackObservation(packPlan, planningTote));
                if (previous != null) {
                    throw new IllegalArgumentException(
                            "Duplicate physical pack ID: " + packPlan.packId());
                }
            }
        }
        return physicalPacks;
    }

    private static Map<String, BagPackDemand> indexAndValidateDemand(
            List<BagPackDemand> demands,
            Map<String, PhysicalPackObservation> physicalPacks) {
        Map<String, BagPackDemand> demandsByPackId = new LinkedHashMap<>();
        for (BagPackDemand demand : demands) {
            if (demandsByPackId.putIfAbsent(demand.reservedPhysicalPackId(), demand) != null) {
                throw new IllegalArgumentException(
                        "Duplicate reserved physical pack ID: " + demand.reservedPhysicalPackId());
            }
        }

        for (PhysicalPackObservation observation : physicalPacks.values()) {
            BagPackDemand demand = demandsByPackId.get(observation.packPlan().packId());
            if (demand == null) {
                throw new IllegalArgumentException(
                        "Physical pack has no planned demand: " + observation.packPlan().packId());
            }
            if (demand.initialPhysicalToteId().isEmpty()) {
                throw new IllegalArgumentException(
                        "Physical pack is assigned to a station-pending slot: "
                                + observation.packPlan().packId());
            }
            validatePhysicalObservation(demand, observation);
        }

        for (BagPackDemand demand : demands) {
            PhysicalPackObservation observation = physicalPacks.get(demand.reservedPhysicalPackId());
            if (demand.initialPhysicalToteId().isPresent()) {
                if (observation == null) {
                    throw new IllegalArgumentException(
                            "Planned physical pack is missing from its input tote: "
                                    + demand.reservedPhysicalPackId());
                }
                validatePhysicalObservation(demand, observation);
            } else if (observation != null) {
                throw new IllegalArgumentException(
                        "Station-pending slot has an existing physical pack: "
                                + demand.reservedPhysicalPackId());
            }
        }
        return demandsByPackId;
    }

    private static void validatePhysicalObservation(
            BagPackDemand demand,
            PhysicalPackObservation observation) {
        PhysicalToteId expectedToteId = demand.initialPhysicalToteId().orElseThrow();
        if (!expectedToteId.equals(observation.planningTote().toteLoadPlan().physicalToteId())) {
            throw new IllegalArgumentException(
                    "Physical pack is in the wrong input tote: " + demand.reservedPhysicalPackId());
        }
        if (!demand.fulfilmentOrderSheetKey().equals(
                observation.planningTote().fulfilmentOrderSheetKey())) {
            throw new IllegalArgumentException(
                    "Physical pack is in the wrong fulfilment sheet: "
                            + demand.reservedPhysicalPackId());
        }
        if (!demand.sourceProvenance().serviceCentreId().equals(
                observation.planningTote().serviceCentreId())) {
            throw new IllegalArgumentException(
                    "Physical pack is in the wrong service centre: "
                            + demand.reservedPhysicalPackId());
        }
        if (!demand.dimensions().equals(observation.packPlan().dimensions())) {
            throw new IllegalArgumentException(
                    "Physical pack dimensions do not match planned demand: "
                            + demand.reservedPhysicalPackId());
        }
    }

    private static List<PrescriptionGroup> groupDemands(List<BagPackDemand> demands) {
        Map<DemandGroupKey, PrescriptionGroup> groupsByIdentity = new LinkedHashMap<>();
        Map<String, DemandGroupKey> identitiesByPrescription = new LinkedHashMap<>();
        for (BagPackDemand demand : demands) {
            PackSourceProvenance provenance = demand.sourceProvenance();
            DemandGroupKey identity = new DemandGroupKey(
                    provenance.pharmacyId(),
                    provenance.patientId(),
                    provenance.prescriptionId(),
                    provenance.serviceCentreId());
            DemandGroupKey priorIdentity = identitiesByPrescription.putIfAbsent(
                    identity.prescriptionId(),
                    identity);
            if (priorIdentity != null && !priorIdentity.equals(identity)) {
                throw new IllegalStateException(
                        "Conflicting pharmacy, patient, or service centre for prescription "
                                + identity.prescriptionId());
            }
            groupsByIdentity.computeIfAbsent(identity, ignored -> new PrescriptionGroup(identity))
                    .add(demand);
        }
        return List.copyOf(groupsByIdentity.values());
    }

    private List<List<BagPackDemand>> partition(List<BagPackDemand> demands) {
        List<List<BagPackDemand>> bags = new ArrayList<>();
        List<BagPackDemand> current = new ArrayList<>();
        int currentPackCount = 0;
        for (BagPackDemand candidate : demands) {
            if (!capacityPolicy.canAdd(currentPackCount, candidate)) {
                if (current.isEmpty()) {
                    throw policyContractException(candidate);
                }
                bags.add(List.copyOf(current));
                current = new ArrayList<>();
                currentPackCount = 0;
                if (!capacityPolicy.canAdd(currentPackCount, candidate)) {
                    throw policyContractException(candidate);
                }
            }
            current.add(candidate);
            currentPackCount++;
        }
        if (!current.isEmpty()) {
            bags.add(List.copyOf(current));
        }
        return List.copyOf(bags);
    }

    private static IllegalStateException policyContractException(BagPackDemand candidate) {
        return new IllegalStateException(
                "Bag capacity policy rejected logical pack slot for an empty bag: "
                        + candidate.slotKey());
    }

    private static List<ToteLoadPlan> createP2pToteLoadPlans(
            List<BagPlanningTote> planningTotes,
            Map<String, BagKey> bagKeysByPackId,
            Map<String, BagPackDemand> demandsByReservedPackId) {
        List<ToteLoadPlan> p2pToteLoadPlans = new ArrayList<>();
        for (BagPlanningTote planningTote : planningTotes) {
            List<PackPlan> rewrittenPackPlans = new ArrayList<>();
            for (PackPlan packPlan : planningTote.toteLoadPlan().getPackPlans()) {
                BagKey bagKey = bagKeysByPackId.get(packPlan.packId());
                if (bagKey == null || !demandsByReservedPackId.containsKey(packPlan.packId())) {
                    throw new IllegalStateException(
                            "No planned bag found for physical pack: " + packPlan.packId());
                }
                rewrittenPackPlans.add(new PackPlan(
                        packPlan.packId(),
                        bagKey.correlationId(),
                        packPlan.dimensions()));
            }
            p2pToteLoadPlans.add(new ToteLoadPlan(
                    planningTote.toteLoadPlan().physicalToteId(),
                    rewrittenPackPlans));
        }
        return List.copyOf(p2pToteLoadPlans);
    }

    private record PhysicalPackObservation(PackPlan packPlan, BagPlanningTote planningTote) {
    }

    private record DemandGroupKey(
            String pharmacyId,
            String patientId,
            String prescriptionId,
            String serviceCentreId) {
    }

    private static final class PrescriptionGroup {
        private final DemandGroupKey identity;
        private final List<BagPackDemand> demands = new ArrayList<>();

        private PrescriptionGroup(DemandGroupKey identity) {
            this.identity = identity;
        }

        private void add(BagPackDemand demand) {
            demands.add(demand);
        }

        private List<BagPackDemand> demands() {
            return List.copyOf(demands);
        }

        private String pharmacyId() {
            return identity.pharmacyId();
        }

        private String patientId() {
            return identity.patientId();
        }

        private String prescriptionId() {
            return identity.prescriptionId();
        }

        private String serviceCentreId() {
            return identity.serviceCentreId();
        }
    }
}
