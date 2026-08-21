package online.davisfamily.warehouse.sim.dsp.osr.release.launch;

import java.util.Optional;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.SimulationController;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.transport.OsrOutboundToteHydrationException;
import online.davisfamily.warehouse.sim.dsp.transport.OsrOutboundToteHydrator;
import online.davisfamily.warehouse.sim.dsp.transport.OsrOutboundTransportQueue;
import online.davisfamily.warehouse.sim.dsp.transport.OsrOutboundTransportQueueSnapshot;
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;

public final class OsrOutboundRouteLaunchController implements SimulationController {
    private final OsrOutboundRouteLaunchQueue launchQueue;
    private final OsrOutboundTransportQueue transportQueue;
    private final OsrOutboundToteHydrator hydrator;
    private Optional<PhysicalToteId> lastHydratedPhysicalToteId = Optional.empty();
    private Optional<OperationalRouteDestination> lastHydratedDestination = Optional.empty();
    private Optional<PhysicalToteId> blockedPhysicalToteId = Optional.empty();
    private String blockedReason = "";
    private long successfulHydrationCount;

    public OsrOutboundRouteLaunchController(
            OsrOutboundRouteLaunchQueue launchQueue,
            OsrOutboundTransportQueue transportQueue,
            OsrOutboundToteHydrator hydrator) {
        if (launchQueue == null) {
            throw new IllegalArgumentException("launchQueue must not be null");
        }
        if (transportQueue == null) {
            throw new IllegalArgumentException("transportQueue must not be null");
        }
        if (hydrator == null) {
            throw new IllegalArgumentException("hydrator must not be null");
        }
        this.launchQueue = launchQueue;
        this.transportQueue = transportQueue;
        this.hydrator = hydrator;
    }

    @Override
    public void update(SimulationContext context, double dtSeconds) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        if (!Double.isFinite(dtSeconds) || dtSeconds < 0d) {
            throw new IllegalArgumentException("dtSeconds must be finite and >= 0");
        }

        Optional<OsrOutboundRouteLaunchRequest> optionalHead = launchQueue.peek();
        if (optionalHead.isEmpty()) {
            clearBlock();
            return;
        }
        OsrOutboundRouteLaunchRequest head = optionalHead.orElseThrow();

        if (!transportQueue.canAccept()) {
            recordBlock(head, "OSR outbound transport queue has no capacity");
            return;
        }
        if (transportQueue.contains(head.physicalToteId())) {
            recordBlock(
                    head,
                    "Physical tote is already present in the outbound transport queue");
            return;
        }

        RoutedPhysicalTote routedTote;
        try {
            routedTote = hydrator.hydrate(head);
        } catch (OsrOutboundToteHydrationException exception) {
            recordBlock(head, exception.getMessage());
            return;
        }
        if (routedTote == null) {
            recordBlock(head, "Outbound tote hydrator returned null");
            return;
        }

        Optional<OsrOutboundRouteLaunchRequest> currentHead = launchQueue.peek();
        if (currentHead.isEmpty() || currentHead.orElseThrow() != head) {
            recordBlock(head, "Outbound route-launch head changed during hydration");
            return;
        }
        if (routedTote.launchRequest() != head) {
            recordBlock(head, "Hydrated tote does not retain the exact launch request");
            return;
        }
        if (!head.physicalToteId().equals(routedTote.physicalToteId())) {
            recordBlock(head, "Hydrated tote physical identity does not match launch head");
            return;
        }
        if (!head.destination().equals(routedTote.destination())) {
            recordBlock(head, "Hydrated tote destination does not match launch head");
            return;
        }
        if (!transportQueue.canAccept()) {
            recordBlock(head, "OSR outbound transport capacity changed during hydration");
            return;
        }
        if (transportQueue.contains(head.physicalToteId())) {
            recordBlock(
                    head,
                    "Physical tote entered the outbound transport queue during hydration");
            return;
        }

        transportQueue.enqueue(routedTote);
        OsrOutboundRouteLaunchRequest dequeued = launchQueue.dequeue().orElseThrow(() ->
                new IllegalStateException(
                        "Outbound route-launch head disappeared after transport acceptance"));
        if (dequeued != head) {
            throw new IllegalStateException(
                    "Outbound route-launch queue dequeued a different request after acceptance");
        }

        successfulHydrationCount++;
        lastHydratedPhysicalToteId = Optional.of(head.physicalToteId());
        lastHydratedDestination = Optional.of(head.destination());
        clearBlock();
    }

    public OsrOutboundRouteLaunchControllerSnapshot snapshot() {
        OsrOutboundRouteLaunchQueueSnapshot launchSnapshot = launchQueue.snapshot();
        OsrOutboundTransportQueueSnapshot transportSnapshot = transportQueue.snapshot();
        Optional<PhysicalToteId> headPhysicalToteId = Optional.empty();
        Optional<OperationalRouteDestination> headDestination = Optional.empty();
        if (!launchSnapshot.entries().isEmpty()) {
            OsrOutboundRouteLaunchQueueSnapshot.Entry head =
                    launchSnapshot.entries().get(0);
            headPhysicalToteId = Optional.of(head.physicalToteId());
            headDestination = Optional.of(head.destination());
        }
        return new OsrOutboundRouteLaunchControllerSnapshot(
                launchSnapshot.capacity(),
                launchSnapshot.occupancy(),
                transportSnapshot.capacity(),
                transportSnapshot.occupancy(),
                headPhysicalToteId,
                headDestination,
                lastHydratedPhysicalToteId,
                lastHydratedDestination,
                blockedPhysicalToteId,
                blockedReason,
                successfulHydrationCount);
    }

    private void recordBlock(OsrOutboundRouteLaunchRequest request, String reason) {
        blockedPhysicalToteId = Optional.of(request.physicalToteId());
        blockedReason = reason == null || reason.isBlank()
                ? "Outbound route launch is blocked"
                : reason.trim();
    }

    private void clearBlock() {
        blockedPhysicalToteId = Optional.empty();
        blockedReason = "";
    }
}
