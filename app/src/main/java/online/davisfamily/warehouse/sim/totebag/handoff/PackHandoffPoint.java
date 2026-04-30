package online.davisfamily.warehouse.sim.totebag.handoff;

import online.davisfamily.threedee.matrices.Vec3;

public record PackHandoffPoint(
        String id,
        Vec3 worldPosition,
        float yawRadians) {

    public PackHandoffPoint {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (worldPosition == null) {
            throw new IllegalArgumentException("worldPosition must not be null");
        }
        if (Float.isNaN(yawRadians) || Float.isInfinite(yawRadians)) {
            throw new IllegalArgumentException("yawRadians must be finite");
        }
    }
}
