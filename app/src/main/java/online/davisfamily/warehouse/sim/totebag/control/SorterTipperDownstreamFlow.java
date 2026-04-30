package online.davisfamily.warehouse.sim.totebag.control;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import online.davisfamily.warehouse.sim.totebag.handoff.PackReceiveTarget;
import online.davisfamily.warehouse.sim.totebag.machine.SortingMachine;
import online.davisfamily.warehouse.sim.totebag.pack.Pack;

public class SorterTipperDownstreamFlow implements TipperDownstreamFlow {
    private final SortingMachine sortingMachine;
    private final PackReceiveTarget sorterOutfeedTarget;
    private final Queue<Pack> pendingSorterOutfeed = new ArrayDeque<>();
    private final List<Pack> completedOutputPacks = new ArrayList<>();

    public SorterTipperDownstreamFlow(
            SortingMachine sortingMachine,
            PackReceiveTarget sorterOutfeedTarget) {
        if (sortingMachine == null) {
            throw new IllegalArgumentException("sortingMachine must not be null");
        }
        this.sortingMachine = sortingMachine;
        this.sorterOutfeedTarget = sorterOutfeedTarget;
    }

    @Override
    public boolean canAcceptDischargedPack(Pack pack) {
        return pack != null;
    }

    @Override
    public void acceptDischargedPack(Pack pack) {
        sortingMachine.receive(pack);
    }

    @Override
    public void update(double dtSeconds) {
        while (sortingMachine.hasReleasedPack()) {
            pendingSorterOutfeed.add(sortingMachine.pollReleasedPack());
        }

        while (!pendingSorterOutfeed.isEmpty()) {
            Pack pack = pendingSorterOutfeed.peek();
            if (sorterOutfeedTarget != null && !sorterOutfeedTarget.canAccept(pack)) {
                return;
            }
            pack = pendingSorterOutfeed.remove();
            if (sorterOutfeedTarget != null) {
                sorterOutfeedTarget.accept(pack);
            } else {
                pack.setState(Pack.PackMotionState.CONSUMED);
            }
            completedOutputPacks.add(pack);
        }
    }

    @Override
    public boolean keepsTipperOccupied() {
        return !sortingMachine.getQueuedPacks().isEmpty()
                || sortingMachine.hasReleasedPack()
                || !pendingSorterOutfeed.isEmpty();
    }

    public List<Pack> getCompletedOutputPacks() {
        return List.copyOf(completedOutputPacks);
    }
}
