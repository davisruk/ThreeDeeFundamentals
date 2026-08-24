package online.davisfamily.warehouse.sim.dsp.p2p.lease;

@FunctionalInterface
public interface P2pLineActivityProbe {
    P2pLineActivitySnapshot snapshot();
}
