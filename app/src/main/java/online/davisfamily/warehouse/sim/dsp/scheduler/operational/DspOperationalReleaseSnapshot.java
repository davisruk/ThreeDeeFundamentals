package online.davisfamily.warehouse.sim.dsp.scheduler.operational;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionSnapshot;

public record DspOperationalReleaseSnapshot(
        List<DspOperationalReleaseCandidate> candidates,
        List<ServiceCentrePharmacyGroup> pharmacyGroups,
        Map<StationType, StationAdmissionSnapshot> stationAdmissions,
        Set<PreparedLineKey> preparedLineKeys,
        List<OperationalCandidateRouteAdmission> routeAdmissions) {

    public DspOperationalReleaseSnapshot(
            List<DspOperationalReleaseCandidate> candidates,
            List<ServiceCentrePharmacyGroup> pharmacyGroups,
            Map<StationType, StationAdmissionSnapshot> stationAdmissions,
            Set<PreparedLineKey> preparedLineKeys) {
        this(
                candidates,
                pharmacyGroups,
                stationAdmissions,
                preparedLineKeys,
                deriveCompatibilityRouteAdmissions(candidates, stationAdmissions));
    }

    public DspOperationalReleaseSnapshot {
        candidates = copyCandidates(candidates);
        pharmacyGroups = copyAndValidateGroups(pharmacyGroups);
        stationAdmissions = copyStationAdmissions(stationAdmissions);
        preparedLineKeys = copyPreparedLineKeys(preparedLineKeys);
        routeAdmissions = copyRouteAdmissions(routeAdmissions, candidates);
        validateCandidateGroups(candidates, pharmacyGroups);
    }

    public Optional<DspOperationalReleaseCandidate> findByPhysicalToteId(
            PhysicalToteId physicalToteId) {
        if (physicalToteId == null) {
            throw new IllegalArgumentException("physicalToteId must not be null");
        }
        return candidates.stream()
                .filter(candidate -> candidate.physicalCandidate().physicalToteId()
                        .equals(physicalToteId))
                .findFirst();
    }

    public List<ServiceCentrePharmacyGroup> groupsForServiceCentre(String serviceCentreId) {
        String normalizedServiceCentreId = requireTrimmed(serviceCentreId, "serviceCentreId");
        return pharmacyGroups.stream()
                .filter(group -> group.serviceCentreId().equals(normalizedServiceCentreId))
                .toList();
    }

    public Optional<OperationalCandidateRouteAdmission> findRouteAdmission(
            PhysicalToteId physicalToteId,
            StationType stationType) {
        if (physicalToteId == null) {
            throw new IllegalArgumentException("physicalToteId must not be null");
        }
        if (stationType == null) {
            throw new IllegalArgumentException("stationType must not be null");
        }
        return routeAdmissions.stream()
                .filter(admission -> admission.physicalToteId().equals(physicalToteId)
                        && admission.stationAdmission().stationType() == stationType)
                .findFirst();
    }

    public int groupIndexFor(DspOperationalReleaseCandidate candidate) {
        if (candidate == null) {
            throw new IllegalArgumentException("candidate must not be null");
        }
        DspOperationalReleaseCandidate storedCandidate = findByPhysicalToteId(
                candidate.physicalCandidate().physicalToteId())
                .filter(candidate::equals)
                .orElseThrow(() -> new IllegalArgumentException("candidate is not in this snapshot"));

        String serviceCentreId = storedCandidate.physicalCandidate().serviceCentreId();
        return storedCandidate.pharmacyIds().stream()
                .mapToInt(pharmacyId -> findGroup(serviceCentreId, pharmacyId).groupIndex())
                .min()
                .orElseThrow();
    }

    private static List<DspOperationalReleaseCandidate> copyCandidates(
            List<DspOperationalReleaseCandidate> candidates) {
        if (candidates == null) {
            throw new IllegalArgumentException("candidates must not be null");
        }
        List<DspOperationalReleaseCandidate> copy = new ArrayList<>(candidates.size());
        Set<PhysicalToteId> physicalToteIds = new LinkedHashSet<>();
        for (DspOperationalReleaseCandidate candidate : candidates) {
            if (candidate == null) {
                throw new IllegalArgumentException("candidates must not contain null elements");
            }
            if (!physicalToteIds.add(candidate.physicalCandidate().physicalToteId())) {
                throw new IllegalArgumentException("candidate physical tote IDs must be distinct");
            }
            copy.add(candidate);
        }
        return List.copyOf(copy);
    }

    private static List<ServiceCentrePharmacyGroup> copyAndValidateGroups(
            List<ServiceCentrePharmacyGroup> pharmacyGroups) {
        if (pharmacyGroups == null) {
            throw new IllegalArgumentException("pharmacyGroups must not be null");
        }
        List<ServiceCentrePharmacyGroup> copy = new ArrayList<>(pharmacyGroups.size());
        Map<String, Set<Integer>> indicesByServiceCentre = new LinkedHashMap<>();
        Map<String, Set<String>> pharmacyIdsByServiceCentre = new LinkedHashMap<>();
        for (ServiceCentrePharmacyGroup group : pharmacyGroups) {
            if (group == null) {
                throw new IllegalArgumentException("pharmacyGroups must not contain null elements");
            }
            Set<String> pharmacyIds = pharmacyIdsByServiceCentre.computeIfAbsent(
                    group.serviceCentreId(), ignored -> new LinkedHashSet<>());
            if (!pharmacyIds.add(group.pharmacyId())) {
                throw new IllegalArgumentException(
                        "service-centre/pharmacy groups must be unique");
            }
            Set<Integer> indices = indicesByServiceCentre.computeIfAbsent(
                    group.serviceCentreId(), ignored -> new LinkedHashSet<>());
            if (!indices.add(group.groupIndex())) {
                throw new IllegalArgumentException(
                        "group indices must be unique within a service centre");
            }
            copy.add(group);
        }
        for (Map.Entry<String, Set<Integer>> entry : indicesByServiceCentre.entrySet()) {
            for (int expectedIndex = 0; expectedIndex < entry.getValue().size(); expectedIndex++) {
                if (!entry.getValue().contains(expectedIndex)) {
                    throw new IllegalArgumentException(
                            "group indices must be contiguous from zero for service centre "
                                    + entry.getKey());
                }
            }
        }
        return List.copyOf(copy);
    }

    private static Map<StationType, StationAdmissionSnapshot> copyStationAdmissions(
            Map<StationType, StationAdmissionSnapshot> stationAdmissions) {
        if (stationAdmissions == null) {
            throw new IllegalArgumentException("stationAdmissions must not be null");
        }
        Map<StationType, StationAdmissionSnapshot> copy = new LinkedHashMap<>();
        for (Map.Entry<StationType, StationAdmissionSnapshot> entry : stationAdmissions.entrySet()) {
            StationType stationType = entry.getKey();
            StationAdmissionSnapshot admission = entry.getValue();
            if (stationType == null || admission == null) {
                throw new IllegalArgumentException(
                        "stationAdmissions must not contain null keys or values");
            }
            if (admission.stationType() != stationType) {
                throw new IllegalArgumentException("station admission key must match station type");
            }
            copy.put(stationType, admission);
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Set<PreparedLineKey> copyPreparedLineKeys(Set<PreparedLineKey> preparedLineKeys) {
        if (preparedLineKeys == null) {
            throw new IllegalArgumentException("preparedLineKeys must not be null");
        }
        Set<PreparedLineKey> copy = new LinkedHashSet<>();
        for (PreparedLineKey preparedLineKey : preparedLineKeys) {
            if (preparedLineKey == null) {
                throw new IllegalArgumentException(
                        "preparedLineKeys must not contain null elements");
            }
            copy.add(preparedLineKey);
        }
        return Collections.unmodifiableSet(copy);
    }

    private static List<OperationalCandidateRouteAdmission> copyRouteAdmissions(
            List<OperationalCandidateRouteAdmission> routeAdmissions,
            List<DspOperationalReleaseCandidate> candidates) {
        if (routeAdmissions == null) {
            throw new IllegalArgumentException("routeAdmissions must not be null");
        }
        Map<PhysicalToteId, DspOperationalReleaseCandidate> candidatesByPhysicalToteId =
                new LinkedHashMap<>();
        for (DspOperationalReleaseCandidate candidate : candidates) {
            candidatesByPhysicalToteId.put(
                    candidate.physicalCandidate().physicalToteId(), candidate);
        }

        List<OperationalCandidateRouteAdmission> copy = new ArrayList<>();
        Set<PhysicalToteId> admittedPhysicalToteIds = new LinkedHashSet<>();
        OperationalRouteEntrySelector routeEntrySelector = new OperationalRouteEntrySelector();
        for (OperationalCandidateRouteAdmission routeAdmission : routeAdmissions) {
            if (routeAdmission == null) {
                throw new IllegalArgumentException("routeAdmissions must not contain null");
            }
            PhysicalToteId physicalToteId = routeAdmission.physicalToteId();
            if (!admittedPhysicalToteIds.add(physicalToteId)) {
                throw new IllegalArgumentException(
                        "routeAdmissions must have distinct physical tote IDs");
            }
            DspOperationalReleaseCandidate candidate = candidatesByPhysicalToteId.get(
                    physicalToteId);
            if (candidate == null) {
                throw new IllegalArgumentException(
                        "Route admission physical tote is not a snapshot candidate: "
                                + physicalToteId.value());
            }
            StationType expectedStationType = routeEntrySelector.firstStation(
                    candidate.logicalOrderState().routeRequirements())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Candidate with route admission has no route-entry station"));
            if (routeAdmission.stationAdmission().stationType() != expectedStationType) {
                throw new IllegalArgumentException(
                        "Route admission station must match candidate first route-entry station");
            }
            copy.add(routeAdmission);
        }
        return List.copyOf(copy);
    }

    private static List<OperationalCandidateRouteAdmission> deriveCompatibilityRouteAdmissions(
            List<DspOperationalReleaseCandidate> candidates,
            Map<StationType, StationAdmissionSnapshot> stationAdmissions) {
        if (candidates == null || stationAdmissions == null) {
            return List.of();
        }
        OperationalRouteEntrySelector selector = new OperationalRouteEntrySelector();
        List<OperationalCandidateRouteAdmission> admissions = new ArrayList<>();
        for (DspOperationalReleaseCandidate candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            selector.firstStation(candidate.logicalOrderState().routeRequirements())
                    .map(stationAdmissions::get)
                    .filter(admission -> !admission.canAccept()
                            || admission.selectedTargetId().isPresent())
                    .map(admission -> new OperationalCandidateRouteAdmission(
                            candidate.physicalCandidate().physicalToteId(), admission))
                    .ifPresent(admissions::add);
        }
        return List.copyOf(admissions);
    }

    private static void validateCandidateGroups(
            List<DspOperationalReleaseCandidate> candidates,
            List<ServiceCentrePharmacyGroup> pharmacyGroups) {
        for (DspOperationalReleaseCandidate candidate : candidates) {
            String serviceCentreId = candidate.physicalCandidate().serviceCentreId();
            for (String pharmacyId : candidate.pharmacyIds()) {
                boolean groupExists = pharmacyGroups.stream().anyMatch(group ->
                        group.serviceCentreId().equals(serviceCentreId)
                                && group.pharmacyId().equals(pharmacyId));
                if (!groupExists) {
                    throw new IllegalArgumentException(
                            "No pharmacy group configured for " + serviceCentreId + "/" + pharmacyId);
                }
            }
        }
    }

    private ServiceCentrePharmacyGroup findGroup(String serviceCentreId, String pharmacyId) {
        return pharmacyGroups.stream()
                .filter(group -> group.serviceCentreId().equals(serviceCentreId)
                        && group.pharmacyId().equals(pharmacyId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Missing validated pharmacy group for " + serviceCentreId + "/" + pharmacyId));
    }

    private static String requireTrimmed(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
