package online.davisfamily.warehouse.sim.dsp.routing;

import online.davisfamily.warehouse.sim.dsp.model.StartLocation;

public record RouteRequirements(
        boolean requiresThirdParty,
        boolean requiresSortable,
        boolean requiresManual,
        boolean requiresP2p,
        boolean requiresManualMerge,
        StartLocation startLocation) {

    public RouteRequirements {
        if (startLocation == null) {
            throw new IllegalArgumentException("startLocation must not be null");
        }
    }
}
