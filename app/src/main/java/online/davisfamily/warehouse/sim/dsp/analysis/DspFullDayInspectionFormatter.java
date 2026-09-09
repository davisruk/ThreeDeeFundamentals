package online.davisfamily.warehouse.sim.dsp.analysis;

import java.util.List;

import online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayAnalysisReport;

/** Compatibility facade for the pure formatter in the reporting package. */
public final class DspFullDayInspectionFormatter {
    private final online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayInspectionFormatter delegate =
            new online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayInspectionFormatter();

    public List<String> describe(DspFullDayInspectionSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }
        return delegate.describe(snapshot.reportSnapshot());
    }

    public List<String> describe(DspFullDayAnalysisReport report) {
        return delegate.describe(report);
    }

    public List<String> format(DspFullDayInspectionSnapshot snapshot) {
        return describe(snapshot);
    }

    public String formatText(DspFullDayInspectionSnapshot snapshot) {
        return delegate.formatText(snapshot.reportSnapshot());
    }
}
