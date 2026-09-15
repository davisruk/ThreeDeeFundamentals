package online.davisfamily.warehouse.sim.dsp.p2p.lease;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.p2p.allocation.P2pElasticAllocationSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.bag.P2pBagCorrelationAssignmentSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.bag.P2pBagCorrelationRequirement;

public final class P2pLineAllocationRequest {
    private final PhysicalToteId physicalToteId;
    private final String serviceCentreId;
    private final List<String> pharmacyIds;
    private final boolean p2pFirstRouteStation;
    private final P2pLineLeaseCatalogSnapshot lineCatalog;
    private final Map<OperationalRouteDestination, Boolean> routeAdmissionByDestination;
    private final Optional<P2pElasticAllocationSnapshot> elasticAllocation;
    private final Set<P2pBagCorrelationRequirement> bagCorrelationRequirements;
    private final P2pBagCorrelationAssignmentSnapshot bagCorrelationAssignments;

    public P2pLineAllocationRequest(
            PhysicalToteId physicalToteId,
            String serviceCentreId,
            List<String> pharmacyIds,
            boolean p2pFirstRouteStation,
            P2pLineLeaseCatalogSnapshot lineCatalog,
            Map<OperationalRouteDestination, Boolean> routeAdmissionByDestination,
            Optional<P2pElasticAllocationSnapshot> elasticAllocation,
            Set<P2pBagCorrelationRequirement> bagCorrelationRequirements,
            P2pBagCorrelationAssignmentSnapshot bagCorrelationAssignments) {
        if (physicalToteId == null) {
            throw new IllegalArgumentException("physicalToteId must not be null");
        }
        this.physicalToteId = physicalToteId;
        this.serviceCentreId = requireValue(serviceCentreId, "serviceCentreId");
        this.pharmacyIds = copyPharmacyIds(pharmacyIds);
        if (lineCatalog == null) {
            throw new IllegalArgumentException("lineCatalog must not be null");
        }
        if (lineCatalog.findAssignment(physicalToteId).isPresent()) {
            throw new IllegalArgumentException("physical tote already has a P2P line assignment");
        }
        this.lineCatalog = lineCatalog;
        this.routeAdmissionByDestination = copyAdmissions(
                routeAdmissionByDestination, lineCatalog);
        validateElasticAllocation(elasticAllocation, lineCatalog);
        this.elasticAllocation = elasticAllocation;
        this.bagCorrelationRequirements = copyCorrelationRequirements(
                bagCorrelationRequirements);
        if (bagCorrelationAssignments == null) {
            throw new IllegalArgumentException("bagCorrelationAssignments must not be null");
        }
        this.bagCorrelationAssignments = bagCorrelationAssignments;
        this.p2pFirstRouteStation = p2pFirstRouteStation;
    }

    public P2pLineAllocationRequest(
            PhysicalToteId physicalToteId,
            String serviceCentreId,
            List<String> pharmacyIds,
            boolean p2pFirstRouteStation,
            P2pLineLeaseCatalogSnapshot lineCatalog,
            Map<OperationalRouteDestination, Boolean> routeAdmissionByDestination) {
        this(
                physicalToteId,
                serviceCentreId,
                pharmacyIds,
                p2pFirstRouteStation,
                lineCatalog,
                routeAdmissionByDestination,
                Optional.empty(),
                Set.of(),
                P2pBagCorrelationAssignmentSnapshot.empty());
    }

    public P2pLineAllocationRequest(
            PhysicalToteId physicalToteId,
            String serviceCentreId,
            List<String> pharmacyIds,
            boolean p2pFirstRouteStation,
            P2pLineLeaseCatalogSnapshot lineCatalog,
            Map<OperationalRouteDestination, Boolean> routeAdmissionByDestination,
            Optional<P2pElasticAllocationSnapshot> elasticAllocation) {
        this(
                physicalToteId,
                serviceCentreId,
                pharmacyIds,
                p2pFirstRouteStation,
                lineCatalog,
                routeAdmissionByDestination,
                elasticAllocation,
                Set.of(),
                P2pBagCorrelationAssignmentSnapshot.empty());
    }

    public P2pLineAllocationRequest(
            PhysicalToteId physicalToteId,
            String serviceCentreId,
            List<String> pharmacyIds,
            boolean p2pFirstRouteStation,
            P2pLineLeaseCatalogSnapshot lineCatalog,
            Map<OperationalRouteDestination, Boolean> routeAdmissionByDestination,
            Set<P2pBagCorrelationRequirement> bagCorrelationRequirements,
            P2pBagCorrelationAssignmentSnapshot bagCorrelationAssignments) {
        this(
                physicalToteId,
                serviceCentreId,
                pharmacyIds,
                p2pFirstRouteStation,
                lineCatalog,
                routeAdmissionByDestination,
                Optional.empty(),
                bagCorrelationRequirements,
                bagCorrelationAssignments);
    }

    P2pLineAllocationRequest(
            PhysicalToteId physicalToteId,
            String serviceCentreId,
            List<String> pharmacyIds,
            boolean p2pFirstRouteStation,
            ValidatedBatchValues batchValues,
            Set<P2pBagCorrelationRequirement> bagCorrelationRequirements) {
        this.physicalToteId = physicalToteId;
        this.serviceCentreId = serviceCentreId;
        this.pharmacyIds = pharmacyIds;
        this.p2pFirstRouteStation = p2pFirstRouteStation;
        this.lineCatalog = batchValues.lineCatalog();
        this.routeAdmissionByDestination = batchValues.routeAdmissionByDestination();
        this.elasticAllocation = batchValues.elasticAllocation();
        this.bagCorrelationRequirements = bagCorrelationRequirements;
        this.bagCorrelationAssignments = batchValues.bagCorrelationAssignments();
    }

    public PhysicalToteId physicalToteId() {
        return physicalToteId;
    }

    public String serviceCentreId() {
        return serviceCentreId;
    }

    public List<String> pharmacyIds() {
        return pharmacyIds;
    }

    public boolean p2pFirstRouteStation() {
        return p2pFirstRouteStation;
    }

    public P2pLineLeaseCatalogSnapshot lineCatalog() {
        return lineCatalog;
    }

    public Map<OperationalRouteDestination, Boolean> routeAdmissionByDestination() {
        return routeAdmissionByDestination;
    }

    public Optional<P2pElasticAllocationSnapshot> elasticAllocation() {
        return elasticAllocation;
    }

    public Set<P2pBagCorrelationRequirement> bagCorrelationRequirements() {
        return bagCorrelationRequirements;
    }

    public P2pBagCorrelationAssignmentSnapshot bagCorrelationAssignments() {
        return bagCorrelationAssignments;
    }

    public boolean routeAdmissible(OperationalRouteDestination destination) {
        if (destination == null) {
            throw new IllegalArgumentException("destination must not be null");
        }
        Boolean admissible = routeAdmissionByDestination.get(destination);
        if (admissible == null) {
            throw new IllegalArgumentException("No route admission for P2P destination: " + destination);
        }
        return admissible;
    }

    public boolean includesPharmacy(String pharmacyId) {
        return pharmacyIds.contains(requireValue(pharmacyId, "pharmacyId"));
    }

    public Set<P2pBagCorrelationRequirement> requiredBagCorrelations() {
        return bagCorrelationRequirements;
    }

    public P2pBagCorrelationAssignmentSnapshot correlationAssignmentSnapshot() {
        return bagCorrelationAssignments;
    }

    public P2pBagCorrelationAssignmentSnapshot bagCorrelationAssignmentSnapshot() {
        return bagCorrelationAssignments;
    }

    public boolean bagCorrelationsCompatibleWith(P2pLineId lineId) {
        if (lineId == null) {
            throw new IllegalArgumentException("lineId must not be null");
        }
        return bagCorrelationAssignments.compatibleWith(bagCorrelationRequirements, lineId);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof P2pLineAllocationRequest that)) {
            return false;
        }
        return physicalToteId.equals(that.physicalToteId)
                && serviceCentreId.equals(that.serviceCentreId)
                && pharmacyIds.equals(that.pharmacyIds)
                && p2pFirstRouteStation == that.p2pFirstRouteStation
                && lineCatalog.equals(that.lineCatalog)
                && routeAdmissionByDestination.equals(that.routeAdmissionByDestination)
                && elasticAllocation.equals(that.elasticAllocation)
                && bagCorrelationRequirements.equals(that.bagCorrelationRequirements)
                && bagCorrelationAssignments.equals(that.bagCorrelationAssignments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                physicalToteId,
                serviceCentreId,
                pharmacyIds,
                p2pFirstRouteStation,
                lineCatalog,
                routeAdmissionByDestination,
                elasticAllocation,
                bagCorrelationRequirements,
                bagCorrelationAssignments);
    }

    @Override
    public String toString() {
        return "P2pLineAllocationRequest[physicalToteId=" + physicalToteId
                + ", serviceCentreId=" + serviceCentreId
                + ", pharmacyIds=" + pharmacyIds
                + ", p2pFirstRouteStation=" + p2pFirstRouteStation
                + ", lineCatalog=" + lineCatalog
                + ", routeAdmissionByDestination=" + routeAdmissionByDestination
                + ", elasticAllocation=" + elasticAllocation
                + ", bagCorrelationRequirements=" + bagCorrelationRequirements
                + ", bagCorrelationAssignments=" + bagCorrelationAssignments + "]";
    }

    static List<String> copyPharmacyIds(List<String> pharmacyIds) {
        if (pharmacyIds == null || pharmacyIds.isEmpty()) {
            throw new IllegalArgumentException("pharmacyIds must not be null or empty");
        }
        Set<String> distinct = new LinkedHashSet<>();
        for (String pharmacyId : pharmacyIds) {
            String normalized = requireValue(pharmacyId, "pharmacyIds value");
            if (!distinct.add(normalized)) {
                throw new IllegalArgumentException("pharmacyIds must be distinct");
            }
        }
        return List.copyOf(distinct);
    }

    static Map<OperationalRouteDestination, Boolean> copyAdmissions(
            Map<OperationalRouteDestination, Boolean> admissions,
            P2pLineLeaseCatalogSnapshot lineCatalog) {
        if (admissions == null) {
            throw new IllegalArgumentException("routeAdmissionByDestination must not be null");
        }
        Map<OperationalRouteDestination, Boolean> copy = new LinkedHashMap<>();
        admissions.forEach((destination, admissible) -> {
            if (destination == null || admissible == null) {
                throw new IllegalArgumentException(
                        "routeAdmissionByDestination must not contain null keys or values");
            }
            copy.put(destination, admissible);
        });
        for (P2pLineLeaseSnapshot line : lineCatalog.lines()) {
            if (!copy.containsKey(line.definition().destination())) {
                throw new IllegalArgumentException(
                        "Missing route admission for configured P2P destination: "
                                + line.definition().destination());
            }
        }
        return Collections.unmodifiableMap(copy);
    }

    static void validateElasticAllocation(
            Optional<P2pElasticAllocationSnapshot> elasticAllocation,
            P2pLineLeaseCatalogSnapshot lineCatalog) {
        if (elasticAllocation == null) {
            throw new IllegalArgumentException("elasticAllocation must not be null");
        }
        elasticAllocation.ifPresent(allocation -> {
            List<P2pLineId> catalogLineIds = lineCatalog.lines().stream()
                    .map(line -> line.definition().lineId())
                    .toList();
            if (!allocation.configuredLineIds().equals(catalogLineIds)) {
                throw new IllegalArgumentException(
                        "elastic allocation lines must match the request line catalog");
            }
        });
    }

    private static Set<P2pBagCorrelationRequirement> copyCorrelationRequirements(
            Set<P2pBagCorrelationRequirement> requirements) {
        if (requirements == null) {
            throw new IllegalArgumentException("bagCorrelationRequirements must not be null");
        }
        Map<String, P2pBagCorrelationRequirement> byCorrelation = new LinkedHashMap<>();
        for (P2pBagCorrelationRequirement requirement : requirements) {
            if (requirement == null) {
                throw new IllegalArgumentException(
                        "bagCorrelationRequirements must not contain null");
            }
            if (byCorrelation.putIfAbsent(requirement.correlationId(), requirement) != null) {
                throw new IllegalArgumentException(
                        "bagCorrelationRequirements must contain distinct correlations");
            }
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(byCorrelation.values()));
    }

    static String requireValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    static final class ValidatedBatchValues {
        private final P2pLineLeaseCatalogSnapshot lineCatalog;
        private final Map<OperationalRouteDestination, Boolean> routeAdmissionByDestination;
        private final Optional<P2pElasticAllocationSnapshot> elasticAllocation;
        private final P2pBagCorrelationAssignmentSnapshot bagCorrelationAssignments;

        ValidatedBatchValues(
                P2pLineLeaseCatalogSnapshot lineCatalog,
                Map<OperationalRouteDestination, Boolean> routeAdmissionByDestination,
                Optional<P2pElasticAllocationSnapshot> elasticAllocation,
                P2pBagCorrelationAssignmentSnapshot bagCorrelationAssignments) {
            this.lineCatalog = lineCatalog;
            this.routeAdmissionByDestination = routeAdmissionByDestination;
            this.elasticAllocation = elasticAllocation;
            this.bagCorrelationAssignments = bagCorrelationAssignments;
        }

        P2pLineLeaseCatalogSnapshot lineCatalog() {
            return lineCatalog;
        }

        Map<OperationalRouteDestination, Boolean> routeAdmissionByDestination() {
            return routeAdmissionByDestination;
        }

        Optional<P2pElasticAllocationSnapshot> elasticAllocation() {
            return elasticAllocation;
        }

        P2pBagCorrelationAssignmentSnapshot bagCorrelationAssignments() {
            return bagCorrelationAssignments;
        }
    }
}
