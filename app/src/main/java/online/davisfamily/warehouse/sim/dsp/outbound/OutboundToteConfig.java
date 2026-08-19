package online.davisfamily.warehouse.sim.dsp.outbound;

public record OutboundToteConfig(int maximumBagCount) {

    public OutboundToteConfig {
        if (maximumBagCount < 1) {
            throw new IllegalArgumentException("maximumBagCount must be >= 1");
        }
    }
}
