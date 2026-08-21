package online.davisfamily.warehouse.sim.dsp.osr.release.route;

import java.util.EnumSet;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.model.StationType;

public record OperationalRouteTargetDefinition(
        StationType stationType,
        String targetId,
        int waitingCapacity) {

    private static final Set<StationType> SUPPORTED_STATION_TYPES = EnumSet.of(
            StationType.THIRD_PARTY,
            StationType.ADAPTING,
            StationType.P2P);

    public OperationalRouteTargetDefinition {
        if (stationType == null) {
            throw new IllegalArgumentException("stationType must not be null");
        }
        if (!SUPPORTED_STATION_TYPES.contains(stationType)) {
            throw new IllegalArgumentException(
                    "Unsupported operational route-entry station type: " + stationType);
        }
        if (targetId == null || targetId.isBlank()) {
            throw new IllegalArgumentException("targetId must not be blank");
        }
        if (waitingCapacity < 0) {
            throw new IllegalArgumentException("waitingCapacity must be >= 0");
        }
        targetId = targetId.trim();
    }
}
