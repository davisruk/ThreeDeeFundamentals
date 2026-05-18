package online.davisfamily.warehouse.sim.dsp.adapting;

public enum AdaptingBenchState {
    IDLE,
    QUEUED,
    PROCESSING_STORE,
    PROCESSING_COLLECT,
    COMPLETED,
    BLOCKED
}
