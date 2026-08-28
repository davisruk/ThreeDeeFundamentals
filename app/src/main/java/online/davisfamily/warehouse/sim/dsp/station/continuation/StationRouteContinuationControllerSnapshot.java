package online.davisfamily.warehouse.sim.dsp.station.continuation;

import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingDispositionType;

/**
 * Immutable inspection values for the simulation-thread station route continuation handoff.
 *
 * <p>No live station, route, tote, renderable, queue, or coordinator object is retained by this
 * value.  The selected destination is only exposed while it belongs to the current disposition
 * head; a normal deferral therefore leaves the prior evaluation visible for that same head without
 * exposing it after the head changes.</p>
 */
public record StationRouteContinuationControllerSnapshot(
        Optional<PhysicalToteId> headPhysicalToteId,
        Optional<StationProcessingDispositionType> headDispositionType,
        Optional<StationType> selectedNextStation,
        Optional<OperationalRouteDestination> selectedNextDestination,
        Optional<PhysicalToteId> blockedPhysicalToteId,
        String blockedReason,
        long continuedCount,
        long consumedAcknowledgementCount,
        Optional<PhysicalToteId> lastHandledPhysicalToteId,
        Optional<StationProcessingDispositionType> lastHandledDispositionType,
        Optional<OperationalRouteDestination> lastHandledNextDestination) {

    public StationRouteContinuationControllerSnapshot {
        if (headPhysicalToteId == null || headDispositionType == null) {
            throw new IllegalArgumentException("head optionals must not be null");
        }
        if (selectedNextStation == null || selectedNextDestination == null) {
            throw new IllegalArgumentException("selected-next optionals must not be null");
        }
        if (blockedPhysicalToteId == null) {
            throw new IllegalArgumentException("blockedPhysicalToteId must not be null");
        }
        if (lastHandledPhysicalToteId == null
                || lastHandledDispositionType == null
                || lastHandledNextDestination == null) {
            throw new IllegalArgumentException("last-handled optionals must not be null");
        }

        requirePair(
                "head physical tote and disposition type",
                headPhysicalToteId.isPresent(),
                headDispositionType.isPresent());
        if (selectedNextDestination.isPresent() && selectedNextStation.isEmpty()) {
            throw new IllegalArgumentException(
                    "selected next destination requires a selected next station");
        }
        if (selectedNextDestination.isPresent()
                && selectedNextStation.orElseThrow()
                        != selectedNextDestination.orElseThrow().stationType()) {
            throw new IllegalArgumentException(
                    "selected next station must match selected destination station type");
        }
        if (headPhysicalToteId.isEmpty()
                && (selectedNextStation.isPresent() || selectedNextDestination.isPresent())) {
            throw new IllegalArgumentException(
                    "selected next destination requires a current disposition head");
        }
        if (selectedNextStation.isPresent()
                && headDispositionType.orElseThrow() != StationProcessingDispositionType.CONTINUE) {
            throw new IllegalArgumentException(
                    "selected next station is only valid for a CONTINUE head");
        }

        blockedReason = blockedReason == null ? "" : blockedReason.trim();
        if (blockedPhysicalToteId.isPresent() != !blockedReason.isEmpty()) {
            throw new IllegalArgumentException(
                    "blocked physical tote and reason must both be present or both be absent");
        }

        if (continuedCount < 0) {
            throw new IllegalArgumentException("continuedCount must be >= 0");
        }
        if (consumedAcknowledgementCount < 0) {
            throw new IllegalArgumentException("consumedAcknowledgementCount must be >= 0");
        }
        requirePair(
                "last handled physical tote and disposition type",
                lastHandledPhysicalToteId.isPresent(),
                lastHandledDispositionType.isPresent());
        if (lastHandledDispositionType.isPresent()) {
            StationProcessingDispositionType lastType = lastHandledDispositionType.orElseThrow();
            boolean consume = lastType == StationProcessingDispositionType.CONSUME;
            if (consume && consumedAcknowledgementCount == 0) {
                throw new IllegalArgumentException(
                        "last terminal consume requires a positive consumed acknowledgement count");
            }
            if (!consume && continuedCount == 0) {
                throw new IllegalArgumentException(
                        "last continuation requires a positive continued count");
            }
            if (consume && lastHandledNextDestination.isPresent()) {
                throw new IllegalArgumentException(
                        "terminal consume must not expose a next destination");
            }
            if (!consume && lastHandledNextDestination.isEmpty()) {
                throw new IllegalArgumentException(
                        "continued disposition must expose its next destination");
            }
        } else if (lastHandledNextDestination.isPresent()) {
            throw new IllegalArgumentException(
                    "last handled next destination requires a last handled disposition");
        }
        if ((continuedCount + consumedAcknowledgementCount == 0)
                != lastHandledPhysicalToteId.isEmpty()) {
            throw new IllegalArgumentException(
                    "last handled identity must be present exactly when a disposition was acknowledged");
        }
    }

    /** Compatibility alias using the coordinator's acknowledged-consume terminology. */
    public long acknowledgedConsumeCount() {
        return consumedAcknowledgementCount;
    }

    /** Compatibility alias using the past-tense spelling used by some inspection consumers. */
    public long consumedAcknowledgedCount() {
        return consumedAcknowledgementCount;
    }

    /** Compatibility alias for consumers that call the terminal count a consumed count. */
    public long consumedAcknowledgementTotal() {
        return consumedAcknowledgementCount;
    }

    public long consumedAckCount() {
        return consumedAcknowledgementCount;
    }

    public long successfulConsumeCount() {
        return consumedAcknowledgementCount;
    }

    public long successfulConsumeAcknowledgementCount() {
        return consumedAcknowledgementCount;
    }

    /** Compatibility alias using the shorter consumed-count terminology. */
    public long consumedCount() {
        return consumedAcknowledgementCount;
    }

    public boolean blocked() {
        return blockedPhysicalToteId.isPresent();
    }

    public Optional<StationProcessingDispositionType> headType() {
        return headDispositionType;
    }

    public Optional<StationProcessingDispositionType> headDisposition() {
        return headDispositionType;
    }

    public Optional<PhysicalToteId> currentHeadPhysicalToteId() {
        return headPhysicalToteId;
    }

    public Optional<StationProcessingDispositionType> currentHeadDispositionType() {
        return headDispositionType;
    }

    public Optional<StationProcessingDispositionType> dispositionType() {
        return headDispositionType;
    }

    public Optional<StationType> selectedStation() {
        return selectedNextStation;
    }

    public Optional<OperationalRouteDestination> selectedDestination() {
        return selectedNextDestination;
    }

    public Optional<OperationalRouteDestination> lastHandledDestination() {
        return lastHandledNextDestination;
    }

    public Optional<OperationalRouteDestination> lastDestination() {
        return lastHandledNextDestination;
    }

    public Optional<StationProcessingDispositionType> lastHandledDisposition() {
        return lastHandledDispositionType;
    }

    public Optional<StationProcessingDispositionType> lastHandledType() {
        return lastHandledDispositionType;
    }

    public long successfulContinuationCount() {
        return continuedCount;
    }

    public long successfulContinueCount() {
        return continuedCount;
    }

    private static void requirePair(String owner, boolean firstPresent, boolean secondPresent) {
        if (firstPresent != secondPresent) {
            throw new IllegalArgumentException(owner + " must be present together");
        }
    }
}
