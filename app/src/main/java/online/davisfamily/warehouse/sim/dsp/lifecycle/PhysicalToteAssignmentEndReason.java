package online.davisfamily.warehouse.sim.dsp.lifecycle;

public enum PhysicalToteAssignmentEndReason {
    ADVANCED_TO_NEXT_STAGE,
    CONSUMED_AT_ADAPTING,
    CONSUMED_AT_P2P,
    OUTBOUND_TOTE_CLOSED,
    REALLOCATED_TO_GENERATED_SHEET
}
