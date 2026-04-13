package online.davisfamily.warehouse.sim.totebag.control;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.SimulationController;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.tote.Tote.ToteMotionState;
import online.davisfamily.warehouse.sim.totebag.conveyor.PdcConveyor;
import online.davisfamily.warehouse.sim.totebag.machine.SortingMachine;
import online.davisfamily.warehouse.sim.totebag.machine.TippingMachine;
import online.davisfamily.warehouse.sim.totebag.pack.Pack;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;
import online.davisfamily.warehouse.sim.totebag.transfer.TippingDischargeTransfer;

public class ToteTrackTipperFlowController implements SimulationController {
    private final Tote tote;
    private final ToteLoadPlan toteLoadPlan;
    private final RouteSegment tipperSegment;
    private final float tipperStopDistance;
    private final float tipperTippedAngleRadians;
    private final TippingMachine tippingMachine;
    private final SortingMachine sortingMachine;
    private final PdcConveyor sorterOutfeedConveyor;
    private final double dischargeDurationSeconds;
    private final Map<String, Pack> observedPacksById = new LinkedHashMap<>();
    private final List<TippingDischargeTransfer> activeDischarges = new ArrayList<>();
    private final Queue<Pack> pendingSorterOutfeed = new ArrayDeque<>();
    private final List<Pack> completedOutputPacks = new ArrayList<>();
    private boolean toteCaptured;
    private boolean toteReleased;

    public ToteTrackTipperFlowController(
            Tote tote,
            ToteLoadPlan toteLoadPlan,
            RouteSegment tipperSegment,
            float tipperStopDistance,
            float tipperTippedAngleRadians,
            TippingMachine tippingMachine,
            SortingMachine sortingMachine,
            PdcConveyor sorterOutfeedConveyor,
            double dischargeDurationSeconds) {
        if (tote == null
                || toteLoadPlan == null
                || tipperSegment == null
                || tippingMachine == null
                || sortingMachine == null
                || sorterOutfeedConveyor == null) {
            throw new IllegalArgumentException("Controller dependencies must not be null");
        }
        if (!tote.getId().equals(toteLoadPlan.getToteId())) {
            throw new IllegalArgumentException("Tote load plan must match the tote id");
        }
        if (tipperStopDistance < 0f) {
            throw new IllegalArgumentException("tipperStopDistance must be >= 0");
        }
        if (Float.isNaN(tipperTippedAngleRadians) || Float.isInfinite(tipperTippedAngleRadians)) {
            throw new IllegalArgumentException("tipperTippedAngleRadians must be finite");
        }
        if (dischargeDurationSeconds <= 0d) {
            throw new IllegalArgumentException("dischargeDurationSeconds must be > 0");
        }
        this.tote = tote;
        this.toteLoadPlan = toteLoadPlan;
        this.tipperSegment = tipperSegment;
        this.tipperStopDistance = tipperStopDistance;
        this.tipperTippedAngleRadians = tipperTippedAngleRadians;
        this.tippingMachine = tippingMachine;
        this.sortingMachine = sortingMachine;
        this.sorterOutfeedConveyor = sorterOutfeedConveyor;
        this.dischargeDurationSeconds = dischargeDurationSeconds;
    }

    @Override
    public void update(SimulationContext context, double dtSeconds) {
        captureToteIfReady();
        syncToteVisualTilt();
        drainTippingMachine();
        updateActiveDischarges(dtSeconds);
        drainSortingMachine();
        startPendingSorterOutfeed();
        updateSorterOutfeed(dtSeconds);
        completeOutfedPacks();
        releaseToteIfReady();
    }

    public List<Pack> getObservedPacks() {
        return List.copyOf(observedPacksById.values());
    }

    public List<TippingDischargeTransfer> getActiveDischarges() {
        return List.copyOf(activeDischarges);
    }

    public List<Pack> getCompletedOutputPacks() {
        return List.copyOf(completedOutputPacks);
    }

    public boolean isToteCaptured() {
        return toteCaptured && !toteReleased;
    }

    private void captureToteIfReady() {
        if (toteCaptured || tote.getLastSnapshot() == null || !tippingMachine.isIdle()) {
            return;
        }
        if (tote.getLastSnapshot().currentSegment() != tipperSegment) {
            return;
        }
        if (tote.getLastSnapshot().distanceAlongSegment() < tipperStopDistance) {
            return;
        }
        tote.getRouteFollower().setDistanceAlongSegment(tipperStopDistance);
        tote.setInteractionMode(ToteMotionState.HELD);
        tote.snapToRouteDistance(tipperStopDistance);
        tippingMachine.loadTote(toteLoadPlan);
        toteCaptured = true;
    }

    private void drainTippingMachine() {
        while (tippingMachine.hasEmittedPack()) {
            Pack pack = tippingMachine.pollEmittedPack();
            observedPacksById.put(pack.getId(), pack);
            pack.setState(Pack.PackMotionState.DIVERTING);
            activeDischarges.add(new TippingDischargeTransfer(pack, dischargeDurationSeconds));
        }
    }

    private void updateActiveDischarges(double dtSeconds) {
        Iterator<TippingDischargeTransfer> iterator = activeDischarges.iterator();
        while (iterator.hasNext()) {
            TippingDischargeTransfer transfer = iterator.next();
            transfer.advance(dtSeconds);
            if (!transfer.isComplete()) {
                continue;
            }
            transfer.getPack().setState(Pack.PackMotionState.HELD);
            sortingMachine.receive(transfer.getPack());
            iterator.remove();
        }
    }

    private void drainSortingMachine() {
        while (sortingMachine.hasReleasedPack()) {
            pendingSorterOutfeed.add(sortingMachine.pollReleasedPack());
        }
    }

    private void startPendingSorterOutfeed() {
        while (!pendingSorterOutfeed.isEmpty() && sorterOutfeedConveyor.canAcceptIncomingPack(pendingSorterOutfeed.peek())) {
            sorterOutfeedConveyor.acceptIncomingPack(pendingSorterOutfeed.remove());
        }
    }

    private void updateSorterOutfeed(double dtSeconds) {
        sorterOutfeedConveyor.setRunning(true);
        sorterOutfeedConveyor.update(dtSeconds);
    }

    private void completeOutfedPacks() {
        while (true) {
            Pack pack = sorterOutfeedConveyor.pollLeadingPackAtOutfeed().orElse(null);
            if (pack == null) {
                return;
            }
            pack.setState(Pack.PackMotionState.CONSUMED);
            completedOutputPacks.add(pack);
        }
    }

    private void releaseToteIfReady() {
        if (!toteCaptured || toteReleased) {
            return;
        }
        boolean machinesClear = tippingMachine.isIdle()
                && activeDischarges.isEmpty()
                && sortingMachine.getQueuedPacks().isEmpty()
                && !sortingMachine.hasReleasedPack()
                && pendingSorterOutfeed.isEmpty();
        if (!machinesClear) {
            return;
        }
        tote.clearVisualTiltAngleZ();
        tote.setInteractionMode(ToteMotionState.MOVING);
        toteReleased = true;
    }

    private void syncToteVisualTilt() {
        if (!isToteCaptured()) {
            tote.clearVisualTiltAngleZ();
            return;
        }
        tote.setVisualTiltAngleZ(-currentTipAngle());
    }

    private float currentTipAngle() {
        return switch (tippingMachine.getState()) {
            case TIPPING -> tipperTippedAngleRadians * 0.45f;
            case EMITTING_PACKS -> tipperTippedAngleRadians;
            case RESETTING -> tipperTippedAngleRadians * 0.18f;
            case RECEIVING_TOTE -> tipperTippedAngleRadians * 0.08f;
            case IDLE -> 0f;
        };
    }
}
