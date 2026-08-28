package online.davisfamily.warehouse.sim.dsp.station.continuation;

import java.util.List;
import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements;

/**
 * Pure selector for the first required station after a completed station.
 */
public final class StationRouteContinuationSelector {
    private static final List<StationType> ROUTE_ORDER = List.of(
            StationType.THIRD_PARTY,
            StationType.ADAPTING,
            StationType.MANUAL,
            StationType.P2P,
            StationType.MANUAL_MERGE);

    public Optional<StationType> nextStation(
            RouteRequirements routeRequirements,
            StationType completedStation) {
        if (routeRequirements == null) {
            throw new IllegalArgumentException("routeRequirements must not be null");
        }
        if (completedStation == null) {
            throw new IllegalArgumentException("completedStation must not be null");
        }

        int completedIndex = ROUTE_ORDER.indexOf(completedStation);
        if (completedIndex < 0 || !isRequired(routeRequirements, completedStation)) {
            throw new IllegalArgumentException(
                    "completedStation must be required by the supplied route: "
                            + completedStation);
        }

        for (int index = completedIndex + 1; index < ROUTE_ORDER.size(); index++) {
            StationType candidate = ROUTE_ORDER.get(index);
            if (isRequired(routeRequirements, candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static boolean isRequired(
            RouteRequirements routeRequirements,
            StationType stationType) {
        return switch (stationType) {
            case THIRD_PARTY -> routeRequirements.requiresThirdParty();
            case ADAPTING -> routeRequirements.requiresSortable();
            case MANUAL -> routeRequirements.requiresManual();
            case P2P -> routeRequirements.requiresP2p();
            case MANUAL_MERGE -> routeRequirements.requiresManualMerge();
            case OSR, AV02, DISPATCH -> false;
        };
    }
}
