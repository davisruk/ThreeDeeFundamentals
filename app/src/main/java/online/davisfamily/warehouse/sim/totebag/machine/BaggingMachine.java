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
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.objects.StatefulSimObject;
import online.davisfamily.warehouse.sim.totebag.bag.Bag;
import online.davisfamily.warehouse.sim.totebag.bag.BagDischarge;
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

    private static final class PendingDischarge {
        private final Bag completedRuntimeBag;
        private final CompletedBag completedBag;

        private PendingDischarge(Bag completedRuntimeBag, CompletedBag completedBag) {
            this.completedRuntimeBag = completedRuntimeBag;
            this.completedBag = completedBag;
        }
    }

    private BaggingMachineState state = BaggingMachineState.IDLE;
    private ReleasedPackGroup reservedGroup;
    private PackGroupReservation activeReservation;
    private ReleasedPackGroup currentGroup;
    private boolean incomingTransferComplete;
    private double timeInStateSeconds;
    private BaggingMachineState intakeState = BaggingMachineState.IDLE;
    private final Queue<PendingDischarge> pendingDischarges = new ArrayDeque<>();
    private BagDischarge activeDischarge;
    private boolean dischargeAwaitingReceiver;
    private boolean dischargeWithoutReceiverActive;
    private double dischargeTimeInStateSeconds;

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
        boolean dischargeWasActiveAtStart = activeDischarge != null || dischargeWithoutReceiverActive;

        if (currentGroup != null) {
            timeInStateSeconds += dtSeconds;
            switch (intakeState) {
                case RECEIVING -> {
                    if (incomingTransferComplete) {
                        transitionIntakeWhenElapsed(BaggingMachineState.DROPPING, receivingDurationSeconds);
                    }
                }
                case DROPPING -> transitionIntakeWhenElapsed(BaggingMachineState.SEALING, droppingDurationSeconds);
                case SEALING -> {
                    if (timeInStateSeconds >= sealingDurationSeconds) {
                        prepareBagForDischarge();
                    }
                }
                case IDLE -> {
                }
                case WAITING_FOR_RECEIVER -> {
                }
                case DISCHARGING -> {
                }
            }
        }

        if (activeDischarge == null && !dischargeWithoutReceiverActive) {
            tryStartNextPendingDischarge();
        }

        if (dischargeWasActiveAtStart) {
            if (activeDischarge != null) {
                activeDischarge.advance(dtSeconds);
                if (activeDischarge.isComplete()) {
                    completeActiveDischarge();
                }
            } else if (dischargeWithoutReceiverActive) {
                dischargeTimeInStateSeconds += dtSeconds;
                if (dischargeTimeInStateSeconds >= dischargingDurationSeconds) {
                    completeBagWithoutReceiver();
                }
            }
        }
    }

    @Override
    public BaggingMachineState getState() {
        return state;
    }

    public boolean isAvailable() {
        return state == BaggingMachineState.IDLE
                && reservedGroup == null
                && currentGroup == null
                && activeDischarge == null
                && !dischargeWithoutReceiverActive;
    }

    public void startBagging(ReleasedPackGroup group) {
        beginReceiving(reserveIncomingGroup(group));
    }

    @Override
    public boolean canReserveIncomingGroup(ReleasedPackGroup group) {
        return group != null
                && reservedGroup == null
                && currentGroup == null
                && (state == BaggingMachineState.IDLE || state == BaggingMachineState.DISCHARGING)
                && (state == BaggingMachineState.DISCHARGING || canReserveOutputBagFor(group));
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
        intakeState = BaggingMachineState.RECEIVING;
        state = activeDischarge != null || dischargeWithoutReceiverActive
                ? BaggingMachineState.DISCHARGING
                : BaggingMachineState.RECEIVING;
        refreshState();
        incomingTransferComplete = false;
        timeInStateSeconds = 0d;
    }

    @Override
    public boolean isReceivingGroup(ReleasedPackGroup group) {
        return group != null
                && currentGroup != null
                && currentGroup.correlationId().equals(group.correlationId());
    }

    @Override
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

    public BagDischarge getActiveDischarge() {
        return activeDischarge;
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

    private void transitionIntakeWhenElapsed(BaggingMachineState nextState, double durationSeconds) {
        if (timeInStateSeconds >= durationSeconds) {
            intakeState = nextState;
            timeInStateSeconds = 0d;
        }
    }

    private CompletedBag buildCompletedBag(ReleasedPackGroup group) {
        return new CompletedBag(group.correlationId(), group.packs().size(), bagSpec);
    }

    private Bag buildRuntimeBag(ReleasedPackGroup group) {
        return Bag.fromReleasedPackGroup("bag_" + group.correlationId(), group, bagSpec);
    }

    private boolean canReserveOutputBagFor(ReleasedPackGroup group) {
        return bagReceiver == null || bagReceiver.canReserveIncomingBag(buildRuntimeBag(group));
    }

    private void prepareBagForDischarge() {
        for (Pack pack : currentGroup.packs()) {
            pack.setState(Pack.PackMotionState.CONSUMED);
        }

        pendingDischarges.add(new PendingDischarge(
                buildRuntimeBag(currentGroup),
                buildCompletedBag(currentGroup)));
        reservedGroup = null;
        activeReservation = null;
        currentGroup = null;
        incomingTransferComplete = false;
        intakeState = BaggingMachineState.IDLE;
        timeInStateSeconds = 0d;
        dischargeAwaitingReceiver = false;
        refreshState();
        tryStartNextPendingDischarge();
    }

    private void tryStartNextPendingDischarge() {
        if (!pendingDischarges.isEmpty() && activeDischarge == null && !dischargeWithoutReceiverActive) {
            PendingDischarge nextPending = pendingDischarges.peek();
            if (bagReceiver != null) {
                if (!bagReceiver.canReserveIncomingBag(nextPending.completedRuntimeBag)) {
                    dischargeAwaitingReceiver = true;
                    refreshState();
                    return;
                }
                BagReservation reservation = bagReceiver.reserveIncomingBag(nextPending.completedRuntimeBag);
                bagReceiver.beginReceiving(reservation);
                activeDischarge = new BagDischarge(nextPending.completedRuntimeBag, reservation, dischargingDurationSeconds);
                dischargeAwaitingReceiver = false;
                refreshState();
                return;
            }

            dischargeWithoutReceiverActive = true;
            dischargeTimeInStateSeconds = 0d;
            refreshState();
        }
    }

    private void completeActiveDischarge() {
        PendingDischarge completedPending = pendingDischarges.remove();
        BagDischarge completedDischarge = activeDischarge;
        bagReceiver.completeReceiving(completedDischarge.getReservation());
        completedRuntimeBags.add(completedDischarge.getBag());
        completedBags.add(completedPending.completedBag);
        completedCorrelationIds.add(completedPending.completedBag.correlationId());
        activeDischarge = null;
        refreshState();
    }

    private void completeBagWithoutReceiver() {
        PendingDischarge completedPending = pendingDischarges.remove();
        completedRuntimeBags.add(completedPending.completedRuntimeBag);
        completedBags.add(completedPending.completedBag);
        completedCorrelationIds.add(completedPending.completedBag.correlationId());
        dischargeWithoutReceiverActive = false;
        dischargeTimeInStateSeconds = 0d;
        refreshState();
    }

    private void refreshState() {
        if (currentGroup != null) {
            state = intakeState;
            return;
        }
        if (activeDischarge != null || dischargeWithoutReceiverActive) {
            state = BaggingMachineState.DISCHARGING;
            return;
        }
        if (dischargeAwaitingReceiver || !pendingDischarges.isEmpty()) {
            state = BaggingMachineState.WAITING_FOR_RECEIVER;
            return;
        }
        state = BaggingMachineState.IDLE;
    }
}
