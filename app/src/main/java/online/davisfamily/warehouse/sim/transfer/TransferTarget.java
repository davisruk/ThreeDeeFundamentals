package online.davisfamily.warehouse.sim.transfer;

import online.davisfamily.threedee.behaviour.routing.RouteFollower.TravelDirection;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;

public record TransferTarget(
        RouteSegment segment,
        float entryDistance,
        TravelDirection travelDirection) {

    public TransferTarget {
        if (segment == null) {
            throw new IllegalArgumentException("segment must not be null");
        }
        if (entryDistance < 0f || entryDistance > segment.length()) {
            throw new IllegalArgumentException("entryDistance must be within the target segment length");
        }
        if (travelDirection == null) {
            throw new IllegalArgumentException("travelDirection must not be null");
        }
    }
}
