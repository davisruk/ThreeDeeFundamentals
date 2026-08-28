package online.davisfamily.warehouse.sim.dsp.transport.routing;

import java.util.Optional;

import online.davisfamily.threedee.behaviour.routing.RouteFollower;
import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.SimulationController;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.transport.OsrOutboundTransportQueue;
import online.davisfamily.warehouse.sim.dsp.transport.OsrOutboundTransportQueueSnapshot;
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;
import online.davisfamily.warehouse.sim.tote.Tote.ToteMotionState;

public final class WarehouseTransportIngressController implements SimulationController {
    private final OsrOutboundTransportQueue transportQueue;
    private final WarehouseRouteCatalog routeCatalog;
    private final WarehouseTransportInFlightRegistry inFlightRegistry;
    private final WarehouseTransportPublisher publisher;
    private Optional<PhysicalToteId> lastIngressPhysicalToteId = Optional.empty();
    private Optional<OperationalRouteDestination> lastIngressDestination = Optional.empty();
    private Optional<PhysicalToteId> blockedPhysicalToteId = Optional.empty();
    private String blockedReason = "";
    private long successfulIngressCount;
    private long initialPublicationCount;
    private long exactObjectReentryCount;

    public WarehouseTransportIngressController(
            OsrOutboundTransportQueue transportQueue,
            WarehouseRouteCatalog routeCatalog,
            WarehouseTransportInFlightRegistry inFlightRegistry,
            WarehouseTransportPublisher publisher) {
        if (transportQueue == null) {
            throw new IllegalArgumentException("transportQueue must not be null");
        }
        if (routeCatalog == null) {
            throw new IllegalArgumentException("routeCatalog must not be null");
        }
        if (inFlightRegistry == null) {
            throw new IllegalArgumentException("inFlightRegistry must not be null");
        }
        if (publisher == null) {
            throw new IllegalArgumentException("publisher must not be null");
        }
        this.transportQueue = transportQueue;
        this.routeCatalog = routeCatalog;
        this.inFlightRegistry = inFlightRegistry;
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

        Optional<RoutedPhysicalTote> optionalHead = transportQueue.peek();
        if (optionalHead.isEmpty()) {
            clearBlock();
            return;
        }
        RoutedPhysicalTote head = optionalHead.orElseThrow();

        WarehouseRouteDefinition routeDefinition;
        try {
            routeDefinition = routeCatalog.find(head.destination()).orElse(null);
        } catch (IllegalArgumentException exception) {
            recordBlock(head, exception.getMessage());
            return;
        }
        if (routeDefinition == null) {
            recordBlock(head, "No warehouse route is configured for destination");
            return;
        }
        String bindingFailure = routeBindingFailure(head, routeDefinition);
        if (!bindingFailure.isEmpty()) {
            recordBlock(head, bindingFailure);
            return;
        }
        WarehouseTransportPublicationState publicationState = publisher.publicationState(head);
        if (publicationState == WarehouseTransportPublicationState.PHYSICAL_ID_CONFLICT) {
            recordBlock(head, "Physical tote publication has conflicting physical objects");
            return;
        }
        if (inFlightRegistry.contains(head.physicalToteId())) {
            recordBlock(head, "Physical tote is already active in warehouse transport");
            return;
        }
        if (!inFlightRegistry.canAccept()) {
            recordBlock(head, "Warehouse transport has no in-flight capacity");
            return;
        }

        if (publicationState == WarehouseTransportPublicationState.UNPUBLISHED) {
            publisher.publish(head);
        }
        inFlightRegistry.register(head);
        RoutedPhysicalTote dequeued = transportQueue.dequeue().orElseThrow(() ->
                new IllegalStateException(
                        "Outbound transport head disappeared after warehouse publication"));
        if (dequeued != head) {
            throw new IllegalStateException(
                    "Outbound transport queue dequeued a different tote after publication");
        }

        if (publicationState == WarehouseTransportPublicationState.PUBLISHED_EXACT_OBJECTS) {
            head.tote().setInteractionMode(ToteMotionState.MOVING);
            exactObjectReentryCount++;
        } else {
            initialPublicationCount++;
        }

        successfulIngressCount++;
        lastIngressPhysicalToteId = Optional.of(head.physicalToteId());
        lastIngressDestination = Optional.of(head.destination());
        clearBlock();
    }

    public WarehouseTransportIngressControllerSnapshot snapshot() {
        OsrOutboundTransportQueueSnapshot transportSnapshot = transportQueue.snapshot();
        WarehouseTransportInFlightSnapshot inFlightSnapshot = inFlightRegistry.snapshot();
        Optional<PhysicalToteId> headPhysicalToteId = Optional.empty();
        Optional<OperationalRouteDestination> headDestination = Optional.empty();
        if (!transportSnapshot.entries().isEmpty()) {
            OsrOutboundTransportQueueSnapshot.Entry head =
                    transportSnapshot.entries().get(0);
            headPhysicalToteId = Optional.of(head.physicalToteId());
            headDestination = Optional.of(head.destination());
        }
        return new WarehouseTransportIngressControllerSnapshot(
                transportSnapshot.capacity(),
                transportSnapshot.occupancy(),
                inFlightSnapshot.capacity(),
                inFlightSnapshot.occupancy(),
                headPhysicalToteId,
                headDestination,
                lastIngressPhysicalToteId,
                lastIngressDestination,
                blockedPhysicalToteId,
                blockedReason,
                successfulIngressCount,
                initialPublicationCount,
                exactObjectReentryCount);
    }

    private static String routeBindingFailure(
            RoutedPhysicalTote routedTote,
            WarehouseRouteDefinition definition) {
        RouteFollower follower = routedTote.tote().getRouteFollower();
        if (follower.getCurrentSegment() != definition.entrySegment()) {
            return "Tote route follower is not bound to the exact warehouse entry segment";
        }
        if (Double.compare(
                follower.getDistanceAlongSegment(), definition.entryDistance()) != 0) {
            return "Tote route follower has the wrong warehouse entry distance";
        }
        if (follower.getTravelDirection() != definition.entryDirection()) {
            return "Tote route follower has the wrong warehouse entry direction";
        }
        return "";
    }

    private void recordBlock(RoutedPhysicalTote routedTote, String reason) {
        blockedPhysicalToteId = Optional.of(routedTote.physicalToteId());
        blockedReason = reason == null || reason.isBlank()
                ? "Warehouse transport ingress is blocked"
                : reason.trim();
    }

    private void clearBlock() {
        blockedPhysicalToteId = Optional.empty();
        blockedReason = "";
    }
}
