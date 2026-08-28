package online.davisfamily.warehouse.sim.dsp.adapting;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteLifecycleController;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingClaim;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingCompletionController;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingCoordinator;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingDispositionType;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

/**
 * Simulation-thread completion boundary for one shared Adapting area.
 *
 * <p>Bench processing is advanced once per update. Completion identity and all completion
 * preconditions are checked before the area consumes its domain completion, after which the
 * lifecycle and generic station disposition are applied in the locked order.</p>
 */
public final class AdaptingStationProcessingController
        implements StationProcessingCompletionController {
    private static final long NANOSECONDS_PER_SECOND = 1_000_000_000L;

    private final String processingControllerId;
    private final Set<OperationalRouteDestination> destinations;
    private final List<AdaptingBenchId> sortedBenchIds;
    private final Map<AdaptingBenchId, OperationalRouteDestination> destinationByBenchId;
    private final MutableToteLoadPlanRegistry loadPlanRegistry;
    private final AdaptingArea area;
    private final AdaptingAreaController areaController;
    private final InboundToteLifecycleController inboundLifecycleController;
    private final StationProcessingCoordinator coordinator;

    public AdaptingStationProcessingController(
            String processingControllerId,
            Set<OperationalRouteDestination> destinations,
            MutableToteLoadPlanRegistry loadPlanRegistry,
            AdaptingArea area,
            AdaptingAreaController areaController,
            InboundToteLifecycleController inboundLifecycleController,
            StationProcessingCoordinator coordinator) {
        if (processingControllerId == null || processingControllerId.isBlank()) {
            throw new IllegalArgumentException("processingControllerId must not be blank");
        }
        if (destinations == null || destinations.isEmpty()) {
            throw new IllegalArgumentException("destinations must not be null or empty");
        }
        if (loadPlanRegistry == null) {
            throw new IllegalArgumentException("loadPlanRegistry must not be null");
        }
        if (area == null) {
            throw new IllegalArgumentException("area must not be null");
        }
        if (areaController == null) {
            throw new IllegalArgumentException("areaController must not be null");
        }
        if (inboundLifecycleController == null) {
            throw new IllegalArgumentException("inboundLifecycleController must not be null");
        }
        if (coordinator == null) {
            throw new IllegalArgumentException("coordinator must not be null");
        }

        LinkedHashSet<OperationalRouteDestination> destinationCopy = new LinkedHashSet<>();
        Map<AdaptingBenchId, OperationalRouteDestination> destinationByBench = new LinkedHashMap<>();
        for (OperationalRouteDestination destination : destinations) {
            if (destination == null) {
                throw new IllegalArgumentException("destinations must not contain null");
            }
            if (destination.stationType() != StationType.ADAPTING) {
                throw new IllegalArgumentException(
                        "Adapting processing destinations must identify Adapting stations");
            }
            AdaptingBenchId benchId = new AdaptingBenchId(destination.targetId());
            area.bench(benchId);
            if (destinationByBench.putIfAbsent(benchId, destination) != null) {
                throw new IllegalArgumentException(
                        "Duplicate Adapting processing bench destination: " + benchId);
            }
            destinationCopy.add(destination);
        }

        List<AdaptingBenchId> sortedIds = new ArrayList<>(destinationByBench.keySet());
        sortedIds.sort(Comparator.naturalOrder());
        this.processingControllerId = processingControllerId.trim();
        this.destinations = Collections.unmodifiableSet(destinationCopy);
        this.sortedBenchIds = List.copyOf(sortedIds);
        this.destinationByBenchId = Map.copyOf(destinationByBench);
        this.loadPlanRegistry = loadPlanRegistry;
        this.area = area;
        this.areaController = areaController;
        this.inboundLifecycleController = inboundLifecycleController;
        this.coordinator = coordinator;
    }

    @Override
    public String processingControllerId() {
        return processingControllerId;
    }

    @Override
    public Set<OperationalRouteDestination> destinations() {
        return destinations;
    }

    @Override
    public StationProcessingCoordinator coordinator() {
        return coordinator;
    }

    @Override
    public void update(SimulationContext context, double dtSeconds) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        if (!Double.isFinite(dtSeconds) || dtSeconds < 0d) {
            throw new IllegalArgumentException("dtSeconds must be finite and >= 0");
        }

        Duration completedAt = simulationTime(context.getSimulationTimeSeconds());

        // The shared area is advanced exactly once per controller update: each owned bench is
        // ticked once in deterministic bench-id order.
        for (AdaptingBenchId benchId : sortedBenchIds) {
            area.bench(benchId).tick(dtSeconds);
        }

        // Only one completed claim may cross the generic boundary in one update.
        for (AdaptingBenchId benchId : sortedBenchIds) {
            AdaptingBench bench = area.bench(benchId);
            if (bench.state() != AdaptingBenchState.COMPLETED) {
                continue;
            }

            Optional<AdaptingBenchCompletion> completionOptional = bench.peekCompletion();
            if (completionOptional.isEmpty()) {
                throw new IllegalStateException(
                        "Completed Adapting bench has no staged completion: " + benchId);
            }
            AdaptingBenchCompletion completion = completionOptional.orElseThrow();
            String activeToteId = bench.snapshot().activeToteId();
            if (activeToteId == null || activeToteId.isBlank()) {
                throw new IllegalStateException(
                        "Completed Adapting bench has no retained active tote: " + benchId);
            }
            PhysicalToteId physicalToteId = new PhysicalToteId(activeToteId);
            Optional<StationProcessingClaim> activeClaim = activeClaimFor(physicalToteId);
            if (activeClaim.isEmpty()) {
                continue;
            }

            StationProcessingClaim claim = activeClaim.orElseThrow();
            OperationalRouteDestination expectedDestination = destinationByBenchId.get(benchId);
            if (!expectedDestination.equals(claim.destination())) {
                throw new IllegalStateException(
                        "Adapting completion claim destination does not match bench " + benchId);
            }
            requireMatchingVisitIdentity(claim, completion.visit());

            ToteLoadPlan currentLoadPlan = loadPlanRegistry.getLoadPlanFor(physicalToteId);
            if (currentLoadPlan == null) {
                throw new IllegalStateException(
                        "Missing current tote load plan for completed Adapting claim: "
                                + physicalToteId.value());
            }
            if (currentLoadPlan != claim.routedTote().loadPlan()) {
                throw new IllegalStateException(
                        "Completed Adapting claim does not carry the current registered load plan");
            }

            if (completion.visit().visitType() == AdaptingVisitType.STORE) {
                completeStore(benchId, claim, currentLoadPlan, completedAt);
            } else {
                completeCollect(benchId, claim, currentLoadPlan, completedAt);
            }

            if (area.dispatchNextQueuedVisit(benchId)
                    && area.bench(benchId).state() == AdaptingBenchState.QUEUED) {
                area.bench(benchId).startProcessing();
            }
            break;
        }
    }

    private void completeStore(
            AdaptingBenchId benchId,
            StationProcessingClaim claim,
            ToteLoadPlan currentLoadPlan,
            Duration completedAt) {
        PhysicalToteId physicalToteId = claim.physicalToteId();

        // All expected failures are checked before the area consumes its completion.
        coordinator.validateCanComplete(
                physicalToteId,
                StationProcessingDispositionType.CONSUME,
                currentLoadPlan,
                completedAt);
        inboundLifecycleController.validateConsumeAtAdapting(physicalToteId, completedAt);

        AdaptingBenchCompletion applied = areaController.applyBenchCompletion(benchId)
                .orElseThrow(() -> new IllegalStateException(
                        "Adapting STORE completion disappeared before application"));
        requireMatchingVisitIdentity(claim, applied.visit());

        inboundLifecycleController.consumeAtAdapting(physicalToteId, completedAt);
        ToteLoadPlan retainedPlan = loadPlanRegistry.getLoadPlanFor(physicalToteId);
        if (retainedPlan == null || retainedPlan != currentLoadPlan) {
            throw new IllegalStateException(
                    "Adapting STORE completion did not retain the current load plan");
        }
        coordinator.complete(
                physicalToteId,
                StationProcessingDispositionType.CONSUME,
                retainedPlan,
                completedAt);
    }

    private void completeCollect(
            AdaptingBenchId benchId,
            StationProcessingClaim claim,
            ToteLoadPlan currentLoadPlan,
            Duration completedAt) {
        PhysicalToteId physicalToteId = claim.physicalToteId();

        // Validate the generic completion before applying the domain completion. The second
        // validation below uses the exact replacement plan installed by the area controller.
        coordinator.validateCanComplete(
                physicalToteId,
                StationProcessingDispositionType.CONTINUE,
                currentLoadPlan,
                completedAt);

        AdaptingBenchCompletion applied = areaController.applyBenchCompletion(benchId)
                .orElseThrow(() -> new IllegalStateException(
                        "Adapting COLLECT completion disappeared before application"));
        requireMatchingVisitIdentity(claim, applied.visit());

        ToteLoadPlan replacementPlan = loadPlanRegistry.getLoadPlanFor(physicalToteId);
        if (replacementPlan == null) {
            throw new IllegalStateException(
                    "Adapting COLLECT completion did not install a replacement load plan");
        }
        if (!physicalToteId.equals(replacementPlan.physicalToteId())) {
            throw new IllegalStateException(
                    "Adapting COLLECT replacement load plan physical ID does not match claim");
        }
        if (replacementPlan == currentLoadPlan) {
            throw new IllegalStateException(
                    "Adapting COLLECT completion did not replace the current load plan");
        }

        coordinator.validateCanComplete(
                physicalToteId,
                StationProcessingDispositionType.CONTINUE,
                replacementPlan,
                completedAt);
        coordinator.complete(
                physicalToteId,
                StationProcessingDispositionType.CONTINUE,
                replacementPlan,
                completedAt);
    }

    private Optional<StationProcessingClaim> activeClaimFor(PhysicalToteId physicalToteId) {
        return coordinator.snapshot().activeClaims().stream()
                .filter(active -> active.physicalToteId().equals(physicalToteId))
                .findFirst()
                .map(active -> coordinator.requireActiveClaim(physicalToteId));
    }

    private static void requireMatchingVisitIdentity(
            StationProcessingClaim claim,
            AdaptingVisit visit) {
        if (visit == null) {
            throw new IllegalStateException("Adapting completion visit must not be null");
        }
        if (!claim.physicalToteId().equals(visit.physicalToteId())) {
            throw new IllegalStateException(
                    "Adapting completion visit physical ID does not match active claim");
        }
        if (!claim.routedTote().launchRequest().orderSheetKey().equals(
                visit.profile().orderSheetKey())) {
            throw new IllegalStateException(
                    "Adapting completion visit sheet does not match active claim");
        }
        OrderType claimedOrderType = claim.routedTote().launchRequest().orderType();
        if (!visitMatchesOrderType(visit, claimedOrderType)) {
            throw new IllegalStateException(
                    "Adapting completion visit type does not match active claim");
        }
        if (!claim.routedTote().launchRequest().serviceCentreId().equals(
                visit.profile().serviceCentreId())) {
            throw new IllegalStateException(
                    "Adapting completion visit service centre does not match active claim");
        }
    }

    private static boolean visitMatchesOrderType(
            AdaptingVisit visit,
            OrderType orderType) {
        if (visit.visitType() == AdaptingVisitType.STORE) {
            return orderType == OrderType.ADAPTED;
        }
        return orderType == OrderType.ASSOCIATED || orderType == OrderType.EMPTY;
    }

    private static Duration simulationTime(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0d) {
            throw new IllegalArgumentException("simulation time must be finite and nonnegative");
        }
        return Duration.ofNanos(Math.round(seconds * NANOSECONDS_PER_SECOND));
    }
}
