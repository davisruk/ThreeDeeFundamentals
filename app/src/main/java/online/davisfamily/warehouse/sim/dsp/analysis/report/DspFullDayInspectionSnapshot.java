package online.davisfamily.warehouse.sim.dsp.analysis.report;

/** Immutable value boundary consumed by the full-day text formatter. */
public record DspFullDayInspectionSnapshot(DspFullDayAnalysisReport report) {
    public DspFullDayInspectionSnapshot {
        if (report == null) {
            throw new IllegalArgumentException("report must not be null");
        }
    }

    public static DspFullDayInspectionSnapshot from(DspFullDayAnalysisReport report) {
        return new DspFullDayInspectionSnapshot(report);
    }
}
