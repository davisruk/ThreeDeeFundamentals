package online.davisfamily.warehouse.sim.dsp.outbound;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.bagging.BagKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

public record OutboundToteSnapshot(
        PhysicalToteId physicalToteId,
        P2pLineId p2pLineId,
        Optional<String> serviceCentreId,
        Optional<String> pharmacyId,
        int maximumBagCount,
        List<AllocatedOutboundBag> allocatedBags,
        Optional<OutboundToteClosureReason> closureReason) {

    public OutboundToteSnapshot {
        if (physicalToteId == null) {
            throw new IllegalArgumentException("physicalToteId must not be null");
        }
        if (p2pLineId == null) {
            throw new IllegalArgumentException("p2pLineId must not be null");
        }
        serviceCentreId = normalizedOptional(serviceCentreId, "serviceCentreId");
        pharmacyId = normalizedOptional(pharmacyId, "pharmacyId");
        if (serviceCentreId.isPresent() != pharmacyId.isPresent()) {
            throw new IllegalArgumentException("serviceCentreId and pharmacyId must both be present or absent");
        }
        if (maximumBagCount < 1) {
            throw new IllegalArgumentException("maximumBagCount must be >= 1");
        }
        if (allocatedBags == null) {
            throw new IllegalArgumentException("allocatedBags must not be null");
        }
        if (allocatedBags.stream().anyMatch(bag -> bag == null)) {
            throw new IllegalArgumentException("allocatedBags must not contain null");
        }
        allocatedBags = List.copyOf(allocatedBags);
        if (closureReason == null) {
            throw new IllegalArgumentException("closureReason must not be null");
        }
        if (allocatedBags.size() > maximumBagCount) {
            throw new IllegalArgumentException("allocatedBags must not exceed maximumBagCount");
        }
        if (serviceCentreId.isEmpty() && !allocatedBags.isEmpty()) {
            throw new IllegalArgumentException("unassigned tote must not contain bags");
        }

        Set<BagKey> bagKeys = new LinkedHashSet<>();
        for (AllocatedOutboundBag allocatedBag : allocatedBags) {
            if (!allocatedBag.outboundPhysicalToteId().equals(physicalToteId)) {
                throw new IllegalArgumentException("allocated bag physical tote ID must match snapshot");
            }
            if (serviceCentreId.isPresent()
                    && (!allocatedBag.plannedBag().serviceCentreId().equals(serviceCentreId.orElseThrow())
                            || !allocatedBag.plannedBag().pharmacyId().equals(pharmacyId.orElseThrow()))) {
                throw new IllegalArgumentException("allocated bag must match outbound tote purity");
            }
            if (!bagKeys.add(allocatedBag.bagKey())) {
                throw new IllegalArgumentException("Duplicate allocated bag key: " + allocatedBag.bagKey());
            }
        }
    }

    public boolean open() {
        return closureReason.isEmpty();
    }

    public boolean assigned() {
        return serviceCentreId.isPresent();
    }

    public int bagCount() {
        return allocatedBags.size();
    }

    public int remainingBagCapacity() {
        return maximumBagCount - bagCount();
    }

    public boolean containsPatient(String patientId) {
        String normalizedPatientId = requireValue(patientId, "patientId");
        return allocatedBags.stream()
                .anyMatch(bag -> bag.plannedBag().patientId().equals(normalizedPatientId));
    }

    private static Optional<String> normalizedOptional(Optional<String> value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return value.map(item -> requireValue(item, fieldName));
    }

    private static String requireValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
