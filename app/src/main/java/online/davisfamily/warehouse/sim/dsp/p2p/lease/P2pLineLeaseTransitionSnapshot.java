package online.davisfamily.warehouse.sim.dsp.p2p.lease;

public record P2pLineLeaseTransitionSnapshot(long sequence, String details) {
    public P2pLineLeaseTransitionSnapshot {
        if (sequence < 1) {
            throw new IllegalArgumentException("sequence must be >= 1");
        }
        if (details == null || details.isBlank()) {
            throw new IllegalArgumentException("details must not be blank");
        }
        details = details.trim();
    }
}
