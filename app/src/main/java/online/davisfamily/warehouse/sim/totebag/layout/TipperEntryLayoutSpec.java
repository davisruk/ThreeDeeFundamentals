package online.davisfamily.warehouse.sim.totebag.layout;

import online.davisfamily.threedee.matrices.Vec3;

public record TipperEntryLayoutSpec(
        Vec3 origin,
        float yawRadians) {

    public TipperEntryLayoutSpec {
        if (origin == null) {
            throw new IllegalArgumentException("origin must not be null");
        }
        if (Float.isNaN(yawRadians) || Float.isInfinite(yawRadians)) {
            throw new IllegalArgumentException("yawRadians must be finite");
        }
    }

    public static TipperEntryLayoutSpec debugDefaults() {
        return new TipperEntryLayoutSpec(new Vec3(0f, 0f, 0f), 0f);
    }
}
