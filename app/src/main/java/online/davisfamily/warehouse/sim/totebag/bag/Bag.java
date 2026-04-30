package online.davisfamily.warehouse.sim.totebag.bag;

import java.util.List;

import online.davisfamily.warehouse.sim.totebag.plan.BagSpec;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;
import online.davisfamily.warehouse.sim.totebag.transfer.ReleasedPackGroup;

public class Bag {
    public enum BagMotionState {
        STAGED,
        MOVING,
        RECEIVED
    }

    public enum BagContainmentState {
        FREE,
        IN_RECEIVER
    }

    private final String id;
    private final String correlationId;
    private final List<PackPlan> packContents;
    private final BagSpec bagSpec;
    private BagMotionState motionState = BagMotionState.STAGED;
    private BagContainmentState containmentState = BagContainmentState.FREE;

    public Bag(String id, String correlationId, List<PackPlan> packContents, BagSpec bagSpec) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId must not be blank");
        }
        if (packContents == null || packContents.isEmpty()) {
            throw new IllegalArgumentException("packContents must not be empty");
        }
        if (packContents.stream().anyMatch(packPlan -> packPlan == null)) {
            throw new IllegalArgumentException("packContents must not contain null entries");
        }
        if (bagSpec == null) {
            throw new IllegalArgumentException("bagSpec must not be null");
        }
        this.id = id;
        this.correlationId = correlationId;
        this.packContents = List.copyOf(packContents);
        this.bagSpec = bagSpec;
    }

    public static Bag fromReleasedPackGroup(String id, ReleasedPackGroup group, BagSpec bagSpec) {
        if (group == null) {
            throw new IllegalArgumentException("group must not be null");
        }
        return new Bag(id, group.correlationId(), group.toPackPlans(), bagSpec);
    }

    public String getId() {
        return id;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public int getPackCount() {
        return packContents.size();
    }

    public List<PackPlan> getPackContents() {
        return packContents;
    }

    public BagSpec getBagSpec() {
        return bagSpec;
    }

    public BagMotionState getMotionState() {
        return motionState;
    }

    public void setMotionState(BagMotionState motionState) {
        if (motionState == null) {
            throw new IllegalArgumentException("motionState must not be null");
        }
        this.motionState = motionState;
    }

    public BagContainmentState getContainmentState() {
        return containmentState;
    }

    public void setContainmentState(BagContainmentState containmentState) {
        if (containmentState == null) {
            throw new IllegalArgumentException("containmentState must not be null");
        }
        this.containmentState = containmentState;
    }
}
