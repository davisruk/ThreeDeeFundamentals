package online.davisfamily.warehouse.sim.dsp.analysis.runtime;

import online.davisfamily.warehouse.sim.totebag.control.PdcPackDispositionPolicy;

/** Full-day adapter sharing the single simulation-thread exception ledger across P2P lines. */
public final class DspFullDayPdcPackDispositionPolicy implements PdcPackDispositionPolicy {
    private final DspPreparedPackExceptionLedger ledger;

    public DspFullDayPdcPackDispositionPolicy(DspPreparedPackExceptionLedger ledger) {
        if (ledger == null) {
            throw new IllegalArgumentException("ledger must not be null");
        }
        this.ledger = ledger;
    }

    @Override public boolean bypassPrl(String packId) { return ledger.isMisplaced(packId); }
    @Override public int effectivePackCount(String correlationId, int plannedCount) {
        return ledger.effectivePackCount(correlationId, plannedCount);
    }
    @Override public boolean allowEmptyTote(String toteId) { return ledger.allowEmptyTote(toteId); }
    @Override public void collectedAtPdcOutfeed(String packId) { ledger.confirmPdcCollection(packId); }
    @Override public boolean deferInitialPrlAssignments() { return true; }
    @Override public long classificationEpoch() {
        return ledger.snapshot().firstCollectedSheetByOrderId().size();
    }
}
