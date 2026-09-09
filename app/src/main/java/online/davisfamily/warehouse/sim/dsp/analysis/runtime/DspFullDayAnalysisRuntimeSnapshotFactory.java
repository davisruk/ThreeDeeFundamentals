package online.davisfamily.warehouse.sim.dsp.analysis.runtime;

/** Package-local snapshot assembly kept separate from the mutable runtime facade. */
final class DspFullDayAnalysisRuntimeSnapshotFactory {
    private DspFullDayAnalysisRuntimeSnapshotFactory() {
    }

    static DspFullDayAnalysisRuntimeSnapshot create(DspFullDayAnalysisRuntime runtime) {
        return DspFullDayAnalysisRuntime.buildSnapshot(runtime);
    }
}
