package online.davisfamily.warehouse.sim.totebag.control;

import online.davisfamily.warehouse.sim.totebag.plan.*;
import online.davisfamily.warehouse.sim.totebag.pack.*;
import online.davisfamily.warehouse.sim.totebag.machine.*;
import online.davisfamily.warehouse.sim.totebag.conveyor.*;
import online.davisfamily.warehouse.sim.totebag.transfer.*;
import online.davisfamily.warehouse.sim.totebag.device.*;
import online.davisfamily.warehouse.sim.totebag.assignment.*;
import online.davisfamily.warehouse.sim.totebag.control.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.SimulationController;
import online.davisfamily.warehouse.sim.totebag.handoff.PackGroupReceiver;
import online.davisfamily.warehouse.sim.totebag.handoff.PackGroupReservation;

public class ToteToBagFlowController implements SimulationController {
    private final ToteLoadPlan toteLoadPlan;
    private final ToteToBagBatchPlan batchPlan;
    private final TippingMachine tippingMachine;
    private final SortingMachine sortingMachine;
    private final PdcConveyor pdcConveyor;
    private final PcrConveyor pcrConveyor;
    private final PackGroupReceiver downstreamPackGroupReceiver;
    private final ToteToBagAssignmentPlanner assignmentPlanner;
    private final Map<String, PrlConveyor> prlsById = new LinkedHashMap<>();
    private final Map<String, PdcDiversionDevice> pdcDiversionDevicesByPrlId = new LinkedHashMap<>();
    private final Map<String, Pack> observedPacksById = new LinkedHashMap<>();
    private final List<PdcTransfer> activePdcTransfers = new ArrayList<>();
    private final List<PrlToPcrTransfer> activePrlToPcrTransfers = new ArrayList<>();
    private final Queue<ReleasedPackGroup> releasedGroups = new ArrayDeque<>();
    private final PdcTransferDurationProvider pdcTransferDurationProvider;
    private final PdcDiversionDistanceProvider pdcDiversionDistanceProvider;
    private final PrlToPcrTransferDurationProvider prlToPcrTransferDurationProvider;
    private final PrlToPcrEntryDistanceProvider prlToPcrEntryDistanceProvider;
    private PackGroupReservation baggingReservation;
    private boolean initialized;
    private boolean toteLoaded;

    public ToteToBagFlowController(
            ToteLoadPlan toteLoadPlan,
            PdcConveyor pdcConveyor,
            PcrConveyor pcrConveyor,
            PackGroupReceiver downstreamPackGroupReceiver,
            ToteToBagAssignmentPlanner assignmentPlanner,
            List<PrlConveyor> prlConveyors,
            List<PdcDiversionDevice> pdcDiversionDevices,
            PdcTransferDurationProvider pdcTransferDurationProvider,
            PdcDiversionDistanceProvider pdcDiversionDistanceProvider,
            PrlToPcrTransferDurationProvider prlToPcrTransferDurationProvider,
            PrlToPcrEntryDistanceProvider prlToPcrEntryDistanceProvider) {
        this(
                toteLoadPlan,
                ToteToBagBatchPlan.fromToteLoadPlan(toteLoadPlan),
                pdcConveyor,
                pcrConveyor,
                downstreamPackGroupReceiver,
                assignmentPlanner,
                prlConveyors,
                pdcDiversionDevices,
                pdcTransferDurationProvider,
                pdcDiversionDistanceProvider,
                prlToPcrTransferDurationProvider,
                prlToPcrEntryDistanceProvider);
    }

    public ToteToBagFlowController(
            ToteLoadPlan toteLoadPlan,
            ToteToBagBatchPlan batchPlan,
            PdcConveyor pdcConveyor,
            PcrConveyor pcrConveyor,
            PackGroupReceiver downstreamPackGroupReceiver,
            ToteToBagAssignmentPlanner assignmentPlanner,
            List<PrlConveyor> prlConveyors,
            List<PdcDiversionDevice> pdcDiversionDevices,
            PdcTransferDurationProvider pdcTransferDurationProvider,
            PdcDiversionDistanceProvider pdcDiversionDistanceProvider,
            PrlToPcrTransferDurationProvider prlToPcrTransferDurationProvider,
            PrlToPcrEntryDistanceProvider prlToPcrEntryDistanceProvider) {
        this(
                toteLoadPlan,
                batchPlan,
                null,
                null,
                pdcConveyor,
                pcrConveyor,
                downstreamPackGroupReceiver,
                assignmentPlanner,
                prlConveyors,
                pdcDiversionDevices,
                pdcTransferDurationProvider,
                pdcDiversionDistanceProvider,
                prlToPcrTransferDurationProvider,
                prlToPcrEntryDistanceProvider);
    }

    public ToteToBagFlowController(
            ToteLoadPlan toteLoadPlan,
            ToteToBagBatchPlan batchPlan,
            TippingMachine tippingMachine,
            SortingMachine sortingMachine,
            PdcConveyor pdcConveyor,
            PcrConveyor pcrConveyor,
            PackGroupReceiver downstreamPackGroupReceiver,
            ToteToBagAssignmentPlanner assignmentPlanner,
            List<PrlConveyor> prlConveyors) {
        this(
                toteLoadPlan,
                batchPlan,
                tippingMachine,
                sortingMachine,
                pdcConveyor,
                pcrConveyor,
                downstreamPackGroupReceiver,
                assignmentPlanner,
                prlConveyors,
                createDefaultDiversionDevices(prlConveyors),
                ignored -> 0.45d,
                (ignoredPrlId, pack) -> pack.getDimensions().length(),
                ignored -> 0.25d,
                (ignoredPrlId, pack) -> pack.getDimensions().length());
    }

    public ToteToBagFlowController(
            ToteLoadPlan toteLoadPlan,
            TippingMachine tippingMachine,
            SortingMachine sortingMachine,
            PdcConveyor pdcConveyor,
            PcrConveyor pcrConveyor,
            PackGroupReceiver downstreamPackGroupReceiver,
            ToteToBagAssignmentPlanner assignmentPlanner,
            List<PrlConveyor> prlConveyors) {
        this(
                toteLoadPlan,
                ToteToBagBatchPlan.fromToteLoadPlan(toteLoadPlan),
                tippingMachine,
                sortingMachine,
                pdcConveyor,
                pcrConveyor,
                downstreamPackGroupReceiver,
                assignmentPlanner,
                prlConveyors,
                createDefaultDiversionDevices(prlConveyors),
                ignored -> 0.45d,
                (ignoredPrlId, pack) -> pack.getDimensions().length(),
                ignored -> 0.25d,
                (ignoredPrlId, pack) -> pack.getDimensions().length());
    }

    public ToteToBagFlowController(
            ToteLoadPlan toteLoadPlan,
            ToteToBagBatchPlan batchPlan,
            TippingMachine tippingMachine,
            SortingMachine sortingMachine,
            PdcConveyor pdcConveyor,
            PcrConveyor pcrConveyor,
            PackGroupReceiver downstreamPackGroupReceiver,
            ToteToBagAssignmentPlanner assignmentPlanner,
            List<PrlConveyor> prlConveyors,
            double pdcTransferDurationSeconds) {
        this(
                toteLoadPlan,
                batchPlan,
                tippingMachine,
                sortingMachine,
                pdcConveyor,
                pcrConveyor,
                downstreamPackGroupReceiver,
                assignmentPlanner,
                prlConveyors,
                createDefaultDiversionDevices(prlConveyors),
                ignored -> pdcTransferDurationSeconds,
                (ignoredPrlId, pack) -> pack.getDimensions().length(),
                ignored -> 0.25d,
                (ignoredPrlId, pack) -> pack.getDimensions().length());
    }

    public ToteToBagFlowController(
            ToteLoadPlan toteLoadPlan,
            TippingMachine tippingMachine,
            SortingMachine sortingMachine,
            PdcConveyor pdcConveyor,
            PcrConveyor pcrConveyor,
            PackGroupReceiver downstreamPackGroupReceiver,
            ToteToBagAssignmentPlanner assignmentPlanner,
            List<PrlConveyor> prlConveyors,
            double pdcTransferDurationSeconds) {
        this(
                toteLoadPlan,
                ToteToBagBatchPlan.fromToteLoadPlan(toteLoadPlan),
                tippingMachine,
                sortingMachine,
                pdcConveyor,
                pcrConveyor,
                downstreamPackGroupReceiver,
                assignmentPlanner,
                prlConveyors,
                createDefaultDiversionDevices(prlConveyors),
                ignored -> pdcTransferDurationSeconds,
                (ignoredPrlId, pack) -> pack.getDimensions().length(),
                ignored -> 0.25d,
                (ignoredPrlId, pack) -> pack.getDimensions().length());
    }

    public ToteToBagFlowController(
            ToteLoadPlan toteLoadPlan,
            ToteToBagBatchPlan batchPlan,
            TippingMachine tippingMachine,
            SortingMachine sortingMachine,
            PdcConveyor pdcConveyor,
            PcrConveyor pcrConveyor,
            PackGroupReceiver downstreamPackGroupReceiver,
            ToteToBagAssignmentPlanner assignmentPlanner,
            List<PrlConveyor> prlConveyors,
            List<PdcDiversionDevice> pdcDiversionDevices,
            PdcTransferDurationProvider pdcTransferDurationProvider,
            double prlToPcrTransferDurationSeconds) {
        this(
                toteLoadPlan,
                batchPlan,
                tippingMachine,
                sortingMachine,
                pdcConveyor,
                pcrConveyor,
                downstreamPackGroupReceiver,
                assignmentPlanner,
                prlConveyors,
                pdcDiversionDevices,
                pdcTransferDurationProvider,
                (ignoredPrlId, pack) -> pack.getDimensions().length(),
                ignored -> prlToPcrTransferDurationSeconds,
                (ignoredPrlId, pack) -> pack.getDimensions().length());
    }

    public ToteToBagFlowController(
            ToteLoadPlan toteLoadPlan,
            TippingMachine tippingMachine,
            SortingMachine sortingMachine,
            PdcConveyor pdcConveyor,
            PcrConveyor pcrConveyor,
            PackGroupReceiver downstreamPackGroupReceiver,
            ToteToBagAssignmentPlanner assignmentPlanner,
            List<PrlConveyor> prlConveyors,
            List<PdcDiversionDevice> pdcDiversionDevices,
            PdcTransferDurationProvider pdcTransferDurationProvider,
            double prlToPcrTransferDurationSeconds) {
        this(
                toteLoadPlan,
                ToteToBagBatchPlan.fromToteLoadPlan(toteLoadPlan),
                tippingMachine,
                sortingMachine,
                pdcConveyor,
                pcrConveyor,
                downstreamPackGroupReceiver,
                assignmentPlanner,
                prlConveyors,
                pdcDiversionDevices,
                pdcTransferDurationProvider,
                (ignoredPrlId, pack) -> pack.getDimensions().length(),
                ignored -> prlToPcrTransferDurationSeconds,
                (ignoredPrlId, pack) -> pack.getDimensions().length());
    }

    public ToteToBagFlowController(
            ToteLoadPlan toteLoadPlan,
            ToteToBagBatchPlan batchPlan,
            TippingMachine tippingMachine,
            SortingMachine sortingMachine,
            PdcConveyor pdcConveyor,
            PcrConveyor pcrConveyor,
            PackGroupReceiver downstreamPackGroupReceiver,
            ToteToBagAssignmentPlanner assignmentPlanner,
            List<PrlConveyor> prlConveyors,
            List<PdcDiversionDevice> pdcDiversionDevices,
            PdcTransferDurationProvider pdcTransferDurationProvider,
            PdcDiversionDistanceProvider pdcDiversionDistanceProvider,
            PrlToPcrTransferDurationProvider prlToPcrTransferDurationProvider,
            PrlToPcrEntryDistanceProvider prlToPcrEntryDistanceProvider) {
        if (toteLoadPlan == null
                || batchPlan == null
                || pdcConveyor == null
                || pcrConveyor == null
                || downstreamPackGroupReceiver == null
                || assignmentPlanner == null
                || prlConveyors == null
                || prlConveyors.isEmpty()
                || pdcDiversionDevices == null
                || pdcDiversionDevices.isEmpty()
                || pdcTransferDurationProvider == null
                || pdcDiversionDistanceProvider == null
                || prlToPcrTransferDurationProvider == null
                || prlToPcrEntryDistanceProvider == null) {
            throw new IllegalArgumentException("Controller dependencies must not be null or empty");
        }
        if ((tippingMachine == null) != (sortingMachine == null)) {
            throw new IllegalArgumentException("tippingMachine and sortingMachine must either both be present or both be absent");
        }
        this.toteLoadPlan = toteLoadPlan;
        this.batchPlan = batchPlan;
        this.tippingMachine = tippingMachine;
        this.sortingMachine = sortingMachine;
        this.pdcConveyor = pdcConveyor;
        this.pcrConveyor = pcrConveyor;
        this.downstreamPackGroupReceiver = downstreamPackGroupReceiver;
        this.assignmentPlanner = assignmentPlanner;
        this.pdcTransferDurationProvider = pdcTransferDurationProvider;
        this.pdcDiversionDistanceProvider = pdcDiversionDistanceProvider;
        this.prlToPcrTransferDurationProvider = prlToPcrTransferDurationProvider;
        this.prlToPcrEntryDistanceProvider = prlToPcrEntryDistanceProvider;
        for (PrlConveyor prlConveyor : prlConveyors) {
            prlsById.put(prlConveyor.getId(), prlConveyor);
        }
        for (PdcDiversionDevice pdcDiversionDevice : pdcDiversionDevices) {
            pdcDiversionDevicesByPrlId.put(pdcDiversionDevice.getTargetPrlId(), pdcDiversionDevice);
        }
        if (!pdcDiversionDevicesByPrlId.keySet().containsAll(prlsById.keySet())) {
            throw new IllegalArgumentException("Each PRL must have a matching PDC diversion device");
        }
    }

    public ToteToBagFlowController(
            ToteLoadPlan toteLoadPlan,
            TippingMachine tippingMachine,
            SortingMachine sortingMachine,
            PdcConveyor pdcConveyor,
            PcrConveyor pcrConveyor,
            PackGroupReceiver downstreamPackGroupReceiver,
            ToteToBagAssignmentPlanner assignmentPlanner,
            List<PrlConveyor> prlConveyors,
            List<PdcDiversionDevice> pdcDiversionDevices,
            PdcTransferDurationProvider pdcTransferDurationProvider,
            PdcDiversionDistanceProvider pdcDiversionDistanceProvider,
            PrlToPcrTransferDurationProvider prlToPcrTransferDurationProvider,
            PrlToPcrEntryDistanceProvider prlToPcrEntryDistanceProvider) {
        this(
                toteLoadPlan,
                ToteToBagBatchPlan.fromToteLoadPlan(toteLoadPlan),
                tippingMachine,
                sortingMachine,
                pdcConveyor,
                pcrConveyor,
                downstreamPackGroupReceiver,
                assignmentPlanner,
                prlConveyors,
                pdcDiversionDevices,
                pdcTransferDurationProvider,
                pdcDiversionDistanceProvider,
                prlToPcrTransferDurationProvider,
                prlToPcrEntryDistanceProvider);
    }

    @Override
    public void update(SimulationContext context, double dtSeconds) {
        initializeIfNeeded();
        loadToteIfNeeded();
        drainTippingMachine();
        drainSortingMachine();
        updatePdcConveyor(dtSeconds);
        requestPdcDiversions();
        updatePdcDiversionDevices(dtSeconds);
        startPdcTransfersFromActuatingDevices();
        updatePdcTransfers(dtSeconds);
        updatePrls(dtSeconds);
        attemptPrlRelease();
        startPrlToPcrTransfers();
        updatePrlToPcrTransfers(dtSeconds);
        handOffPcrOutfeedToBagger();
    }

    public Map<String, PrlConveyor> getPrlsById() {
        return prlsById;
    }

    public Queue<ReleasedPackGroup> getReleasedGroups() {
        return releasedGroups;
    }

    public List<Pack> getObservedPacks() {
        return observedPacksById.values().stream().toList();
    }

    public List<PdcTransfer> getActivePdcTransfers() {
        return List.copyOf(activePdcTransfers);
    }

    public List<LinearLaneEntrySnapshot> getPdcLaneEntries() {
        return pdcConveyor.getLaneEntries();
    }

    public List<PdcDiversionDevice> getPdcDiversionDevices() {
        return List.copyOf(pdcDiversionDevicesByPrlId.values());
    }

    public List<PrlToPcrTransfer> getActivePrlToPcrTransfers() {
        return List.copyOf(activePrlToPcrTransfers);
    }

    private void initializeIfNeeded() {
        if (initialized) {
            return;
        }
        List<PrlAssignmentPlan> plans = assignmentPlanner.createPlans(batchPlan, prlsById.keySet().stream().toList());
        for (PrlAssignmentPlan plan : plans) {
            prlsById.get(plan.prlId()).assign(plan);
        }
        initialized = true;
    }

    private void loadToteIfNeeded() {
        if (tippingMachine == null) {
            return;
        }
        if (!toteLoaded && tippingMachine.isIdle()) {
            tippingMachine.loadTote(toteLoadPlan);
            toteLoaded = true;
        }
    }

    private void drainTippingMachine() {
        if (tippingMachine == null || sortingMachine == null) {
            return;
        }
        while (tippingMachine.hasEmittedPack()) {
            Pack pack = tippingMachine.pollEmittedPack();
            observedPacksById.put(pack.getId(), pack);
            sortingMachine.receive(pack);
        }
    }

    private void drainSortingMachine() {
        if (sortingMachine == null) {
            return;
        }
        while (sortingMachine.hasReleasedPack()) {
            Pack pack = sortingMachine.pollReleasedPack();
            pdcConveyor.acceptIncomingPack(pack);
        }
    }

    private void updatePdcConveyor(double dtSeconds) {
        pdcConveyor.update(dtSeconds);
    }

    private void requestPdcDiversions() {
        for (LinearLaneEntrySnapshot entry : pdcConveyor.getLaneEntries()) {
            Pack pack = entry.pack();
            observedPacksById.putIfAbsent(pack.getId(), pack);
            PrlConveyor prl = findOrAssignPrlForCorrelation(pack.getCorrelationId());
            float diversionFrontDistance = pdcDiversionDistanceProvider.frontDistanceFor(prl.getId(), pack);
            if (entry.frontDistance() < diversionFrontDistance || !prl.accepts(pack)) {
                continue;
            }
            PdcDiversionDevice device = pdcDiversionDevicesByPrlId.get(prl.getId());
            if (device == null) {
                throw new IllegalStateException("No PDC diversion device for PRL " + prl.getId());
            }
            device.requestDiversion(pack);
        }
    }

    private void updatePdcDiversionDevices(double dtSeconds) {
        for (PdcDiversionDevice device : pdcDiversionDevicesByPrlId.values()) {
            device.update(dtSeconds);
        }
    }

    private void startPdcTransfersFromActuatingDevices() {
        for (PdcDiversionDevice device : pdcDiversionDevicesByPrlId.values()) {
            Pack pack = device.consumeActuationStartPack().orElse(null);
            if (pack == null) {
                continue;
            }
            PrlConveyor prl = prlsById.get(device.getTargetPrlId());
            if (prl == null) {
                throw new IllegalStateException("Unknown PRL id " + device.getTargetPrlId());
            }
            float actualFrontDistance = pdcConveyor.divertPack(pack)
                    .orElseThrow(() -> new IllegalStateException("Expected pack on PDC lane for diversion " + pack.getId()));
            activePdcTransfers.add(new PdcTransfer(
                    pack,
                    prl.getId(),
                    actualFrontDistance,
                    pdcTransferDurationProvider.durationSecondsFor(prl.getId())));
        }
    }

    private void updatePdcTransfers(double dtSeconds) {
        Iterator<PdcTransfer> iterator = activePdcTransfers.iterator();
        while (iterator.hasNext()) {
            PdcTransfer transfer = iterator.next();
            transfer.advance(dtSeconds);
            if (!transfer.isComplete()) {
                continue;
            }
            PrlConveyor prl = prlsById.get(transfer.getTargetPrlId());
            if (prl == null) {
                throw new IllegalStateException("Unknown PRL id " + transfer.getTargetPrlId());
            }
            if (!prl.accepts(transfer.getPack())) {
                continue;
            }
            prl.acceptPack(transfer.getPack());
            iterator.remove();
        }
    }

    private void updatePrls(double dtSeconds) {
        for (PrlConveyor prl : prlsById.values()) {
            prl.update(dtSeconds);
        }
    }

    private void attemptPrlRelease() {
        boolean prlAlreadyReleasing = prlsById.values().stream()
                .anyMatch(prl -> prl.getAssignment().getState() == PrlState.RELEASING);
        if (prlAlreadyReleasing || pcrConveyor.hasWorkInFlight()) {
            return;
        }

        Optional<PrlConveyor> readyPrl = prlsById.values().stream()
                .filter(PrlConveyor::isReadyToRelease)
                .sorted(Comparator.comparing(PrlConveyor::getId))
                .filter(prl -> downstreamPackGroupReceiver.canReserveIncomingGroup(prl.peekReadyGroup()))
                .findFirst();
        if (readyPrl.isEmpty()) {
            return;
        }

        ReleasedPackGroup candidate = readyPrl.get().peekReadyGroup();
        baggingReservation = downstreamPackGroupReceiver.reserveIncomingGroup(candidate);
        ReleasedPackGroup releasedGroup = readyPrl.get().releaseGroup();
        pcrConveyor.startReceivingGroup(releasedGroup);
        releasedGroups.add(releasedGroup);
    }

    private void startPrlToPcrTransfers() {
        for (PrlConveyor prl : prlsById.values()) {
            while (true) {
                Optional<Pack> outfeedPack = prl.peekPackAtOutfeed();
                if (outfeedPack.isEmpty()) {
                    break;
                }
                float targetFrontDistance = prlToPcrEntryDistanceProvider.frontDistanceFor(prl.getId(), outfeedPack.get());
                Pack pack = prl.pollPackAtOutfeed()
                        .orElseThrow(() -> new IllegalStateException("Expected PRL pack at outfeed"));
                pack.setState(Pack.PackMotionState.DIVERTING);
                activePrlToPcrTransfers.add(new PrlToPcrTransfer(
                        pack,
                        prl.getId(),
                        targetFrontDistance,
                        prlToPcrTransferDurationProvider.durationSecondsFor(prl.getId())));
            }
            prl.completeReleaseIfEmpty();
        }
    }

    private void updatePrlToPcrTransfers(double dtSeconds) {
        Iterator<PrlToPcrTransfer> iterator = activePrlToPcrTransfers.iterator();
        while (iterator.hasNext()) {
            PrlToPcrTransfer transfer = iterator.next();
            transfer.advance(dtSeconds);
            if (!transfer.isComplete()) {
                continue;
            }
            if (!pcrConveyor.canAcceptIncomingPackAtDistance(transfer.getPack(), transfer.getTargetPcrFrontDistance())) {
                continue;
            }
            pcrConveyor.acceptIncomingPackAtDistance(transfer.getPack(), transfer.getTargetPcrFrontDistance());
            iterator.remove();
        }
    }

    private void handOffPcrOutfeedToBagger() {
        ReleasedPackGroup groupAtOutfeed = pcrConveyor.peekGroupAtOutfeed().orElse(null);
        if (groupAtOutfeed == null) {
            return;
        }
        if (!pcrConveyor.isGroupFullyAccepted(groupAtOutfeed)) {
            return;
        }
        if (downstreamPackGroupReceiver.hasReservationFor(groupAtOutfeed)) {
            downstreamPackGroupReceiver.beginReceiving(baggingReservation);
            baggingReservation = null;
        }

        if (!downstreamPackGroupReceiver.isReceivingGroup(groupAtOutfeed)) {
            return;
        }

        while (true) {
            ReleasedPackGroup nextGroupAtOutfeed = pcrConveyor.peekGroupAtOutfeed().orElse(null);
            if (nextGroupAtOutfeed == null
                    || !nextGroupAtOutfeed.correlationId().equals(groupAtOutfeed.correlationId())) {
                return;
            }
            pcrConveyor.pollPackAtOutfeed();
            if (!pcrConveyor.hasWorkInFlight()) {
                downstreamPackGroupReceiver.completeIncomingTransfer(groupAtOutfeed);
                return;
            }
        }
    }

    private Optional<PrlConveyor> findPrlForCorrelation(String correlationId) {
        return prlsById.values().stream()
                .filter(prl -> correlationId.equals(prl.getAssignment().getCorrelationId()))
                .findFirst();
    }

    private PrlConveyor findOrAssignPrlForCorrelation(String correlationId) {
        PrlConveyor assignedPrl = findPrlForCorrelation(correlationId).orElse(null);
        if (assignedPrl != null) {
            return assignedPrl;
        }
        int expectedPackCount = batchPlan.expectedPackCountFor(correlationId);
        if (expectedPackCount <= 0) {
            throw new IllegalStateException("No batch assignment for correlation " + correlationId);
        }
        PrlConveyor idlePrl = prlsById.values().stream()
                .sorted(Comparator.comparing(PrlConveyor::getId))
                .filter(prl -> prl.getAssignment().getState() == PrlState.IDLE)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No idle PRL available for correlation " + correlationId));
        idlePrl.assign(new PrlAssignmentPlan(idlePrl.getId(), correlationId, expectedPackCount));
        return idlePrl;
    }

    private static List<PdcDiversionDevice> createDefaultDiversionDevices(List<PrlConveyor> prlConveyors) {
        List<PdcDiversionDevice> devices = new ArrayList<>();
        for (PrlConveyor prlConveyor : prlConveyors) {
            devices.add(new PdcDiversionDevice(
                    "pdc_diverter_" + prlConveyor.getId(),
                    prlConveyor.getId(),
                    0d,
                    0.08d,
                    0.08d));
        }
        return devices;
    }
}
