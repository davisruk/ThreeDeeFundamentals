package online.davisfamily.warehouse.sim.dsp.runtime;

public record SchedulerCommandApplicationResult(boolean applied, boolean deferred, String reason) {

    public SchedulerCommandApplicationResult {
        reason = reason == null ? "" : reason.trim();
        if (applied && deferred) {
            throw new IllegalArgumentException("applied and deferred cannot both be true");
        }
        if (applied && !reason.isEmpty()) {
            throw new IllegalArgumentException("applied result must not include a reason");
        }
        if (!applied && reason.isEmpty()) {
            throw new IllegalArgumentException("non-applied result must include a reason");
        }
    }

    public static SchedulerCommandApplicationResult appliedResult() {
        return new SchedulerCommandApplicationResult(true, false, "");
    }

    public static SchedulerCommandApplicationResult deferredResult(String reason) {
        return new SchedulerCommandApplicationResult(false, true, reason);
    }

    public static SchedulerCommandApplicationResult rejectedResult(String reason) {
        return new SchedulerCommandApplicationResult(false, false, reason);
    }
}
