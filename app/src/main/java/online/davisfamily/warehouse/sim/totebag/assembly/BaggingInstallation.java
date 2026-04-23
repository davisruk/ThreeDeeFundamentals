package online.davisfamily.warehouse.sim.totebag.assembly;

import online.davisfamily.warehouse.sim.totebag.handoff.RecordingCompletedBagReceiver;
import online.davisfamily.warehouse.sim.totebag.machine.BaggingMachine;

public class BaggingInstallation {
    private final BaggingMachine baggingMachine;
    private final BaggingModule baggingModule;
    private final RecordingCompletedBagReceiver completedBagReceiver;

    public BaggingInstallation(
            BaggingMachine baggingMachine,
            BaggingModule baggingModule,
            RecordingCompletedBagReceiver completedBagReceiver) {
        if (baggingMachine == null || baggingModule == null || completedBagReceiver == null) {
            throw new IllegalArgumentException("Bagging installation inputs must not be null");
        }
        this.baggingMachine = baggingMachine;
        this.baggingModule = baggingModule;
        this.completedBagReceiver = completedBagReceiver;
    }

    public BaggingMachine getBaggingMachine() {
        return baggingMachine;
    }

    public BaggingModule getBaggingModule() {
        return baggingModule;
    }

    public RecordingCompletedBagReceiver getCompletedBagReceiver() {
        return completedBagReceiver;
    }
}
