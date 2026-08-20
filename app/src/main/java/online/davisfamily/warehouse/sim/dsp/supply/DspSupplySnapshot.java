package online.davisfamily.warehouse.sim.dsp.supply;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;

public record DspSupplySnapshot(
        String policyId,
        int lowWaterMark,
        int osrCapacity,
        int osrOccupancy,
        Optional<String> activeInboundServiceCentreId,
        Optional<Duration> nextPhysicalAdmissionElapsedTime,
        Set<OrderSheetKey> authorizedEmptyOrderSheetKeys,
        List<ServiceCentreSupplySnapshot> serviceCentres,
        long admittedAfterStartupCount) {

    public DspSupplySnapshot {
        if (policyId == null || policyId.isBlank()) {
            throw new IllegalArgumentException("policyId must not be blank");
        }
        policyId = policyId.trim();
        if (lowWaterMark < 0) {
            throw new IllegalArgumentException("lowWaterMark must be >= 0");
        }
        if (osrCapacity < 1) {
            throw new IllegalArgumentException("osrCapacity must be >= 1");
        }
        if (osrOccupancy < 0 || osrOccupancy > osrCapacity) {
            throw new IllegalArgumentException(
                    "osrOccupancy must be between zero and osrCapacity");
        }
        if (activeInboundServiceCentreId == null) {
            throw new IllegalArgumentException("activeInboundServiceCentreId must not be null");
        }
        activeInboundServiceCentreId = activeInboundServiceCentreId.map(String::trim);
        if (activeInboundServiceCentreId.isPresent()
                && activeInboundServiceCentreId.orElseThrow().isBlank()) {
            throw new IllegalArgumentException(
                    "activeInboundServiceCentreId must not contain a blank value");
        }
        if (nextPhysicalAdmissionElapsedTime == null) {
            throw new IllegalArgumentException(
                    "nextPhysicalAdmissionElapsedTime must not be null");
        }
        if (nextPhysicalAdmissionElapsedTime.isPresent()
                && nextPhysicalAdmissionElapsedTime.orElseThrow().isNegative()) {
            throw new IllegalArgumentException(
                    "nextPhysicalAdmissionElapsedTime must not be negative");
        }
        if (authorizedEmptyOrderSheetKeys == null) {
            throw new IllegalArgumentException("authorizedEmptyOrderSheetKeys must not be null");
        }
        Set<OrderSheetKey> copiedEmptyKeys = new LinkedHashSet<>();
        for (OrderSheetKey key : authorizedEmptyOrderSheetKeys) {
            if (key == null) {
                throw new IllegalArgumentException(
                        "authorizedEmptyOrderSheetKeys must not contain null");
            }
            copiedEmptyKeys.add(key);
        }
        if (serviceCentres == null) {
            throw new IllegalArgumentException("serviceCentres must not be null");
        }
        Set<String> serviceCentreIds = new LinkedHashSet<>();
        List<ServiceCentreSupplySnapshot> copiedServiceCentres = new ArrayList<>();
        long countedAdmissions = 0;
        Set<OrderSheetKey> derivedEmptyKeys = new LinkedHashSet<>();
        for (ServiceCentreSupplySnapshot serviceCentre : serviceCentres) {
            if (serviceCentre == null) {
                throw new IllegalArgumentException("serviceCentres must not contain null");
            }
            if (!serviceCentreIds.add(serviceCentre.serviceCentreId())) {
                throw new IllegalArgumentException(
                        "Duplicate serviceCentreId in supply snapshot: "
                                + serviceCentre.serviceCentreId());
            }
            copiedServiceCentres.add(serviceCentre);
            countedAdmissions += serviceCentre.admittedAfterStartupCount();
            derivedEmptyKeys.addAll(serviceCentre.authorizedEmptyOrderSheetKeys());
        }
        if (admittedAfterStartupCount < 0) {
            throw new IllegalArgumentException("admittedAfterStartupCount must be >= 0");
        }
        if (countedAdmissions != admittedAfterStartupCount) {
            throw new IllegalArgumentException(
                    "Service-centre admitted counts must sum to admittedAfterStartupCount");
        }
        if (!derivedEmptyKeys.equals(copiedEmptyKeys)) {
            throw new IllegalArgumentException(
                    "authorizedEmptyOrderSheetKeys must equal service-centre authorization union");
        }
        authorizedEmptyOrderSheetKeys = Collections.unmodifiableSet(
                new LinkedHashSet<>(copiedEmptyKeys));
        serviceCentres = List.copyOf(copiedServiceCentres);
    }
}
