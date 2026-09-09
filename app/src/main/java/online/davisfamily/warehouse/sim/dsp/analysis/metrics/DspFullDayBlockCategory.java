package online.davisfamily.warehouse.sim.dsp.analysis.metrics;

/** Mutually exclusive full-day blockage categories, in evaluation precedence order. */
public enum DspFullDayBlockCategory {
    DEPENDENCY,
    STATION_CAPACITY,
    OSR_STATE,
    P2P_ASSIGNMENT,
    UNSUPPORTED_WORK
}
