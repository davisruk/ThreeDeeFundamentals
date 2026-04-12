package online.davisfamily.warehouse.sim.totebag;

import online.davisfamily.threedee.behaviour.routing.transfer.RouteFollowerSnapshot;
import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.objects.TrackableObject;

public class Pack implements TrackableObject {
    public enum PackMotionState {
        STAGED,
        MOVING,
        HELD,
        DIVERTING,
        CONSUMED
    }

    private final String id;
    private final String correlationId;
    private final PackDimensions dimensions;
    private PackMotionState state = PackMotionState.STAGED;
    private RouteFollowerSnapshot lastSnapshot;

    public Pack(String id, String correlationId, PackDimensions dimensions) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId must not be blank");
        }
        if (dimensions == null) {
            throw new IllegalArgumentException("dimensions must not be null");
        }
        this.id = id;
        this.correlationId = correlationId;
        this.dimensions = dimensions;
    }

    @Override
    public String getId() {
        return id;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public PackDimensions getDimensions() {
        return dimensions;
    }

    public PackMotionState getState() {
        return state;
    }

    public void setState(PackMotionState state) {
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
        this.state = state;
    }

    @Override
    public void update(SimulationContext context, double dtSeconds) {
        // Movement wiring comes in a later slice; the domain object exists now so
        // controllers and plans can be modelled before route-backed flow is added.
    }

    @Override
    public RouteFollowerSnapshot getLastSnapshot() {
        return lastSnapshot;
    }

    public void setLastSnapshot(RouteFollowerSnapshot lastSnapshot) {
        this.lastSnapshot = lastSnapshot;
    }
}
