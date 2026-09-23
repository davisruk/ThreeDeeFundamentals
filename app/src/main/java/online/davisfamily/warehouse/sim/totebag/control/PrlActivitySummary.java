package online.davisfamily.warehouse.sim.totebag.control;

public record PrlActivitySummary(int nonIdlePrlCount, int packCount) {
    public PrlActivitySummary {
        if (nonIdlePrlCount < 0) {
            throw new IllegalArgumentException("nonIdlePrlCount must be nonnegative");
        }
        if (packCount < 0) {
            throw new IllegalArgumentException("packCount must be nonnegative");
        }
    }
}
