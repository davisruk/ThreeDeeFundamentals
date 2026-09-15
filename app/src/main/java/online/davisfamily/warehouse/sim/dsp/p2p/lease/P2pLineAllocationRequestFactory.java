package online.davisfamily.warehouse.sim.dsp.p2p.lease;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteSource;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.p2p.allocation.P2pElasticAllocationSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.bag.P2pBagCorrelationAssignmentSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.bag.P2pBagCorrelationRequirement;
import online.davisfamily.warehouse.sim.dsp.p2p.bag.P2pBagCorrelationRequirementCatalog;

public final class P2pLineAllocationRequestFactory {
    private final P2pLineAllocationRequest.ValidatedBatchValues batchValues;
    private final P2pBagCorrelationRequirementCatalog correlationRequirementCatalog;

    public P2pLineAllocationRequestFactory(
            P2pLineLeaseCatalogSnapshot lineCatalog,
            Map<OperationalRouteDestination, Boolean> routeAdmissionByDestination,
            Optional<P2pElasticAllocationSnapshot> elasticAllocation,
            P2pBagCorrelationAssignmentSnapshot bagCorrelationAssignments,
            P2pBagCorrelationRequirementCatalog correlationRequirementCatalog) {
        if (lineCatalog == null) {
            throw new IllegalArgumentException("lineCatalog must not be null");
        }
        Map<OperationalRouteDestination, Boolean> validatedAdmissions =
                P2pLineAllocationRequest.copyAdmissions(
                        routeAdmissionByDestination, lineCatalog);
        P2pLineAllocationRequest.validateElasticAllocation(elasticAllocation, lineCatalog);
        if (bagCorrelationAssignments == null) {
            throw new IllegalArgumentException("bagCorrelationAssignments must not be null");
        }
        if (correlationRequirementCatalog == null) {
            throw new IllegalArgumentException(
                    "correlationRequirementCatalog must not be null");
        }
        this.batchValues = new P2pLineAllocationRequest.ValidatedBatchValues(
                lineCatalog,
                validatedAdmissions,
                elasticAllocation,
                bagCorrelationAssignments);
        this.correlationRequirementCatalog = correlationRequirementCatalog;
    }

    public P2pLineAllocationRequest create(
            OperationalPhysicalToteSource source,
            PhysicalToteId physicalToteId,
            OrderSheetKey orderSheetKey,
            String serviceCentreId,
            List<String> pharmacyIds,
            boolean p2pFirstRouteStation) {
        if (physicalToteId == null) {
            throw new IllegalArgumentException("physicalToteId must not be null");
        }
        String normalizedServiceCentreId = P2pLineAllocationRequest.requireValue(
                serviceCentreId, "serviceCentreId");
        List<String> normalizedPharmacyIds = P2pLineAllocationRequest.copyPharmacyIds(
                pharmacyIds);
        if (batchValues.lineCatalog().findAssignment(physicalToteId).isPresent()) {
            throw new IllegalArgumentException("physical tote already has a P2P line assignment");
        }
        Set<P2pBagCorrelationRequirement> requirements = correlationRequirementCatalog.requirementsFor(
                        source, physicalToteId, orderSheetKey);
        return new P2pLineAllocationRequest(
                physicalToteId,
                normalizedServiceCentreId,
                normalizedPharmacyIds,
                p2pFirstRouteStation,
                batchValues,
                requirements);
    }
}
