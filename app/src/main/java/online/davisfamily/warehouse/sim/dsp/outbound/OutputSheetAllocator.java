package online.davisfamily.warehouse.sim.dsp.outbound;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteAssignmentStage;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleSnapshot;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

public final class OutputSheetAllocator {
    private static final int MAX_SHEET_NUMBER = 999;
    private static final int MAX_OUTGOING_TOTE_ORDINAL = 19;

    private final Set<OrderSheetKey> knownOrderSheetKeys;
    private final Map<SourceToteKey, OrderSheetKey> outputSheetBySourceAndTote = new LinkedHashMap<>();
    private final Map<OrderSheetKey, Integer> nextOrdinalBySource = new LinkedHashMap<>();
    private final Map<OrderSheetKey, SourceToteKey> sourceToteByOutputSheet = new LinkedHashMap<>();

    public OutputSheetAllocator(Collection<OrderSheetKey> knownOrderSheetKeys) {
        if (knownOrderSheetKeys == null) {
            throw new IllegalArgumentException("knownOrderSheetKeys must not be null");
        }
        LinkedHashSet<OrderSheetKey> knownKeys = new LinkedHashSet<>();
        for (OrderSheetKey knownOrderSheetKey : knownOrderSheetKeys) {
            if (knownOrderSheetKey == null) {
                throw new IllegalArgumentException("knownOrderSheetKeys must not contain null");
            }
            knownKeys.add(knownOrderSheetKey);
        }
        this.knownOrderSheetKeys = Set.copyOf(knownKeys);
    }

    public List<OutputSheetAllocation> resolve(
            List<OrderSheetKey> sourceOwningSheetKeys,
            PhysicalToteId targetOutboundToteId,
            PhysicalToteLifecycleSnapshot lifecycleSnapshot) {
        validateInputs(sourceOwningSheetKeys, targetOutboundToteId, lifecycleSnapshot);

        Map<SourceToteKey, OrderSheetKey> stagedMappings = new LinkedHashMap<>(outputSheetBySourceAndTote);
        Map<OrderSheetKey, Integer> stagedNextOrdinals = new LinkedHashMap<>(nextOrdinalBySource);
        Map<OrderSheetKey, SourceToteKey> stagedOwners = new LinkedHashMap<>(sourceToteByOutputSheet);
        List<OutputSheetAllocation> allocations = new ArrayList<>();

        for (OrderSheetKey sourceKey : sourceOwningSheetKeys) {
            SourceToteKey mappingKey = new SourceToteKey(sourceKey, targetOutboundToteId);
            OrderSheetKey outputKey = stagedMappings.get(mappingKey);
            if (outputKey == null) {
                int ordinal = nextOrdinal(sourceKey, stagedNextOrdinals);
                outputKey = deriveOutputSheet(sourceKey, ordinal);
                rejectOutputKeyCollision(outputKey, mappingKey, stagedOwners);
                rejectActiveAssignmentToNewOutputKey(outputKey, lifecycleSnapshot);
                stagedNextOrdinals.put(sourceKey, ordinal);
                stagedMappings.put(mappingKey, outputKey);
                stagedOwners.put(outputKey, mappingKey);
            } else {
                requireExistingOutputAssignment(outputKey, targetOutboundToteId, lifecycleSnapshot);
            }
            allocations.add(new OutputSheetAllocation(sourceKey, outputKey));
        }

        outputSheetBySourceAndTote.clear();
        outputSheetBySourceAndTote.putAll(stagedMappings);
        nextOrdinalBySource.clear();
        nextOrdinalBySource.putAll(stagedNextOrdinals);
        sourceToteByOutputSheet.clear();
        sourceToteByOutputSheet.putAll(stagedOwners);
        return List.copyOf(allocations);
    }

    private int nextOrdinal(
            OrderSheetKey sourceKey,
            Map<OrderSheetKey, Integer> stagedNextOrdinals) {
        int ordinal;
        try {
            ordinal = Math.incrementExact(stagedNextOrdinals.getOrDefault(sourceKey, 0));
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "Outgoing tote ordinal overflows for source sheet: " + sourceKey,
                    exception);
        }
        if (ordinal > MAX_OUTGOING_TOTE_ORDINAL) {
            throw new IllegalArgumentException(
                    "Outgoing tote ordinal exceeds the supported range for source sheet: " + sourceKey);
        }
        return ordinal;
    }

    private OrderSheetKey deriveOutputSheet(OrderSheetKey sourceKey, int ordinal) {
        int outputSheetNumber;
        try {
            outputSheetNumber = Math.addExact(
                    80,
                    Math.addExact(Math.multiplyExact(sourceKey.sheetNumber(), 20), ordinal));
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "Derived output sheet number overflows for source sheet: " + sourceKey,
                    exception);
        }
        if (outputSheetNumber > MAX_SHEET_NUMBER) {
            throw new IllegalArgumentException(
                    "Derived output sheet number exceeds 999 for source sheet: " + sourceKey);
        }
        return new OrderSheetKey(sourceKey.orderId(), outputSheetNumber);
    }

    private void rejectOutputKeyCollision(
            OrderSheetKey outputKey,
            SourceToteKey mappingKey,
            Map<OrderSheetKey, SourceToteKey> stagedOwners) {
        if (knownOrderSheetKeys.contains(outputKey)) {
            throw new IllegalArgumentException(
                    "Derived output sheet collides with a known order sheet: " + outputKey);
        }
        SourceToteKey existingOwner = stagedOwners.get(outputKey);
        if (existingOwner != null && !existingOwner.equals(mappingKey)) {
            throw new IllegalStateException(
                    "Derived output sheet is already owned by another source/tote mapping: " + outputKey);
        }
    }

    private void rejectActiveAssignmentToNewOutputKey(
            OrderSheetKey outputKey,
            PhysicalToteLifecycleSnapshot lifecycleSnapshot) {
        lifecycleSnapshot.activeAssignmentFor(outputKey).ifPresent(assignment -> {
            if (assignment.stage() != PhysicalToteAssignmentStage.OUTBOUND_BAG
                    && assignment.stage() != PhysicalToteAssignmentStage.OUTBOUND) {
                throw new IllegalStateException(
                        "Output sheet has an active non-outbound assignment: " + outputKey);
            }
            throw new IllegalStateException(
                    "Derived output sheet already has an active assignment: " + outputKey);
        });
    }

    private void requireExistingOutputAssignment(
            OrderSheetKey outputKey,
            PhysicalToteId targetOutboundToteId,
            PhysicalToteLifecycleSnapshot lifecycleSnapshot) {
        lifecycleSnapshot.activeAssignmentFor(outputKey).ifPresent(assignment -> {
            if (assignment.stage() != PhysicalToteAssignmentStage.OUTBOUND_BAG
                    && assignment.stage() != PhysicalToteAssignmentStage.OUTBOUND) {
                throw new IllegalStateException(
                        "Output sheet has an active non-outbound assignment: " + outputKey);
            }
            if (!assignment.physicalToteId().equals(targetOutboundToteId)) {
                throw new IllegalStateException(
                        "Output sheet is actively assigned to another physical tote: " + outputKey);
            }
        });
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
