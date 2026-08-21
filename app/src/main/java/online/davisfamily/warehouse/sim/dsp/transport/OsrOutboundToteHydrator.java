package online.davisfamily.warehouse.sim.dsp.transport;

import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OsrOutboundRouteLaunchRequest;

@FunctionalInterface
public interface OsrOutboundToteHydrator {
    RoutedPhysicalTote hydrate(OsrOutboundRouteLaunchRequest request);
}
