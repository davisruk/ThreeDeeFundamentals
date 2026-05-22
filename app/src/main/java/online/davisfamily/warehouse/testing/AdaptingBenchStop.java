package online.davisfamily.warehouse.testing;

import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingBenchId;

public record AdaptingBenchStop(
        AdaptingBenchId benchId,
        RouteSegment segment,
        float sensorDistance,
        float holdDistance) {

    public AdaptingBenchStop {
        if (benchId == null) {
            throw new IllegalArgumentException("benchId must not be null");
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
    }

    public AdaptingBenchStop(AdaptingBenchId benchId, RouteSegment segment, float sensorDistance) {
        this(benchId, segment, sensorDistance, sensorDistance);
    }
}
