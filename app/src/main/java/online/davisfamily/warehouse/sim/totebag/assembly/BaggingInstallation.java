package online.davisfamily.warehouse.sim.totebag.assembly;

import online.davisfamily.warehouse.sim.totebag.handoff.RecordingBagReceiver;
import online.davisfamily.warehouse.sim.totebag.machine.BaggingMachine;

public class BaggingInstallation {
    private final BaggingMachine baggingMachine;
    private final BaggingModule baggingModule;
    private final RecordingBagReceiver bagReceiver;

    public BaggingInstallation(
            BaggingMachine baggingMachine,
            BaggingModule baggingModule,
            RecordingBagReceiver bagReceiver) {
        if (baggingMachine == null || baggingModule == null || bagReceiver == null) {
            throw new IllegalArgumentException("Bagging installation inputs must not be null");
        }
        this.baggingMachine = baggingMachine;
        this.baggingModule = baggingModule;
        this.bagReceiver = bagReceiver;
    }

    public BaggingMachine getBaggingMachine() {
        return baggingMachine;
    }

    public BaggingModule getBaggingModule() {
        return baggingModule;
    }

    public RecordingBagReceiver getBagReceiver() {
        return bagReceiver;
    }
}
