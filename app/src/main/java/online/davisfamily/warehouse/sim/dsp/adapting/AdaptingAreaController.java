package online.davisfamily.warehouse.sim.dsp.adapting;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.runtime.DspSchedulerRuntimeState;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;

public class AdaptingAreaController {
    private final AdaptingArea area;
    private final DspSchedulerRuntimeState runtimeState;

    public AdaptingAreaController(AdaptingArea area, DspSchedulerRuntimeState runtimeState) {
        if (area == null) {
            throw new IllegalArgumentException("area must not be null");
        }
        if (runtimeState == null) {
            throw new IllegalArgumentException("runtimeState must not be null");
        }
        this.area = area;
        this.runtimeState = runtimeState;
    }

    public Optional<AdaptingBenchCompletion> applyBenchCompletion(AdaptingBenchId benchId) {
        if (benchId == null) {
            throw new IllegalArgumentException("benchId must not be null");
        }

        Optional<AdaptingBenchCompletion> completion = area.bench(benchId).consumeCompletion();
        if (completion.isEmpty()) {
            return Optional.empty();
        }

        AdaptingBenchCompletion value = completion.get();
        if (value.visit().visitType() == AdaptingVisitType.STORE) {
            Set<PreparedLineKey> preparedLineKeys = new LinkedHashSet<>();
            for (var line : value.visit().preparedLines()) {
                preparedLineKeys.add(PreparedLineKey.forPreparedLine(line));
            }
            runtimeState.addPreparedLineKeys(preparedLineKeys);
        }

        return completion;
    }
}
