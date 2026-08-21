package online.davisfamily.warehouse.sim.dsp.osr.release.route;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseTarget;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseTargetRegistry;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteTargetAdmissionCatalog;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteTargetAdmissionSnapshot;

public final class OperationalRouteTargetRegistry
        implements OperationalRouteTargetAdmissionCatalog {
    private final List<OperationalRouteEntryQueue> queues;
    private final Map<String, OperationalRouteEntryQueue> queuesByTargetId;
    private final List<OsrProcessingReleaseTarget> releaseTargets;
    private final OsrProcessingReleaseTargetRegistry processingReleaseTargetRegistry;

    public OperationalRouteTargetRegistry(List<OperationalRouteEntryQueue> queues) {
        if (queues == null) {
            throw new IllegalArgumentException("queues must not be null");
        }

        List<OperationalRouteEntryQueue> configuredQueues = new ArrayList<>();
        Map<String, OperationalRouteEntryQueue> configuredQueuesByTargetId =
                new LinkedHashMap<>();
        List<OsrProcessingReleaseTarget> configuredTargets = new ArrayList<>();
        Set<OperationalRouteEntryQueue> configuredQueueInstances =
                Collections.newSetFromMap(new IdentityHashMap<>());

        for (OperationalRouteEntryQueue queue : queues) {
            if (queue == null) {
                throw new IllegalArgumentException("queues must not contain null");
            }
            if (!configuredQueueInstances.add(queue)) {
                throw new IllegalArgumentException(
                        "Operational route-entry queue is registered more than once: "
                                + queue.definition().targetId());
            }

            String targetId = queue.definition().targetId();
            if (configuredQueuesByTargetId.putIfAbsent(targetId, queue) != null) {
                throw new IllegalArgumentException(
                        "Duplicate operational route target ID: " + targetId);
            }
            configuredQueues.add(queue);
            configuredTargets.add(new QueuedOsrProcessingReleaseTarget(queue));
        }

        this.queues = List.copyOf(configuredQueues);
        this.queuesByTargetId = Collections.unmodifiableMap(configuredQueuesByTargetId);
        this.releaseTargets = List.copyOf(configuredTargets);
        this.processingReleaseTargetRegistry =
                new OsrProcessingReleaseTargetRegistry(this.releaseTargets);
    }

    public Optional<OperationalRouteEntryQueue> find(String targetId) {
        return Optional.ofNullable(queuesByTargetId.get(requireTargetId(targetId)));
    }

    public List<OperationalRouteEntryQueue> queues() {
        return queues;
    }

    public List<OperationalRouteEntryQueue> queuesFor(StationType stationType) {
        requireStationType(stationType);
        return queues.stream()
                .filter(queue -> queue.definition().stationType() == stationType)
                .toList();
    }

    public List<OperationalRouteTargetDefinition> definitionsFor(StationType stationType) {
        return queuesFor(stationType).stream()
                .map(OperationalRouteEntryQueue::definition)
                .toList();
    }

    public List<OsrProcessingReleaseTarget> releaseTargets() {
        return releaseTargets;
    }

    @Override
    public OsrProcessingReleaseTargetRegistry processingReleaseTargetRegistry() {
        return processingReleaseTargetRegistry;
    }

    @Override
    public List<OperationalRouteTargetAdmissionSnapshot> snapshotAdmissions() {
        return queues.stream()
                .map(OperationalRouteEntryQueue::snapshot)
                .map(snapshot -> new OperationalRouteTargetAdmissionSnapshot(
                        snapshot.stationType(),
                        snapshot.targetId(),
                        snapshot.capacity(),
                        snapshot.occupancy()))
                .toList();
    }

    public List<OperationalRouteEntryQueueSnapshot> snapshots() {
        return queues.stream()
                .map(OperationalRouteEntryQueue::snapshot)
                .toList();
    }

    private static String requireTargetId(String targetId) {
        if (targetId == null || targetId.isBlank()) {
            throw new IllegalArgumentException("targetId must not be blank");
        }
        return targetId.trim();
    }

    private static void requireStationType(StationType stationType) {
        if (stationType == null) {
            throw new IllegalArgumentException("stationType must not be null");
        }
    }
}
