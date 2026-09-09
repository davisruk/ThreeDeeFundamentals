package online.davisfamily.warehouse.sim.dsp.p2p.lease;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.p2p.allocation.P2pElasticAllocationSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.bag.P2pBagCorrelationAssignmentSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.bag.P2pBagCorrelationRequirement;

public record P2pLineAllocationRequest(
        PhysicalToteId physicalToteId,
        String serviceCentreId,
        List<String> pharmacyIds,
        boolean p2pFirstRouteStation,
        P2pLineLeaseCatalogSnapshot lineCatalog,
        Map<OperationalRouteDestination, Boolean> routeAdmissionByDestination,
        Optional<P2pElasticAllocationSnapshot> elasticAllocation,
        Set<P2pBagCorrelationRequirement> bagCorrelationRequirements,
        P2pBagCorrelationAssignmentSnapshot bagCorrelationAssignments) {

    public P2pLineAllocationRequest {
        if (physicalToteId == null) {
            throw new IllegalArgumentException("physicalToteId must not be null");
        }
        serviceCentreId = requireValue(serviceCentreId, "serviceCentreId");
        pharmacyIds = copyPharmacyIds(pharmacyIds);
        if (lineCatalog == null) {
            throw new IllegalArgumentException("lineCatalog must not be null");
        }
        if (lineCatalog.findAssignment(physicalToteId).isPresent()) {
            throw new IllegalArgumentException("physical tote already has a P2P line assignment");
        }
        routeAdmissionByDestination = copyAdmissions(
                routeAdmissionByDestination, lineCatalog);
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
        bagCorrelationRequirements = copyCorrelationRequirements(bagCorrelationRequirements);
        if (bagCorrelationAssignments == null) {
            throw new IllegalArgumentException("bagCorrelationAssignments must not be null");
        }
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

    private static List<String> copyPharmacyIds(List<String> pharmacyIds) {
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

    private static Map<OperationalRouteDestination, Boolean> copyAdmissions(
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

    private static String requireValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
