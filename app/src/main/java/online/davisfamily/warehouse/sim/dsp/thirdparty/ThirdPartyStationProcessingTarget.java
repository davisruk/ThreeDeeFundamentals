package online.davisfamily.warehouse.sim.dsp.thirdparty;

import java.time.Duration;
import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.adapting.MutableToteLoadPlanRegistry;
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
 * Production station-processing target for one configured Third Party destination.
 *
 * <p>The target owns only the arrival-to-area admission boundary. The area remains responsible
 * for waiting and processing visits, while the shared coordinator owns the exact station claim.</p>
 */
public final class ThirdPartyStationProcessingTarget implements StationProcessingTarget {
    private static final String CAPACITY_BLOCKED_REASON = "Third Party area has no capacity";

    private final OperationalRouteDestination destination;
    private final StationProcessingOrderCatalog orderCatalog;
    private final MutableToteLoadPlanRegistry loadPlanRegistry;
    private final ThirdPartyVisitFactory visitFactory;
    private final ThirdPartyArea area;
    private final StationProcessingCoordinator coordinator;

    public ThirdPartyStationProcessingTarget(
            OperationalRouteDestination destination,
            StationProcessingOrderCatalog orderCatalog,
            MutableToteLoadPlanRegistry loadPlanRegistry,
            ThirdPartyVisitFactory visitFactory,
            ThirdPartyArea area,
            StationProcessingCoordinator coordinator) {
        if (destination == null) {
            throw new IllegalArgumentException("destination must not be null");
        }
        if (destination.stationType() != StationType.THIRD_PARTY) {
            throw new IllegalArgumentException("destination must identify a Third Party station");
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
        this.destination = destination;
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
        coordinator.validateCanClaim(routedTote, Duration.ZERO);
        if (!area.canAccept()) {
            return StationProcessingAdmissionDecision.defer(CAPACITY_BLOCKED_REASON);
        }
        return StationProcessingAdmissionDecision.permit();
    }

    @Override
    public StationProcessingClaim accept(RoutedPhysicalTote routedTote, Duration claimedAt) {
        ThirdPartyVisit visit = validateAndResolveVisit(routedTote);
        coordinator.validateCanClaim(routedTote, claimedAt);
        if (!area.submitVisit(visit)) {
            throw new IllegalStateException(
                    "Third Party area rejected a visit after admission validation: "
                            + routedTote.physicalToteId().value());
        }
        return coordinator.claim(routedTote, claimedAt);
    }

    private ThirdPartyVisit validateAndResolveVisit(RoutedPhysicalTote routedTote) {
        if (routedTote == null) {
            throw new IllegalArgumentException("routedTote must not be null");
        }
        if (routedTote.destination().stationType() != StationType.THIRD_PARTY
                || !destination.equals(routedTote.destination())) {
            throw new IllegalStateException(
                    "Routed tote destination does not match the configured Third Party target");
        }

        OperationalPhysicalToteIdentity identity = routedTote.launchRequest().identity();
        OrderSheetKey orderSheetKey = identity.orderSheetKey();
        NotionalToteOrder order = orderCatalog.find(orderSheetKey).orElseThrow(() ->
                new IllegalStateException(
                        "No retained order for routed Third Party sheet: " + orderSheetKey));
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

        Optional<ThirdPartyVisit> visitOptional = visitFactory.create(
                routedTote.physicalToteId(),
                order);
        if (visitOptional == null) {
            throw new IllegalStateException("Third Party visit factory returned null");
        }
        ThirdPartyVisit visit = visitOptional.orElseThrow(() ->
                new IllegalStateException(
                        "Routed tote has no Third Party visit: "
                                + routedTote.physicalToteId().value()));
        if (!routedTote.physicalToteId().equals(visit.physicalToteId())) {
            throw new IllegalStateException(
                    "Third Party visit physical ID does not match routed tote");
        }
        if (!orderSheetKey.equals(visit.orderSheetKey())) {
            throw new IllegalStateException(
                    "Third Party visit sheet does not match routed tote");
        }
        if (visit.orderType() != identity.orderType()) {
            throw new IllegalStateException(
                    "Third Party visit order type does not match routed tote");
        }
        if (!identity.serviceCentreId().equals(visit.serviceCentreId())) {
            throw new IllegalStateException(
                    "Third Party visit service centre does not match routed tote");
        }
        return visit;
    }
}
