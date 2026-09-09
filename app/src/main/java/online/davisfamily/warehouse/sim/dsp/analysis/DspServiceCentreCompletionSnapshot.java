package online.davisfamily.warehouse.sim.dsp.analysis;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.schedule.ServiceCentreDeadlineSnapshot;

/** Immutable, mutation-free completion observation for one service centre. */
public record DspServiceCentreCompletionSnapshot(
        String serviceCentreId,
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
        Optional<Duration> completionElapsedTime,
        Optional<LocalDateTime> completionDateTime,
        DspServiceCentreCompletionOutcome outcome,
        ServiceCentreDeadlineSnapshot deadline,
        boolean complete) {

    public DspServiceCentreCompletionSnapshot {
        if (serviceCentreId == null || serviceCentreId.isBlank()) {
            throw new IllegalArgumentException("serviceCentreId must not be blank");
        }
        serviceCentreId = serviceCentreId.trim();
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
        if (unsupportedWork == null) {
            throw new IllegalArgumentException("unsupportedWork must not be null");
        }
        unsupportedWork = unsupportedWork.stream()
                .map(value -> value == null ? "" : value.trim())
                .filter(value -> !value.isEmpty())
                .toList();
        if (completionElapsedTime == null || completionDateTime == null) {
            throw new IllegalArgumentException("completion times must not be null");
        }
        if (completionElapsedTime.isPresent() && completionElapsedTime.orElseThrow().isNegative()) {
            throw new IllegalArgumentException("completionElapsedTime must not be negative");
        }
        if (outcome == null) {
            throw new IllegalArgumentException("outcome must not be null");
        }
        if (deadline == null) {
            throw new IllegalArgumentException("deadline must not be null");
        }
        if (!serviceCentreId.equals(deadline.serviceCentreId())) {
            throw new IllegalArgumentException("deadline serviceCentreId must match snapshot");
        }
        if (complete != provisionalComplete(
                supplyComplete,
                upstreamWaitingCount,
                capacityBlockedManifestCount,
                osrWaitingCount,
                av02WaitingCount,
                nonTerminalInboundToteCount,
                remainingPhysicalToteCount,
                remainingPhysicalPackCount,
                remainingPlannedBagCount,
                activeStationClaimCount,
                pendingStationDispositionCount,
                transportEnvelopeCount,
                tipperInputCount,
                p2pAssignmentCount,
                openOutboundToteCount,
                unallocatedCompletedBagCount,
                unsupportedWork)) {
            throw new IllegalArgumentException("complete does not match completion predicates");
        }
        if (complete && completionElapsedTime.isEmpty()) {
            throw new IllegalArgumentException("a complete snapshot requires a completion time");
        }
        if (!complete && completionElapsedTime.isPresent()) {
            throw new IllegalArgumentException(
                    "an incomplete snapshot must not expose a completion time");
        }
    }

    public boolean completed() {
        return complete;
    }

    public boolean provisionalComplete() {
        return complete;
    }

    public Optional<Duration> completionTime() {
        return completionElapsedTime;
    }

    public Optional<Duration> firstCompletionTime() {
        return completionElapsedTime;
    }

    public int remainingWork() {
        return remainingPhysicalToteCount
                + remainingPhysicalPackCount
                + remainingPlannedBagCount;
    }

    public int remainingWorkCount() {
        return remainingWork();
    }

    public boolean unsupported() {
        return !unsupportedWork.isEmpty();
    }

    private static boolean provisionalComplete(
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
            List<String> unsupportedWork) {
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

    private static void requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must be >= 0");
        }
    }
}
