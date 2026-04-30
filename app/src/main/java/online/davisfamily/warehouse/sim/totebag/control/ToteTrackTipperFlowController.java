package online.davisfamily.warehouse.sim.totebag.control;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.SimulationController;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.tote.Tote.ToteMotionState;
import online.davisfamily.warehouse.sim.totebag.handoff.PackReceiveTarget;
import online.davisfamily.warehouse.sim.totebag.machine.SortingMachine;
import online.davisfamily.warehouse.sim.totebag.machine.TippingMachine;
import online.davisfamily.warehouse.sim.totebag.pack.Pack;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlanProvider;
import online.davisfamily.warehouse.sim.totebag.transfer.TippingDischargeTransfer;

public class ToteTrackTipperFlowController implements SimulationController {
    private final ToteLoadPlanProvider toteLoadPlanProvider;
    private final RouteSegment tipperSegment;
    private final float tipperStopDistance;
    private final float tipperTippedAngleRadians;
    private final TippingMachine tippingMachine;
    private final TipperDownstreamFlow downstreamFlow;
    private final double dischargeDurationSeconds;
    private final Map<String, Pack> observedPacksById = new LinkedHashMap<>();
    private final List<TippingDischargeTransfer> activeDischarges = new ArrayList<>();
    private float visualTipProgress;
    private Tote activeTote;
    private boolean toteCaptured;
    private boolean toteReleased;
    private ToteLoadPlan activeToteLoadPlan;
    private Predicate<ToteLoadPlan> toteAdmissionPredicate = ignored -> true;

    public ToteTrackTipperFlowController(
            Tote tote,
            ToteLoadPlan toteLoadPlan,
            RouteSegment tipperSegment,
            float tipperStopDistance,
            float tipperTippedAngleRadians,
            TippingMachine tippingMachine,
            SortingMachine sortingMachine,
            double dischargeDurationSeconds) {
        this(
                tote,
                singlePlanProvider(toteLoadPlan),
                tipperSegment,
                tipperStopDistance,
                tipperTippedAngleRadians,
                tippingMachine,
                new SorterTipperDownstreamFlow(sortingMachine, null),
                dischargeDurationSeconds);
    }

    public void setToteAdmissionPredicate(Predicate<ToteLoadPlan> toteAdmissionPredicate) {
        if (toteAdmissionPredicate == null) {
            throw new IllegalArgumentException("toteAdmissionPredicate must not be null");
        }
        this.toteAdmissionPredicate = toteAdmissionPredicate;
    }

    public ToteTrackTipperFlowController(
            Tote tote,
            ToteLoadPlanProvider toteLoadPlanProvider,
            RouteSegment tipperSegment,
            float tipperStopDistance,
            float tipperTippedAngleRadians,
            TippingMachine tippingMachine,
            SortingMachine sortingMachine,
            double dischargeDurationSeconds) {
        this(
                tote,
                toteLoadPlanProvider,
                tipperSegment,
                tipperStopDistance,
                tipperTippedAngleRadians,
                tippingMachine,
                new SorterTipperDownstreamFlow(sortingMachine, null),
                dischargeDurationSeconds);
    }

    public ToteTrackTipperFlowController(
            Tote tote,
            ToteLoadPlanProvider toteLoadPlanProvider,
            RouteSegment tipperSegment,
            float tipperStopDistance,
            float tipperTippedAngleRadians,
            TippingMachine tippingMachine,
            SortingMachine sortingMachine,
            double dischargeDurationSeconds,
            PackReceiveTarget sorterOutfeedTarget) {
        this(
                tote,
                toteLoadPlanProvider,
                tipperSegment,
                tipperStopDistance,
                tipperTippedAngleRadians,
                tippingMachine,
                new SorterTipperDownstreamFlow(sortingMachine, sorterOutfeedTarget),
                dischargeDurationSeconds);
    }

    public ToteTrackTipperFlowController(
            Tote tote,
            ToteLoadPlanProvider toteLoadPlanProvider,
            RouteSegment tipperSegment,
            float tipperStopDistance,
            float tipperTippedAngleRadians,
            TippingMachine tippingMachine,
            TipperDownstreamFlow downstreamFlow,
            double dischargeDurationSeconds) {
        if (tote == null
                || toteLoadPlanProvider == null
                || tipperSegment == null
                || tippingMachine == null
                || downstreamFlow == null) {
            throw new IllegalArgumentException("Controller dependencies must not be null");
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
        this.activeTote = tote;
        this.toteLoadPlanProvider = toteLoadPlanProvider;
        this.tipperSegment = tipperSegment;
        this.tipperStopDistance = tipperStopDistance;
        this.tipperTippedAngleRadians = tipperTippedAngleRadians;
        this.tippingMachine = tippingMachine;
        this.downstreamFlow = downstreamFlow;
        this.dischargeDurationSeconds = dischargeDurationSeconds;
    }

    @Override
    public void update(SimulationContext context, double dtSeconds) {
        captureToteIfReady();
        drainTippingMachine();
        updateActiveDischarges(dtSeconds);
        updateVisualTipProgress(dtSeconds);
        syncToteVisualTilt();
        downstreamFlow.update(dtSeconds);
        releaseToteIfReady();
    }

    public List<Pack> getObservedPacks() {
        return List.copyOf(observedPacksById.values());
    }

    public List<TippingDischargeTransfer> getActiveDischarges() {
        return List.copyOf(activeDischarges);
    }

    public boolean isToteCaptured() {
        return activeTote != null && toteCaptured && !toteReleased;
    }

    public boolean canAcceptNextTote() {
        return activeTote == null
                && tippingMachine.isIdle()
                && activeDischarges.isEmpty()
                && !downstreamFlow.keepsTipperOccupied();
    }

    public void acceptNextTote(Tote tote) {
        if (tote == null) {
            throw new IllegalArgumentException("tote must not be null");
        }
        if (!canAcceptNextTote()) {
            throw new IllegalStateException("Tipper is not ready to accept another tote");
        }
        activeTote = tote;
        toteCaptured = false;
        toteReleased = false;
        activeToteLoadPlan = null;
        visualTipProgress = 0f;
    }

    private void captureToteIfReady() {
        if (activeTote == null || toteCaptured || activeTote.getLastSnapshot() == null || !tippingMachine.isIdle()) {
            return;
        }
        if (activeTote.getLastSnapshot().currentSegment() != tipperSegment) {
            return;
        }
        if (activeTote.getLastSnapshot().distanceAlongSegment() < tipperStopDistance) {
            return;
        }
        activeTote.getRouteFollower().setDistanceAlongSegment(tipperStopDistance);
        activeTote.setInteractionMode(ToteMotionState.HELD);
        activeTote.snapToRouteDistance(tipperStopDistance);
        activeToteLoadPlan = resolveLoadPlanForCapturedTote();
        if (!toteAdmissionPredicate.test(activeToteLoadPlan)) {
            return;
        }
        tippingMachine.loadTote(activeToteLoadPlan);
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
            if (!downstreamFlow.canAcceptDischargedPack(transfer.getPack())) {
                continue;
            }
            transfer.getPack().setState(Pack.PackMotionState.HELD);
            downstreamFlow.acceptDischargedPack(transfer.getPack());
            iterator.remove();
        }
    }

    private void releaseToteIfReady() {
        if (activeTote == null || !toteCaptured || toteReleased) {
            return;
        }
        boolean machinesClear = tippingMachine.isIdle()
                && activeDischarges.isEmpty()
                && !downstreamFlow.keepsTipperOccupied();
        if (!machinesClear) {
            return;
        }
        activeTote.clearVisualTiltAngleZ();
        activeTote.clearVisualOffset();
        activeTote.setInteractionMode(ToteMotionState.MOVING);
        toteReleased = true;
        toteCaptured = false;
        activeToteLoadPlan = null;
        activeTote = null;
    }

    private void syncToteVisualTilt() {
        Tote tote = activeTote;
        if (!isToteCaptured()) {
            if (tote != null) {
                tote.clearVisualTiltAngleZ();
                tote.clearVisualOffset();
            }
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

    private ToteLoadPlan resolveLoadPlanForCapturedTote() {
        if (activeTote == null) {
            throw new IllegalStateException("No active tote is available");
        }
        ToteLoadPlan toteLoadPlan = toteLoadPlanProvider.getLoadPlanFor(activeTote.getId());
        if (toteLoadPlan == null) {
            throw new IllegalStateException("No tote load plan available for tote " + activeTote.getId());
        }
        if (!activeTote.getId().equals(toteLoadPlan.getToteId())) {
            throw new IllegalArgumentException("Tote load plan must match the tote id");
        }
        return toteLoadPlan;
    }

    private static ToteLoadPlanProvider singlePlanProvider(ToteLoadPlan toteLoadPlan) {
        if (toteLoadPlan == null) {
            throw new IllegalArgumentException("toteLoadPlan must not be null");
        }
        return toteId -> toteLoadPlan.getToteId().equals(toteId) ? toteLoadPlan : null;
    }
}
