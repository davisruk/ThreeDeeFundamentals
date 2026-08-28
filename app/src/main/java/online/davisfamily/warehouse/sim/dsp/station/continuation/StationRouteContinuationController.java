package online.davisfamily.warehouse.sim.dsp.station.continuation;

import java.util.Optional;

import online.davisfamily.threedee.behaviour.routing.RouteFollower;
import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.SimulationController;
import online.davisfamily.warehouse.sim.dsp.adapting.MutableToteLoadPlanRegistry;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.OperationalPhysicalToteReleaseRequest;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteIdentity;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteLaunchRequest;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteLaunchRequestFactory;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pPhysicalToteAssignment;
import online.davisfamily.warehouse.sim.dsp.routing.DspRouteDeriver;
import online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingCoordinator;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingDisposition;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingDispositionType;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingOrderCatalog;
import online.davisfamily.warehouse.sim.dsp.transport.OsrOutboundTransportQueue;
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;
import online.davisfamily.warehouse.sim.dsp.transport.routing.WarehouseRouteCatalog;
import online.davisfamily.warehouse.sim.dsp.transport.routing.WarehouseRouteDefinition;
import online.davisfamily.warehouse.sim.dsp.transport.routing.WarehouseTransportPublicationState;
import online.davisfamily.warehouse.sim.dsp.transport.routing.WarehouseTransportPublisher;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

/**
 * Simulation-thread owner of the station-disposition to warehouse-transport handoff.
 *
 * <p>Only the exact FIFO head is evaluated in one update.  All validation is completed before the
 * same tote is rebound, enqueued, and acknowledged, so normal capacity/presentation deferrals do
 * not mutate downstream or physical state.</p>
 */
public final class StationRouteContinuationController implements SimulationController {
    private static final String CONSUME_PRESENTATION_DEFERRAL =
            "Terminal consume presentation is not complete";
    private static final String CONTINUATION_QUEUE_CAPACITY_DEFERRAL =
            "Continuation transport queue is full";

    private final StationProcessingCoordinator coordinator;
    private final StationProcessingOrderCatalog orderCatalog;
    private final MutableToteLoadPlanRegistry loadPlanRegistry;
    private final DspRouteDeriver routeDeriver;
    private final StationRouteContinuationSelector selector;
    private final StationRouteContinuationTargetResolver targetResolver;
    private final WarehouseRouteCatalog routeCatalog;
    private final OsrOutboundTransportQueue transportQueue;
    private final WarehouseTransportPublisher publisher;

    private Optional<PhysicalToteId> blockedPhysicalToteId = Optional.empty();
    private String blockedReason = "";
    private Optional<StationType> selectedNextStation = Optional.empty();
    private Optional<OperationalRouteDestination> selectedNextDestination = Optional.empty();
    private StationProcessingDisposition selectedForDisposition;
    private long continuedCount;
    private long consumedAcknowledgementCount;
    private Optional<PhysicalToteId> lastHandledPhysicalToteId = Optional.empty();
    private Optional<StationProcessingDispositionType> lastHandledDispositionType = Optional.empty();
    private Optional<OperationalRouteDestination> lastHandledNextDestination = Optional.empty();

    public StationRouteContinuationController(
            StationProcessingCoordinator coordinator,
            StationProcessingOrderCatalog orderCatalog,
            MutableToteLoadPlanRegistry loadPlanRegistry,
            DspRouteDeriver routeDeriver,
            StationRouteContinuationSelector selector,
            StationRouteContinuationTargetResolver targetResolver,
            WarehouseRouteCatalog routeCatalog,
            OsrOutboundTransportQueue transportQueue,
            WarehouseTransportPublisher publisher) {
        if (coordinator == null) {
            throw new IllegalArgumentException("coordinator must not be null");
        }
        if (orderCatalog == null) {
            throw new IllegalArgumentException("orderCatalog must not be null");
        }
        if (loadPlanRegistry == null) {
            throw new IllegalArgumentException("loadPlanRegistry must not be null");
        }
        if (routeDeriver == null) {
            throw new IllegalArgumentException("routeDeriver must not be null");
        }
        if (selector == null) {
            throw new IllegalArgumentException("selector must not be null");
        }
        if (targetResolver == null) {
            throw new IllegalArgumentException("targetResolver must not be null");
        }
        if (routeCatalog == null) {
            throw new IllegalArgumentException("routeCatalog must not be null");
        }
        if (transportQueue == null) {
            throw new IllegalArgumentException("transportQueue must not be null");
        }
        if (publisher == null) {
            throw new IllegalArgumentException("publisher must not be null");
        }
        this.coordinator = coordinator;
        this.orderCatalog = orderCatalog;
        this.loadPlanRegistry = loadPlanRegistry;
        this.routeDeriver = routeDeriver;
        this.selector = selector;
        this.targetResolver = targetResolver;
        this.routeCatalog = routeCatalog;
        this.transportQueue = transportQueue;
        this.publisher = publisher;
    }

    @Override
    public void update(SimulationContext context, double dtSeconds) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        if (!Double.isFinite(dtSeconds) || dtSeconds < 0d) {
            throw new IllegalArgumentException("dtSeconds must be finite and >= 0");
        }

        Optional<StationProcessingDisposition> optionalHead = coordinator.peekDisposition();
        if (optionalHead.isEmpty()) {
            clearBlock();
            clearSelection();
            return;
        }

        StationProcessingDisposition head = optionalHead.orElseThrow();
        if (head.type() == StationProcessingDispositionType.CONSUME) {
            handleConsume(head);
        } else if (head.type() == StationProcessingDispositionType.CONTINUE) {
            handleContinue(head);
        } else {
            throw new IllegalStateException("Unsupported station processing disposition type");
        }
    }

    /** Returns a fresh value-only inspection of the current continuation state. */
    public StationRouteContinuationControllerSnapshot snapshot() {
        Optional<StationProcessingDisposition> optionalHead = coordinator.peekDisposition();
        Optional<PhysicalToteId> headPhysicalToteId = optionalHead.map(
                StationProcessingDisposition::physicalToteId);
        Optional<StationProcessingDispositionType> headDispositionType = optionalHead.map(
                StationProcessingDisposition::type);

        Optional<StationType> visibleSelectedStation = Optional.empty();
        Optional<OperationalRouteDestination> visibleSelectedDestination = Optional.empty();
        if (optionalHead.isPresent() && selectedForDisposition == optionalHead.orElseThrow()) {
            visibleSelectedStation = selectedNextStation;
            visibleSelectedDestination = selectedNextDestination;
        }

        return new StationRouteContinuationControllerSnapshot(
                headPhysicalToteId,
                headDispositionType,
                visibleSelectedStation,
                visibleSelectedDestination,
                blockedPhysicalToteId,
                blockedReason,
                continuedCount,
                consumedAcknowledgementCount,
                lastHandledPhysicalToteId,
                lastHandledDispositionType,
                lastHandledNextDestination);
    }

    public long continuedCount() {
        return continuedCount;
    }

    public long consumedAcknowledgementCount() {
        return consumedAcknowledgementCount;
    }

    public long consumedAcknowledgedCount() {
        return consumedAcknowledgementCount;
    }

    private void handleConsume(StationProcessingDisposition head) {
        RoutedPhysicalTote routedTote = requireExactRoutedTote(head);
        Tote tote = routedTote.tote();
        if (tote.getInteractionMode() != Tote.ToteMotionState.HELD
                || tote.areLidsOpen()
                || routedTote.renderable().isVisible()) {
            recordBlock(head.physicalToteId(), CONSUME_PRESENTATION_DEFERRAL);
            return;
        }

        if (transportQueue.contains(head.physicalToteId())) {
            throw new IllegalStateException(
                    "Terminal consume physical tote is already queued for continuation transport: "
                            + head.physicalToteId().value());
        }

        coordinator.validateCanAcknowledgeDisposition(head);
        coordinator.acknowledgeDisposition(head);

        consumedAcknowledgementCount++;
        lastHandledPhysicalToteId = Optional.of(head.physicalToteId());
        lastHandledDispositionType = Optional.of(StationProcessingDispositionType.CONSUME);
        lastHandledNextDestination = Optional.empty();
        clearSelection();
        clearBlock();
    }

    private void handleContinue(StationProcessingDisposition head) {
        RoutedPhysicalTote routedTote = requireExactRoutedTote(head);
        Tote tote = routedTote.tote();
        requireHeldVisibleContinue(routedTote, tote);

        OperationalRouteLaunchRequest previousRequest = routedTote.launchRequest();
        OperationalPhysicalToteIdentity identity = previousRequest.identity();
        NotionalToteOrder order = orderCatalog.find(previousRequest.orderSheetKey())
                .orElseThrow(() -> new IllegalStateException(
                        "No retained order for continued launch sheet: "
                                + previousRequest.orderSheetKey()));
        if (identity.orderType() != order.orderType()
                || !identity.serviceCentreId().equals(order.serviceCentreId())) {
            throw new IllegalStateException(
                    "Continued order does not match the source-neutral release identity");
        }

        ToteLoadPlan registeredPlan = loadPlanRegistry.getLoadPlanFor(head.physicalToteId());
        if (registeredPlan == null) {
            throw new IllegalStateException(
                    "No registered current load plan for continued physical tote: "
                            + head.physicalToteId().value());
        }
        if (registeredPlan != head.currentLoadPlan()) {
            throw new IllegalStateException(
                    "Registered current load plan is not the exact disposition load plan");
        }

        RouteRequirements routeRequirements = routeDeriver.derive(order);
        StationType completedStation = head.claim().destination().stationType();
        if (completedStation != StationType.THIRD_PARTY
                && completedStation != StationType.ADAPTING) {
            throw new IllegalStateException(
                    "Unsupported completed continuation station: " + completedStation);
        }
        Optional<StationType> optionalNextStation = selector.nextStation(
                routeRequirements,
                completedStation);
        if (optionalNextStation == null || optionalNextStation.isEmpty()) {
            throw new IllegalStateException(
                    "Continued station route has no next required station");
        }
        StationType nextStation = optionalNextStation.orElseThrow();
        if (nextStation == StationType.MANUAL || nextStation == StationType.MANUAL_MERGE) {
            throw new IllegalStateException(
                    "Unsupported manual station in continued route: " + nextStation);
        }
        if (nextStation == StationType.P2P) {
            requireP2pAssignmentIdentity(routedTote, identity, order);
        }

        StationRouteContinuationDecision decision = targetResolver.resolve(
                head,
                order,
                nextStation);
        if (decision == null) {
            throw new IllegalStateException("Station route continuation resolver returned null");
        }
        if (decision.deferred()) {
            setSelection(head, nextStation, Optional.empty());
            recordBlock(head.physicalToteId(), decision.reason());
            return;
        }
        if (!decision.permitted() || decision.nextDestination().isEmpty()) {
            throw new IllegalStateException(
                    "Station route continuation resolver returned an invalid decision");
        }
        OperationalRouteDestination nextDestination = decision.nextDestination().orElseThrow();
        if (nextDestination.stationType() != nextStation) {
            throw new IllegalStateException(
                    "Continuation target station does not match the selected next station");
        }
        if (nextStation == StationType.P2P
                && !nextDestination.equals(
                        previousRequest.p2pAssignment().orElseThrow().destination())) {
            throw new IllegalStateException(
                    "Continuation P2P destination does not match the committed assignment");
        }

        WarehouseRouteDefinition currentRoute = requireRoute(
                head.claim().destination(),
                "completed");
        WarehouseRouteDefinition nextRoute = requireRoute(nextDestination, "next");
        RouteFollower follower = tote.getRouteFollower();
        if (follower == null) {
            throw new IllegalStateException("Continued tote route follower must not be null");
        }
        if (follower.getCurrentSegment() != currentRoute.terminalSegment()) {
            throw new IllegalStateException(
                    "Continued tote route follower is not on the exact completed terminal segment");
        }
        String reservation = tote.getReservedByMachineId();
        if (reservation != null && !reservation.isBlank()) {
            throw new IllegalStateException(
                    "Continued tote has an active machine reservation: " + reservation);
        }
        if (tote.getInteractionMode() != Tote.ToteMotionState.HELD) {
            throw new IllegalStateException("Continued tote must remain HELD before handoff");
        }

        WarehouseTransportPublicationState publicationState = publisher.publicationState(routedTote);
        if (publicationState == null) {
            throw new IllegalStateException("Warehouse publisher returned a null publication state");
        }
        if (publicationState != WarehouseTransportPublicationState.PUBLISHED_EXACT_OBJECTS) {
            throw new IllegalStateException(
                    "Continued tote must already be published with exact physical objects: "
                            + publicationState);
        }

        if (transportQueue.contains(head.physicalToteId())) {
            throw new IllegalStateException(
                    "Continued physical tote is already queued for outbound transport: "
                            + head.physicalToteId().value());
        }
        if (!transportQueue.canAccept()) {
            setSelection(head, nextStation, Optional.of(nextDestination));
            recordBlock(head.physicalToteId(), CONTINUATION_QUEUE_CAPACITY_DEFERRAL);
            return;
        }

        OperationalRouteLaunchRequest nextLaunchRequest = OperationalRouteLaunchRequestFactory.continueTo(
                previousRequest,
                nextDestination);
        RoutedPhysicalTote nextRoutedTote = new RoutedPhysicalTote(
                nextLaunchRequest,
                head.currentLoadPlan(),
                tote,
                routedTote.renderable());
        coordinator.validateCanAcknowledgeDisposition(head);

        // Mutation boundary: all checks above are read-only and all operations below are
        // simulation-thread-local, mechanically non-failing operations.
        tote.closeLids();
        tote.setInteractionMode(Tote.ToteMotionState.HELD);
        follower.setCurrentSegment(nextRoute.entrySegment());
        follower.setDistanceAlongSegment(nextRoute.entryDistance());
        follower.setTravelDirection(nextRoute.entryDirection());
        tote.snapToRouteDistance(nextRoute.entryDistance());
        transportQueue.enqueue(nextRoutedTote);
        coordinator.acknowledgeDisposition(head);

        continuedCount++;
        lastHandledPhysicalToteId = Optional.of(head.physicalToteId());
        lastHandledDispositionType = Optional.of(StationProcessingDispositionType.CONTINUE);
        lastHandledNextDestination = Optional.of(nextDestination);
        selectedForDisposition = head;
        selectedNextStation = Optional.of(nextStation);
        selectedNextDestination = Optional.of(nextDestination);
        clearBlock();
    }

    private static RoutedPhysicalTote requireExactRoutedTote(
            StationProcessingDisposition disposition) {
        if (disposition == null || disposition.claim() == null
                || disposition.claim().routedTote() == null) {
            throw new IllegalStateException("Station disposition has no exact routed tote claim");
        }
        RoutedPhysicalTote routedTote = disposition.claim().routedTote();
        PhysicalToteId physicalToteId = disposition.physicalToteId();
        if (!physicalToteId.equals(routedTote.physicalToteId())
                || !physicalToteId.equals(disposition.currentLoadPlan().physicalToteId())) {
            throw new IllegalStateException(
                    "Station disposition claim and current plan do not share exact physical identity");
        }
        OperationalPhysicalToteReleaseRequest releaseRequest = routedTote.launchRequest()
                .releaseRequest();
        if (!physicalToteId.equals(releaseRequest.physicalToteId())) {
            throw new IllegalStateException(
                    "Station disposition routed launch request has a stale physical identity");
        }
        if (routedTote.tote().getRenderable() != routedTote.renderable()) {
            throw new IllegalStateException(
                    "Station disposition routed tote does not own its exact renderable");
        }
        return routedTote;
    }

    private static void requireHeldVisibleContinue(
            RoutedPhysicalTote routedTote,
            Tote tote) {
        if (tote.getInteractionMode() != Tote.ToteMotionState.HELD) {
            throw new IllegalStateException("Continued tote must be HELD");
        }
        if (!routedTote.renderable().isVisible()) {
            throw new IllegalStateException(
                    "Continued tote must retain visible physical presentation");
        }
    }

    private static void requireP2pAssignmentIdentity(
            RoutedPhysicalTote routedTote,
            OperationalPhysicalToteIdentity identity,
            NotionalToteOrder order) {
        Optional<P2pPhysicalToteAssignment> optionalAssignment =
                routedTote.launchRequest().p2pAssignment();
        if (optionalAssignment.isEmpty()) {
            throw new IllegalStateException(
                    "P2P continuation requires an exact committed P2P assignment");
        }
        P2pPhysicalToteAssignment assignment = optionalAssignment.orElseThrow();
        if (!assignment.physicalToteId().equals(routedTote.physicalToteId())
                || !assignment.physicalToteId().equals(identity.physicalToteId())
                || !assignment.serviceCentreId().equals(identity.serviceCentreId())
                || !assignment.serviceCentreId().equals(order.serviceCentreId())) {
            throw new IllegalStateException(
                    "P2P assignment does not match the continued tote identity");
        }
    }

    private WarehouseRouteDefinition requireRoute(
            OperationalRouteDestination destination,
            String routeRole) {
        try {
            return routeCatalog.find(destination).orElseThrow(() ->
                    new IllegalStateException(
                            "No " + routeRole + " warehouse route is configured for destination: "
                                    + destination));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Invalid " + routeRole + " warehouse route destination: " + destination,
                    exception);
        }
    }

    private void recordBlock(PhysicalToteId physicalToteId, String reason) {
        if (physicalToteId == null) {
            throw new IllegalArgumentException("physicalToteId must not be null");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalStateException(
                    "Deferred station route continuation must provide a reason");
        }
        blockedPhysicalToteId = Optional.of(physicalToteId);
        blockedReason = reason.trim();
    }

    private void clearBlock() {
        blockedPhysicalToteId = Optional.empty();
        blockedReason = "";
    }

    private void clearSelection() {
        selectedForDisposition = null;
        selectedNextStation = Optional.empty();
        selectedNextDestination = Optional.empty();
    }

    private void setSelection(
            StationProcessingDisposition disposition,
            StationType nextStation,
            Optional<OperationalRouteDestination> nextDestination) {
        selectedForDisposition = disposition;
        selectedNextStation = Optional.of(nextStation);
        selectedNextDestination = nextDestination;
    }
}
