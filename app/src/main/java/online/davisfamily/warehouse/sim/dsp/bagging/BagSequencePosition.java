package online.davisfamily.warehouse.sim.dsp.bagging;

/** Immutable one-based bag position within one prescription's complete plan. */
public record BagSequencePosition(int bagOrdinal, int totalBagCount) {

    public BagSequencePosition {
        if (bagOrdinal < 1) {
            throw new IllegalArgumentException("bagOrdinal must be >= 1");
        }
        if (totalBagCount < 1) {
            throw new IllegalArgumentException("totalBagCount must be >= 1");
        }
        if (bagOrdinal > totalBagCount) {
            throw new IllegalArgumentException("bagOrdinal must be <= totalBagCount");
        }
    }
}
