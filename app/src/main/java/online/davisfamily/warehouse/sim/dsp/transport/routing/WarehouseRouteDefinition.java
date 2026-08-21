package online.davisfamily.warehouse.sim.dsp.transport.routing;

import online.davisfamily.threedee.behaviour.routing.RouteFollower.TravelDirection;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;

public record WarehouseRouteDefinition(
        OperationalRouteDestination destination,
        RouteSegment entrySegment,
        float entryDistance,
        TravelDirection entryDirection,
        String terminalArrivalSensorId,
        RouteSegment terminalSegment) {

    public WarehouseRouteDefinition {
        if (destination == null) {
            throw new IllegalArgumentException("destination must not be null");
        }
        if (entrySegment == null) {
            throw new IllegalArgumentException("entrySegment must not be null");
        }
        float entrySegmentLength = entrySegment.length();
        if (!Float.isFinite(entrySegmentLength) || entrySegmentLength < 0f) {
            throw new IllegalArgumentException(
                    "entrySegment length must be finite and >= 0");
        }
        if (!Float.isFinite(entryDistance)
                || entryDistance < 0f
                || entryDistance > entrySegmentLength) {
            throw new IllegalArgumentException(
                    "entryDistance must be finite and within the entry segment length");
        }
        if (entryDirection == null) {
            throw new IllegalArgumentException("entryDirection must not be null");
        }
        if (terminalArrivalSensorId == null || terminalArrivalSensorId.isBlank()) {
            throw new IllegalArgumentException(
                    "terminalArrivalSensorId must not be blank");
        }
        if (terminalSegment == null) {
            throw new IllegalArgumentException("terminalSegment must not be null");
        }
        terminalArrivalSensorId = terminalArrivalSensorId.trim();
    }
}
