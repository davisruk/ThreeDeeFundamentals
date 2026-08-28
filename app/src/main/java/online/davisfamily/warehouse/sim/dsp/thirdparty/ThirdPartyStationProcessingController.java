package online.davisfamily.warehouse.sim.dsp.thirdparty;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.warehouse.sim.dsp.adapting.MutableToteLoadPlanRegistry;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingClaim;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingCompletionController;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingCoordinator;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingSnapshot;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

/**
 * Simulation-thread completion boundary for one shared Third Party area.
 *
 * <p>The underlying area controller remains the owner of domain completion and load-plan
 * mutation. This controller only turns an observed, identity-matched completion into one generic
 * {@code CONTINUE} disposition.</p>
 */
public final class ThirdPartyStationProcessingController
        implements StationProcessingCompletionController {
    private static final long NANOSECONDS_PER_SECOND = 1_000_000_000L;

    private final String processingControllerId;
    private final Set<OperationalRouteDestination> destinations;
    private final MutableToteLoadPlanRegistry loadPlanRegistry;
    private final ThirdPartyAreaController areaController;
    private final StationProcessingCoordinator coordinator;

    public ThirdPartyStationProcessingController(
            String processingControllerId,
            Set<OperationalRouteDestination> destinations,
            MutableToteLoadPlanRegistry loadPlanRegistry,
            ThirdPartyAreaController areaController,
            StationProcessingCoordinator coordinator) {
        if (processingControllerId == null || processingControllerId.isBlank()) {
            throw new IllegalArgumentException("processingControllerId must not be blank");
        }
        if (destinations == null || destinations.isEmpty()) {
            throw new IllegalArgumentException("destinations must not be null or empty");
        }
        LinkedHashSet<OperationalRouteDestination> destinationCopy = new LinkedHashSet<>();
        for (OperationalRouteDestination destination : destinations) {
            if (destination == null) {
                throw new IllegalArgumentException("destinations must not contain null");
            }
            if (destination.stationType() != StationType.THIRD_PARTY) {
                throw new IllegalArgumentException(
                        "Third Party processing destinations must identify Third Party stations");
            }
            destinationCopy.add(destination);
        }
        if (loadPlanRegistry == null) {
            throw new IllegalArgumentException("loadPlanRegistry must not be null");
        }
        if (areaController == null) {
            throw new IllegalArgumentException("areaController must not be null");
        }
        if (coordinator == null) {
            throw new IllegalArgumentException("coordinator must not be null");
        }
        this.processingControllerId = processingControllerId.trim();
        this.destinations = Collections.unmodifiableSet(destinationCopy);
        this.loadPlanRegistry = loadPlanRegistry;
        this.areaController = areaController;
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
        areaController.update(dtSeconds);

        List<StationProcessingSnapshot.ActiveClaim> activeClaims =
                coordinator.snapshot().activeClaims();
        for (StationProcessingSnapshot.ActiveClaim activeClaim : activeClaims) {
            if (!destinations.contains(activeClaim.destination())) {
                continue;
            }

            PhysicalToteId physicalToteId = activeClaim.physicalToteId();
            Optional<ThirdPartyCompletion> completion = areaController.completionForTote(physicalToteId);
            if (completion == null) {
                throw new IllegalStateException("Third Party area controller returned null completion");
            }
            if (completion.isEmpty()) {
                continue;
            }

            StationProcessingClaim claim = coordinator.requireActiveClaim(physicalToteId);
            requireMatchingVisitIdentity(claim, completion.orElseThrow().visit());

            ToteLoadPlan currentLoadPlan = loadPlanRegistry.getLoadPlanFor(physicalToteId);
            if (currentLoadPlan == null) {
                throw new IllegalStateException(
                        "Missing current replacement load plan for " + physicalToteId.value());
            }
            if (!physicalToteId.equals(currentLoadPlan.physicalToteId())) {
                throw new IllegalStateException(
                        "Current replacement load plan physical ID does not match claim");
            }
            if (currentLoadPlan == claim.routedTote().loadPlan()) {
                throw new IllegalStateException(
                        "Third Party completion did not install a replacement load plan for "
                                + physicalToteId.value());
            }

            coordinator.validateCanComplete(
                    physicalToteId,
                    online.davisfamily.warehouse.sim.dsp.station.processing
                            .StationProcessingDispositionType.CONTINUE,
                    currentLoadPlan,
                    completedAt);
            coordinator.complete(
                    physicalToteId,
                    online.davisfamily.warehouse.sim.dsp.station.processing
                            .StationProcessingDispositionType.CONTINUE,
                    currentLoadPlan,
                    completedAt);
            break;
        }
    }

    private void requireMatchingVisitIdentity(
            StationProcessingClaim claim,
            ThirdPartyVisit visit) {
        if (visit == null) {
            throw new IllegalStateException("Third Party completion visit must not be null");
        }
        if (!claim.physicalToteId().equals(visit.physicalToteId())) {
            throw new IllegalStateException(
                    "Third Party completion visit physical ID does not match active claim");
        }
        if (!claim.routedTote().launchRequest().orderSheetKey().equals(visit.orderSheetKey())) {
            throw new IllegalStateException(
                    "Third Party completion visit sheet does not match active claim");
        }
        if (claim.routedTote().launchRequest().orderType() != visit.orderType()) {
            throw new IllegalStateException(
                    "Third Party completion visit order type does not match active claim");
        }
        if (!claim.routedTote().launchRequest().serviceCentreId().equals(visit.serviceCentreId())) {
            throw new IllegalStateException(
                    "Third Party completion visit service centre does not match active claim");
        }
    }

    private static Duration simulationTime(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0d) {
            throw new IllegalArgumentException("simulation time must be finite and nonnegative");
        }
        return Duration.ofNanos(Math.round(seconds * NANOSECONDS_PER_SECOND));
    }
}
