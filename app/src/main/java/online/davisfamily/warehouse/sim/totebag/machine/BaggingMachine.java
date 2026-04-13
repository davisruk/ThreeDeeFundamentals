package online.davisfamily.warehouse.sim.totebag.machine;

import online.davisfamily.warehouse.sim.totebag.plan.*;
import online.davisfamily.warehouse.sim.totebag.pack.*;
import online.davisfamily.warehouse.sim.totebag.machine.*;
import online.davisfamily.warehouse.sim.totebag.conveyor.*;
import online.davisfamily.warehouse.sim.totebag.transfer.*;
import online.davisfamily.warehouse.sim.totebag.device.*;
import online.davisfamily.warehouse.sim.totebag.assignment.*;
import online.davisfamily.warehouse.sim.totebag.control.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.objects.StatefulSimObject;

public class BaggingMachine implements StatefulSimObject<BaggingMachineState> {
    private final String id;
    private final BagSpec bagSpec;
    private final double receivingDurationSeconds;
    private final double droppingDurationSeconds;
    private final double sealingDurationSeconds;
    private final double dischargingDurationSeconds;
    private final List<String> completedCorrelationIds = new ArrayList<>();
    private final List<CompletedBag> completedBags = new ArrayList<>();

    private BaggingMachineState state = BaggingMachineState.IDLE;
    private ReleasedPackGroup currentGroup;
    private double timeInStateSeconds;

    public BaggingMachine(
            String id,
            BagSpec bagSpec,
            double receivingDurationSeconds,
            double droppingDurationSeconds,
            double sealingDurationSeconds,
            double dischargingDurationSeconds) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (bagSpec == null) {
            throw new IllegalArgumentException("bagSpec must not be null");
        }
        this.id = id;
        this.bagSpec = bagSpec;
        this.receivingDurationSeconds = receivingDurationSeconds;
        this.droppingDurationSeconds = droppingDurationSeconds;
        this.sealingDurationSeconds = sealingDurationSeconds;
        this.dischargingDurationSeconds = dischargingDurationSeconds;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void update(SimulationContext context, double dtSeconds) {
        if (state == BaggingMachineState.IDLE || currentGroup == null) {
            return;
        }

        timeInStateSeconds += dtSeconds;
        switch (state) {
            case RECEIVING -> transitionWhenElapsed(BaggingMachineState.DROPPING, receivingDurationSeconds);
            case DROPPING -> transitionWhenElapsed(BaggingMachineState.SEALING, droppingDurationSeconds);
            case SEALING -> transitionWhenElapsed(BaggingMachineState.DISCHARGING, sealingDurationSeconds);
            case DISCHARGING -> {
                if (timeInStateSeconds >= dischargingDurationSeconds) {
                    for (Pack pack : currentGroup.packs()) {
                        pack.setState(Pack.PackMotionState.CONSUMED);
                    }
                    completedBags.add(buildCompletedBag(currentGroup));
                    completedCorrelationIds.add(currentGroup.correlationId());
                    currentGroup = null;
                    state = BaggingMachineState.IDLE;
                    timeInStateSeconds = 0d;
                }
            }
            case IDLE -> {
            }
        }
    }

    @Override
    public BaggingMachineState getState() {
        return state;
    }

    public boolean isAvailable() {
        return state == BaggingMachineState.IDLE;
    }

    public void startBagging(ReleasedPackGroup group) {
        if (!isAvailable()) {
            throw new IllegalStateException("Bagging machine is not available");
        }
        if (group == null) {
            throw new IllegalArgumentException("group must not be null");
        }
        currentGroup = group;
        for (Pack pack : group.packs()) {
            pack.setState(Pack.PackMotionState.HELD);
        }
        state = BaggingMachineState.RECEIVING;
        timeInStateSeconds = 0d;
    }

    public ReleasedPackGroup getCurrentGroup() {
        return currentGroup;
    }

    public List<String> getCompletedCorrelationIds() {
        return Collections.unmodifiableList(completedCorrelationIds);
    }

    public List<CompletedBag> getCompletedBags() {
        return Collections.unmodifiableList(completedBags);
    }

    private void transitionWhenElapsed(BaggingMachineState nextState, double durationSeconds) {
        if (timeInStateSeconds >= durationSeconds) {
            state = nextState;
            timeInStateSeconds = 0d;
        }
    }

    private CompletedBag buildCompletedBag(ReleasedPackGroup group) {
        return new CompletedBag(group.correlationId(), group.packs().size(), bagSpec);
    }
}
