package online.davisfamily.warehouse.sim.dsp.analysis;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.SimulationController;
import online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspHeadlessP2pLineRuntime;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteSnapshot;
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClockSnapshot;

/**
 * Owns the one-way full-day terminal boundary.
 *
 * <p>Completion evaluation is supplied as immutable snapshots. The controller only closes
 * applicable output before evaluating early completion and closes all assigned open output totes
 * once at hard cutoff.</p>
 */
public final class DspFullDayCutoffController implements SimulationController {
    private final Supplier<DspOperationalClockSnapshot> clockSnapshotSupplier;
    private final Supplier<List<DspServiceCentreCompletionSnapshot>> completionSnapshotSupplier;
    private final Runnable closeApplicableOutputs;
    private final List<DspHeadlessP2pLineRuntime> lineRuntimes;
    private final Consumer<DspFullDayRuntimeState> stateConsumer;
    private DspFullDayRuntimeState state = DspFullDayRuntimeState.RUNNING;
    private boolean acted;
    private Optional<Duration> terminalElapsedTime = Optional.empty();
    private final List<OutboundToteSnapshot> hardCutoffClosedTotes = new ArrayList<>();
    private String diagnostic = "";

    public DspFullDayCutoffController(
            Supplier<DspOperationalClockSnapshot> clockSnapshotSupplier,
            Supplier<List<DspServiceCentreCompletionSnapshot>> completionSnapshotSupplier,
            Runnable closeApplicableOutputs,
            List<DspHeadlessP2pLineRuntime> lineRuntimes,
            Consumer<DspFullDayRuntimeState> stateConsumer) {
        if (clockSnapshotSupplier == null
                || completionSnapshotSupplier == null
                || closeApplicableOutputs == null
                || lineRuntimes == null
                || stateConsumer == null) {
            throw new IllegalArgumentException("cutoff controller inputs must not be null");
        }
        if (lineRuntimes.stream().anyMatch(runtime -> runtime == null)) {
            throw new IllegalArgumentException("lineRuntimes must not contain null");
        }
        this.clockSnapshotSupplier = clockSnapshotSupplier;
        this.completionSnapshotSupplier = completionSnapshotSupplier;
        this.closeApplicableOutputs = closeApplicableOutputs;
        this.lineRuntimes = List.copyOf(lineRuntimes);
        this.stateConsumer = stateConsumer;
    }

    public DspFullDayCutoffController(
            Supplier<DspOperationalClockSnapshot> clockSnapshotSupplier,
            Supplier<List<DspServiceCentreCompletionSnapshot>> completionSnapshotSupplier,
            List<DspHeadlessP2pLineRuntime> lineRuntimes,
            Consumer<DspFullDayRuntimeState> stateConsumer) {
        this(
                clockSnapshotSupplier,
                completionSnapshotSupplier,
                () -> { },
                lineRuntimes,
                stateConsumer);
    }

    @Override
    public void update(SimulationContext context, double dtSeconds) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        if (!Double.isFinite(dtSeconds) || dtSeconds < 0d) {
            throw new IllegalArgumentException("dtSeconds must be finite and >= 0");
        }
        if (acted) {
            return;
        }

        DspOperationalClockSnapshot clockSnapshot = clockSnapshotSupplier.get();
        if (clockSnapshot == null) {
            throw new IllegalStateException("clockSnapshotSupplier returned null");
        }

        closeApplicableOutputs.run();
        List<DspServiceCentreCompletionSnapshot> completions = completionSnapshotSupplier.get();
        if (completions == null) {
            throw new IllegalStateException("completionSnapshotSupplier returned null");
        }
        if (!completions.isEmpty() && completions.stream()
                .allMatch(DspServiceCentreCompletionSnapshot::complete)) {
            terminal(DspFullDayRuntimeState.ALL_SUPPORTED_WORK_COMPLETE, clockSnapshot);
            return;
        }

        if (clockSnapshot.hardCutoffReached()) {
            for (DspHeadlessP2pLineRuntime lineRuntime : lineRuntimes) {
                Optional<OutboundToteSnapshot> closedTote = lineRuntime
                        .closeOutboundToteForHardCutoff(clockSnapshot.elapsedSimulationTime());
                closedTote.ifPresent(hardCutoffClosedTotes::add);
            }
            terminal(DspFullDayRuntimeState.HARD_CUTOFF_REACHED, clockSnapshot);
        }
    }

    public DspFullDayRuntimeState state() {
        return state;
    }

    public boolean acted() {
        return acted;
    }

    public DspFullDayCutoffSnapshot snapshot() {
        return new DspFullDayCutoffSnapshot(
                state,
                acted,
                terminalElapsedTime,
                hardCutoffClosedTotes,
                diagnostic);
    }

    private void terminal(
            DspFullDayRuntimeState terminalState,
            DspOperationalClockSnapshot clockSnapshot) {
        if (acted) {
            return;
        }
        state = terminalState;
        acted = true;
        terminalElapsedTime = Optional.of(clockSnapshot.elapsedSimulationTime());
        stateConsumer.accept(terminalState);
    }
}
