package online.davisfamily.warehouse.sim.dsp.p2p.lease;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import online.davisfamily.warehouse.sim.dsp.outbound.OutboundAllocationSnapshot;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteSnapshot;
import online.davisfamily.warehouse.sim.dsp.transport.routing.StationRoutedToteArrivalQueue;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperInputQueue;
import online.davisfamily.warehouse.sim.totebag.assignment.PrlState;
import online.davisfamily.warehouse.sim.totebag.control.SorterTipperDownstreamFlow;
import online.davisfamily.warehouse.sim.totebag.control.ToteToBagFlowController;
import online.davisfamily.warehouse.sim.totebag.control.ToteTrackTipperFlowController;
import online.davisfamily.warehouse.sim.totebag.conveyor.PcrConveyor;
import online.davisfamily.warehouse.sim.totebag.conveyor.PrlConveyor;
import online.davisfamily.warehouse.sim.totebag.handoff.StoredBagReceiver;
import online.davisfamily.warehouse.sim.totebag.machine.BaggingMachine;
import online.davisfamily.warehouse.sim.totebag.machine.SortingMachine;

public final class ToteToBagP2pLineActivityProbe implements P2pLineActivityProbe {
    private final P2pLineDefinition lineDefinition;
    private final StationRoutedToteArrivalQueue stationArrivalQueue;
    private final TipperInputQueue tipperInputQueue;
    private final ToteTrackTipperFlowController tipperFlowController;
    private final SortingMachine sortingMachine;
    private final SorterTipperDownstreamFlow sorterDownstreamFlow;
    private final ToteToBagFlowController toteToBagFlowController;
    private final PcrConveyor pcrConveyor;
    private final BaggingMachine baggingMachine;
    private final StoredBagReceiver bagReceiver;
    private final Supplier<OutboundAllocationSnapshot> outboundSnapshotSupplier;

    public ToteToBagP2pLineActivityProbe(
            P2pLineDefinition lineDefinition,
            StationRoutedToteArrivalQueue stationArrivalQueue,
            TipperInputQueue tipperInputQueue,
            ToteTrackTipperFlowController tipperFlowController,
            SortingMachine sortingMachine,
            SorterTipperDownstreamFlow sorterDownstreamFlow,
            ToteToBagFlowController toteToBagFlowController,
            PcrConveyor pcrConveyor,
            BaggingMachine baggingMachine,
            StoredBagReceiver bagReceiver,
            Supplier<OutboundAllocationSnapshot> outboundSnapshotSupplier) {
        if (lineDefinition == null
                || stationArrivalQueue == null
                || tipperInputQueue == null
                || tipperFlowController == null
                || sortingMachine == null
                || sorterDownstreamFlow == null
                || toteToBagFlowController == null
                || pcrConveyor == null
                || baggingMachine == null
                || bagReceiver == null
                || outboundSnapshotSupplier == null) {
            throw new IllegalArgumentException("P2P line activity dependencies must not be null");
        }
        if (!lineDefinition.destination().equals(stationArrivalQueue.destination())) {
            throw new IllegalArgumentException(
                    "stationArrivalQueue destination must match the P2P line definition");
        }
        this.lineDefinition = lineDefinition;
        this.stationArrivalQueue = stationArrivalQueue;
        this.tipperInputQueue = tipperInputQueue;
        this.tipperFlowController = tipperFlowController;
        this.sortingMachine = sortingMachine;
        this.sorterDownstreamFlow = sorterDownstreamFlow;
        this.toteToBagFlowController = toteToBagFlowController;
        this.pcrConveyor = pcrConveyor;
        this.baggingMachine = baggingMachine;
        this.bagReceiver = bagReceiver;
        this.outboundSnapshotSupplier = outboundSnapshotSupplier;
    }

    @Override
    public P2pLineActivitySnapshot snapshot() {
        OutboundAllocationSnapshot outboundSnapshot = outboundSnapshotSupplier.get();
        if (outboundSnapshot == null) {
            throw new IllegalStateException("outboundSnapshotSupplier returned null");
        }
        Optional<OutboundToteSnapshot> openOutboundTote = outboundSnapshot.openToteFor(
                lineDefinition.lineId());

        return new P2pLineActivitySnapshot(
                inputSnapshot(),
                packPathSnapshot(),
                baggingSnapshot(),
                openOutboundTote);
    }

    private P2pInputActivitySnapshot inputSnapshot() {
        return new P2pInputActivitySnapshot(
                stationArrivalQueue.snapshot().occupancy(),
                tipperInputQueue.snapshot().toteIds().size(),
                tipperFlowController.hasActiveTote(),
                tipperFlowController.getActiveDischarges().size());
    }

    private P2pPackPathActivitySnapshot packPathSnapshot() {
        Map<String, PrlConveyor> prlsById = toteToBagFlowController.getPrlsById();
        int nonIdlePrlCount = (int) prlsById.values().stream()
                .filter(prl -> prl.getAssignment().getState() != PrlState.IDLE)
                .count();
        int prlPackCount = prlsById.values().stream()
                .mapToInt(prl -> prl.getPacks().size())
                .sum();

        return new P2pPackPathActivitySnapshot(
                sortingMachine.getQueuedPacks().size(),
                sortingMachine.getReleasedPackCount(),
                sorterDownstreamFlow.getPendingSorterOutfeedCount(),
                toteToBagFlowController.getPdcLaneEntries().size(),
                toteToBagFlowController.getActivePdcTransfers().size(),
                nonIdlePrlCount,
                prlPackCount,
                toteToBagFlowController.getActivePrlToPcrTransfers().size(),
                pcrConveyor.getLaneEntries().size(),
                pcrConveyor.getTravellingGroups().size(),
                pcrConveyor.getReadyGroups().size(),
                toteToBagFlowController.getOutstandingExpectedBagGroupCount());
    }

    private P2pBaggingActivitySnapshot baggingSnapshot() {
        return new P2pBaggingActivitySnapshot(
                baggingMachine.getCurrentGroup() != null,
                baggingMachine.getReservedGroup() != null,
                baggingMachine.getActiveReservation() != null,
                baggingMachine.getPendingDischargeCount(),
                baggingMachine.hasActiveDischarge(),
                bagReceiver.getActiveReservation() != null,
                bagReceiver.isReceiving(),
                bagReceiver.getReceivedBags().size());
    }
}
