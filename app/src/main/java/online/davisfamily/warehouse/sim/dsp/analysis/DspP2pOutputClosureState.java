package online.davisfamily.warehouse.sim.dsp.analysis;

/** Per-service-centre P2P output state, separate from dispatch outcome. */
public enum DspP2pOutputClosureState {
    NOT_CLOSED,
    P2P_OUTPUT_CLOSED,
    P2P_OUTPUT_CLOSED_WITH_EXCEPTION
}
