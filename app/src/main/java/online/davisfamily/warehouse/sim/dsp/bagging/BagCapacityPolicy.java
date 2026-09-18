package online.davisfamily.warehouse.sim.dsp.bagging;

public interface BagCapacityPolicy {
    boolean canAdd(int currentPackCount, BagPackDemand candidate);
}
