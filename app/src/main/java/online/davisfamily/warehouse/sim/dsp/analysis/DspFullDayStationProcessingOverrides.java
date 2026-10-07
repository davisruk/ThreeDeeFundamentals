package online.davisfamily.warehouse.sim.dsp.analysis;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;

/** Immutable startup-only station settings; absent values retain the baseline. */
record DspFullDayStationProcessingOverrides(
        OptionalDouble thirdPartyDurationSeconds,
        OptionalDouble adaptingStoreDurationSeconds,
        OptionalDouble adaptingCollectDurationSeconds,
        OptionalInt processingPositionsPerBench,
        OptionalInt waitingCapacityPerBench,
        Optional<List<String>> benchIds) {

    private static final DspFullDayStationProcessingOverrides EMPTY =
            new DspFullDayStationProcessingOverrides(
                    OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty(),
                    OptionalInt.empty(), OptionalInt.empty(), Optional.empty());

    DspFullDayStationProcessingOverrides {
        if (thirdPartyDurationSeconds == null || adaptingStoreDurationSeconds == null
                || adaptingCollectDurationSeconds == null || processingPositionsPerBench == null
                || waitingCapacityPerBench == null || benchIds == null) {
            throw new IllegalArgumentException("station processing overrides must not be null");
        }
        requirePositiveDuration(thirdPartyDurationSeconds, "thirdParty.processingDurationSeconds");
        requirePositiveDuration(adaptingStoreDurationSeconds, "adapting.storeDurationSeconds");
        requirePositiveDuration(adaptingCollectDurationSeconds, "adapting.collectDurationSeconds");
        if (processingPositionsPerBench.isPresent() && processingPositionsPerBench.getAsInt() < 1) {
            throw new IllegalArgumentException("adapting.processingPositionsPerBench must be positive");
        }
        if (waitingCapacityPerBench.isPresent() && waitingCapacityPerBench.getAsInt() < 0) {
            throw new IllegalArgumentException("adapting.waitingCapacityPerBench must be nonnegative");
        }
        if (benchIds.isPresent()) {
            List<String> supplied = benchIds.orElseThrow();
            if (supplied.isEmpty()) {
                throw new IllegalArgumentException("adapting.benchIds must not be empty");
            }
            List<String> normalized = new ArrayList<>(supplied.size());
            Set<String> seen = new HashSet<>();
            for (String id : supplied) {
                if (id == null || id.isBlank()) {
                    throw new IllegalArgumentException("adapting.benchIds must contain nonblank strings");
                }
                String trimmed = id.trim();
                if (trimmed.isBlank()) {
                    throw new IllegalArgumentException("adapting.benchIds must contain nonblank strings");
                }
                if (!seen.add(trimmed)) {
                    throw new IllegalArgumentException("adapting.benchIds must be distinct: " + trimmed);
                }
                normalized.add(trimmed);
            }
            benchIds = Optional.of(List.copyOf(normalized));
        }
    }

    static DspFullDayStationProcessingOverrides empty() {
        return EMPTY;
    }

    private static void requirePositiveDuration(OptionalDouble value, String name) {
        if (value.isPresent() && (!Double.isFinite(value.getAsDouble()) || value.getAsDouble() <= 0d)) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }
}
