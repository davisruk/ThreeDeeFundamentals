package online.davisfamily.warehouse.sim.totebag;

import java.util.ArrayDeque;
import java.util.Comparator;
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
    private final Queue<ReleasedPackGroup> releasedGroups = new ArrayDeque<>();
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
        if (toteLoadPlan == null
                || tippingMachine == null
                || sortingMachine == null
                || pcrConveyor == null
                || baggingMachine == null
                || assignmentPlanner == null
                || prlConveyors == null
                || prlConveyors.isEmpty()) {
            throw new IllegalArgumentException("Controller dependencies must not be null or empty");
        }
        this.toteLoadPlan = toteLoadPlan;
        this.tippingMachine = tippingMachine;
        this.sortingMachine = sortingMachine;
        this.pcrConveyor = pcrConveyor;
        this.baggingMachine = baggingMachine;
        this.assignmentPlanner = assignmentPlanner;
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
        attemptPrlRelease();
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
            prl.acceptPack(pack);
        }
    }

    private void attemptPrlRelease() {
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
        pcrConveyor.accept(releasedGroup);
        releasedGroups.add(releasedGroup);
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
