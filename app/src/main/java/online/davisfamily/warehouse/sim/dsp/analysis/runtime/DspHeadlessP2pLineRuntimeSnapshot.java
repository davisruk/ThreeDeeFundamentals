package online.davisfamily.warehouse.sim.dsp.analysis.runtime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import online.davisfamily.warehouse.sim.dsp.outbound.OutboundAllocationSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineActivitySnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineDefinition;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingSnapshot;
import online.davisfamily.warehouse.sim.totebag.assignment.PrlState;

/** Fresh immutable inspection values for one headless P2P line. */
public record DspHeadlessP2pLineRuntimeSnapshot(
        P2pLineDefinition lineDefinition,
        P2pLineActivitySnapshot activity,
        StationProcessingSnapshot stationProcessing,
        Map<String, PrlState> prlStatesById,
        Map<String, Integer> prlReceivedPackCountsById,
        List<String> completedBagCorrelationIds,
        OutboundAllocationSnapshot outboundAllocation,
        boolean closed) {

    public DspHeadlessP2pLineRuntimeSnapshot {
        if (lineDefinition == null) {
            throw new IllegalArgumentException("lineDefinition must not be null");
        }
        if (activity == null) {
            throw new IllegalArgumentException("activity must not be null");
        }
        if (stationProcessing == null) {
            throw new IllegalArgumentException("stationProcessing must not be null");
        }
        if (prlStatesById == null || prlReceivedPackCountsById == null) {
            throw new IllegalArgumentException("PRL snapshots must not be null");
        }
        if (!prlStatesById.keySet().equals(prlReceivedPackCountsById.keySet())) {
            throw new IllegalArgumentException("PRL snapshot maps must have matching keys");
        }
        Map<String, PrlState> stateCopy = new LinkedHashMap<>();
        prlStatesById.forEach((prlId, state) -> {
            if (prlId == null || prlId.isBlank() || state == null) {
                throw new IllegalArgumentException("PRL states must not contain null or blank values");
            }
            stateCopy.put(prlId, state);
        });
        Map<String, Integer> countCopy = new LinkedHashMap<>();
        prlReceivedPackCountsById.forEach((prlId, count) -> {
            if (prlId == null || prlId.isBlank() || count == null || count < 0) {
                throw new IllegalArgumentException(
                        "PRL received pack counts must be nonnegative and keyed by PRL IDs");
            }
            countCopy.put(prlId, count);
        });
        if (completedBagCorrelationIds == null
                || completedBagCorrelationIds.stream().anyMatch(id -> id == null || id.isBlank())) {
            throw new IllegalArgumentException(
                    "completedBagCorrelationIds must not contain null or blank values");
        }
        if (outboundAllocation == null) {
            throw new IllegalArgumentException("outboundAllocation must not be null");
        }
        prlStatesById = Map.copyOf(stateCopy);
        prlReceivedPackCountsById = Map.copyOf(countCopy);
        completedBagCorrelationIds = List.copyOf(completedBagCorrelationIds);
    }

    public boolean processingDrained() {
        return activity.processingDrained();
    }

    public boolean quiescent() {
        return activity.quiescent();
    }
}
