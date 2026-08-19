package online.davisfamily.warehouse.sim.dsp.bagging;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;

public final class DeterministicBagPlanner {
    private final BagCapacityPolicy capacityPolicy;
    private final PackProvenanceSnapshot provenanceSnapshot;

    public DeterministicBagPlanner(
            BagCapacityPolicy capacityPolicy,
            PackProvenanceSnapshot provenanceSnapshot) {
        if (capacityPolicy == null) {
            throw new IllegalArgumentException("capacityPolicy must not be null");
        }
        if (provenanceSnapshot == null) {
            throw new IllegalArgumentException("provenanceSnapshot must not be null");
        }
        this.capacityPolicy = capacityPolicy;
        this.provenanceSnapshot = provenanceSnapshot;
    }

    public BagPlanningResult plan(BagPlanningRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }

        List<EncounteredPack> encounteredPacks = resolveProvenance(request);
        Map<String, PrescriptionGroup> groupsByPrescription = groupByPrescription(encounteredPacks);
        List<PlannedBag> plannedBags = new ArrayList<>();
        List<PlannedPackTrace> packTraces = new ArrayList<>();

        for (PrescriptionGroup group : groupsByPrescription.values()) {
            planPrescription(group, plannedBags, packTraces);
        }

        return new BagPlanningResult(plannedBags, List.of(), packTraces);
    }

    private List<EncounteredPack> resolveProvenance(BagPlanningRequest request) {
        List<EncounteredPack> encounteredPacks = new ArrayList<>();
        for (BagPlanningTote planningTote : request.planningTotes()) {
            for (PackPlan packPlan : planningTote.toteLoadPlan().getPackPlans()) {
                PackSourceProvenance provenance = provenanceSnapshot.find(packPlan.packId())
                        .orElseThrow(() -> new IllegalStateException(
                                "Missing source provenance for physical pack: " + packPlan.packId()));
                encounteredPacks.add(new EncounteredPack(planningTote, packPlan, provenance));
            }
        }
        return List.copyOf(encounteredPacks);
    }

    private Map<String, PrescriptionGroup> groupByPrescription(List<EncounteredPack> encounteredPacks) {
        Map<String, PrescriptionGroup> groupsByPrescription = new LinkedHashMap<>();
        for (EncounteredPack encounteredPack : encounteredPacks) {
            String prescriptionId = encounteredPack.provenance().prescriptionId();
            PrescriptionGroup group = groupsByPrescription.computeIfAbsent(
                    prescriptionId,
                    ignored -> new PrescriptionGroup(encounteredPack));
            group.add(encounteredPack);
        }
        return groupsByPrescription;
    }

    private void planPrescription(
            PrescriptionGroup group,
            List<PlannedBag> plannedBags,
            List<PlannedPackTrace> packTraces) {
        int bagOrdinal = 1;
        List<EncounteredPack> currentBagPacks = new ArrayList<>();

        for (EncounteredPack candidate : group.packs()) {
            if (!capacityPolicy.canAdd(currentPackPlans(currentBagPacks), candidate.packPlan())) {
                if (currentBagPacks.isEmpty()) {
                    throw policyContractException(candidate);
                }
                addPlannedBag(group, bagOrdinal++, currentBagPacks, plannedBags, packTraces);
                currentBagPacks = new ArrayList<>();
                if (!capacityPolicy.canAdd(List.of(), candidate.packPlan())) {
                    throw policyContractException(candidate);
                }
            }
            currentBagPacks.add(candidate);
        }

        if (!currentBagPacks.isEmpty()) {
            addPlannedBag(group, bagOrdinal, currentBagPacks, plannedBags, packTraces);
        }
    }

    private static List<PackPlan> currentPackPlans(List<EncounteredPack> currentBagPacks) {
        return currentBagPacks.stream().map(EncounteredPack::packPlan).toList();
    }

    private static IllegalStateException policyContractException(EncounteredPack candidate) {
        return new IllegalStateException(
                "Bag capacity policy rejected physical pack for an empty bag: "
                        + candidate.packPlan().packId());
    }

    private static void addPlannedBag(
            PrescriptionGroup group,
            int bagOrdinal,
            List<EncounteredPack> bagPacks,
            List<PlannedBag> plannedBags,
            List<PlannedPackTrace> packTraces) {
        BagKey bagKey = new BagKey(group.prescriptionId(), bagOrdinal);
        plannedBags.add(new PlannedBag(
                bagKey,
                group.serviceCentreId(),
                group.pharmacyId(),
                group.patientId(),
                group.prescriptionId(),
                bagPacks.stream().map(pack -> pack.packPlan().packId()).toList(),
                bagPacks.stream().map(pack -> pack.planningTote().fulfilmentOrderSheetKey()).toList()));

        for (EncounteredPack pack : bagPacks) {
            packTraces.add(new PlannedPackTrace(
                    pack.packPlan().packId(),
                    pack.provenance(),
                    pack.planningTote().toteLoadPlan().physicalToteId(),
                    pack.planningTote().fulfilmentOrderSheetKey(),
                    bagKey));
        }
    }

    private record EncounteredPack(
            BagPlanningTote planningTote,
            PackPlan packPlan,
            PackSourceProvenance provenance) {
    }

    private static final class PrescriptionGroup {
        private final String prescriptionId;
        private final String patientId;
        private final String pharmacyId;
        private final String serviceCentreId;
        private final List<EncounteredPack> packs = new ArrayList<>();

        private PrescriptionGroup(EncounteredPack firstPack) {
            PackSourceProvenance provenance = firstPack.provenance();
            prescriptionId = provenance.prescriptionId();
            patientId = provenance.patientId();
            pharmacyId = provenance.pharmacyId();
            serviceCentreId = provenance.serviceCentreId();
        }

        private void add(EncounteredPack pack) {
            PackSourceProvenance provenance = pack.provenance();
            requireMatching("patient", patientId, provenance.patientId());
            requireMatching("pharmacy", pharmacyId, provenance.pharmacyId());
            requireMatching("service centre", serviceCentreId, provenance.serviceCentreId());
            requireMatching("planning tote service centre", serviceCentreId, pack.planningTote().serviceCentreId());
            packs.add(pack);
        }

        private void requireMatching(String fieldName, String expected, String actual) {
            if (!expected.equals(actual)) {
                throw new IllegalStateException(
                        "Conflicting " + fieldName + " for prescription " + prescriptionId
                                + ": " + expected + " vs " + actual);
            }
        }

        private String prescriptionId() {
            return prescriptionId;
        }

        private String patientId() {
            return patientId;
        }

        private String pharmacyId() {
            return pharmacyId;
        }

        private String serviceCentreId() {
            return serviceCentreId;
        }

        private List<EncounteredPack> packs() {
            return List.copyOf(packs);
        }
    }
}
