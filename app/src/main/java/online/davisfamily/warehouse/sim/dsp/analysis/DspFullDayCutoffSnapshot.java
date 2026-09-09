package online.davisfamily.warehouse.sim.dsp.analysis;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteSnapshot;

/** Immutable terminal-boundary observation. */
public record DspFullDayCutoffSnapshot(
        DspFullDayRuntimeState state,
        boolean acted,
        Optional<Duration> terminalElapsedTime,
        List<OutboundToteSnapshot> hardCutoffClosedTotes,
        String diagnostic) {

    public DspFullDayCutoffSnapshot {
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
        if (terminalElapsedTime == null) {
            throw new IllegalArgumentException("terminalElapsedTime must not be null");
        }
        terminalElapsedTime.ifPresent(value -> {
            if (value.isNegative()) {
                throw new IllegalArgumentException("terminalElapsedTime must not be negative");
            }
        });
        if (hardCutoffClosedTotes == null
                || hardCutoffClosedTotes.stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException("hardCutoffClosedTotes must not contain null");
        }
        hardCutoffClosedTotes = List.copyOf(hardCutoffClosedTotes);
        diagnostic = diagnostic == null ? "" : diagnostic.trim();
        if (state == DspFullDayRuntimeState.RUNNING && acted) {
            throw new IllegalArgumentException("running cutoff snapshot cannot have acted=true");
        }
    }

    public boolean terminal() {
        return state != DspFullDayRuntimeState.RUNNING;
    }
}
