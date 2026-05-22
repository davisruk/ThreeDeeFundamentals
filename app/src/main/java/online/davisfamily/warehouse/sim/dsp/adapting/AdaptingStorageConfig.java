package online.davisfamily.warehouse.sim.dsp.adapting;

public record AdaptingStorageConfig(
        int linesPerBin,
        int binsPerShelf,
        int shelvesPerRack) {

    public AdaptingStorageConfig {
        if (linesPerBin < 1) {
            throw new IllegalArgumentException("linesPerBin must be >= 1");
        }
        if (binsPerShelf < 1) {
            throw new IllegalArgumentException("binsPerShelf must be >= 1");
        }
        if (shelvesPerRack < 1) {
            throw new IllegalArgumentException("shelvesPerRack must be >= 1");
        }
    }

    public static AdaptingStorageConfig defaults() {
        return new AdaptingStorageConfig(20, 8, 6);
    }
}
