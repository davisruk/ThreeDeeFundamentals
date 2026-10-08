package online.davisfamily.warehouse.sim.dsp.scheduler.policy;

/** Complete compatible scheduling policy sets; names are exact external identifiers. */
public enum DspSchedulerPolicy {
    DEADLINE_AWARE_ELASTIC_STICKY_LEASES,
    WHOLE_SERVICE_CENTRE_DRAINED_HANDOVER;

    public static DspSchedulerPolicy parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("schedulerPolicy must be a supported exact enum name");
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported schedulerPolicy: " + value, exception);
        }
    }
}
