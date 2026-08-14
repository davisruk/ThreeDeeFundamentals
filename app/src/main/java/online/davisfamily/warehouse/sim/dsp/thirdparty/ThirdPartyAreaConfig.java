package online.davisfamily.warehouse.sim.dsp.thirdparty;

public record ThirdPartyAreaConfig(
        int waitingCapacity,
        int maxConcurrentVisits,
        double processingDurationSeconds) {

    public ThirdPartyAreaConfig {
        if (waitingCapacity < 0) {
            throw new IllegalArgumentException("waitingCapacity must be >= 0");
        }
        if (maxConcurrentVisits <= 0) {
            throw new IllegalArgumentException("maxConcurrentVisits must be > 0");
        }
        if (!Double.isFinite(processingDurationSeconds) || processingDurationSeconds < 0d) {
            throw new IllegalArgumentException("processingDurationSeconds must be finite and >= 0");
        }
    }
}
