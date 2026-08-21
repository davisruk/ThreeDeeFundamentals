package online.davisfamily.warehouse.sim.dsp.scheduler.operational;

import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements;

public final class OperationalRouteEntrySelector {

    public Optional<StationType> firstStation(RouteRequirements routeRequirements) {
        if (routeRequirements == null) {
            throw new IllegalArgumentException("routeRequirements must not be null");
        }
        if (routeRequirements.requiresThirdParty()) {
            return Optional.of(StationType.THIRD_PARTY);
        }
        if (routeRequirements.requiresSortable()) {
            return Optional.of(StationType.ADAPTING);
        }
        if (routeRequirements.requiresManual()) {
            return Optional.of(StationType.MANUAL);
        }
        if (routeRequirements.requiresP2p()) {
            return Optional.of(StationType.P2P);
        }
        if (routeRequirements.requiresManualMerge()) {
            return Optional.of(StationType.MANUAL_MERGE);
        }
        return Optional.empty();
    }
}
