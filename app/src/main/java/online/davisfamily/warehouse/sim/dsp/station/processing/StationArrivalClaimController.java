package online.davisfamily.warehouse.sim.dsp.station.processing;

import java.time.Duration;
import java.util.Optional;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.SimulationController;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;

/**
 * Claims at most one exact head from one station arrival FIFO per simulation update.
 */
public final class StationArrivalClaimController implements SimulationController {
    private static final long NANOSECONDS_PER_SECOND = 1_000_000_000L;

    private final StationProcessingBinding binding;
    private final StationProcessingCoordinator coordinator;
    private Optional<PhysicalToteId> blockedPhysicalToteId = Optional.empty();
    private String blockedReason = "";
    private Optional<PhysicalToteId> lastClaimedPhysicalToteId = Optional.empty();
    private long successfulClaimCount;

    public StationArrivalClaimController(StationProcessingBinding binding) {
        if (binding == null) {
            throw new IllegalArgumentException("binding must not be null");
        }
        this.binding = binding;
        this.coordinator = binding.target().coordinator();
    }

    public StationProcessingBinding binding() {
        return binding;
    }

    @Override
    public void update(SimulationContext context, double dtSeconds) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        if (!Double.isFinite(dtSeconds) || dtSeconds < 0d) {
            throw new IllegalArgumentException("dtSeconds must be finite and >= 0");
        }

        Optional<RoutedPhysicalTote> optionalHead = binding.sourceQueue().peek();
        if (optionalHead.isEmpty()) {
            clearBlock();
            return;
        }

        clearBlock();
        RoutedPhysicalTote head = optionalHead.orElseThrow();
        requireMatchingDestination(head);
        Duration claimTime = simulationTime(context.getSimulationTimeSeconds());

        StationProcessingSnapshot beforeEvaluation = coordinator.snapshot();
        StationProcessingAdmissionDecision decision = binding.target().evaluate(head);
        if (decision == null) {
            throw new IllegalStateException("Station processing target returned a null admission decision");
        }
        if (!beforeEvaluation.equals(coordinator.snapshot())) {
            throw new IllegalStateException(
                    "Station processing target changed coordinator state during evaluation");
        }
        if (!decision.permitted()) {
            recordBlock(head.physicalToteId(), decision.reason());
            return;
        }

        StationProcessingClaim claim = binding.target().accept(head, claimTime);
        requireAcceptedClaim(head, claim, claimTime);

        RoutedPhysicalTote dequeued = binding.sourceQueue().dequeue().orElseThrow(() ->
                new IllegalStateException(
                        "Station arrival head disappeared after target acceptance"));
        if (dequeued != head) {
            throw new IllegalStateException(
                    "Station arrival dequeued a different routed tote after target acceptance");
        }

        successfulClaimCount++;
        lastClaimedPhysicalToteId = Optional.of(head.physicalToteId());
        clearBlock();
    }

    public StationArrivalClaimControllerSnapshot snapshot() {
        var sourceSnapshot = binding.sourceQueue().snapshot();
        Optional<PhysicalToteId> headPhysicalToteId = sourceSnapshot.entries().isEmpty()
                ? Optional.empty()
                : Optional.of(sourceSnapshot.entries().getFirst().physicalToteId());
        return new StationArrivalClaimControllerSnapshot(
                sourceSnapshot.destination(),
                sourceSnapshot.capacity(),
                sourceSnapshot.occupancy(),
                headPhysicalToteId,
                blockedPhysicalToteId,
                blockedReason,
                lastClaimedPhysicalToteId,
                successfulClaimCount);
    }

    private void requireMatchingDestination(RoutedPhysicalTote head) {
        if (!binding.sourceQueue().destination().equals(binding.target().destination())
                || !binding.sourceQueue().destination().equals(head.destination())) {
            throw new IllegalStateException(
                    "Station arrival source, target, and routed-tote destinations must match");
        }
    }

    private void requireAcceptedClaim(
            RoutedPhysicalTote head,
            StationProcessingClaim claim,
            Duration claimTime) {
        if (claim == null) {
            throw new IllegalStateException("Station processing target returned a null claim");
        }
        if (claim.routedTote() != head) {
            throw new IllegalStateException(
                    "Station processing target returned a claim for a different routed tote");
        }
        if (!claim.claimedAt().equals(claimTime)) {
            throw new IllegalStateException(
                    "Station processing target returned a claim with a different claim time");
        }
        StationProcessingClaim activeClaim = coordinator.requireActiveClaim(head.physicalToteId());
        if (activeClaim != claim) {
            throw new IllegalStateException(
                    "Station processing target did not return the coordinator's exact active claim");
        }
    }

    private void recordBlock(PhysicalToteId physicalToteId, String reason) {
        if (physicalToteId == null) {
            throw new IllegalArgumentException("physicalToteId must not be null");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalStateException("Deferred station processing admission must provide a reason");
        }
        blockedPhysicalToteId = Optional.of(physicalToteId);
        blockedReason = reason.trim();
    }

    private void clearBlock() {
        blockedPhysicalToteId = Optional.empty();
        blockedReason = "";
    }

    private static Duration simulationTime(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0d) {
            throw new IllegalArgumentException("simulation time must be finite and nonnegative");
        }
        return Duration.ofNanos(Math.round(seconds * NANOSECONDS_PER_SECOND));
    }
}
