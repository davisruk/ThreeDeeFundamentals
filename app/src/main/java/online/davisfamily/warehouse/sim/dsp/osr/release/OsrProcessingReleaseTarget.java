package online.davisfamily.warehouse.sim.dsp.osr.release;

import online.davisfamily.warehouse.sim.dsp.runtime.SchedulerCommandApplicationResult;

public interface OsrProcessingReleaseTarget {
    String targetId();

    SchedulerCommandApplicationResult accept(OsrProcessingReleaseRequest request);
}
