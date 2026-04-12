package online.davisfamily.warehouse.sim.totebag;

public record PrlAssignmentPlan(String prlId, String correlationId, int expectedPackCount) {
    public PrlAssignmentPlan {
        if (prlId == null || prlId.isBlank()) {
            throw new IllegalArgumentException("prlId must not be blank");
        }
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId must not be blank");
        }
        if (expectedPackCount <= 0) {
            throw new IllegalArgumentException("expectedPackCount must be > 0");
        }
    }
}
