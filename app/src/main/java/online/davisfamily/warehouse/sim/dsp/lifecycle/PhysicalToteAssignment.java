package online.davisfamily.warehouse.sim.dsp.lifecycle;

import java.time.Duration;
import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

public record PhysicalToteAssignment(
        long sequenceNumber,
        OrderSheetKey orderSheetKey,
        PhysicalToteId physicalToteId,
        PhysicalToteAssignmentStage stage,
        Duration activatedAt,
        Optional<Duration> terminatedAt,
        Optional<PhysicalToteAssignmentEndReason> endReason) {

    public PhysicalToteAssignment {
        if (sequenceNumber < 0) {
            throw new IllegalArgumentException("sequenceNumber must be >= 0");
        }
        if (orderSheetKey == null) {
            throw new IllegalArgumentException("orderSheetKey must not be null");
        }
        if (physicalToteId == null) {
            throw new IllegalArgumentException("physicalToteId must not be null");
        }
        if (stage == null) {
            throw new IllegalArgumentException("stage must not be null");
        }
        requireNonNegative(activatedAt, "activatedAt");
        if (terminatedAt == null) {
            throw new IllegalArgumentException("terminatedAt must not be null");
        }
        if (endReason == null) {
            throw new IllegalArgumentException("endReason must not be null");
        }
        if (terminatedAt.isPresent() != endReason.isPresent()) {
            throw new IllegalArgumentException("terminatedAt and endReason must both be present or both be empty");
        }
        if (terminatedAt.isPresent()) {
            Duration terminationTime = terminatedAt.orElseThrow();
            requireNonNegative(terminationTime, "terminatedAt value");
            if (terminationTime.compareTo(activatedAt) < 0) {
                throw new IllegalArgumentException("terminatedAt must not precede activatedAt");
            }
        }
    }

    public static PhysicalToteAssignment active(
            long sequenceNumber,
            OrderSheetKey orderSheetKey,
            PhysicalToteId physicalToteId,
            PhysicalToteAssignmentStage stage,
            Duration activatedAt) {
        return new PhysicalToteAssignment(
                sequenceNumber,
                orderSheetKey,
                physicalToteId,
                stage,
                activatedAt,
                Optional.empty(),
                Optional.empty());
    }

    public boolean active() {
        return terminatedAt.isEmpty();
    }

    public PhysicalToteAssignment terminate(
            Duration terminationTime,
            PhysicalToteAssignmentEndReason reason) {
        if (!active()) {
            throw new IllegalStateException("Assignment is already terminated");
        }
        if (terminationTime == null) {
            throw new IllegalArgumentException("terminationTime must not be null");
        }
        if (reason == null) {
            throw new IllegalArgumentException("reason must not be null");
        }
        return new PhysicalToteAssignment(
                sequenceNumber,
                orderSheetKey,
                physicalToteId,
                stage,
                activatedAt,
                Optional.of(terminationTime),
                Optional.of(reason));
    }

    private static void requireNonNegative(Duration value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        if (value.isNegative()) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
    }
}
