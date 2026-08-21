package online.davisfamily.warehouse.sim.dsp.osr.release.launch;

import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.model.StationType;

public record OperationalRouteDestination(
        StationType stationType,
        String targetId) {

    private static final Set<StationType> SUPPORTED_STATION_TYPES = Set.of(
            StationType.THIRD_PARTY,
            StationType.ADAPTING,
            StationType.P2P);

    public OperationalRouteDestination {
        if (stationType == null) {
            throw new IllegalArgumentException("stationType must not be null");
        }
        if (!SUPPORTED_STATION_TYPES.contains(stationType)) {
            throw new IllegalArgumentException(
                    "Unsupported operational route destination station type: " + stationType);
        }
        if (targetId == null || targetId.isBlank()) {
            throw new IllegalArgumentException("targetId must not be blank");
        }
        targetId = targetId.trim();
    }
}
