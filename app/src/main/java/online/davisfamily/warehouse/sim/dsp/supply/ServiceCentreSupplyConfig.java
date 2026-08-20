package online.davisfamily.warehouse.sim.dsp.supply;

public record ServiceCentreSupplyConfig(int lowWaterMark) {

    public ServiceCentreSupplyConfig {
        if (lowWaterMark < 0) {
            throw new IllegalArgumentException("lowWaterMark must be >= 0");
        }
    }
}
