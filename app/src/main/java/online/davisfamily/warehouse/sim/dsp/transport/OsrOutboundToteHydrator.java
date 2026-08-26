package online.davisfamily.warehouse.sim.dsp.transport;

import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteLaunchRequest;

@FunctionalInterface
public interface OsrOutboundToteHydrator {
    RoutedPhysicalTote hydrate(OperationalRouteLaunchRequest request);
}
