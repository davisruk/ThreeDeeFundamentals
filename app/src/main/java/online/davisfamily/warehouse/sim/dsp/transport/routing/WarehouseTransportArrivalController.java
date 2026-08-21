package online.davisfamily.warehouse.sim.dsp.transport.routing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.SimulationController;
import online.davisfamily.threedee.sim.framework.events.DetectionEvent;
import online.davisfamily.threedee.sim.framework.events.DetectionEvent.DetectionType;
import online.davisfamily.threedee.sim.framework.events.SimulationEventListener;
import online.davisfamily.threedee.sim.framework.objects.TrackableObject;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.tote.Tote.ToteMotionState;

public final class WarehouseTransportArrivalController implements SimulationController {
    private final WarehouseRouteCatalog routeCatalog;
    private final WarehouseTransportInFlightRegistry inFlightRegistry;
    private final StationRoutedToteArrivalRegistry arrivalRegistry;
    private final Map<String, PendingArrival> pendingByPhysicalToteId =
            new LinkedHashMap<>();
    private Optional<PhysicalToteId> lastArrivedPhysicalToteId = Optional.empty();
    private Optional<OperationalRouteDestination> lastArrivedDestination = Optional.empty();
    private Optional<PhysicalToteId> blockedPhysicalToteId = Optional.empty();
    private String blockedReason = "";
    private long successfulArrivalCount;

    public WarehouseTransportArrivalController(
            WarehouseRouteCatalog routeCatalog,
            WarehouseTransportInFlightRegistry inFlightRegistry,
            StationRoutedToteArrivalRegistry arrivalRegistry) {
        if (routeCatalog == null) {
            throw new IllegalArgumentException("routeCatalog must not be null");
        }
        if (inFlightRegistry == null) {
            throw new IllegalArgumentException("inFlightRegistry must not be null");
        }
        if (arrivalRegistry == null) {
            throw new IllegalArgumentException("arrivalRegistry must not be null");
        }
        this.routeCatalog = routeCatalog;
        this.inFlightRegistry = inFlightRegistry;
        this.arrivalRegistry = arrivalRegistry;
    }

    public SimulationEventListener<DetectionEvent> detectionHandler() {
        return this::handleDetection;
    }

    public void handleDetection(DetectionEvent event, SimulationContext context) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        if (event.getDetectionType() != DetectionType.ENTER) {
            return;
        }
        if (event.getSensorId() == null || event.getSensorId().isBlank()) {
            return;
        }
        Optional<WarehouseRouteDefinition> optionalDefinition =
                routeCatalog.findByTerminalSensorId(event.getSensorId());
        if (optionalDefinition.isEmpty()) {
            return;
        }
        WarehouseRouteDefinition definition = optionalDefinition.orElseThrow();

        PhysicalToteId physicalToteId;
        try {
            physicalToteId = new PhysicalToteId(event.getObjectId());
        } catch (IllegalArgumentException exception) {
            recordBlock(Optional.empty(), "Terminal event has no valid physical tote ID");
            return;
        }
        Optional<RoutedPhysicalTote> optionalRoutedTote =
                inFlightRegistry.find(physicalToteId);
        if (optionalRoutedTote.isEmpty()) {
            Optional<StationRoutedToteArrivalQueue> completedQueue =
                    arrivalRegistry.find(definition.destination().targetId());
            if (completedQueue.isPresent()
                    && completedQueue.orElseThrow().destination()
                            .equals(definition.destination())
                    && completedQueue.orElseThrow().contains(physicalToteId)) {
                clearBlock();
                return;
            }
            recordBlock(Optional.of(physicalToteId),
                    "Terminal event tote is not active in warehouse transport");
            return;
        }
        RoutedPhysicalTote routedTote = optionalRoutedTote.orElseThrow();
        if (!definition.destination().equals(routedTote.destination())) {
            recordBlock(Optional.of(physicalToteId),
                    "Terminal sensor destination does not match routed tote destination");
            return;
        }
        if (routedTote.tote().getRouteFollower().getCurrentSegment()
                != definition.terminalSegment()) {
            recordBlock(Optional.of(physicalToteId),
                    "Routed tote is not on the terminal sensor route segment");
            return;
        }
        TrackableObject trackedObject = context.getTrackedObjects().stream()
                .filter(candidate -> physicalToteId.value().equals(candidate.getId()))
                .findFirst()
                .orElse(null);
        if (trackedObject != routedTote.tote()) {
            recordBlock(Optional.of(physicalToteId),
                    "Terminal event does not resolve to the exact active tote");
            return;
        }

        PendingArrival existing = pendingByPhysicalToteId.get(physicalToteId.value());
        if (existing != null) {
            if (existing.routedTote != routedTote
                    || !existing.terminalSensorId.equals(event.getSensorId())) {
                recordBlock(Optional.of(physicalToteId),
                        "Pending terminal arrival identity or sensor changed");
                return;
            }
            routedTote.tote().setInteractionMode(ToteMotionState.HELD);
            clearBlock();
            return;
        }

        routedTote.tote().setInteractionMode(ToteMotionState.HELD);
        inFlightRegistry.markArrivalPending(routedTote);
        pendingByPhysicalToteId.put(
                physicalToteId.value(),
                new PendingArrival(routedTote, event.getSensorId()));
        clearBlock();
    }

    @Override
    public void update(SimulationContext context, double dtSeconds) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        if (!Double.isFinite(dtSeconds) || dtSeconds < 0d) {
            throw new IllegalArgumentException("dtSeconds must be finite and >= 0");
        }
        if (pendingByPhysicalToteId.isEmpty()) {
            clearBlock();
            return;
        }

        Map.Entry<String, PendingArrival> headEntry =
                pendingByPhysicalToteId.entrySet().iterator().next();
        PendingArrival pending = headEntry.getValue();
        RoutedPhysicalTote routedTote = pending.routedTote;
        PhysicalToteId physicalToteId = routedTote.physicalToteId();
        Optional<RoutedPhysicalTote> active = inFlightRegistry.find(physicalToteId);
        if (active.isEmpty() || active.orElseThrow() != routedTote) {
            recordBlock(Optional.of(physicalToteId),
                    "Pending arrival no longer owns the exact in-flight tote");
            return;
        }

        StationRoutedToteArrivalQueue arrivalQueue = arrivalRegistry
                .find(routedTote.destination().targetId())
                .orElse(null);
        if (arrivalQueue == null) {
            recordBlock(Optional.of(physicalToteId),
                    "No station arrival queue is configured for destination");
            return;
        }
        if (!arrivalQueue.destination().equals(routedTote.destination())) {
            recordBlock(Optional.of(physicalToteId),
                    "Station arrival queue destination does not match routed tote");
            return;
        }
        if (arrivalQueue.contains(physicalToteId)) {
            recordBlock(Optional.of(physicalToteId),
                    "Physical tote is already present in the station arrival queue");
            return;
        }
        if (!arrivalQueue.canAccept()) {
            recordBlock(Optional.of(physicalToteId),
                    "Station routed-tote arrival queue has no capacity");
            return;
        }

        arrivalQueue.enqueue(routedTote);
        RoutedPhysicalTote completed = inFlightRegistry.completeArrival(routedTote);
        if (completed != routedTote) {
            throw new IllegalStateException(
                    "In-flight registry completed a different routed tote");
        }
        PendingArrival removed = pendingByPhysicalToteId.remove(headEntry.getKey());
        if (removed != pending) {
            throw new IllegalStateException(
                    "Pending arrival ownership changed during station handoff");
        }

        successfulArrivalCount++;
        lastArrivedPhysicalToteId = Optional.of(physicalToteId);
        lastArrivedDestination = Optional.of(routedTote.destination());
        clearBlock();
    }

    public WarehouseTransportArrivalControllerSnapshot snapshot() {
        List<WarehouseTransportArrivalControllerSnapshot.PendingArrival> pending =
                new ArrayList<>();
        for (PendingArrival arrival : pendingByPhysicalToteId.values()) {
            pending.add(new WarehouseTransportArrivalControllerSnapshot.PendingArrival(
                    arrival.routedTote.physicalToteId(),
                    arrival.routedTote.destination(),
                    arrival.terminalSensorId));
        }
        return new WarehouseTransportArrivalControllerSnapshot(
                pending,
                lastArrivedPhysicalToteId,
                lastArrivedDestination,
                blockedPhysicalToteId,
                blockedReason,
                successfulArrivalCount);
    }

    private void recordBlock(Optional<PhysicalToteId> physicalToteId, String reason) {
        blockedPhysicalToteId = physicalToteId;
        blockedReason = reason;
    }

    private void clearBlock() {
        blockedPhysicalToteId = Optional.empty();
        blockedReason = "";
    }

    private static final class PendingArrival {
        private final RoutedPhysicalTote routedTote;
        private final String terminalSensorId;

        private PendingArrival(
                RoutedPhysicalTote routedTote,
                String terminalSensorId) {
            this.routedTote = routedTote;
            this.terminalSensorId = terminalSensorId;
        }
    }
}
