package online.davisfamily.warehouse.sim.dsp.station.continuation;

import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;

/**
 * Immutable result of resolving the next destination for a completed station visit.
 */
public record StationRouteContinuationDecision(
        Optional<OperationalRouteDestination> destination,
        Optional<String> deferralReason) {

    public StationRouteContinuationDecision {
        if (destination == null) {
            throw new IllegalArgumentException("destination must not be null");
        }
        if (deferralReason == null) {
            throw new IllegalArgumentException("deferralReason must not be null");
        }
        if (destination.isPresent() == deferralReason.isPresent()) {
            throw new IllegalArgumentException(
                    "exactly one of destination or deferralReason must be present");
        }
        deferralReason = deferralReason.map(String::trim);
        if (deferralReason.isPresent() && deferralReason.orElseThrow().isEmpty()) {
            throw new IllegalArgumentException("deferralReason must not be blank");
        }
    }

    public static StationRouteContinuationDecision continueTo(
            OperationalRouteDestination destination) {
        if (destination == null) {
            throw new IllegalArgumentException("destination must not be null");
        }
        return new StationRouteContinuationDecision(Optional.of(destination), Optional.empty());
    }

    public static StationRouteContinuationDecision defer(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        return new StationRouteContinuationDecision(
                Optional.empty(), Optional.of(reason.trim()));
    }

    public boolean permitted() {
        return destination.isPresent();
    }

    public boolean deferred() {
        return deferralReason.isPresent();
    }

    /** Compatibility-style text accessor for callers that only need the reason value. */
    public String reason() {
        return deferralReason.orElse("");
    }

    public Optional<OperationalRouteDestination> nextDestination() {
        return destination;
    }
}
