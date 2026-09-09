package online.davisfamily.warehouse.sim.dsp.analysis;

/** Terminal state of one full-day simulation. */
public enum DspFullDayRuntimeState {
    RUNNING,
    ALL_SUPPORTED_WORK_COMPLETE,
    HARD_CUTOFF_REACHED
}
