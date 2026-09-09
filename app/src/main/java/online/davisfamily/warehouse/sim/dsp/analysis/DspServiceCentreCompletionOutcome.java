package online.davisfamily.warehouse.sim.dsp.analysis;

/** Timetable classification of a service-centre provisional completion. */
public enum DspServiceCentreCompletionOutcome {
    ON_TARGET,
    OVERTIME_BUT_DISPATCHABLE,
    MISSED_TRUNKER,
    UNFINISHED_AT_HARD_CUTOFF
}
