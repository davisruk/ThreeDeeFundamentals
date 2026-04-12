package online.davisfamily.warehouse.sim.totebag;

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

public class ToteToBagFlowController implements SimulationController {
    private final ToteLoadPlan toteLoadPlan;
    private final TippingMachine tippingMachine;
    private final SortingMachine sortingMachine;
    private final PcrConveyor pcrConveyor;
    private final BaggingMachine baggingMachine;
    private final ToteToBagAssignmentPlanner assignmentPlanner;
    private final Map<String, PrlConveyor> prlsById = new LinkedHashMap<>();
    private final Map<String, Pack> observedPacksById = new LinkedHashMap<>();
    private final List<PdcTransfer> activePdcTransfers = new ArrayList<>();
    private final Queue<ReleasedPackGroup> releasedGroups = new ArrayDeque<>();
    private final PdcTransferDurationProvider pdcTransferDurationProvider;
    private boolean initialized;
    private boolean toteLoaded;

    public ToteToBagFlowController(
            ToteLoadPlan toteLoadPlan,
            TippingMachine tippingMachine,
            SortingMachine sortingMachine,
            PcrConveyor pcrConveyor,
            BaggingMachine baggingMachine,
            ToteToBagAssignmentPlanner assignmentPlanner,
            List<PrlConveyor> prlConveyors) {
        this(
                toteLoadPlan,
                tippingMachine,
                sortingMachine,
                pcrConveyor,
                baggingMachine,
                assignmentPlanner,
                prlConveyors,
                ignored -> 0.45d);
    }

    public ToteToBagFlowController(
            ToteLoadPlan toteLoadPlan,
            TippingMachine tippingMachine,
            SortingMachine sortingMachine,
            PcrConveyor pcrConveyor,
            BaggingMachine baggingMachine,
            ToteToBagAssignmentPlanner assignmentPlanner,
            List<PrlConveyor> prlConveyors,
            double pdcTransferDurationSeconds) {
        this(
                toteLoadPlan,
                tippingMachine,
                sortingMachine,
                pcrConveyor,
                baggingMachine,
                assignmentPlanner,
                prlConveyors,
                ignored -> pdcTransferDurationSeconds);
    }

    public ToteToBagFlowController(
            ToteLoadPlan toteLoadPlan,
            TippingMachine tippingMachine,
            SortingMachine sortingMachine,
            PcrConveyor pcrConveyor,
            BaggingMachine baggingMachine,
            ToteToBagAssignmentPlanner assignmentPlanner,
            List<PrlConveyor> prlConveyors,
            PdcTransferDurationProvider pdcTransferDurationProvider) {
        if (toteLoadPlan == null
                || tippingMachine == null
                || sortingMachine == null
                || pcrConveyor == null
                || baggingMachine == null
                || assignmentPlanner == null
                || prlConveyors == null
                || prlConveyors.isEmpty()
                || pdcTransferDurationProvider == null) {
            throw new IllegalArgumentException("Controller dependencies must not be null or empty");
        }
        this.toteLoadPlan = toteLoadPlan;
        this.tippingMachine = tippingMachine;
        this.sortingMachine = sortingMachine;
        this.pcrConveyor = pcrConveyor;
        this.baggingMachine = baggingMachine;
        this.assignmentPlanner = assignmentPlanner;
        this.pdcTransferDurationProvider = pdcTransferDurationProvider;
        for (PrlConveyor prlConveyor : prlConveyors) {
            prlsById.put(prlConveyor.getId(), prlConveyor);
        }
    }

    @Override
    public void update(SimulationContext context, double dtSeconds) {
        initializeIfNeeded();
        loadToteIfNeeded();
        drainTippingMachine();
        drainSortingMachine();
        updatePdcTransfers(dtSeconds);
        updatePrls(dtSeconds);
        attemptPrlRelease();
        transferReleasedPrlPacksToPcr();
        attemptBaggingStart();
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

    private void initializeIfNeeded() {
        if (initialized) {
            return;
        }
        List<PrlAssignmentPlan> plans = assignmentPlanner.createPlans(toteLoadPlan, prlsById.keySet().stream().toList());
        for (PrlAssignmentPlan plan : plans) {
            prlsById.get(plan.prlId()).assign(plan);
        }
        initialized = true;
    }

    private void loadToteIfNeeded() {
        if (!toteLoaded && tippingMachine.isIdle()) {
            tippingMachine.loadTote(toteLoadPlan);
            toteLoaded = true;
        }
    }

    private void drainTippingMachine() {
        while (tippingMachine.hasEmittedPack()) {
            Pack pack = tippingMachine.pollEmittedPack();
            observedPacksById.put(pack.getId(), pack);
            sortingMachine.receive(pack);
        }
    }

    private void drainSortingMachine() {
        while (sortingMachine.hasReleasedPack()) {
            Pack pack = sortingMachine.pollReleasedPack();
            PrlConveyor prl = findPrlForCorrelation(pack.getCorrelationId())
                    .orElseThrow(() -> new IllegalStateException("No PRL assignment for correlation " + pack.getCorrelationId()));
            pack.setState(Pack.PackMotionState.DIVERTING);
            activePdcTransfers.add(new PdcTransfer(pack, prl.getId(), pdcTransferDurationProvider.durationSecondsFor(prl.getId())));
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
        if (prlAlreadyReleasing || pcrConveyor.hasWorkInFlight() || baggingMachine.getCurrentGroup() != null) {
            return;
        }

        Optional<PrlConveyor> readyPrl = prlsById.values().stream()
                .filter(PrlConveyor::isReadyToRelease)
                .min(Comparator.comparing(PrlConveyor::getId));
        if (readyPrl.isEmpty()) {
            return;
        }

        ReleasedPackGroup candidate = readyPrl.get().peekReadyGroup();
        PcrReleaseDecision decision = pcrConveyor.evaluateRelease(candidate);
        if (!decision.allowed()) {
            return;
        }
        ReleasedPackGroup releasedGroup = readyPrl.get().releaseGroup();
        pcrConveyor.startReceivingGroup(releasedGroup);
        releasedGroups.add(releasedGroup);
    }

    private void transferReleasedPrlPacksToPcr() {
        for (PrlConveyor prl : prlsById.values()) {
            while (true) {
                Optional<Pack> outfeedPack = prl.peekPackAtOutfeed();
                if (outfeedPack.isEmpty()) {
                    break;
                }
                if (!pcrConveyor.canAcceptIncomingPack(outfeedPack.get())) {
                    break;
                }
                pcrConveyor.acceptIncomingPack(prl.pollPackAtOutfeed()
                        .orElseThrow(() -> new IllegalStateException("Expected PRL pack at outfeed")));
            }
            prl.completeReleaseIfEmpty();
        }
    }

    private void attemptBaggingStart() {
        if (!baggingMachine.isAvailable() || !pcrConveyor.hasReadyGroup()) {
            return;
        }
        baggingMachine.startBagging(pcrConveyor.pollReadyGroup());
    }

    private Optional<PrlConveyor> findPrlForCorrelation(String correlationId) {
        return prlsById.values().stream()
                .filter(prl -> correlationId.equals(prl.getAssignment().getCorrelationId()))
                .findFirst();
    }
}
