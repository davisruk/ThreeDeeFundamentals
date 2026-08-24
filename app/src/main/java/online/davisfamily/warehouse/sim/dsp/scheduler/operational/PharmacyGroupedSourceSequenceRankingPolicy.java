package online.davisfamily.warehouse.sim.dsp.scheduler.operational;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

public final class PharmacyGroupedSourceSequenceRankingPolicy
        implements OperationalCandidateRankingPolicy {

    @Override
    public List<OperationalReleaseSelection> rank(
            List<OperationalReleaseSelection> eligibleCandidates,
            DspOperationalReleaseSnapshot snapshot) {
        if (eligibleCandidates == null) {
            throw new IllegalArgumentException("eligibleCandidates must not be null");
        }
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }
        if (eligibleCandidates.isEmpty()) {
            return List.of();
        }

        List<OperationalReleaseSelection> validatedSelections = new ArrayList<>(
                eligibleCandidates.size());
        Set<PhysicalToteId> physicalToteIds = new LinkedHashSet<>();
        Map<String, Integer> priorityByServiceCentre = new LinkedHashMap<>();
        for (OperationalReleaseSelection selection : eligibleCandidates) {
            if (selection == null) {
                throw new IllegalArgumentException(
                        "eligibleCandidates must not contain null elements");
            }
            DspOperationalReleaseCandidate candidate = selection.candidate();
            PhysicalToteId physicalToteId = candidate.physicalCandidate().physicalToteId();
            if (!physicalToteIds.add(physicalToteId)) {
                throw new IllegalArgumentException(
                        "eligible candidate physical tote IDs must be distinct");
            }
            DspOperationalReleaseCandidate snapshotCandidate = snapshot
                    .findByPhysicalToteId(physicalToteId)
                    .filter(candidate::equals)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "eligible candidate is absent from the operational snapshot"));
            String serviceCentreId = snapshotCandidate.physicalCandidate().serviceCentreId();
            int orderPriority = snapshotCandidate.logicalOrderState().order().orderPriority();
            Integer existingPriority = priorityByServiceCentre.putIfAbsent(
                    serviceCentreId, orderPriority);
            if (existingPriority != null && existingPriority != orderPriority) {
                throw new IllegalArgumentException(
                        "Eligible candidates for service centre " + serviceCentreId
                                + " must have one consistent order priority");
            }
            validatedSelections.add(selection);
        }

        String selectedServiceCentreId = priorityByServiceCentre.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.<String, Integer>comparingByKey()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow();

        List<OperationalReleaseSelection> ranked = validatedSelections.stream()
                .filter(selection -> selection.candidate().physicalCandidate().serviceCentreId()
                        .equals(selectedServiceCentreId))
                .sorted(rankingComparator(snapshot))
                .toList();
        return List.copyOf(ranked);
    }

    private static Comparator<OperationalReleaseSelection> rankingComparator(
            DspOperationalReleaseSnapshot snapshot) {
        return Comparator
                .comparing((OperationalReleaseSelection selection) ->
                        !selection.activePharmacyAffinity())
                .thenComparingInt((OperationalReleaseSelection selection) ->
                        snapshot.groupIndexFor(selection.candidate()))
                .thenComparingLong(selection ->
                        selection.candidate().physicalCandidate().sourceSequenceNumber())
                .thenComparingInt(selection ->
                        selection.candidate().logicalOrderState().order().sheetNumber())
                .thenComparing(selection ->
                        selection.candidate().logicalOrderState().order().orderId())
                .thenComparing(selection ->
                        selection.candidate().physicalCandidate().physicalToteId().value());
    }
}
