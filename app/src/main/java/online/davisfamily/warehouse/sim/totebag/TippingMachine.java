package online.davisfamily.warehouse.sim.totebag;

import java.util.ArrayDeque;
import java.util.Queue;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.objects.StatefulSimObject;

public class TippingMachine implements StatefulSimObject<TippingMachineState> {
    private final String id;
    private final double tippingDurationSeconds;
    private final double emitIntervalSeconds;
    private final double resetDurationSeconds;
    private final Queue<Pack> emittedPacks = new ArrayDeque<>();

    private TippingMachineState state = TippingMachineState.IDLE;
    private ToteLoadPlan activePlan;
    private int nextPackIndex;
    private double countdownSeconds;

    public TippingMachine(String id, double tippingDurationSeconds, double emitIntervalSeconds, double resetDurationSeconds) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (tippingDurationSeconds < 0d || emitIntervalSeconds < 0d || resetDurationSeconds < 0d) {
            throw new IllegalArgumentException("durations must be >= 0");
        }
        this.id = id;
        this.tippingDurationSeconds = tippingDurationSeconds;
        this.emitIntervalSeconds = emitIntervalSeconds;
        this.resetDurationSeconds = resetDurationSeconds;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void update(SimulationContext context, double dtSeconds) {
        switch (state) {
            case IDLE -> {
            }
            case RECEIVING_TOTE -> transitionTo(TippingMachineState.TIPPING, tippingDurationSeconds);
            case TIPPING -> tickTransition(dtSeconds, TippingMachineState.EMITTING_PACKS, emitIntervalSeconds);
            case EMITTING_PACKS -> updateEmitting(dtSeconds);
            case RESETTING -> tickTransition(dtSeconds, TippingMachineState.IDLE, 0d);
        }
    }

    @Override
    public TippingMachineState getState() {
        return state;
    }

    public boolean isIdle() {
        return state == TippingMachineState.IDLE;
    }

    public void loadTote(ToteLoadPlan toteLoadPlan) {
        if (!isIdle()) {
            throw new IllegalStateException("Tipping machine is busy");
        }
        if (toteLoadPlan == null) {
            throw new IllegalArgumentException("toteLoadPlan must not be null");
        }
        activePlan = toteLoadPlan;
        nextPackIndex = 0;
        state = TippingMachineState.RECEIVING_TOTE;
        countdownSeconds = 0d;
    }

    public boolean hasEmittedPack() {
        return !emittedPacks.isEmpty();
    }

    public Pack pollEmittedPack() {
        return emittedPacks.poll();
    }

    public String getActiveToteId() {
        return activePlan != null ? activePlan.getToteId() : null;
    }

    public int getRemainingPackCount() {
        if (activePlan == null) {
            return 0;
        }
        return Math.max(0, activePlan.getPackPlans().size() - nextPackIndex);
    }

    private void updateEmitting(double dtSeconds) {
        if (activePlan == null) {
            state = TippingMachineState.RESETTING;
            countdownSeconds = resetDurationSeconds;
            return;
        }

        countdownSeconds -= dtSeconds;
        if (countdownSeconds > 0d) {
            return;
        }

        if (nextPackIndex < activePlan.getPackPlans().size()) {
            emittedPacks.add(activePlan.getPackPlans().get(nextPackIndex).createPack());
            nextPackIndex++;
            countdownSeconds = emitIntervalSeconds;
        }

        if (nextPackIndex >= activePlan.getPackPlans().size()) {
            activePlan = null;
            state = TippingMachineState.RESETTING;
            countdownSeconds = resetDurationSeconds;
        }
    }

    private void transitionTo(TippingMachineState nextState, double nextCountdownSeconds) {
        state = nextState;
        countdownSeconds = nextCountdownSeconds;
    }

    private void tickTransition(double dtSeconds, TippingMachineState nextState, double nextCountdownSeconds) {
        countdownSeconds -= dtSeconds;
        if (countdownSeconds <= 0d) {
            transitionTo(nextState, nextCountdownSeconds);
        }
    }
}
