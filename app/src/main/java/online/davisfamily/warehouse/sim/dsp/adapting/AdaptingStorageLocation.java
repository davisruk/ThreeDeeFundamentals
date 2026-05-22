package online.davisfamily.warehouse.sim.dsp.adapting;

public record AdaptingStorageLocation(
        String pharmacyId,
        AdaptingBenchId benchId,
        int rackIndex,
        int shelfIndex,
        int binIndex) {

    public AdaptingStorageLocation {
        if (pharmacyId == null || pharmacyId.isBlank()) {
            throw new IllegalArgumentException("pharmacyId must not be blank");
        }
        if (benchId == null) {
            throw new IllegalArgumentException("benchId must not be null");
        }
        if (rackIndex < 0) {
            throw new IllegalArgumentException("rackIndex must be >= 0");
        }
        if (shelfIndex < 0) {
            throw new IllegalArgumentException("shelfIndex must be >= 0");
        }
        if (binIndex < 0) {
            throw new IllegalArgumentException("binIndex must be >= 0");
        }
        pharmacyId = pharmacyId.trim();
    }
}
