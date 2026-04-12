package online.davisfamily.warehouse.sim.totebag;

public record BagSpec(float width, float height, float depth) {
    public BagSpec {
        if (width <= 0f) {
            throw new IllegalArgumentException("width must be > 0");
        }
        if (height <= 0f) {
            throw new IllegalArgumentException("height must be > 0");
        }
        if (depth <= 0f) {
            throw new IllegalArgumentException("depth must be > 0");
        }
    }
}
