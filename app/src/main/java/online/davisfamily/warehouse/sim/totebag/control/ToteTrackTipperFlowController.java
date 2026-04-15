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
    private final float sorterOutfeedEntryCenterDistance;
    private final double dischargeDurationSeconds;
    private final PackSink sorterOutfeedSink;
    private final Map<String, Pack> observedPacksById = new LinkedHashMap<>();
    private final List<TippingDischargeTransfer> activeDischarges = new ArrayList<>();
    private final Queue<Pack> pendingSorterOutfeed = new ArrayDeque<>();
    private final List<Pack> completedOutputPacks = new ArrayList<>();
    private float visualTipProgress;
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
            float sorterOutfeedEntryCenterDistance,
            double dischargeDurationSeconds) {
        this(
                tote,
                toteLoadPlan,
                tipperSegment,
                tipperStopDistance,
                tipperTippedAngleRadians,
                tippingMachine,
                sortingMachine,
                sorterOutfeedConveyor,
                sorterOutfeedEntryCenterDistance,
                dischargeDurationSeconds,
                null);
    }

    public ToteTrackTipperFlowController(
            Tote tote,
            ToteLoadPlan toteLoadPlan,
            RouteSegment tipperSegment,
            float tipperStopDistance,
            float tipperTippedAngleRadians,
            TippingMachine tippingMachine,
            SortingMachine sortingMachine,
            PdcConveyor sorterOutfeedConveyor,
            float sorterOutfeedEntryCenterDistance,
            double dischargeDurationSeconds,
            PackSink sorterOutfeedSink) {
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
        if (sorterOutfeedEntryCenterDistance < 0f || sorterOutfeedEntryCenterDistance > sorterOutfeedConveyor.getUsableLength()) {
            throw new IllegalArgumentException("sorterOutfeedEntryCenterDistance must be within the conveyor usable length");
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
        this.sorterOutfeedEntryCenterDistance = sorterOutfeedEntryCenterDistance;
        this.dischargeDurationSeconds = dischargeDurationSeconds;
        this.sorterOutfeedSink = sorterOutfeedSink;
    }

    @Override
    public void update(SimulationContext context, double dtSeconds) {
        captureToteIfReady();
        drainTippingMachine();
        updateActiveDischarges(dtSeconds);
        updateVisualTipProgress(dtSeconds);
        syncToteVisualTilt();
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
        while (!pendingSorterOutfeed.isEmpty()) {
            Pack pack = pendingSorterOutfeed.peek();
            float entryFrontDistance = sorterOutfeedEntryCenterDistance + (pack.getDimensions().length() * 0.5f);
            if (!sorterOutfeedConveyor.canAcceptIncomingPackAtFrontDistance(pack, entryFrontDistance)) {
                return;
            }
            sorterOutfeedConveyor.acceptIncomingPackAtFrontDistance(pendingSorterOutfeed.remove(), entryFrontDistance);
        }
    }

    private void updateSorterOutfeed(double dtSeconds) {
        sorterOutfeedConveyor.setRunning(true);
        sorterOutfeedConveyor.update(dtSeconds);
    }

    private void completeOutfedPacks() {
        while (true) {
            Pack pack = sorterOutfeedConveyor.peekLeadingPackAtOutfeed().orElse(null);
            if (pack == null) {
                return;
            }
            if (sorterOutfeedSink != null && !sorterOutfeedSink.canAccept(pack)) {
                return;
            }
            pack = sorterOutfeedConveyor.pollLeadingPackAtOutfeed().orElse(null);
            if (pack == null) {
                return;
            }
            if (sorterOutfeedSink != null) {
                sorterOutfeedSink.accept(pack);
            } else {
                pack.setState(Pack.PackMotionState.CONSUMED);
            }
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
        tote.clearVisualOffset();
        tote.setInteractionMode(ToteMotionState.MOVING);
        toteReleased = true;
    }

    private void syncToteVisualTilt() {
        if (!isToteCaptured()) {
            tote.clearVisualTiltAngleZ();
            tote.clearVisualOffset();
            return;
        }
        float angle = currentTipAngle();
        float halfDepth = 0.25f;
        float dy = -halfDepth * (float) Math.sin(angle);
        float lateralOffset = halfDepth * ((float) Math.cos(angle) - 1f);
        var snapshot = tote.getLastSnapshot();
        float dx = 0f;
        float dz = lateralOffset;
        if (snapshot != null) {
            float forwardX = snapshot.forward().x;
            float forwardZ = snapshot.forward().z;
            dx = -forwardZ * lateralOffset;
            dz = forwardX * lateralOffset;
        }
        tote.setVisualTiltAngleZ(-angle);
        tote.setVisualOffset(dx, dy, dz);
    }

    private float currentTipAngle() {
        return tipperTippedAngleRadians * visualTipProgress;
    }

    public float getVisualTipProgress() {
        return visualTipProgress;
    }

    private void updateVisualTipProgress(double dtSeconds) {
        if (!activeDischarges.isEmpty()) {
            visualTipProgress = 1f;
            return;
        }
        float target = tippingMachine.getTipProgress();
        if (target >= visualTipProgress) {
            visualTipProgress = target;
            return;
        }
        double resetDurationSeconds = tippingMachine.getResetDurationSeconds();
        if (resetDurationSeconds <= 0d) {
            visualTipProgress = target;
            return;
        }
        float maxStep = (float) (Math.max(0d, dtSeconds) / resetDurationSeconds);
        visualTipProgress = Math.max(target, visualTipProgress - maxStep);
    }
}
