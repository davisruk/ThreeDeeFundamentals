package online.davisfamily.warehouse.sim.dsp.station.processing;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;

/**
 * Test-only target used to exercise the generic arrival claimant without a station adapter.
 */
final class StationProcessingTestTarget implements StationProcessingTarget {
    private final OperationalRouteDestination destination;
    private final StationProcessingCoordinator coordinator;
    private Function<RoutedPhysicalTote, StationProcessingAdmissionDecision> evaluator =
            routedTote -> StationProcessingAdmissionDecision.permit();
    private Consumer<RoutedPhysicalTote> evaluationMutation = routedTote -> {
    };
    private Consumer<RoutedPhysicalTote> acceptanceMutation = routedTote -> {
    };
    private boolean registerClaim = true;
    private StationProcessingClaim returnedClaim;
    private final List<RoutedPhysicalTote> evaluatedTotes = new ArrayList<>();
    private final List<RoutedPhysicalTote> acceptedTotes = new ArrayList<>();
    private final List<Duration> acceptedTimes = new ArrayList<>();

    StationProcessingTestTarget(
            OperationalRouteDestination destination,
            StationProcessingCoordinator coordinator) {
        this.destination = destination;
        this.coordinator = coordinator;
    }

    StationProcessingTestTarget decision(
            StationProcessingAdmissionDecision decision) {
        evaluator = routedTote -> decision;
        return this;
    }

    StationProcessingTestTarget evaluator(
            Function<RoutedPhysicalTote, StationProcessingAdmissionDecision> evaluator) {
        this.evaluator = evaluator;
        return this;
    }

    StationProcessingTestTarget evaluationMutation(Consumer<RoutedPhysicalTote> mutation) {
        evaluationMutation = mutation;
        return this;
    }

    StationProcessingTestTarget acceptanceMutation(Consumer<RoutedPhysicalTote> mutation) {
        acceptanceMutation = mutation;
        return this;
    }

    StationProcessingTestTarget registerClaim(boolean registerClaim) {
        this.registerClaim = registerClaim;
        return this;
    }

    StationProcessingTestTarget returnedClaim(StationProcessingClaim returnedClaim) {
        this.returnedClaim = returnedClaim;
        return this;
    }

    List<RoutedPhysicalTote> evaluatedTotes() {
        return List.copyOf(evaluatedTotes);
    }

    List<RoutedPhysicalTote> acceptedTotes() {
        return List.copyOf(acceptedTotes);
    }

    List<Duration> acceptedTimes() {
        return List.copyOf(acceptedTimes);
    }

    @Override
    public OperationalRouteDestination destination() {
        return destination;
    }

    @Override
    public StationProcessingCoordinator coordinator() {
        return coordinator;
    }

    @Override
    public StationProcessingAdmissionDecision evaluate(RoutedPhysicalTote routedTote) {
        evaluatedTotes.add(routedTote);
        evaluationMutation.accept(routedTote);
        return evaluator.apply(routedTote);
    }

    @Override
    public StationProcessingClaim accept(RoutedPhysicalTote routedTote, Duration claimedAt) {
        acceptedTotes.add(routedTote);
        acceptedTimes.add(claimedAt);
        acceptanceMutation.accept(routedTote);
        StationProcessingClaim registeredClaim = registerClaim
                ? coordinator.claim(routedTote, claimedAt)
                : null;
        return returnedClaim == null ? registeredClaim : returnedClaim;
    }
}
