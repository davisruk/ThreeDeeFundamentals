package online.davisfamily.warehouse.sim.dsp.scheduler.operational;

public record ServiceCentrePharmacyGroup(
        String serviceCentreId,
        String pharmacyId,
        int groupIndex,
        long firstSourceSequenceNumber) {

    public ServiceCentrePharmacyGroup {
        serviceCentreId = requireTrimmed(serviceCentreId, "serviceCentreId");
        pharmacyId = requireTrimmed(pharmacyId, "pharmacyId");
        if (groupIndex < 0) {
            throw new IllegalArgumentException("groupIndex must be >= 0");
        }
        if (firstSourceSequenceNumber < 0) {
            throw new IllegalArgumentException("firstSourceSequenceNumber must be >= 0");
        }
    }

    private static String requireTrimmed(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
