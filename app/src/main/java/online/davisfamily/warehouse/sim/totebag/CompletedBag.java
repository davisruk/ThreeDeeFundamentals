package online.davisfamily.warehouse.sim.totebag;

public record CompletedBag(
        String correlationId,
        int packCount,
        BagSpec bagSpec) {
    public CompletedBag {
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId must not be blank");
        }
        if (packCount <= 0) {
            throw new IllegalArgumentException("packCount must be > 0");
        }
        if (bagSpec == null) {
            throw new IllegalArgumentException("bagSpec must not be null");
        }
    }
}
