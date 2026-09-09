package online.davisfamily.warehouse.sim.dsp.analysis.runtime;

import java.util.List;

import online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayCutoffSnapshot;
import online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayRuntimeState;
import online.davisfamily.warehouse.sim.dsp.analysis.DspServiceCentreCompletionSnapshot;
import online.davisfamily.warehouse.sim.dsp.av02.Av02InventorySnapshot;
import online.davisfamily.warehouse.sim.dsp.av02.DspAv02AllocationRuntimeSnapshot;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleSnapshot;
import online.davisfamily.warehouse.sim.dsp.osr.OsrInventorySnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.allocation.DspP2pElasticAllocationRuntimeSnapshot;
import online.davisfamily.warehouse.sim.dsp.runtime.operational.DspOperationalReleaseControllerSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;
import online.davisfamily.warehouse.sim.dsp.station.continuation.StationRouteContinuationControllerSnapshot;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationArrivalClaimControllerSnapshot;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingSnapshot;
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClockSnapshot;
import online.davisfamily.warehouse.sim.dsp.transport.OsrOutboundTransportQueueSnapshot;
import online.davisfamily.warehouse.sim.dsp.transport.routing.StationRoutedToteArrivalQueueSnapshot;
import online.davisfamily.warehouse.sim.dsp.transport.routing.WarehouseRouteCatalogSnapshot;
import online.davisfamily.warehouse.sim.dsp.transport.routing.WarehouseTransportArrivalControllerSnapshot;
import online.davisfamily.warehouse.sim.dsp.transport.routing.WarehouseTransportInFlightSnapshot;
import online.davisfamily.warehouse.sim.dsp.transport.routing.WarehouseTransportIngressControllerSnapshot;
import online.davisfamily.warehouse.sim.dsp.supply.DspSupplySnapshot;

/** Immutable inspection boundary for one composed full-day runtime. */
public record DspFullDayAnalysisRuntimeSnapshot(
        DspFullDayRuntimeState state,
        DspOperationalClockSnapshot clock,
        WarehouseSchedulerSnapshot scheduler,
        DspSupplySnapshot supply,
        OsrInventorySnapshot osr,
        Av02InventorySnapshot av02,
        PhysicalToteLifecycleSnapshot lifecycle,
        DspAv02AllocationRuntimeSnapshot av02Allocation,
        DspOperationalReleaseControllerSnapshot operationalRelease,
        DspP2pElasticAllocationRuntimeSnapshot elastic,
        List<DspHeadlessP2pLineRuntimeSnapshot> p2pLines,
        StationProcessingSnapshot stationProcessing,
        List<StationArrivalClaimControllerSnapshot> stationClaims,
        WarehouseRouteCatalogSnapshot routes,
        OsrOutboundTransportQueueSnapshot outboundTransport,
        WarehouseTransportInFlightSnapshot transportInFlight,
        WarehouseTransportIngressControllerSnapshot transportIngress,
        WarehouseTransportArrivalControllerSnapshot transportArrival,
        List<StationRoutedToteArrivalQueueSnapshot> stationArrivals,
        StationRouteContinuationControllerSnapshot continuation,
        List<DspServiceCentreCompletionSnapshot> completions,
        DspFullDayCutoffSnapshot cutoff,
        boolean closed) {

    public DspFullDayAnalysisRuntimeSnapshot {
        if (state == null || clock == null || scheduler == null || supply == null
                || osr == null || av02 == null || lifecycle == null || av02Allocation == null
                || operationalRelease == null || elastic == null || p2pLines == null
                || stationProcessing == null || stationClaims == null || routes == null
                || outboundTransport == null || transportInFlight == null
                || transportIngress == null || transportArrival == null
                || stationArrivals == null || continuation == null || completions == null
                || cutoff == null) {
            throw new IllegalArgumentException("full-day runtime snapshot values must not be null");
        }
        if (p2pLines.stream().anyMatch(value -> value == null)
                || stationClaims.stream().anyMatch(value -> value == null)
                || stationArrivals.stream().anyMatch(value -> value == null)
                || completions.stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException("full-day runtime snapshot lists must not contain null");
        }
        p2pLines = List.copyOf(p2pLines);
        stationClaims = List.copyOf(stationClaims);
        stationArrivals = List.copyOf(stationArrivals);
        completions = List.copyOf(completions);
    }

    public List<DspHeadlessP2pLineRuntimeSnapshot> lineSnapshots() {
        return p2pLines;
    }

    public List<DspServiceCentreCompletionSnapshot> completionSnapshots() {
        return completions;
    }

    public DspFullDayCutoffSnapshot cutoffSnapshot() {
        return cutoff;
    }
}
