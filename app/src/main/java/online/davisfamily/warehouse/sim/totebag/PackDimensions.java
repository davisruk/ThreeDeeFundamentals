package online.davisfamily.warehouse.sim.totebag;

public record PackDimensions(float length, float width, float height) {
    public PackDimensions {
        if (length <= 0f) {
            throw new IllegalArgumentException("length must be > 0");
        }
        if (width <= 0f) {
            throw new IllegalArgumentException("width must be > 0");
        }
        if (height <= 0f) {
            throw new IllegalArgumentException("height must be > 0");
        }
    }
}
