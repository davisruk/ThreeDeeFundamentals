package online.davisfamily.warehouse.sim.dsp.scheduler.operational;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.p2p.allocation.P2pElasticAllocationSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineLeaseCatalogSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionSnapshot;

public final class DspOperationalReleaseSnapshot {
    private final CandidateState candidateState;
    private final List<DspOperationalReleaseCandidate> candidates;
    private final List<ServiceCentrePharmacyGroup> pharmacyGroups;
    private final Map<StationType, StationAdmissionSnapshot> stationAdmissions;
    private final Set<PreparedLineKey> preparedLineKeys;
    private final List<OperationalCandidateRouteAdmission> routeAdmissions;
    private final P2pLineLeaseCatalogSnapshot p2pLineLeases;
    private final Map<OperationalRouteDestination, Boolean> p2pRouteAdmissions;
    private final Optional<P2pElasticAllocationSnapshot> elasticP2pAllocation;
    private final Map<PhysicalToteId, DspOperationalReleaseCandidate>
            candidatesByPhysicalToteId;
    private final Map<PhysicalToteId, Integer> groupIndexByPhysicalToteId;

    public DspOperationalReleaseSnapshot(
            List<DspOperationalReleaseCandidate> candidates,
            List<ServiceCentrePharmacyGroup> pharmacyGroups,
            Map<StationType, StationAdmissionSnapshot> stationAdmissions,
            Set<PreparedLineKey> preparedLineKeys) {
        this(
                validatedCandidateState(candidates, pharmacyGroups),
                stationAdmissions,
                preparedLineKeys,
                deriveCompatibilityRouteAdmissions(candidates, stationAdmissions),
                new P2pLineLeaseCatalogSnapshot(List.of()),
                Map.of(),
                Optional.empty());
    }

    public DspOperationalReleaseSnapshot(
            List<DspOperationalReleaseCandidate> candidates,
            List<ServiceCentrePharmacyGroup> pharmacyGroups,
            Map<StationType, StationAdmissionSnapshot> stationAdmissions,
            Set<PreparedLineKey> preparedLineKeys,
            List<OperationalCandidateRouteAdmission> routeAdmissions) {
        this(
                validatedCandidateState(candidates, pharmacyGroups),
                stationAdmissions,
                preparedLineKeys,
                routeAdmissions,
                new P2pLineLeaseCatalogSnapshot(List.of()),
                Map.of(),
                Optional.empty());
    }

    public DspOperationalReleaseSnapshot(
            List<DspOperationalReleaseCandidate> candidates,
            List<ServiceCentrePharmacyGroup> pharmacyGroups,
            Map<StationType, StationAdmissionSnapshot> stationAdmissions,
            Set<PreparedLineKey> preparedLineKeys,
            List<OperationalCandidateRouteAdmission> routeAdmissions,
            P2pLineLeaseCatalogSnapshot p2pLineLeases,
            Map<OperationalRouteDestination, Boolean> p2pRouteAdmissions) {
        this(
                validatedCandidateState(candidates, pharmacyGroups),
                stationAdmissions,
                preparedLineKeys,
                routeAdmissions,
                p2pLineLeases,
                p2pRouteAdmissions,
                Optional.empty());
    }

    public DspOperationalReleaseSnapshot(
            List<DspOperationalReleaseCandidate> candidates,
            List<ServiceCentrePharmacyGroup> pharmacyGroups,
            Map<StationType, StationAdmissionSnapshot> stationAdmissions,
            Set<PreparedLineKey> preparedLineKeys,
            List<OperationalCandidateRouteAdmission> routeAdmissions,
            P2pLineLeaseCatalogSnapshot p2pLineLeases,
            Map<OperationalRouteDestination, Boolean> p2pRouteAdmissions,
            Optional<P2pElasticAllocationSnapshot> elasticP2pAllocation) {
        this(
                validatedCandidateState(candidates, pharmacyGroups),
                stationAdmissions,
                preparedLineKeys,
                routeAdmissions,
                p2pLineLeases,
                p2pRouteAdmissions,
                elasticP2pAllocation);
    }

    private DspOperationalReleaseSnapshot(
            CandidateState candidateState,
            Map<StationType, StationAdmissionSnapshot> stationAdmissions,
            Set<PreparedLineKey> preparedLineKeys,
            List<OperationalCandidateRouteAdmission> routeAdmissions,
            P2pLineLeaseCatalogSnapshot p2pLineLeases,
            Map<OperationalRouteDestination, Boolean> p2pRouteAdmissions,
            Optional<P2pElasticAllocationSnapshot> elasticP2pAllocation) {
        if (candidateState == null) {
            throw new IllegalArgumentException("candidateState must not be null");
        }
        this.candidateState = candidateState;
        this.candidates = candidateState.candidates();
        this.candidatesByPhysicalToteId = candidateState.candidatesByPhysicalToteId();
        this.pharmacyGroups = candidateState.pharmacyGroups();
        this.stationAdmissions = copyStationAdmissions(stationAdmissions);
        this.preparedLineKeys = copyPreparedLineKeys(preparedLineKeys);
        this.routeAdmissions = copyRouteAdmissions(
                routeAdmissions, this.candidatesByPhysicalToteId);
        if (p2pLineLeases == null) {
            throw new IllegalArgumentException("p2pLineLeases must not be null");
        }
        this.p2pLineLeases = p2pLineLeases;
        this.p2pRouteAdmissions = copyP2pRouteAdmissions(
                p2pRouteAdmissions, p2pLineLeases);
        if (elasticP2pAllocation == null) {
            throw new IllegalArgumentException("elasticP2pAllocation must not be null");
        }
        this.elasticP2pAllocation = elasticP2pAllocation;
        validateElasticAllocation(this.candidates, p2pLineLeases, elasticP2pAllocation);
        this.groupIndexByPhysicalToteId = candidateState.groupIndexByPhysicalToteId();
    }

    static CandidateState validatedCandidateState(
            List<DspOperationalReleaseCandidate> candidates,
            List<ServiceCentrePharmacyGroup> pharmacyGroups) {
        CandidateCopies candidateCopies = copyCandidates(candidates);
        GroupCopies groupCopies = copyAndValidateGroups(pharmacyGroups);
        Map<PhysicalToteId, Integer> groupIndexes = validateCandidateGroups(
                candidateCopies.candidates(), groupCopies.byServiceCentreAndPharmacy());
        return new CandidateState(
                candidateCopies.candidates(),
                candidateCopies.byPhysicalToteId(),
                groupCopies.pharmacyGroups(),
                groupCopies.byServiceCentreAndPharmacy(),
                groupIndexes);
    }

    static DspOperationalReleaseSnapshot fromValidatedCandidateState(
            CandidateState candidateState,
            Map<StationType, StationAdmissionSnapshot> stationAdmissions,
            Set<PreparedLineKey> preparedLineKeys,
            List<OperationalCandidateRouteAdmission> routeAdmissions,
            P2pLineLeaseCatalogSnapshot p2pLineLeases,
            Map<OperationalRouteDestination, Boolean> p2pRouteAdmissions,
            Optional<P2pElasticAllocationSnapshot> elasticP2pAllocation) {
        return new DspOperationalReleaseSnapshot(
                candidateState,
                stationAdmissions,
                preparedLineKeys,
                routeAdmissions,
                p2pLineLeases,
                p2pRouteAdmissions,
                elasticP2pAllocation);
    }

    public List<DspOperationalReleaseCandidate> candidates() {
        return candidates;
    }

    public List<ServiceCentrePharmacyGroup> pharmacyGroups() {
        return pharmacyGroups;
    }

    public Map<StationType, StationAdmissionSnapshot> stationAdmissions() {
        return stationAdmissions;
    }

    public Set<PreparedLineKey> preparedLineKeys() {
        return preparedLineKeys;
    }

    public List<OperationalCandidateRouteAdmission> routeAdmissions() {
        return routeAdmissions;
    }

    public P2pLineLeaseCatalogSnapshot p2pLineLeases() {
        return p2pLineLeases;
    }

    public Map<OperationalRouteDestination, Boolean> p2pRouteAdmissions() {
        return p2pRouteAdmissions;
    }

    public Optional<P2pElasticAllocationSnapshot> elasticP2pAllocation() {
        return elasticP2pAllocation;
    }

    public Optional<DspOperationalReleaseCandidate> findByPhysicalToteId(
            PhysicalToteId physicalToteId) {
        if (physicalToteId == null) {
            throw new IllegalArgumentException("physicalToteId must not be null");
        }
        return Optional.ofNullable(candidatesByPhysicalToteId.get(physicalToteId));
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
        DspOperationalReleaseCandidate storedCandidate = candidatesByPhysicalToteId.get(
                candidate.physicalCandidate().physicalToteId());
        if (storedCandidate == null || !candidate.equals(storedCandidate)) {
            throw new IllegalArgumentException("candidate is not in this snapshot");
        }
        return groupIndexByPhysicalToteId.get(storedCandidate.physicalCandidate().physicalToteId());
    }

    public boolean stickyP2pAllocationEnabled() {
        return !p2pLineLeases.lines().isEmpty();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DspOperationalReleaseSnapshot that)) {
            return false;
        }
        return candidates.equals(that.candidates)
                && pharmacyGroups.equals(that.pharmacyGroups)
                && stationAdmissions.equals(that.stationAdmissions)
                && preparedLineKeys.equals(that.preparedLineKeys)
                && routeAdmissions.equals(that.routeAdmissions)
                && p2pLineLeases.equals(that.p2pLineLeases)
                && p2pRouteAdmissions.equals(that.p2pRouteAdmissions)
                && elasticP2pAllocation.equals(that.elasticP2pAllocation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                candidates,
                pharmacyGroups,
                stationAdmissions,
                preparedLineKeys,
                routeAdmissions,
                p2pLineLeases,
                p2pRouteAdmissions,
                elasticP2pAllocation);
    }

    @Override
    public String toString() {
        return "DspOperationalReleaseSnapshot[candidates=" + candidates
                + ", pharmacyGroups=" + pharmacyGroups
                + ", stationAdmissions=" + stationAdmissions
                + ", preparedLineKeys=" + preparedLineKeys
                + ", routeAdmissions=" + routeAdmissions
                + ", p2pLineLeases=" + p2pLineLeases
                + ", p2pRouteAdmissions=" + p2pRouteAdmissions
                + ", elasticP2pAllocation=" + elasticP2pAllocation + "]";
    }

    private static void validateElasticAllocation(
            List<DspOperationalReleaseCandidate> candidates,
            P2pLineLeaseCatalogSnapshot lineLeases,
            Optional<P2pElasticAllocationSnapshot> elasticAllocation) {
        elasticAllocation.ifPresent(allocation -> {
            List<P2pLineId> lineIds = lineLeases.lines().stream()
                    .map(line -> line.definition().lineId())
                    .toList();
            if (!allocation.configuredLineIds().equals(lineIds)) {
                throw new IllegalArgumentException(
                        "elastic allocation lines must match operational P2P leases");
            }
            for (DspOperationalReleaseCandidate candidate : candidates) {
                if (!candidate.logicalOrderState().routeRequirements().requiresP2p()) {
                    continue;
                }
                String serviceCentreId = candidate.physicalCandidate().serviceCentreId();
                boolean hasDemand = allocation.find(serviceCentreId).isPresent();
                boolean hasExclusion = allocation.issues().stream()
                        .anyMatch(issue -> issue.serviceCentreId().equals(serviceCentreId));
                if (!hasDemand && !hasExclusion) {
                    throw new IllegalArgumentException(
                            "P2P candidate has no elastic demand or exclusion for service centre "
                                    + serviceCentreId);
                }
            }
        });
    }

    private static CandidateCopies copyCandidates(
            List<DspOperationalReleaseCandidate> candidates) {
        if (candidates == null) {
            throw new IllegalArgumentException("candidates must not be null");
        }
        List<DspOperationalReleaseCandidate> copy = new ArrayList<>(candidates.size());
        Map<PhysicalToteId, DspOperationalReleaseCandidate> byPhysicalToteId =
                new LinkedHashMap<>();
        for (DspOperationalReleaseCandidate candidate : candidates) {
            if (candidate == null) {
                throw new IllegalArgumentException("candidates must not contain null elements");
            }
            if (byPhysicalToteId.putIfAbsent(
                    candidate.physicalCandidate().physicalToteId(), candidate) != null) {
                throw new IllegalArgumentException("candidate physical tote IDs must be distinct");
            }
            copy.add(candidate);
        }
        return new CandidateCopies(
                List.copyOf(copy), Collections.unmodifiableMap(byPhysicalToteId));
    }

    private static GroupCopies copyAndValidateGroups(
            List<ServiceCentrePharmacyGroup> pharmacyGroups) {
        if (pharmacyGroups == null) {
            throw new IllegalArgumentException("pharmacyGroups must not be null");
        }
        List<ServiceCentrePharmacyGroup> copy = new ArrayList<>(pharmacyGroups.size());
        Map<String, Set<Integer>> indicesByServiceCentre = new LinkedHashMap<>();
        Map<String, Set<String>> pharmacyIdsByServiceCentre = new LinkedHashMap<>();
        Map<String, Map<String, ServiceCentrePharmacyGroup>> byServiceCentreAndPharmacy =
                new LinkedHashMap<>();
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
            byServiceCentreAndPharmacy
                    .computeIfAbsent(group.serviceCentreId(), ignored -> new LinkedHashMap<>())
                    .put(group.pharmacyId(), group);
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
        Map<String, Map<String, ServiceCentrePharmacyGroup>> frozenLookup = new LinkedHashMap<>();
        byServiceCentreAndPharmacy.forEach((serviceCentreId, groupsByPharmacy) ->
                frozenLookup.put(serviceCentreId, Collections.unmodifiableMap(
                        new LinkedHashMap<>(groupsByPharmacy))));
        return new GroupCopies(
                List.copyOf(copy), Collections.unmodifiableMap(frozenLookup));
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

    private static Map<OperationalRouteDestination, Boolean> copyP2pRouteAdmissions(
            Map<OperationalRouteDestination, Boolean> routeAdmissions,
            P2pLineLeaseCatalogSnapshot lineLeases) {
        if (routeAdmissions == null) {
            throw new IllegalArgumentException("p2pRouteAdmissions must not be null");
        }
        Map<OperationalRouteDestination, Boolean> copy = new LinkedHashMap<>();
        routeAdmissions.forEach((destination, admissionOpen) -> {
            if (destination == null || admissionOpen == null) {
                throw new IllegalArgumentException(
                        "p2pRouteAdmissions must not contain null keys or values");
            }
            if (destination.stationType() != StationType.P2P) {
                throw new IllegalArgumentException(
                        "p2pRouteAdmissions must contain only P2P destinations");
            }
            copy.put(destination, admissionOpen);
        });
        Set<OperationalRouteDestination> configuredDestinations = lineLeases.lines().stream()
                .map(line -> line.definition().destination())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (!copy.keySet().equals(configuredDestinations)) {
            throw new IllegalArgumentException(
                    "p2pRouteAdmissions must contain exactly every configured P2P destination");
        }
        return Collections.unmodifiableMap(copy);
    }

    private static List<OperationalCandidateRouteAdmission> copyRouteAdmissions(
            List<OperationalCandidateRouteAdmission> routeAdmissions,
            Map<PhysicalToteId, DspOperationalReleaseCandidate> candidatesByPhysicalToteId) {
        if (routeAdmissions == null) {
            throw new IllegalArgumentException("routeAdmissions must not be null");
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

    static final class CandidateState {
        private final List<DspOperationalReleaseCandidate> candidates;
        private final Map<PhysicalToteId, DspOperationalReleaseCandidate>
                candidatesByPhysicalToteId;
        private final List<ServiceCentrePharmacyGroup> pharmacyGroups;
        private final Map<String, Map<String, ServiceCentrePharmacyGroup>>
                byServiceCentreAndPharmacy;
        private final Map<PhysicalToteId, Integer> groupIndexByPhysicalToteId;

        private CandidateState(
                List<DspOperationalReleaseCandidate> candidates,
                Map<PhysicalToteId, DspOperationalReleaseCandidate> candidatesByPhysicalToteId,
                List<ServiceCentrePharmacyGroup> pharmacyGroups,
                Map<String, Map<String, ServiceCentrePharmacyGroup>>
                        byServiceCentreAndPharmacy,
                Map<PhysicalToteId, Integer> groupIndexByPhysicalToteId) {
            this.candidates = candidates;
            this.candidatesByPhysicalToteId = candidatesByPhysicalToteId;
            this.pharmacyGroups = pharmacyGroups;
            this.byServiceCentreAndPharmacy = byServiceCentreAndPharmacy;
            this.groupIndexByPhysicalToteId = groupIndexByPhysicalToteId;
        }

        List<DspOperationalReleaseCandidate> candidates() {
            return candidates;
        }

        Map<PhysicalToteId, DspOperationalReleaseCandidate> candidatesByPhysicalToteId() {
            return candidatesByPhysicalToteId;
        }

        List<ServiceCentrePharmacyGroup> pharmacyGroups() {
            return pharmacyGroups;
        }

        Map<String, Map<String, ServiceCentrePharmacyGroup>> byServiceCentreAndPharmacy() {
            return byServiceCentreAndPharmacy;
        }

        Map<PhysicalToteId, Integer> groupIndexByPhysicalToteId() {
            return groupIndexByPhysicalToteId;
        }
    }

    private record CandidateCopies(
            List<DspOperationalReleaseCandidate> candidates,
            Map<PhysicalToteId, DspOperationalReleaseCandidate> byPhysicalToteId) {
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

    private static Map<PhysicalToteId, Integer> validateCandidateGroups(
            List<DspOperationalReleaseCandidate> candidates,
            Map<String, Map<String, ServiceCentrePharmacyGroup>> byServiceCentreAndPharmacy) {
        Map<PhysicalToteId, Integer> groupIndexByPhysicalToteId = new LinkedHashMap<>();
        for (DspOperationalReleaseCandidate candidate : candidates) {
            String serviceCentreId = candidate.physicalCandidate().serviceCentreId();
            Map<String, ServiceCentrePharmacyGroup> groupsByPharmacy =
                    byServiceCentreAndPharmacy.get(serviceCentreId);
            int earliestGroupIndex = Integer.MAX_VALUE;
            for (String pharmacyId : candidate.pharmacyIds()) {
                ServiceCentrePharmacyGroup group = groupsByPharmacy == null
                        ? null
                        : groupsByPharmacy.get(pharmacyId);
                if (group == null) {
                    throw new IllegalArgumentException(
                            "No pharmacy group configured for " + serviceCentreId + "/" + pharmacyId);
                }
                earliestGroupIndex = Math.min(earliestGroupIndex, group.groupIndex());
            }
            groupIndexByPhysicalToteId.put(
                    candidate.physicalCandidate().physicalToteId(), earliestGroupIndex);
        }
        return Collections.unmodifiableMap(groupIndexByPhysicalToteId);
    }

    private record GroupCopies(
            List<ServiceCentrePharmacyGroup> pharmacyGroups,
            Map<String, Map<String, ServiceCentrePharmacyGroup>> byServiceCentreAndPharmacy) {
    }

    private static String requireTrimmed(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
