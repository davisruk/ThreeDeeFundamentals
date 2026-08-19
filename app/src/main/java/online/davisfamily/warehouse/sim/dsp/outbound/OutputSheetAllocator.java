package online.davisfamily.warehouse.sim.dsp.outbound;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteAssignment;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteAssignmentStage;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleSnapshot;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

public final class OutputSheetAllocator {
    private final Map<String, Integer> highestSheetNumberByOrderId = new LinkedHashMap<>();
    private final Map<SourceToteKey, OrderSheetKey> outputSheetBySourceAndTote = new LinkedHashMap<>();
    private final Map<OrderSheetKey, LinkedHashSet<OrderSheetKey>> outputSheetsBySource = new LinkedHashMap<>();

    public OutputSheetAllocator(Collection<OrderSheetKey> knownOrderSheetKeys) {
        if (knownOrderSheetKeys == null) {
            throw new IllegalArgumentException("knownOrderSheetKeys must not be null");
        }
        for (OrderSheetKey knownOrderSheetKey : knownOrderSheetKeys) {
            if (knownOrderSheetKey == null) {
                throw new IllegalArgumentException("knownOrderSheetKeys must not contain null");
            }
            highestSheetNumberByOrderId.merge(
                    knownOrderSheetKey.orderId(),
                    knownOrderSheetKey.sheetNumber(),
                    Math::max);
        }
    }

    public List<OutputSheetAllocation> resolve(
            List<OrderSheetKey> sourceOwningSheetKeys,
            PhysicalToteId targetOutboundToteId,
            PhysicalToteLifecycleSnapshot lifecycleSnapshot) {
        validateInputs(sourceOwningSheetKeys, targetOutboundToteId, lifecycleSnapshot);

        Map<String, Integer> stagedHighestSheetNumbers = new LinkedHashMap<>(highestSheetNumberByOrderId);
        Map<SourceToteKey, OrderSheetKey> stagedMappings = new LinkedHashMap<>(outputSheetBySourceAndTote);
        Map<OrderSheetKey, LinkedHashSet<OrderSheetKey>> stagedOutputs = copyOutputSheetsBySource();
        List<OutputSheetAllocation> allocations = new ArrayList<>();

        for (OrderSheetKey sourceKey : sourceOwningSheetKeys) {
            SourceToteKey mappingKey = new SourceToteKey(sourceKey, targetOutboundToteId);
            OrderSheetKey outputKey = stagedMappings.get(mappingKey);
            if (outputKey == null) {
                outputKey = selectOutputSheet(
                        sourceKey,
                        targetOutboundToteId,
                        lifecycleSnapshot,
                        stagedHighestSheetNumbers,
                        stagedOutputs);
                stagedMappings.put(mappingKey, outputKey);
                stagedOutputs.computeIfAbsent(sourceKey, ignored -> new LinkedHashSet<>()).add(outputKey);
            } else {
                rejectActiveNonOutboundAssignment(outputKey, lifecycleSnapshot);
            }
            allocations.add(new OutputSheetAllocation(sourceKey, outputKey));
        }

        highestSheetNumberByOrderId.clear();
        highestSheetNumberByOrderId.putAll(stagedHighestSheetNumbers);
        outputSheetBySourceAndTote.clear();
        outputSheetBySourceAndTote.putAll(stagedMappings);
        outputSheetsBySource.clear();
        outputSheetsBySource.putAll(stagedOutputs);
        return List.copyOf(allocations);
    }

    private OrderSheetKey selectOutputSheet(
            OrderSheetKey sourceKey,
            PhysicalToteId targetOutboundToteId,
            PhysicalToteLifecycleSnapshot lifecycleSnapshot,
            Map<String, Integer> stagedHighestSheetNumbers,
            Map<OrderSheetKey, LinkedHashSet<OrderSheetKey>> stagedOutputs) {
        LinkedHashSet<OrderSheetKey> candidates = new LinkedHashSet<>();
        candidates.add(sourceKey);
        candidates.addAll(stagedOutputs.getOrDefault(sourceKey, new LinkedHashSet<>()));

        boolean activeOnAnotherTote = false;
        for (OrderSheetKey candidate : candidates) {
            var activeAssignment = lifecycleSnapshot.activeAssignmentFor(candidate);
            if (activeAssignment.isEmpty()) {
                continue;
            }
            PhysicalToteAssignment assignment = activeAssignment.orElseThrow();
            requireOutboundStage(assignment);
            if (assignment.physicalToteId().equals(targetOutboundToteId)) {
                return candidate;
            }
            activeOnAnotherTote = true;
        }

        if (!activeOnAnotherTote) {
            return sourceKey;
        }

        int nextSheetNumber = Math.incrementExact(
                stagedHighestSheetNumbers.getOrDefault(sourceKey.orderId(), sourceKey.sheetNumber()));
        stagedHighestSheetNumbers.put(sourceKey.orderId(), nextSheetNumber);
        return new OrderSheetKey(sourceKey.orderId(), nextSheetNumber);
    }

    private void rejectActiveNonOutboundAssignment(
            OrderSheetKey outputSheetKey,
            PhysicalToteLifecycleSnapshot lifecycleSnapshot) {
        lifecycleSnapshot.activeAssignmentFor(outputSheetKey).ifPresent(this::requireOutboundStage);
    }

    private void requireOutboundStage(PhysicalToteAssignment assignment) {
        if (assignment.stage() != PhysicalToteAssignmentStage.OUTBOUND_BAG
                && assignment.stage() != PhysicalToteAssignmentStage.OUTBOUND) {
            throw new IllegalStateException(
                    "Output sheet still has an active non-outbound assignment: "
                            + assignment.orderSheetKey());
        }
    }

    private Map<OrderSheetKey, LinkedHashSet<OrderSheetKey>> copyOutputSheetsBySource() {
        Map<OrderSheetKey, LinkedHashSet<OrderSheetKey>> copy = new LinkedHashMap<>();
        outputSheetsBySource.forEach((source, outputs) -> copy.put(source, new LinkedHashSet<>(outputs)));
        return copy;
    }

    private static void validateInputs(
            List<OrderSheetKey> sourceOwningSheetKeys,
            PhysicalToteId targetOutboundToteId,
            PhysicalToteLifecycleSnapshot lifecycleSnapshot) {
        if (sourceOwningSheetKeys == null) {
            throw new IllegalArgumentException("sourceOwningSheetKeys must not be null");
        }
        if (sourceOwningSheetKeys.stream().anyMatch(sourceKey -> sourceKey == null)) {
            throw new IllegalArgumentException("sourceOwningSheetKeys must not contain null");
        }
        Set<OrderSheetKey> distinctSourceKeys = new LinkedHashSet<>(sourceOwningSheetKeys);
        if (distinctSourceKeys.size() != sourceOwningSheetKeys.size()) {
            throw new IllegalArgumentException("sourceOwningSheetKeys must not contain duplicates");
        }
        if (targetOutboundToteId == null) {
            throw new IllegalArgumentException("targetOutboundToteId must not be null");
        }
        if (lifecycleSnapshot == null) {
            throw new IllegalArgumentException("lifecycleSnapshot must not be null");
        }
    }

    private record SourceToteKey(
            OrderSheetKey sourceOwningSheetKey,
            PhysicalToteId outboundPhysicalToteId) {
    }
}
