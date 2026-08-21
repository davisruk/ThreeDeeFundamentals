package online.davisfamily.warehouse.sim.dsp.osr.release.launch;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseTarget;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseTargetRegistry;

public final class OsrOutboundRouteLaunchTargetRegistry
        implements OperationalRouteTargetAdmissionCatalog {
    private final OsrOutboundRouteLaunchQueue launchQueue;
    private final List<OperationalRouteDestination> destinations;
    private final Map<String, OperationalRouteDestination> destinationsByTargetId;
    private final List<OsrOutboundRouteLaunchTarget> targets;
    private final OsrProcessingReleaseTargetRegistry processingReleaseTargetRegistry;

    public OsrOutboundRouteLaunchTargetRegistry(
            OsrOutboundRouteLaunchQueue launchQueue,
            List<OperationalRouteDestination> destinations) {
        if (launchQueue == null) {
            throw new IllegalArgumentException("launchQueue must not be null");
        }
        if (destinations == null) {
            throw new IllegalArgumentException("destinations must not be null");
        }

        List<OperationalRouteDestination> configuredDestinations = new ArrayList<>();
        Map<String, OperationalRouteDestination> configuredDestinationsByTargetId =
                new LinkedHashMap<>();
        List<OsrOutboundRouteLaunchTarget> configuredTargets = new ArrayList<>();

        for (OperationalRouteDestination destination : destinations) {
            if (destination == null) {
                throw new IllegalArgumentException("destinations must not contain null");
            }
            if (configuredDestinationsByTargetId.putIfAbsent(
                    destination.targetId(), destination) != null) {
                throw new IllegalArgumentException(
                        "Duplicate operational route destination target ID: "
                                + destination.targetId());
            }
            configuredDestinations.add(destination);
            configuredTargets.add(new OsrOutboundRouteLaunchTarget(destination, launchQueue));
        }

        this.launchQueue = launchQueue;
        this.destinations = List.copyOf(configuredDestinations);
        this.destinationsByTargetId = Map.copyOf(configuredDestinationsByTargetId);
        this.targets = List.copyOf(configuredTargets);
        this.processingReleaseTargetRegistry = new OsrProcessingReleaseTargetRegistry(
                new ArrayList<OsrProcessingReleaseTarget>(configuredTargets));
    }

    public List<OperationalRouteDestination> destinations() {
        return destinations;
    }

    public Optional<OperationalRouteDestination> findDestination(String targetId) {
        return Optional.ofNullable(destinationsByTargetId.get(requireTargetId(targetId)));
    }

    public List<OsrOutboundRouteLaunchTarget> targets() {
        return targets;
    }

    @Override
    public OsrProcessingReleaseTargetRegistry processingReleaseTargetRegistry() {
        return processingReleaseTargetRegistry;
    }

    @Override
    public List<OperationalRouteTargetAdmissionSnapshot> snapshotAdmissions() {
        OsrOutboundRouteLaunchQueueSnapshot queueSnapshot = launchQueue.snapshot();
        return destinations.stream()
                .map(destination -> new OperationalRouteTargetAdmissionSnapshot(
                        destination.stationType(),
                        destination.targetId(),
                        queueSnapshot.capacity(),
                        queueSnapshot.occupancy()))
                .toList();
    }

    public OsrOutboundRouteLaunchQueueSnapshot launchQueueSnapshot() {
        return launchQueue.snapshot();
    }

    private static String requireTargetId(String targetId) {
        if (targetId == null || targetId.isBlank()) {
            throw new IllegalArgumentException("targetId must not be blank");
        }
        return targetId.trim();
    }
}
