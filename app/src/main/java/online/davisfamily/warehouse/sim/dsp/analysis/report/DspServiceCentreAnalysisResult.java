package online.davisfamily.warehouse.sim.dsp.analysis.report;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.analysis.DspServiceCentreCompletionOutcome;
import online.davisfamily.warehouse.sim.dsp.analysis.DspServiceCentreCompletionSnapshot;
import online.davisfamily.warehouse.sim.dsp.analysis.metrics.DspServiceCentreMetricsSnapshot;
import online.davisfamily.warehouse.sim.dsp.schedule.ServiceCentreDeadlineSnapshot;

/**
 * Immutable report values for one service centre.
 *
 * <p>The completion and metrics snapshots are retained as immutable value objects.  This keeps
 * the report faithful to the first completion observation while still exposing convenient flat
 * accessors for inspection and callers.</p>
 */
public record DspServiceCentreAnalysisResult(
        String serviceCentreId,
        DspServiceCentreMetricsSnapshot metrics,
        DspServiceCentreCompletionSnapshot completion,
        List<String> unfinishedIdentities) {

    public DspServiceCentreAnalysisResult {
        if (serviceCentreId == null || serviceCentreId.isBlank()) {
            throw new IllegalArgumentException("serviceCentreId must not be blank");
        }
        serviceCentreId = serviceCentreId.trim();
        if (metrics == null || completion == null) {
            throw new IllegalArgumentException("metrics and completion must not be null");
        }
        if (!serviceCentreId.equals(metrics.serviceCentreId())
                || !serviceCentreId.equals(completion.serviceCentreId())) {
            throw new IllegalArgumentException("service-centre identities must match");
        }
        if (metrics.complete() != completion.complete()
                || metrics.completionOutcome() != completion.outcome()) {
            throw new IllegalArgumentException(
                    "metrics and completion must describe the same completion observation");
        }
        if (unfinishedIdentities == null
                || unfinishedIdentities.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException(
                    "unfinishedIdentities must not contain null or blank values");
        }
        unfinishedIdentities = unfinishedIdentities.stream().map(String::trim).toList();
    }

    public int priority() {
        return metrics.priority();
    }

    public DspServiceCentreCompletionOutcome outcome() {
        return metrics.completionOutcome();
    }

    public boolean complete() {
        return metrics.complete();
    }

    public Optional<Duration> completionElapsedTime() {
        return metrics.completionElapsedTime();
    }

    public Optional<LocalDateTime> completionDateTime() {
        return metrics.completionDateTime();
    }

    public ServiceCentreDeadlineSnapshot deadline() {
        return metrics.deadline();
    }

    public Optional<Duration> targetLateness() {
        return metrics.targetLateness();
    }

    public Optional<Duration> latestAllowedLateness() {
        return metrics.latestAllowedLateness();
    }

    public List<String> unsupportedWork() {
        return metrics.unsupportedWork();
    }
}
