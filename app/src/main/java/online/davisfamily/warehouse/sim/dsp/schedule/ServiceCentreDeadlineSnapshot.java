package online.davisfamily.warehouse.sim.dsp.schedule;

import java.time.Duration;
import java.time.LocalDateTime;

public record ServiceCentreDeadlineSnapshot(
        String serviceCentreId,
        String displayName,
        int priority,
        LocalDateTime evaluatedAt,
        LocalDateTime trunkerDepartureDateTime,
        LocalDateTime trunkerReadyDeadline,
        LocalDateTime targetCompletion,
        LocalDateTime latestAllowedCompletion,
        Duration availableTime,
        boolean targetPassed,
        boolean latestAllowedCompletionPassed) {

    public ServiceCentreDeadlineSnapshot {
        serviceCentreId = requireValue(serviceCentreId, "serviceCentreId");
        displayName = requireValue(displayName, "displayName");
        if (priority <= 0) {
            throw new IllegalArgumentException("priority must be positive");
        }
        requireNonNull(evaluatedAt, "evaluatedAt");
        requireNonNull(trunkerDepartureDateTime, "trunkerDepartureDateTime");
        requireNonNull(trunkerReadyDeadline, "trunkerReadyDeadline");
        requireNonNull(targetCompletion, "targetCompletion");
        requireNonNull(latestAllowedCompletion, "latestAllowedCompletion");
        requireNonNull(availableTime, "availableTime");
        if (trunkerReadyDeadline.isAfter(trunkerDepartureDateTime)) {
            throw new IllegalArgumentException(
                    "trunkerReadyDeadline must not be after trunkerDepartureDateTime");
        }
        if (targetCompletion.isAfter(trunkerReadyDeadline)
                || latestAllowedCompletion.isAfter(trunkerReadyDeadline)) {
            throw new IllegalArgumentException(
                    "completion deadlines must not be after trunkerReadyDeadline");
        }
        if (targetCompletion.isAfter(latestAllowedCompletion)) {
            throw new IllegalArgumentException(
                    "targetCompletion must not be after latestAllowedCompletion");
        }
        if (availableTime.isNegative()) {
            throw new IllegalArgumentException("availableTime must not be negative");
        }
        Duration expectedAvailableTime = evaluatedAt.isBefore(latestAllowedCompletion)
                ? Duration.between(evaluatedAt, latestAllowedCompletion)
                : Duration.ZERO;
        if (!availableTime.equals(expectedAvailableTime)) {
            throw new IllegalArgumentException(
                    "availableTime must match evaluatedAt and latestAllowedCompletion");
        }
        if (targetPassed != !evaluatedAt.isBefore(targetCompletion)) {
            throw new IllegalArgumentException("targetPassed does not match evaluatedAt");
        }
        if (latestAllowedCompletionPassed
                != !evaluatedAt.isBefore(latestAllowedCompletion)) {
            throw new IllegalArgumentException(
                    "latestAllowedCompletionPassed does not match evaluatedAt");
        }
    }

    private static String requireValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static void requireNonNull(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
    }
}
