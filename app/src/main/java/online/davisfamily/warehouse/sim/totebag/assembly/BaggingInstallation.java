package online.davisfamily.warehouse.sim.totebag.assembly;

import online.davisfamily.warehouse.sim.totebag.machine.BaggingMachine;

public class BaggingInstallation {
    private final BaggingMachine baggingMachine;
    private final BaggingModule baggingModule;

    public BaggingInstallation(BaggingMachine baggingMachine, BaggingModule baggingModule) {
        if (baggingMachine == null || baggingModule == null) {
            throw new IllegalArgumentException("Bagging installation inputs must not be null");
        }
        this.baggingMachine = baggingMachine;
        this.baggingModule = baggingModule;
    }

    public BaggingMachine getBaggingMachine() {
        return baggingMachine;
    }

    public BaggingModule getBaggingModule() {
        return baggingModule;
    }
}
