package online.davisfamily.warehouse.sim.dsp.p2p.arrival;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pPhysicalToteAssignment;
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;

public record P2pArrivalAdmissionRequest(
        PhysicalToteId physicalToteId,
        OperationalRouteDestination destination,
        String serviceCentreId,
        OrderSheetKey orderSheetKey,
        OrderType orderType,
        List<String> pharmacyIds,
        Optional<P2pPhysicalToteAssignment> p2pAssignment) {

    public P2pArrivalAdmissionRequest(
            PhysicalToteId physicalToteId,
            OperationalRouteDestination destination,
            String serviceCentreId,
            OrderSheetKey orderSheetKey,
            OrderType orderType,
            List<String> pharmacyIds) {
        this(
                physicalToteId,
                destination,
                serviceCentreId,
                orderSheetKey,
                orderType,
                pharmacyIds,
                Optional.empty());
    }

    public P2pArrivalAdmissionRequest {
        if (physicalToteId == null) {
            throw new IllegalArgumentException("physicalToteId must not be null");
        }
        if (destination == null) {
            throw new IllegalArgumentException("destination must not be null");
        }
        if (destination.stationType() != StationType.P2P) {
            throw new IllegalArgumentException("destination must identify a P2P station");
        }
        if (serviceCentreId == null || serviceCentreId.isBlank()) {
            throw new IllegalArgumentException("serviceCentreId must not be blank");
        }
        if (orderSheetKey == null) {
            throw new IllegalArgumentException("orderSheetKey must not be null");
        }
        if (orderType == null) {
            throw new IllegalArgumentException("orderType must not be null");
        }
        if (pharmacyIds == null || pharmacyIds.isEmpty()) {
            throw new IllegalArgumentException("pharmacyIds must not be empty");
        }
        if (p2pAssignment == null) {
            throw new IllegalArgumentException("p2pAssignment must not be null");
        }

        serviceCentreId = serviceCentreId.trim();
        String normalizedServiceCentreId = serviceCentreId;
        Set<String> distinctPharmacyIds = new LinkedHashSet<>();
        for (String pharmacyId : pharmacyIds) {
            if (pharmacyId == null || pharmacyId.isBlank()) {
                throw new IllegalArgumentException("pharmacyIds must not contain blank values");
            }
            distinctPharmacyIds.add(pharmacyId.trim());
        }
        pharmacyIds = List.copyOf(new ArrayList<>(distinctPharmacyIds));
        p2pAssignment.ifPresent(assignment -> {
            if (!assignment.physicalToteId().equals(physicalToteId)
                    || !assignment.serviceCentreId().equals(normalizedServiceCentreId)) {
                throw new IllegalArgumentException(
                        "P2P assignment must match the arrival physical identity and service centre");
            }
        });
    }

    public static P2pArrivalAdmissionRequest from(
            InboundToteManifest manifest,
            OperationalRouteDestination destination) {
        if (manifest == null) {
            throw new IllegalArgumentException("manifest must not be null");
        }
        List<String> pharmacyIds = manifest.items().stream()
                .map(item -> item.pharmacyId())
                .toList();
        return new P2pArrivalAdmissionRequest(
                manifest.physicalToteId(),
                destination,
                manifest.serviceCentreId(),
                manifest.orderSheetKey(),
                manifest.orderType(),
                pharmacyIds);
    }

    public static P2pArrivalAdmissionRequest from(RoutedPhysicalTote routedTote) {
        if (routedTote == null) {
            throw new IllegalArgumentException("routedTote must not be null");
        }
        InboundToteManifest manifest =
                routedTote.launchRequest().releaseRequest().manifest();
        List<String> pharmacyIds = manifest.items().stream()
                .map(item -> item.pharmacyId())
                .toList();
        return new P2pArrivalAdmissionRequest(
                manifest.physicalToteId(),
                routedTote.destination(),
                manifest.serviceCentreId(),
                manifest.orderSheetKey(),
                manifest.orderType(),
                pharmacyIds,
                routedTote.p2pAssignment());
    }
}
