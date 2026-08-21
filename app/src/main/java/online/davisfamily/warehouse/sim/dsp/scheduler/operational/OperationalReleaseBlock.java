package online.davisfamily.warehouse.sim.dsp.scheduler.operational;

public record OperationalReleaseBlock(
        OperationalReleaseBlockType type,
        String reason) {

    public OperationalReleaseBlock {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        reason = reason.trim();
    }
}
