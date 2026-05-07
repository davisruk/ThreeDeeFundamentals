package online.davisfamily.warehouse.sim.dsp.runtime;

import online.davisfamily.warehouse.sim.dsp.scheduler.SchedulerCommand;

public interface SchedulerCommandHandler {
    SchedulerCommandApplicationResult apply(SchedulerCommand command);
}
