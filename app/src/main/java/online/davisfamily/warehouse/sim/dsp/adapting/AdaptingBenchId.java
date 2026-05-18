package online.davisfamily.warehouse.sim.dsp.adapting;

public record AdaptingBenchId(String value) implements Comparable<AdaptingBenchId> {

    public AdaptingBenchId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        value = value.trim();
    }

    @Override
    public int compareTo(AdaptingBenchId other) {
        if (other == null) {
            throw new IllegalArgumentException("other must not be null");
        }
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
