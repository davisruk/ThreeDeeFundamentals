package online.davisfamily.warehouse.sim.dsp.analysis.report;

import online.davisfamily.warehouse.sim.dsp.analysis.DspCompletionMilestone;
import online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayLoadedInput;
import online.davisfamily.warehouse.sim.dsp.analysis.DspUncalibratedFullDayProfile;
import online.davisfamily.warehouse.sim.dsp.analysis.input.DspInputRejectionCatalog;
import online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayAnalysisRuntimeSnapshot;
import online.davisfamily.warehouse.sim.dsp.io.DspDatasetLoadReport;

/** Immutable bounded projection source for routine full-day progress output. */
public record DspFullDayProgressSnapshot(
        DspFullDayAnalysisRuntimeSnapshot runtime,
        String profileId,
        String calibrationStatus,
        DspCompletionMilestone completionMilestone,
        DspDatasetLoadReport loadReport,
        DspInputRejectionCatalog rejectionCatalog) {

    /** Compatibility constructor for progress callers that predate rejection reporting. */
    public DspFullDayProgressSnapshot(
            DspFullDayAnalysisRuntimeSnapshot runtime,
            String profileId,
            String calibrationStatus,
            DspCompletionMilestone completionMilestone,
            DspDatasetLoadReport loadReport) {
        this(
                runtime,
                profileId,
                calibrationStatus,
                completionMilestone,
                loadReport,
                DspInputRejectionCatalog.empty());
    }

    public DspFullDayProgressSnapshot {
        if (runtime == null || profileId == null || profileId.isBlank()
                || calibrationStatus == null || calibrationStatus.isBlank()
                || completionMilestone == null || loadReport == null
                || rejectionCatalog == null) {
            throw new IllegalArgumentException("progress snapshot values must not be null");
        }
        profileId = profileId.trim();
        calibrationStatus = calibrationStatus.trim();
        if (!profileId.equals(runtime.metrics().profileId())) {
            throw new IllegalArgumentException(
                    "progress snapshot profileId must match runtime metrics");
        }
    }

    /** Creates a bounded progress projection without traversing loaded work identities. */
    public static DspFullDayProgressSnapshot from(
            DspFullDayAnalysisRuntimeSnapshot runtime,
            DspFullDayLoadedInput input,
            DspUncalibratedFullDayProfile profile) {
        if (runtime == null || input == null || profile == null) {
            throw new IllegalArgumentException("progress snapshot source values must not be null");
        }
        return new DspFullDayProgressSnapshot(
                runtime,
                profile.profileId(),
                profile.timingCalibrationStatus(),
                DspCompletionMilestone.valueOf(runtime.metrics().completionMilestone()),
                input.report(),
                input.rejectionCatalog());
    }

    /** Whether a terminal run completed while retaining recoverable input exclusions. */
    public boolean completedWithInputExclusions() {
        return runtime.state() != online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayRuntimeState.RUNNING
                && (rejectionCatalog.rejectedLineCount() > 0
                        || rejectionCatalog.rejectedMessageCount() > 0);
    }
}
