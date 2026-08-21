package online.davisfamily.warehouse.sim.dsp.transport.routing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class StationRoutedToteArrivalRegistry {
    private final List<StationRoutedToteArrivalQueue> queues;
    private final Map<String, StationRoutedToteArrivalQueue> queuesByTargetId;

    public StationRoutedToteArrivalRegistry(
            List<StationRoutedToteArrivalQueue> queues) {
        if (queues == null) {
            throw new IllegalArgumentException("queues must not be null");
        }

        List<StationRoutedToteArrivalQueue> configuredQueues = new ArrayList<>();
        Map<String, StationRoutedToteArrivalQueue> configuredQueuesByTargetId =
                new LinkedHashMap<>();
        for (StationRoutedToteArrivalQueue queue : queues) {
            if (queue == null) {
                throw new IllegalArgumentException("queues must not contain null");
            }
            String targetId = queue.destination().targetId();
            if (configuredQueuesByTargetId.putIfAbsent(targetId, queue) != null) {
                throw new IllegalArgumentException(
                        "Duplicate station arrival target ID: " + targetId);
            }
            configuredQueues.add(queue);
        }
        this.queues = List.copyOf(configuredQueues);
        this.queuesByTargetId = Map.copyOf(configuredQueuesByTargetId);
    }

    public List<StationRoutedToteArrivalQueue> queues() {
        return queues;
    }

    public Optional<StationRoutedToteArrivalQueue> find(String targetId) {
        return Optional.ofNullable(queuesByTargetId.get(requireTargetId(targetId)));
    }

    public List<StationRoutedToteArrivalQueueSnapshot> snapshots() {
        return queues.stream()
                .map(StationRoutedToteArrivalQueue::snapshot)
                .toList();
    }

    private static String requireTargetId(String targetId) {
        if (targetId == null || targetId.isBlank()) {
            throw new IllegalArgumentException("targetId must not be blank");
        }
        return targetId.trim();
    }
}
