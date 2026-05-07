package online.davisfamily.warehouse.testing.scheduler;

import online.davisfamily.warehouse.sim.dsp.runtime.SchedulerCommandApplicationResult;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperTotePayload;

public interface ScheduledToteReleaseTarget {
    boolean canAcceptRelease();

    SchedulerCommandApplicationResult release(TipperTotePayload payload);
}
