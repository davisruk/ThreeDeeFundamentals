package online.davisfamily.warehouse.sim.dsp.analysis;

import online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayAnalysisReport;

/** Compatibility-facing inspection value in the main analysis package. */
public final class DspFullDayInspectionSnapshot {
    private final online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayInspectionSnapshot delegate;

    public DspFullDayInspectionSnapshot(DspFullDayAnalysisReport report) {
        delegate = new online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayInspectionSnapshot(
                report);
    }

    public DspFullDayInspectionSnapshot(
            online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayInspectionSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }
        delegate = snapshot;
    }

    public static DspFullDayInspectionSnapshot from(DspFullDayAnalysisReport report) {
        return new DspFullDayInspectionSnapshot(report);
    }

    public DspFullDayAnalysisReport report() {
        return delegate.report();
    }

    public boolean terminal() {
        return delegate.terminal();
    }

    online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayInspectionSnapshot reportSnapshot() {
        return delegate;
    }
}
