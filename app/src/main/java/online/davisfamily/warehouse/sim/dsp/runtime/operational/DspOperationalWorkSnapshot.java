package online.davisfamily.warehouse.sim.dsp.runtime.operational;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import online.davisfamily.warehouse.sim.dsp.av02.Av02AllocatedTote;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;

/** Immutable remaining work and scalar results; contains no retired physical identities. */
public final class DspOperationalWorkSnapshot {
    private final List<InboundToteManifest> remainingInboundTotes;
    private final List<DspSchedulerOrderState> unallocatedEmptyOrders;
    private final List<Av02AllocatedTote> liveAv02Totes;
    private final Map<String, List<PhysicalToteId>> remainingP2pToteIdsByServiceCentre;
    private final Map<String, RemainingCounts> remainingCountsByServiceCentre;
    private final Map<String, CentreTotals> totalsByServiceCentre;
    private final Map<P2pLineId, LineTotals> totalsByLine;

    // The ledger supplies already-frozen large subviews, shared across unrelated mutations.
    // Only the small centre/line maps are copied here; no mutable owner is retained.
    DspOperationalWorkSnapshot(
            List<InboundToteManifest> remainingInboundTotes,
            List<DspSchedulerOrderState> unallocatedEmptyOrders,
            List<Av02AllocatedTote> liveAv02Totes,
            Map<String, List<PhysicalToteId>> remainingP2pToteIdsByServiceCentre,
            Map<String, RemainingCounts> remainingCountsByServiceCentre,
            Map<String, CentreTotals> totalsByServiceCentre,
            Map<P2pLineId, LineTotals> totalsByLine) {
        this.remainingInboundTotes = remainingInboundTotes;
        this.unallocatedEmptyOrders = unallocatedEmptyOrders;
        this.liveAv02Totes = liveAv02Totes;
        this.remainingP2pToteIdsByServiceCentre = remainingP2pToteIdsByServiceCentre;
        this.remainingCountsByServiceCentre = frozenMap(remainingCountsByServiceCentre);
        this.totalsByServiceCentre = frozenMap(totalsByServiceCentre);
        this.totalsByLine = frozenMap(totalsByLine);
    }

    public List<InboundToteManifest> remainingInboundTotes() {
        return remainingInboundTotes;
    }

    public List<DspSchedulerOrderState> unallocatedEmptyOrders() {
        return unallocatedEmptyOrders;
    }

    public List<Av02AllocatedTote> liveAv02Totes() {
        return liveAv02Totes;
    }

    public Map<String, List<PhysicalToteId>> remainingP2pToteIdsByServiceCentre() {
        return remainingP2pToteIdsByServiceCentre;
    }

    public Map<String, RemainingCounts> remainingCountsByServiceCentre() {
        return remainingCountsByServiceCentre;
    }

    public Map<String, CentreTotals> totalsByServiceCentre() {
        return totalsByServiceCentre;
    }

    public Map<P2pLineId, LineTotals> totalsByLine() {
        return totalsByLine;
    }

    private static <K, V> Map<K, V> frozenMap(Map<K, V> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public record RemainingCounts(int totes, int unallocatedEmptyOrders, int packs, int bags) {
        public RemainingCounts {
            if (totes < 0 || unallocatedEmptyOrders < 0 || packs < 0 || bags < 0) {
                throw new IllegalArgumentException("Remaining counts must be nonnegative");
            }
        }
    }

    public record CentreTotals(long consumedAtAdapting, long consumedAtP2p,
            long allocatedBags, long allocatedPacks, long affectedAllocatedBags) {
        public CentreTotals {
            if (consumedAtAdapting < 0 || consumedAtP2p < 0 || allocatedBags < 0
                    || allocatedPacks < 0 || affectedAllocatedBags < 0) {
                throw new IllegalArgumentException("Centre totals must be nonnegative");
            }
        }
    }

    public record LineTotals(long consumedTotes, long allocatedBags) {
        public LineTotals {
            if (consumedTotes < 0 || allocatedBags < 0) {
                throw new IllegalArgumentException("Line totals must be nonnegative");
            }
        }
    }
}
