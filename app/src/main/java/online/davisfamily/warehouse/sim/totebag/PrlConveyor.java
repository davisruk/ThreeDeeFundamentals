package online.davisfamily.warehouse.sim.totebag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PrlConveyor {
    private final PrlAssignment assignment;
    private final float fixedIndexDistance;
    private final ConveyorOccupancyModel occupancyModel;
    private final List<Pack> packs = new ArrayList<>();
    private float indexedDistance;

    public PrlConveyor(String prlId, float fixedIndexDistance, ConveyorOccupancyModel occupancyModel) {
        if (fixedIndexDistance < 0f) {
            throw new IllegalArgumentException("fixedIndexDistance must be >= 0");
        }
        if (occupancyModel == null) {
            throw new IllegalArgumentException("occupancyModel must not be null");
        }
        this.assignment = new PrlAssignment(prlId);
        this.fixedIndexDistance = fixedIndexDistance;
        this.occupancyModel = occupancyModel;
    }

    public String getId() {
        return assignment.getPrlId();
    }

    public PrlAssignment getAssignment() {
        return assignment;
    }

    public float getIndexedDistance() {
        return indexedDistance;
    }

    public List<Pack> getPacks() {
        return Collections.unmodifiableList(packs);
    }

    public void assign(PrlAssignmentPlan plan) {
        assignment.assign(plan.correlationId(), plan.expectedPackCount());
    }

    public boolean accepts(Pack pack) {
        return pack != null
                && assignment.getCorrelationId() != null
                && assignment.getCorrelationId().equals(pack.getCorrelationId())
                && assignment.getState() != PrlState.RELEASING;
    }

    public void acceptPack(Pack pack) {
        if (!accepts(pack)) {
            throw new IllegalArgumentException("Pack does not belong to this PRL assignment");
        }
        if (assignment.getState() == PrlState.ASSIGNED) {
            assignment.startAccumulating();
        }
        packs.add(pack);
        assignment.recordPackReceived(pack.getId());
        indexedDistance += fixedIndexDistance;
        pack.setState(Pack.PackMotionState.HELD);
    }

    public boolean isReadyToRelease() {
        return assignment.getState() == PrlState.READY_TO_RELEASE;
    }

    public ReleasedPackGroup peekReadyGroup() {
        if (!isReadyToRelease()) {
            throw new IllegalStateException("PRL is not ready to release");
        }
        return new ReleasedPackGroup(
                assignment.getCorrelationId(),
                assignment.getPrlId(),
                List.copyOf(packs),
                occupancyModel.requiredLengthForPacks(packs));
    }

    public ReleasedPackGroup releaseGroup() {
        ReleasedPackGroup group = peekReadyGroup();
        assignment.startRelease();
        clear();
        return group;
    }

    private void clear() {
        packs.clear();
        indexedDistance = 0f;
        assignment.clear();
    }
}
