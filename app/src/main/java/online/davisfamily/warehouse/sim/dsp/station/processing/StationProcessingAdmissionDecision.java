package online.davisfamily.warehouse.sim.dsp.station.processing;

/**
 * A target's non-mutating admission result for one exact routed tote.
 */
public record StationProcessingAdmissionDecision(
        boolean permitted,
        String reason) {

    public StationProcessingAdmissionDecision {
        reason = reason == null ? "" : reason.trim();
        if (permitted && !reason.isEmpty()) {
            throw new IllegalArgumentException("reason must be empty when admission is permitted");
        }
        if (!permitted && reason.isEmpty()) {
            throw new IllegalArgumentException("reason must not be blank when admission is deferred");
        }
    }

    public static StationProcessingAdmissionDecision permit() {
        return new StationProcessingAdmissionDecision(true, "");
    }

    public static StationProcessingAdmissionDecision defer(String reason) {
        return new StationProcessingAdmissionDecision(false, reason);
    }
}
