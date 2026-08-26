package online.davisfamily.warehouse.sim.dsp.p2p.lease;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.outbound.OutboundAllocationSnapshot;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteAllocator;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteSnapshot;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.p2p.arrival.DspP2pArrivalConsumerRuntime;

public final class DspP2pStickyLeaseRuntime implements AutoCloseable {
    private final List<P2pLineDefinition> definitions;
    private final Map<P2pLineId, P2pLineActivityProbe> activityProbes;
    private final P2pLineLeaseRegistry leaseRegistry;
    private final P2pReleaseAssignmentCommitter releaseAssignmentCommitter;
    private final OperationalP2pReleaseAssignmentCommitter operationalReleaseAssignmentCommitter;
    private final OutboundToteAllocator outboundToteAllocator;
    private final DspP2pArrivalConsumerRuntime arrivalRuntime;
    private boolean closed;

    DspP2pStickyLeaseRuntime(
            List<P2pLineDefinition> definitions,
            Map<P2pLineId, P2pLineActivityProbe> activityProbes,
            P2pLineLeaseRegistry leaseRegistry,
            P2pReleaseAssignmentCommitter releaseAssignmentCommitter,
            OutboundToteAllocator outboundToteAllocator,
            DspP2pArrivalConsumerRuntime arrivalRuntime) {
        this(
                definitions,
                activityProbes,
                leaseRegistry,
                releaseAssignmentCommitter,
                releaseAssignmentCommitter instanceof OperationalP2pReleaseAssignmentCommitter
                        ? (OperationalP2pReleaseAssignmentCommitter) releaseAssignmentCommitter
                        : OperationalP2pReleaseAssignmentCommitter.NO_OP,
                outboundToteAllocator,
                arrivalRuntime);
    }

    DspP2pStickyLeaseRuntime(
            List<P2pLineDefinition> definitions,
            Map<P2pLineId, P2pLineActivityProbe> activityProbes,
            P2pLineLeaseRegistry leaseRegistry,
            P2pReleaseAssignmentCommitter releaseAssignmentCommitter,
            OperationalP2pReleaseAssignmentCommitter operationalReleaseAssignmentCommitter,
            OutboundToteAllocator outboundToteAllocator,
            DspP2pArrivalConsumerRuntime arrivalRuntime) {
        if (definitions == null
                || activityProbes == null
                || leaseRegistry == null
                || releaseAssignmentCommitter == null
                || operationalReleaseAssignmentCommitter == null
                || outboundToteAllocator == null
                || arrivalRuntime == null) {
            throw new IllegalArgumentException("sticky lease runtime inputs must not be null");
        }
        this.definitions = List.copyOf(definitions);
        this.activityProbes = Map.copyOf(activityProbes);
        this.leaseRegistry = leaseRegistry;
        this.releaseAssignmentCommitter = releaseAssignmentCommitter;
        this.operationalReleaseAssignmentCommitter = operationalReleaseAssignmentCommitter;
        this.outboundToteAllocator = outboundToteAllocator;
        this.arrivalRuntime = arrivalRuntime;
    }

    public List<P2pLineDefinition> lineDefinitions() {
        return definitions;
    }

    public P2pReleaseAssignmentCommitter releaseAssignmentCommitter() {
        return releaseAssignmentCommitter;
    }

    public OperationalP2pReleaseAssignmentCommitter operationalReleaseAssignmentCommitter() {
        return operationalReleaseAssignmentCommitter;
    }

    public P2pLineLeaseCatalogSnapshot leaseSnapshot() {
        return leaseRegistry.snapshot(activitySnapshots());
    }

    public DspP2pStickyLeaseRuntimeSnapshot snapshot() {
        P2pLineLeaseCatalogSnapshot leases = leaseSnapshot();
        OutboundAllocationSnapshot outbound = outboundToteAllocator.snapshot();
        List<DspP2pStickyLineSnapshot> lines = new ArrayList<>();
        for (P2pLineLeaseSnapshot lease : leases.lines()) {
            P2pLineId lineId = lease.definition().lineId();
            Optional<OutboundToteSnapshot> latestClosed = outbound.closedTotes().stream()
                    .filter(tote -> tote.p2pLineId().equals(lineId))
                    .reduce((first, second) -> second);
            lines.add(new DspP2pStickyLineSnapshot(
                    lease,
                    latestClosed,
                    leaseRegistry.lastTransitionFor(lineId)));
        }
        return new DspP2pStickyLeaseRuntimeSnapshot(lines);
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        arrivalRuntime.close();
        closed = true;
    }

    private Map<P2pLineId, P2pLineActivitySnapshot> activitySnapshots() {
        Map<P2pLineId, P2pLineActivitySnapshot> snapshots = new LinkedHashMap<>();
        for (P2pLineDefinition definition : definitions) {
            P2pLineActivitySnapshot activity =
                    activityProbes.get(definition.lineId()).snapshot();
            if (activity == null) {
                throw new IllegalStateException("P2P line activity probe returned null");
            }
            snapshots.put(definition.lineId(), activity);
        }
        return snapshots;
    }
}
