package online.davisfamily.warehouse.sim.dsp.osr.release;

import online.davisfamily.warehouse.sim.dsp.runtime.SchedulerCommandApplicationResult;

public interface OperationalPhysicalToteReleaseTarget {
    String targetId();

    SchedulerCommandApplicationResult accept(OperationalPhysicalToteReleaseRequest request);
}
