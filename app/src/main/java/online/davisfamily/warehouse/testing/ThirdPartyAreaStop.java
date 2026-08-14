package online.davisfamily.warehouse.testing;

import online.davisfamily.threedee.behaviour.routing.RouteSegment;

public record ThirdPartyAreaStop(
        String id,
        RouteSegment segment,
        float sensorDistance,
        float holdDistance) {

    public ThirdPartyAreaStop {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (segment == null) {
            throw new IllegalArgumentException("segment must not be null");
        }
        if (sensorDistance < 0f || sensorDistance > segment.length()) {
            throw new IllegalArgumentException("sensorDistance must be within segment length");
        }
        if (holdDistance < 0f || holdDistance > segment.length()) {
            throw new IllegalArgumentException("holdDistance must be within segment length");
        }
        id = id.trim();
    }

    public ThirdPartyAreaStop(String id, RouteSegment segment, float sensorDistance) {
        this(id, segment, sensorDistance, sensorDistance);
    }
}
