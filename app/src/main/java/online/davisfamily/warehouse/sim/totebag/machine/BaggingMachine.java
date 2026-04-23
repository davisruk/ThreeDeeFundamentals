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
import online.davisfamily.warehouse.sim.totebag.bag.Bag;
import online.davisfamily.warehouse.sim.totebag.handoff.BagReceiver;
import online.davisfamily.warehouse.sim.totebag.handoff.BagReservation;
import online.davisfamily.warehouse.sim.totebag.handoff.PackGroupReceiver;
import online.davisfamily.warehouse.sim.totebag.handoff.PackGroupReservation;

public class BaggingMachine implements StatefulSimObject<BaggingMachineState>, PackGroupReceiver {
    private final String id;
    private final BagSpec bagSpec;
    private final double receivingDurationSeconds;
    private final double droppingDurationSeconds;
    private final double sealingDurationSeconds;
    private final double dischargingDurationSeconds;
    private final BagReceiver bagReceiver;
    private final List<String> completedCorrelationIds = new ArrayList<>();
    private final List<CompletedBag> completedBags = new ArrayList<>();
    private final List<Bag> completedRuntimeBags = new ArrayList<>();

    private BaggingMachineState state = BaggingMachineState.IDLE;
    private ReleasedPackGroup reservedGroup;
    private PackGroupReservation activeReservation;
    private ReleasedPackGroup currentGroup;
    private boolean incomingTransferComplete;
    private double timeInStateSeconds;

    public BaggingMachine(
            String id,
            BagSpec bagSpec,
            double receivingDurationSeconds,
            double droppingDurationSeconds,
            double sealingDurationSeconds,
            double dischargingDurationSeconds) {
        this(
                id,
                bagSpec,
                receivingDurationSeconds,
                droppingDurationSeconds,
                sealingDurationSeconds,
                dischargingDurationSeconds,
                null);
    }

    public BaggingMachine(
            String id,
            BagSpec bagSpec,
            double receivingDurationSeconds,
            double droppingDurationSeconds,
            double sealingDurationSeconds,
            double dischargingDurationSeconds,
            BagReceiver bagReceiver) {
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
        this.bagReceiver = bagReceiver;
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
            case RECEIVING -> {
                if (incomingTransferComplete) {
                    transitionWhenElapsed(BaggingMachineState.DROPPING, receivingDurationSeconds);
                }
            }
            case DROPPING -> transitionWhenElapsed(BaggingMachineState.SEALING, droppingDurationSeconds);
            case SEALING -> transitionWhenElapsed(BaggingMachineState.DISCHARGING, sealingDurationSeconds);
            case DISCHARGING -> {
                if (timeInStateSeconds >= dischargingDurationSeconds) {
                    for (Pack pack : currentGroup.packs()) {
                        pack.setState(Pack.PackMotionState.CONSUMED);
                    }
                    Bag runtimeBag = buildRuntimeBag(currentGroup);
                    CompletedBag completedBag = buildCompletedBag(currentGroup);
                    completeBag(completedBag, runtimeBag);
                    currentGroup = null;
                    activeReservation = null;
                    incomingTransferComplete = false;
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
        return state == BaggingMachineState.IDLE && reservedGroup == null && currentGroup == null;
    }

    public void startBagging(ReleasedPackGroup group) {
        beginReceiving(reserveIncomingGroup(group));
    }

    @Override
    public boolean canReserveIncomingGroup(ReleasedPackGroup group) {
        return group != null && isAvailable();
    }

    @Override
    public PackGroupReservation reserveIncomingGroup(ReleasedPackGroup group) {
        if (!canReserveIncomingGroup(group)) {
            throw new IllegalStateException("Bagging machine cannot reserve incoming group");
        }
        reservedGroup = group;
        activeReservation = new PackGroupReservation(id, group.correlationId());
        return activeReservation;
    }

    @Override
    public boolean hasReservationFor(ReleasedPackGroup group) {
        return group != null
                && reservedGroup != null
                && group.correlationId().equals(reservedGroup.correlationId());
    }

    @Override
    public void beginReceiving(PackGroupReservation reservation) {
        if (reservation == null) {
            throw new IllegalArgumentException("reservation must not be null");
        }
        if (reservedGroup == null || activeReservation == null || !activeReservation.equals(reservation)) {
            throw new IllegalStateException("Reservation does not match the bagging machine's reserved group");
        }

        currentGroup = reservedGroup;
        reservedGroup = null;
        for (Pack pack : currentGroup.packs()) {
            pack.setState(Pack.PackMotionState.HELD);
        }
        state = BaggingMachineState.RECEIVING;
        incomingTransferComplete = false;
        timeInStateSeconds = 0d;
    }

    public void completeIncomingTransfer(ReleasedPackGroup group) {
        if (group == null) {
            throw new IllegalArgumentException("group must not be null");
        }
        if (currentGroup == null || !currentGroup.correlationId().equals(group.correlationId())) {
            throw new IllegalStateException("Incoming transfer does not match the current bagging group");
        }
        incomingTransferComplete = true;
    }

    public ReleasedPackGroup getCurrentGroup() {
        return currentGroup;
    }

    public ReleasedPackGroup getReservedGroup() {
        return reservedGroup;
    }

    public PackGroupReservation getActiveReservation() {
        return activeReservation;
    }

    public double getTimeInStateSeconds() {
        return timeInStateSeconds;
    }

    public double getReceivingDurationSeconds() {
        return receivingDurationSeconds;
    }

    public List<String> getCompletedCorrelationIds() {
        return Collections.unmodifiableList(completedCorrelationIds);
    }

    public List<CompletedBag> getCompletedBags() {
        return Collections.unmodifiableList(completedBags);
    }

    public List<Bag> getCompletedRuntimeBags() {
        return Collections.unmodifiableList(completedRuntimeBags);
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

    private Bag buildRuntimeBag(ReleasedPackGroup group) {
        return Bag.fromReleasedPackGroup("bag_" + group.correlationId(), group, bagSpec);
    }

    private void completeBag(CompletedBag completedBag, Bag runtimeBag) {
        if (bagReceiver != null) {
            BagReservation reservation = bagReceiver.reserveIncomingBag(runtimeBag);
            bagReceiver.beginReceiving(reservation);
            bagReceiver.completeReceiving(reservation);
        }
        completedRuntimeBags.add(runtimeBag);
        completedBags.add(completedBag);
        completedCorrelationIds.add(completedBag.correlationId());
    }
}
