package online.davisfamily.warehouse.sim.dsp.adapting;

public record AdaptingBenchSelection(
        boolean accepted,
        AdaptingBenchId benchId,
        String blockedReason) {

    public AdaptingBenchSelection {
        blockedReason = blockedReason == null ? "" : blockedReason;
        if (accepted && benchId == null) {
            throw new IllegalArgumentException("benchId must not be null when accepted");
        }
        if (!accepted && blockedReason.isBlank()) {
            throw new IllegalArgumentException("blockedReason must not be blank when blocked");
        }
    }

    public static AdaptingBenchSelection accepted(AdaptingBenchId benchId) {
        return new AdaptingBenchSelection(true, benchId, "");
    }

    public static AdaptingBenchSelection blocked(String blockedReason) {
        return new AdaptingBenchSelection(false, null, blockedReason);
    }
}
