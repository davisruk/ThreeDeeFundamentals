package online.davisfamily.warehouse.sim.dsp.analysis.runtime;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import online.davisfamily.threedee.sim.framework.SimulationController;
import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundAllocationSnapshot;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteAllocationController;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteAllocator;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.arrival.P2pStationProcessingTarget;
import online.davisfamily.warehouse.sim.dsp.p2p.arrival.P2pTipperArrivalTarget;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.ToteToBagP2pLineActivityProbe;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineDefinition;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingBinding;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingCoordinator;
import online.davisfamily.warehouse.sim.dsp.transport.routing.StationRoutedToteArrivalQueue;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperInputQueue;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperInputQueueController;
import online.davisfamily.warehouse.sim.totebag.assignment.PrlState;
import online.davisfamily.warehouse.sim.totebag.control.SorterTipperDownstreamFlow;
import online.davisfamily.warehouse.sim.totebag.control.ToteToBagFlowController;
import online.davisfamily.warehouse.sim.totebag.control.ToteTrackTipperFlowController;
import online.davisfamily.warehouse.sim.totebag.conveyor.PcrConveyor;
import online.davisfamily.warehouse.sim.totebag.conveyor.PrlConveyor;
import online.davisfamily.warehouse.sim.totebag.device.PdcDiversionDevice;
import online.davisfamily.warehouse.sim.totebag.handoff.StoredBagReceiver;
import online.davisfamily.warehouse.sim.totebag.machine.BaggingMachine;
import online.davisfamily.warehouse.sim.totebag.machine.SortingMachine;
import online.davisfamily.warehouse.sim.totebag.machine.TippingMachine;

/**
 * One non-rendering physical P2P tote-to-bag line.
 *
 * <p>Station arrival ownership remains outside this runtime. Callers install
 * {@link #stationProcessingBinding()} into the shared station-processing
 * claimant; the target then deposits the exact routed tote into this line's
 * tipper input queue. The line's input controller is the only owner that
 * moves a queued tote into the live tipper flow.</p>
 */
public final class DspHeadlessP2pLineRuntime implements AutoCloseable {
    private final SimulationWorld simulationWorld;
    private final DspHeadlessP2pLineConfig config;
    private final StationRoutedToteArrivalQueue stationArrivalQueue;
    private final TipperInputQueue tipperInputQueue;
    private final P2pTipperArrivalTarget arrivalTarget;
    private final P2pStationProcessingTarget stationProcessingTarget;
    private final StationProcessingBinding stationProcessingBinding;
    private final TippingMachine tippingMachine;
    private final SortingMachine sortingMachine;
    private final PcrConveyor pcrConveyor;
    private final BaggingMachine baggingMachine;
    private final StoredBagReceiver bagReceiver;
    private final List<PrlConveyor> prlConveyors;
    private final List<PdcDiversionDevice> pdcDiversionDevices;
    private final SorterTipperDownstreamFlow sorterDownstreamFlow;
    private final ToteTrackTipperFlowController tipperFlowController;
    private final ToteToBagFlowController toteToBagFlowController;
    private final TipperInputQueueController tipperInputQueueController;
    private final OutboundToteAllocationController outboundToteAllocationController;
    private final ToteToBagP2pLineActivityProbe activityProbe;
    private final List<SimulationController> lineControllers;
    private boolean closed;

    DspHeadlessP2pLineRuntime(
            SimulationWorld simulationWorld,
            DspHeadlessP2pLineConfig config,
            StationRoutedToteArrivalQueue stationArrivalQueue,
            TipperInputQueue tipperInputQueue,
            P2pTipperArrivalTarget arrivalTarget,
            P2pStationProcessingTarget stationProcessingTarget,
            TippingMachine tippingMachine,
            SortingMachine sortingMachine,
            PcrConveyor pcrConveyor,
            BaggingMachine baggingMachine,
            StoredBagReceiver bagReceiver,
            List<PrlConveyor> prlConveyors,
            List<PdcDiversionDevice> pdcDiversionDevices,
            SorterTipperDownstreamFlow sorterDownstreamFlow,
            ToteTrackTipperFlowController tipperFlowController,
            ToteToBagFlowController toteToBagFlowController,
            TipperInputQueueController tipperInputQueueController,
            OutboundToteAllocationController outboundToteAllocationController,
            ToteToBagP2pLineActivityProbe activityProbe) {
        if (simulationWorld == null
                || config == null
                || stationArrivalQueue == null
                || tipperInputQueue == null
                || arrivalTarget == null
                || stationProcessingTarget == null
                || tippingMachine == null
                || sortingMachine == null
                || pcrConveyor == null
                || baggingMachine == null
                || bagReceiver == null
                || prlConveyors == null
                || pdcDiversionDevices == null
                || sorterDownstreamFlow == null
                || tipperFlowController == null
                || toteToBagFlowController == null
                || tipperInputQueueController == null
                || outboundToteAllocationController == null
                || activityProbe == null) {
            throw new IllegalArgumentException("headless P2P line runtime inputs must not be null");
        }
        if (prlConveyors.size() != DspHeadlessP2pLineRuntimeFactory.PRL_COUNT_PER_LINE
                || pdcDiversionDevices.size() != prlConveyors.size()) {
            throw new IllegalArgumentException(
                    "headless P2P line must contain exactly 31 PRLs and matching diverters");
        }
        this.simulationWorld = simulationWorld;
        this.config = config;
        this.stationArrivalQueue = stationArrivalQueue;
        this.tipperInputQueue = tipperInputQueue;
        this.arrivalTarget = arrivalTarget;
        this.stationProcessingTarget = stationProcessingTarget;
        this.stationProcessingBinding = new StationProcessingBinding(
                stationArrivalQueue, stationProcessingTarget);
        this.tippingMachine = tippingMachine;
        this.sortingMachine = sortingMachine;
        this.pcrConveyor = pcrConveyor;
        this.baggingMachine = baggingMachine;
        this.bagReceiver = bagReceiver;
        this.prlConveyors = List.copyOf(prlConveyors);
        this.pdcDiversionDevices = List.copyOf(pdcDiversionDevices);
        this.sorterDownstreamFlow = sorterDownstreamFlow;
        this.tipperFlowController = tipperFlowController;
        this.toteToBagFlowController = toteToBagFlowController;
        this.tipperInputQueueController = tipperInputQueueController;
        this.outboundToteAllocationController = outboundToteAllocationController;
        this.activityProbe = activityProbe;
        this.lineControllers = List.of(
                tipperFlowController,
                toteToBagFlowController,
                tipperInputQueueController,
                outboundToteAllocationController);
    }

    public SimulationWorld simulationWorld() {
        return simulationWorld;
    }

    public DspHeadlessP2pLineConfig config() {
        return config;
    }

    public P2pLineDefinition lineDefinition() {
        return config.lineDefinition();
    }

    public StationRoutedToteArrivalQueue stationArrivalQueue() {
        return stationArrivalQueue;
    }

    public TipperInputQueue tipperInputQueue() {
        return tipperInputQueue;
    }

    public P2pTipperArrivalTarget arrivalTarget() {
        return arrivalTarget;
    }

    public P2pStationProcessingTarget stationProcessingTarget() {
        return stationProcessingTarget;
    }

    public StationProcessingBinding stationProcessingBinding() {
        return stationProcessingBinding;
    }

    public StationProcessingCoordinator stationProcessingCoordinator() {
        return config.stationProcessingCoordinator();
    }

    public TippingMachine tippingMachine() {
        return tippingMachine;
    }

    public SortingMachine sortingMachine() {
        return sortingMachine;
    }

    public PcrConveyor pcrConveyor() {
        return pcrConveyor;
    }

    public BaggingMachine baggingMachine() {
        return baggingMachine;
    }

    public StoredBagReceiver bagReceiver() {
        return bagReceiver;
    }

    public List<PrlConveyor> prlConveyors() {
        return prlConveyors;
    }

    public List<PdcDiversionDevice> pdcDiversionDevices() {
        return pdcDiversionDevices;
    }

    public SorterTipperDownstreamFlow sorterDownstreamFlow() {
        return sorterDownstreamFlow;
    }

    public ToteTrackTipperFlowController tipperFlowController() {
        return tipperFlowController;
    }

    public ToteToBagFlowController toteToBagFlowController() {
        return toteToBagFlowController;
    }

    public TipperInputQueueController tipperInputQueueController() {
        return tipperInputQueueController;
    }

    public OutboundToteAllocationController outboundToteAllocationController() {
        return outboundToteAllocationController;
    }

    public OutboundToteAllocator outboundToteAllocator() {
        return config.outboundToteAllocator();
    }

    public ToteToBagP2pLineActivityProbe activityProbe() {
        return activityProbe;
    }

    /** Returns the line controllers in their registration order. */
    public List<SimulationController> lineControllers() {
        return lineControllers;
    }

    public void update(double dtSeconds) {
        simulationWorld.update(dtSeconds);
    }

    public Optional<OutboundToteSnapshot> closeOutboundToteForApplicableWorkCompletion(
            Duration time) {
        return outboundToteAllocator().closeForApplicableWorkCompletion(
                lineDefinition().lineId(), time);
    }

    public Optional<OutboundToteSnapshot> closeOutboundToteForHardCutoff(Duration time) {
        return outboundToteAllocator().closeForHardCutoff(
                lineDefinition().lineId(), time);
    }

    public DspHeadlessP2pLineRuntimeSnapshot snapshot() {
        P2pLineActivitySnapshotWithOutbound activityAndOutbound = snapshotActivity();
        Map<String, PrlState> prlStates = new LinkedHashMap<>();
        Map<String, Integer> prlReceivedPackCounts = new LinkedHashMap<>();
        for (PrlConveyor prl : prlConveyors) {
            prlStates.put(prl.getId(), prl.getAssignment().getState());
            prlReceivedPackCounts.put(prl.getId(), prl.getAssignment().getReceivedPackCount());
        }

        return new DspHeadlessP2pLineRuntimeSnapshot(
                lineDefinition(),
                activityAndOutbound.activity(),
                stationProcessingCoordinator().snapshot(),
                prlStates,
                prlReceivedPackCounts,
                baggingMachine.getCompletedCorrelationIds(),
                activityAndOutbound.outbound(),
                closed);
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
    }

    private P2pLineActivitySnapshotWithOutbound snapshotActivity() {
        OutboundAllocationSnapshot outbound = outboundToteAllocator().snapshot();
        var activity = activityProbe.snapshot();
        return new P2pLineActivitySnapshotWithOutbound(activity, outbound);
    }

    private record P2pLineActivitySnapshotWithOutbound(
            online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineActivitySnapshot activity,
            OutboundAllocationSnapshot outbound) {
    }
}
