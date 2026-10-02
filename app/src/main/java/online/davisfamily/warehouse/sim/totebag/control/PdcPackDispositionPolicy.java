package online.davisfamily.warehouse.sim.totebag.control;

/** Simulation-thread pack disposition seam; only the outfeed callback mutates state. */
public interface PdcPackDispositionPolicy {
    boolean bypassPrl(String packId);
    int effectivePackCount(String correlationId, int plannedCount);
    boolean allowEmptyTote(String toteId);
    void collectedAtPdcOutfeed(String packId);
    boolean deferInitialPrlAssignments();
    long classificationEpoch();

    static PdcPackDispositionPolicy noOp() {
        return NoOp.INSTANCE;
    }

    final class NoOp implements PdcPackDispositionPolicy {
        private static final PdcPackDispositionPolicy INSTANCE = new NoOp();

        private NoOp() { }

        @Override public boolean bypassPrl(String packId) { return false; }
        @Override public int effectivePackCount(String correlationId, int plannedCount) { return plannedCount; }
        @Override public boolean allowEmptyTote(String toteId) { return false; }
        @Override public void collectedAtPdcOutfeed(String packId) { }
        @Override public boolean deferInitialPrlAssignments() { return false; }
        @Override public long classificationEpoch() { return 0; }
    }
}
