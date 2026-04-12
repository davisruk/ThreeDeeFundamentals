package online.davisfamily.warehouse.sim.totebag;

public record PackPlan(String packId, String correlationId, PackDimensions dimensions) {
    public PackPlan {
        if (packId == null || packId.isBlank()) {
            throw new IllegalArgumentException("packId must not be blank");
        }
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId must not be blank");
        }
        if (dimensions == null) {
            throw new IllegalArgumentException("dimensions must not be null");
        }
    }

    public Pack createPack() {
        return new Pack(packId, correlationId, dimensions);
    }
}
