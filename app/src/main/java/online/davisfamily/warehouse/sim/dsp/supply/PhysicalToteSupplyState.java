package online.davisfamily.warehouse.sim.dsp.supply;

public enum PhysicalToteSupplyState {
    PRELOADED_IN_OSR,
    HELD_UPSTREAM,
    AUTHORIZED_WAITING,
    BLOCKED_BY_OSR_CAPACITY,
    STORED_IN_OSR,
    DEPARTED_FROM_OSR
}
