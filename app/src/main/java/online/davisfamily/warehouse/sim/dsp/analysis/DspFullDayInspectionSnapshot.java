package online.davisfamily.warehouse.sim.dsp.analysis;

import online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayAnalysisReport;

/** Compatibility-facing inspection value in the main analysis package. */
public record DspFullDayInspectionSnapshot(DspFullDayAnalysisReport report) {
    public DspFullDayInspectionSnapshot {
        if (report == null) {
            throw new IllegalArgumentException("report must not be null");
        }
    }

    public static DspFullDayInspectionSnapshot from(DspFullDayAnalysisReport report) {
        return new DspFullDayInspectionSnapshot(report);
    }

    online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayInspectionSnapshot reportSnapshot() {
        return new online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayInspectionSnapshot(
                report);
    }
}
