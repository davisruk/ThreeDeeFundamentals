package online.davisfamily.warehouse.sim.dsp.analysis;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.schedule.DspServiceCentreTimetable;
import online.davisfamily.warehouse.sim.dsp.schedule.ServiceCentreDeadlineSnapshot;
import online.davisfamily.warehouse.sim.dsp.schedule.ServiceCentreDeadlineSnapshotFactory;
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClockSnapshot;

/** Pure evaluator for the locked {@code P2P_OUTPUT_CLOSED} milestone. */
public final class DspFullDayCompletionEvaluator {
    private final DspServiceCentreTimetable timetable;
    private final Duration downstreamHandlingDuration;
    private final ServiceCentreDeadlineSnapshotFactory deadlineFactory =
            new ServiceCentreDeadlineSnapshotFactory();

    public DspFullDayCompletionEvaluator(
            DspServiceCentreTimetable timetable,
            Duration downstreamHandlingDuration) {
        if (timetable == null) {
            throw new IllegalArgumentException("timetable must not be null");
        }
        if (downstreamHandlingDuration == null
                || downstreamHandlingDuration.isZero()
                || downstreamHandlingDuration.isNegative()) {
            throw new IllegalArgumentException(
                    "downstreamHandlingDuration must be positive");
        }
        this.timetable = timetable;
        this.downstreamHandlingDuration = downstreamHandlingDuration;
    }

    public DspFullDayCompletionEvaluator(DspServiceCentreTimetable timetable) {
        this(timetable, Duration.ofHours(1));
    }

    public DspServiceCentreCompletionSnapshot evaluate(Observation observation) {
        if (observation == null) {
            throw new IllegalArgumentException("observation must not be null");
        }
        return evaluateAt(observation, observation.clockSnapshot());
    }

    private DspServiceCentreCompletionSnapshot evaluateAt(
            Observation observation,
            DspOperationalClockSnapshot clockSnapshot) {
        ServiceCentreDeadlineSnapshot deadline = deadlineFactory.create(
                timetable.require(observation.serviceCentreId()),
                clockSnapshot,
                downstreamHandlingDuration);
        boolean complete = observation.provisionalComplete();
        Optional<Duration> completionTime = observation.previousCompletionElapsedTime();
        if (complete && completionTime.isEmpty()) {
            completionTime = Optional.of(clockSnapshot.elapsedSimulationTime());
        }
        if (!complete) {
            completionTime = Optional.empty();
        }

        DspServiceCentreCompletionOutcome outcome = outcome(
                complete,
                completionTime,
                clockSnapshot,
                deadline);
        Optional<LocalDateTime> completionDateTime = completionTime.map(value ->
                completionDateTime(value, clockSnapshot));
        return new DspServiceCentreCompletionSnapshot(
                observation.serviceCentreId(),
                observation.supplyComplete(),
                observation.upstreamWaitingCount(),
                observation.capacityBlockedManifestCount(),
                observation.osrWaitingCount(),
                observation.av02WaitingCount(),
                observation.nonTerminalInboundToteCount(),
                observation.remainingPhysicalToteCount(),
                observation.remainingPhysicalPackCount(),
                observation.remainingPlannedBagCount(),
                observation.activeStationClaimCount(),
                observation.pendingStationDispositionCount(),
                observation.transportEnvelopeCount(),
                observation.tipperInputCount(),
                observation.p2pAssignmentCount(),
                observation.openOutboundToteCount(),
                observation.unallocatedCompletedBagCount(),
                observation.unsupportedWork(),
                completionTime,
                completionDateTime,
                outcome,
                deadline,
                complete);
    }

    public List<DspServiceCentreCompletionSnapshot> evaluateAll(
            DspOperationalClockSnapshot clockSnapshot,
            List<Observation> observations) {
        if (clockSnapshot == null) {
            throw new IllegalArgumentException("clockSnapshot must not be null");
        }
        if (observations == null) {
            throw new IllegalArgumentException("observations must not be null");
        }
        return observations.stream().map(observation -> {
            if (observation == null) {
                throw new IllegalArgumentException("observations must not contain null");
            }
            return evaluateAt(observation, clockSnapshot);
        }).toList();
    }

    public DspServiceCentreTimetable timetable() {
        return timetable;
    }

    public Duration downstreamHandlingDuration() {
        return downstreamHandlingDuration;
    }

    private static DspServiceCentreCompletionOutcome outcome(
            boolean complete,
            Optional<Duration> completionTime,
            DspOperationalClockSnapshot clockSnapshot,
            ServiceCentreDeadlineSnapshot deadline) {
        if (!complete || completionTime.isEmpty()) {
            return DspServiceCentreCompletionOutcome.UNFINISHED_AT_HARD_CUTOFF;
        }
        LocalDateTime completedAt = completionDateTime(
                completionTime.orElseThrow(), clockSnapshot);
        if (!completedAt.isAfter(deadline.targetCompletion())) {
            return DspServiceCentreCompletionOutcome.ON_TARGET;
        }
        if (!completedAt.isAfter(deadline.latestAllowedCompletion())) {
            return DspServiceCentreCompletionOutcome.OVERTIME_BUT_DISPATCHABLE;
        }
        return DspServiceCentreCompletionOutcome.MISSED_TRUNKER;
    }

    private static LocalDateTime completionDateTime(
            Duration elapsedTime,
            DspOperationalClockSnapshot currentClock) {
        Duration delta = currentClock.elapsedSimulationTime().minus(elapsedTime);
        return currentClock.businessDateTime().minus(delta);
    }

    /** Immutable input to the pure evaluator. */
    public record Observation(
            String serviceCentreId,
            DspOperationalClockSnapshot clockSnapshot,
            boolean supplyComplete,
            int upstreamWaitingCount,
            int capacityBlockedManifestCount,
            int osrWaitingCount,
            int av02WaitingCount,
            int nonTerminalInboundToteCount,
            int remainingPhysicalToteCount,
            int remainingPhysicalPackCount,
            int remainingPlannedBagCount,
            int activeStationClaimCount,
            int pendingStationDispositionCount,
            int transportEnvelopeCount,
            int tipperInputCount,
            int p2pAssignmentCount,
            int openOutboundToteCount,
            int unallocatedCompletedBagCount,
            List<String> unsupportedWork,
            Optional<Duration> previousCompletionElapsedTime) {

        public Observation {
            if (serviceCentreId == null || serviceCentreId.isBlank()) {
                throw new IllegalArgumentException("serviceCentreId must not be blank");
            }
            serviceCentreId = serviceCentreId.trim();
            if (clockSnapshot == null) {
                throw new IllegalArgumentException("clockSnapshot must not be null");
            }
            if (unsupportedWork == null || previousCompletionElapsedTime == null) {
                throw new IllegalArgumentException("observation collections must not be null");
            }
            unsupportedWork = unsupportedWork.stream()
                    .map(value -> value == null ? "" : value.trim())
                    .filter(value -> !value.isEmpty())
                    .toList();
            if (previousCompletionElapsedTime.isPresent()
                    && previousCompletionElapsedTime.orElseThrow().isNegative()) {
                throw new IllegalArgumentException(
                        "previousCompletionElapsedTime must not be negative");
            }
            requireNonNegative(upstreamWaitingCount, "upstreamWaitingCount");
            requireNonNegative(capacityBlockedManifestCount, "capacityBlockedManifestCount");
            requireNonNegative(osrWaitingCount, "osrWaitingCount");
            requireNonNegative(av02WaitingCount, "av02WaitingCount");
            requireNonNegative(nonTerminalInboundToteCount, "nonTerminalInboundToteCount");
            requireNonNegative(remainingPhysicalToteCount, "remainingPhysicalToteCount");
            requireNonNegative(remainingPhysicalPackCount, "remainingPhysicalPackCount");
            requireNonNegative(remainingPlannedBagCount, "remainingPlannedBagCount");
            requireNonNegative(activeStationClaimCount, "activeStationClaimCount");
            requireNonNegative(pendingStationDispositionCount, "pendingStationDispositionCount");
            requireNonNegative(transportEnvelopeCount, "transportEnvelopeCount");
            requireNonNegative(tipperInputCount, "tipperInputCount");
            requireNonNegative(p2pAssignmentCount, "p2pAssignmentCount");
            requireNonNegative(openOutboundToteCount, "openOutboundToteCount");
            requireNonNegative(unallocatedCompletedBagCount, "unallocatedCompletedBagCount");
        }

        public Observation(
                String serviceCentreId,
                DspOperationalClockSnapshot clockSnapshot,
                boolean supplyComplete) {
            this(
                    serviceCentreId,
                    clockSnapshot,
                    supplyComplete,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    List.of(),
                    Optional.empty());
        }

        public boolean provisionalComplete() {
            return supplyComplete
                    && upstreamWaitingCount == 0
                    && capacityBlockedManifestCount == 0
                    && osrWaitingCount == 0
                    && av02WaitingCount == 0
                    && nonTerminalInboundToteCount == 0
                    && remainingPhysicalToteCount == 0
                    && remainingPhysicalPackCount == 0
                    && remainingPlannedBagCount == 0
                    && activeStationClaimCount == 0
                    && pendingStationDispositionCount == 0
                    && transportEnvelopeCount == 0
                    && tipperInputCount == 0
                    && p2pAssignmentCount == 0
                    && openOutboundToteCount == 0
                    && unallocatedCompletedBagCount == 0
                    && unsupportedWork.isEmpty();
        }

        public Optional<Duration> firstCompletionElapsedTime() {
            return previousCompletionElapsedTime;
        }

        private static void requireNonNegative(int value, String fieldName) {
            if (value < 0) {
                throw new IllegalArgumentException(fieldName + " must be >= 0");
            }
        }
    }
}
