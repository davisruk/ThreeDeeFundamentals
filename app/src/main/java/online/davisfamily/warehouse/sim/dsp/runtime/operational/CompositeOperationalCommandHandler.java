package online.davisfamily.warehouse.sim.dsp.runtime.operational;

import online.davisfamily.warehouse.sim.dsp.av02.ReleasePhysicalToteFromAv02Command;
import online.davisfamily.warehouse.sim.dsp.osr.release.ReleasePhysicalToteFromOsrCommand;
import online.davisfamily.warehouse.sim.dsp.runtime.SchedulerCommandApplicationResult;
import online.davisfamily.warehouse.sim.dsp.runtime.SchedulerCommandHandler;
import online.davisfamily.warehouse.sim.dsp.scheduler.SchedulerCommand;

/**
 * Dispatches source-specific operational release commands without allowing one
 * source handler to interpret another source's command.
 */
public final class CompositeOperationalCommandHandler implements SchedulerCommandHandler {
    private final SchedulerCommandHandler osrHandler;
    private final SchedulerCommandHandler av02Handler;

    public CompositeOperationalCommandHandler(
            SchedulerCommandHandler osrHandler,
            SchedulerCommandHandler av02Handler) {
        if (osrHandler == null) {
            throw new IllegalArgumentException("osrHandler must not be null");
        }
        if (av02Handler == null) {
            throw new IllegalArgumentException("av02Handler must not be null");
        }
        this.osrHandler = osrHandler;
        this.av02Handler = av02Handler;
    }

    @Override
    public SchedulerCommandApplicationResult apply(SchedulerCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        if (command.getClass() == ReleasePhysicalToteFromOsrCommand.class) {
            return osrHandler.apply(command);
        }
        if (command.getClass() == ReleasePhysicalToteFromAv02Command.class) {
            return av02Handler.apply(command);
        }
        return SchedulerCommandApplicationResult.rejectedResult(
                "Unsupported operational command: " + command.getClass().getSimpleName());
    }
}
