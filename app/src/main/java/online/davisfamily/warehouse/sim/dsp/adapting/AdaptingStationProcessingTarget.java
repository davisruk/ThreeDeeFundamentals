package online.davisfamily.warehouse.sim.dsp.adapting;

import java.time.Duration;

import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteIdentity;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingAdmissionDecision;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingClaim;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingCoordinator;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingOrderCatalog;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingTarget;
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

/**
 * Production station-processing target for one exact Adapting bench destination.
 *
 * <p>The target owns only arrival validation and claim/admission. The shared area owns the
 * selected bench and its local FIFO, while the completion controller owns processing completion
 * and lifecycle/disposition sequencing.</p>
 */
public final class AdaptingStationProcessingTarget implements StationProcessingTarget {
    private final OperationalRouteDestination destination;
    private final AdaptingBenchId benchId;
    private final StationProcessingOrderCatalog orderCatalog;
    private final MutableToteLoadPlanRegistry loadPlanRegistry;
    private final AdaptingVisitFactory visitFactory;
    private final AdaptingArea area;
    private final StationProcessingCoordinator coordinator;

    public AdaptingStationProcessingTarget(
            OperationalRouteDestination destination,
            StationProcessingOrderCatalog orderCatalog,
            MutableToteLoadPlanRegistry loadPlanRegistry,
            AdaptingVisitFactory visitFactory,
            AdaptingArea area,
            StationProcessingCoordinator coordinator) {
        if (destination == null) {
            throw new IllegalArgumentException("destination must not be null");
        }
        if (destination.stationType() != StationType.ADAPTING) {
            throw new IllegalArgumentException(
                    "destination must identify an Adapting station");
        }
        if (orderCatalog == null) {
            throw new IllegalArgumentException("orderCatalog must not be null");
        }
        if (loadPlanRegistry == null) {
            throw new IllegalArgumentException("loadPlanRegistry must not be null");
        }
        if (visitFactory == null) {
            throw new IllegalArgumentException("visitFactory must not be null");
        }
        if (area == null) {
            throw new IllegalArgumentException("area must not be null");
        }
        if (coordinator == null) {
            throw new IllegalArgumentException("coordinator must not be null");
        }

        AdaptingBenchId selectedBenchId = new AdaptingBenchId(destination.targetId());
        // Resolving the bench here makes a misconfigured route target fail at composition time,
        // before any routed tote can be claimed.
        area.bench(selectedBenchId);

        this.destination = destination;
        this.benchId = selectedBenchId;
        this.orderCatalog = orderCatalog;
        this.loadPlanRegistry = loadPlanRegistry;
        this.visitFactory = visitFactory;
        this.area = area;
        this.coordinator = coordinator;
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
        validateAndResolveVisit(routedTote);
        coordinator.validateCanEvaluateClaim(routedTote);
        if (!area.canAcceptVisitAt(benchId)) {
            return StationProcessingAdmissionDecision.defer(
                    "Adapting bench " + benchId.value()
                            + " has no queue or processing capacity");
        }
        return StationProcessingAdmissionDecision.permit();
    }

    @Override
    public StationProcessingClaim accept(
            RoutedPhysicalTote routedTote,
            Duration claimedAt) {
        AdaptingVisit visit = validateAndResolveVisit(routedTote);
        coordinator.validateCanClaim(routedTote, claimedAt);

        AdaptingBenchSelection selection = area.submitVisitTo(benchId, visit);
        if (!selection.accepted()) {
            throw new IllegalStateException(
                    "Adapting bench rejected a visit after admission validation: "
                            + routedTote.physicalToteId().value());
        }
        if (!benchId.equals(selection.benchId())) {
            throw new IllegalStateException(
                    "Adapting area selected a different bench than the routed destination");
        }

        StationProcessingClaim claim = coordinator.claim(routedTote, claimedAt);
        if (area.bench(benchId).state() == AdaptingBenchState.QUEUED) {
            area.bench(benchId).startProcessing();
        }
        return claim;
    }

    private AdaptingVisit validateAndResolveVisit(RoutedPhysicalTote routedTote) {
        if (routedTote == null) {
            throw new IllegalArgumentException("routedTote must not be null");
        }
        if (routedTote.destination().stationType() != StationType.ADAPTING
                || !destination.equals(routedTote.destination())) {
            throw new IllegalStateException(
                    "Routed tote destination does not match the configured Adapting target");
        }

        OperationalPhysicalToteIdentity identity = routedTote.launchRequest().identity();
        OrderSheetKey orderSheetKey = identity.orderSheetKey();
        NotionalToteOrder order = orderCatalog.find(orderSheetKey).orElseThrow(() ->
                new IllegalStateException(
                        "No retained order for routed Adapting sheet: " + orderSheetKey));
        if (order.orderType() != identity.orderType()) {
            throw new IllegalStateException(
                    "Routed order type does not match retained order for " + orderSheetKey);
        }
        if (!order.serviceCentreId().equals(identity.serviceCentreId())) {
            throw new IllegalStateException(
                    "Routed service centre does not match retained order for " + orderSheetKey);
        }

        ToteLoadPlan registeredPlan = loadPlanRegistry.getLoadPlanFor(routedTote.physicalToteId());
        if (registeredPlan == null) {
            throw new IllegalStateException(
                    "Missing current tote load plan for " + routedTote.physicalToteId().value());
        }
        if (registeredPlan != routedTote.loadPlan()) {
            throw new IllegalStateException(
                    "Routed tote does not carry the current registered load plan for "
                            + routedTote.physicalToteId().value());
        }

        AdaptingVisit visit = visitFactory.create(routedTote.physicalToteId(), order);
        if (visit == null) {
            throw new IllegalStateException("Adapting visit factory returned null");
        }
        if (!routedTote.physicalToteId().equals(visit.physicalToteId())) {
            throw new IllegalStateException(
                    "Adapting visit physical ID does not match routed tote");
        }
        if (!orderSheetKey.equals(visit.profile().orderSheetKey())) {
            throw new IllegalStateException(
                    "Adapting visit sheet does not match routed tote");
        }
        if (!identity.serviceCentreId().equals(visit.profile().serviceCentreId())) {
            throw new IllegalStateException(
                    "Adapting visit service centre does not match routed tote");
        }
        return visit;
    }
}
