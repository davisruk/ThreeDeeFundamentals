package online.davisfamily.warehouse.sim.dsp.av02;

public record Av02AllocationConfig(int capacity) {

    public Av02AllocationConfig {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1");
        }
    }

    public static Av02AllocationConfig productionBaseline() {
        return new Av02AllocationConfig(1);
    }
}
