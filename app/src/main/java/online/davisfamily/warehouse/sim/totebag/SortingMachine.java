package online.davisfamily.warehouse.sim.totebag;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.objects.StatefulSimObject;

public class SortingMachine implements StatefulSimObject<SortingMachineState> {
    private final String id;
    private final double releaseIntervalSeconds;
    private final Queue<Pack> inputQueue = new ArrayDeque<>();
    private final Queue<Pack> outputQueue = new ArrayDeque<>();

    private SortingMachineState state = SortingMachineState.IDLE;
    private double releaseCountdownSeconds;

    public SortingMachine(String id, double releaseIntervalSeconds) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (releaseIntervalSeconds < 0d) {
            throw new IllegalArgumentException("releaseIntervalSeconds must be >= 0");
        }
        this.id = id;
        this.releaseIntervalSeconds = releaseIntervalSeconds;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void update(SimulationContext context, double dtSeconds) {
        if (inputQueue.isEmpty()) {
            state = SortingMachineState.IDLE;
            return;
        }

        if (state == SortingMachineState.IDLE) {
            state = SortingMachineState.RECEIVING;
            releaseCountdownSeconds = releaseIntervalSeconds;
        }

        if (state == SortingMachineState.RECEIVING) {
            state = SortingMachineState.FUNNELLING;
        }

        if (state == SortingMachineState.FUNNELLING || state == SortingMachineState.RELEASING) {
            releaseCountdownSeconds -= dtSeconds;
            if (releaseCountdownSeconds <= 0d) {
                Pack pack = inputQueue.poll();
                if (pack != null) {
                    pack.setState(Pack.PackMotionState.MOVING);
                    outputQueue.add(pack);
                    state = SortingMachineState.RELEASING;
                    releaseCountdownSeconds = releaseIntervalSeconds;
                }
            }

            if (inputQueue.isEmpty()) {
                state = SortingMachineState.IDLE;
            } else if (state == SortingMachineState.RELEASING) {
                state = SortingMachineState.FUNNELLING;
            }
        }
    }

    @Override
    public SortingMachineState getState() {
        return state;
    }

    public void receive(Pack pack) {
        if (pack == null) {
            throw new IllegalArgumentException("pack must not be null");
        }
        inputQueue.add(pack);
        if (state == SortingMachineState.IDLE) {
            state = SortingMachineState.RECEIVING;
            releaseCountdownSeconds = releaseIntervalSeconds;
        }
    }

    public boolean hasReleasedPack() {
        return !outputQueue.isEmpty();
    }

    public Pack pollReleasedPack() {
        return outputQueue.poll();
    }

    public List<Pack> getQueuedPacks() {
        return Collections.unmodifiableList(new ArrayList<>(inputQueue));
    }
}
