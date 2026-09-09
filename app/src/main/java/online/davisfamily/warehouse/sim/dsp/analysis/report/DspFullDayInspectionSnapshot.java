package online.davisfamily.warehouse.sim.dsp.analysis.report;

import java.util.List;
import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.analysis.DspCompletionMilestone;
import online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayLoadedInput;
import online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayAnalysisRuntimeSnapshot;
import online.davisfamily.warehouse.sim.dsp.io.DspDatasetLoadReport;

/** Immutable current/final value boundary consumed by the full-day text formatter. */
public record DspFullDayInspectionSnapshot(
        DspFullDayAnalysisRuntimeSnapshot runtime,
        String profileId,
        String calibrationStatus,
        DspCompletionMilestone completionMilestone,
        DspDatasetLoadReport loadReport,
        List<DspServiceCentreAnalysisResult> serviceCentres,
        List<String> unsupportedWork,
        List<String> unfinishedIdentities,
        Optional<DspFullDayAnalysisReport> finalReport) {

    public DspFullDayInspectionSnapshot {
        if (runtime == null || profileId == null || profileId.isBlank()
                || calibrationStatus == null || calibrationStatus.isBlank()
                || completionMilestone == null || loadReport == null
                || serviceCentres == null || unsupportedWork == null
                || unfinishedIdentities == null || finalReport == null) {
            throw new IllegalArgumentException("inspection snapshot values must not be null");
        }
        profileId = profileId.trim();
        calibrationStatus = calibrationStatus.trim();
        serviceCentres = List.copyOf(serviceCentres);
        unsupportedWork = copyStrings(unsupportedWork, "unsupportedWork");
        unfinishedIdentities = copyStrings(unfinishedIdentities, "unfinishedIdentities");
        finalReport.ifPresent(report -> {
            if (!report.runtimeSnapshot().equals(runtime)) {
                throw new IllegalArgumentException(
                        "final report must use the same runtime snapshot value");
            }
        });
    }

    /** Compatibility constructor for a terminal report. */
    public DspFullDayInspectionSnapshot(DspFullDayAnalysisReport report) {
        this(
                report == null ? null : report.runtimeSnapshot(),
                report == null ? null : report.profileId(),
                report == null ? null : report.calibrationStatus(),
                report == null ? null : report.completionMilestone(),
                report == null ? null : report.loadReport(),
                report == null ? null : report.serviceCentres(),
                report == null ? null : report.unsupportedWork(),
                report == null ? null : report.unfinishedIdentities(),
                Optional.ofNullable(report));
    }

    public DspFullDayInspectionSnapshot(
            DspFullDayAnalysisRuntimeSnapshot runtime,
            DspFullDayLoadedInput input,
            List<DspServiceCentreAnalysisResult> serviceCentres,
            List<String> unfinishedIdentities) {
        this(
                runtime,
                runtime == null ? null : runtime.metrics().profileId(),
                runtime == null ? null : runtime.metrics().calibrationStatus(),
                runtime == null ? null : milestone(runtime.metrics().completionMilestone()),
                input == null ? null : input.report(),
                serviceCentres,
                runtime == null ? null : runtime.metrics().unsupportedWork(),
                unfinishedIdentities,
                Optional.empty());
    }

    public static DspFullDayInspectionSnapshot from(DspFullDayAnalysisReport report) {
        return new DspFullDayInspectionSnapshot(report);
    }

    public boolean terminal() {
        return runtime.state() != online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayRuntimeState.RUNNING;
    }

    /** Returns the final report when this is a final snapshot. */
    public DspFullDayAnalysisReport report() {
        return finalReport.orElseThrow(() -> new IllegalStateException(
                "current inspection snapshot has no final report"));
    }

    private static DspCompletionMilestone milestone(String value) {
        return DspCompletionMilestone.valueOf(value);
    }

    private static List<String> copyStrings(List<String> values, String fieldName) {
        if (values.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException(fieldName + " must not contain blank values");
        }
        return values.stream().map(String::trim).toList();
    }
}
