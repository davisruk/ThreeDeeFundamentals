package online.davisfamily.warehouse.sim.totebag;

import java.util.List;
import java.util.Optional;

public class PrlConveyor {
    private static final float DEFAULT_BELT_SPEED_METERS_PER_SECOND = 0.85f;

    private final PrlAssignment assignment;
    private final float fixedIndexDistance;
    private final ConveyorOccupancyModel occupancyModel;
    private final LinearConveyorLane lane;
    private final float releaseLeadClearance;
    private float indexedDistance;
    private float remainingControlledTravelDistance;

    public PrlConveyor(String prlId, float fixedIndexDistance, ConveyorOccupancyModel occupancyModel) {
        this(prlId, fixedIndexDistance, occupancyModel, DEFAULT_BELT_SPEED_METERS_PER_SECOND);
    }

    public PrlConveyor(String prlId, float fixedIndexDistance, ConveyorOccupancyModel occupancyModel, float beltSpeedMetersPerSecond) {
        if (fixedIndexDistance < 0f) {
            throw new IllegalArgumentException("fixedIndexDistance must be >= 0");
        }
        if (occupancyModel == null) {
            throw new IllegalArgumentException("occupancyModel must not be null");
        }
        this.assignment = new PrlAssignment(prlId);
        this.fixedIndexDistance = fixedIndexDistance;
        this.occupancyModel = occupancyModel;
        this.lane = new LinearConveyorLane(prlId + "_lane", occupancyModel.getUsableLength(), occupancyModel.getMinimumGap(), beltSpeedMetersPerSecond);
        this.releaseLeadClearance = Math.max(fixedIndexDistance, occupancyModel.getMinimumGap());
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
        return lane.getPacks();
    }

    public List<LinearLaneEntrySnapshot> getLaneEntries() {
        return lane.getEntrySnapshots();
    }

    public boolean isRunning() {
        return lane.isRunning();
    }

    public void assign(PrlAssignmentPlan plan) {
        assignment.assign(plan.correlationId(), plan.expectedPackCount());
    }

    public boolean accepts(Pack pack) {
        return pack != null
                && assignment.getCorrelationId() != null
                && assignment.getCorrelationId().equals(pack.getCorrelationId())
                && assignment.getState() != PrlState.RELEASING
                && lane.canAcceptAtInfeed(pack);
    }

    public void acceptPack(Pack pack) {
        if (!accepts(pack)) {
            throw new IllegalArgumentException("Pack does not belong to this PRL assignment");
        }
        if (assignment.getState() == PrlState.ASSIGNED) {
            assignment.startAccumulating();
        }
        lane.acceptAtInfeed(pack);
        assignment.recordPackReceived(pack.getId());
        if (assignment.getState() == PrlState.READY_TO_RELEASE) {
            queueTravelToReleasePosition();
        } else {
            queueControlledTravel(fixedIndexDistance);
        }
        pack.setState(Pack.PackMotionState.MOVING);
    }

    public void update(double dtSeconds) {
        boolean shouldRunControlledTravel = remainingControlledTravelDistance > 0f;
        boolean shouldRunRelease = assignment.getState() == PrlState.RELEASING && !lane.isEmpty();
        lane.setRunning(shouldRunControlledTravel || shouldRunRelease);
        if (!lane.isRunning()) {
            setLanePackState(Pack.PackMotionState.HELD);
            return;
        }

        float movedDistance = shouldRunControlledTravel
                ? lane.advanceDistance(Math.min(lane.getSpeedMetersPerSecond() * (float) Math.max(0d, dtSeconds), remainingControlledTravelDistance))
                : lane.advance(dtSeconds);
        indexedDistance += movedDistance;
        if (shouldRunControlledTravel) {
            remainingControlledTravelDistance = Math.max(0f, remainingControlledTravelDistance - movedDistance);
        }
        setLanePackState(Pack.PackMotionState.MOVING);
        if (!lane.isRunning()) {
            setLanePackState(Pack.PackMotionState.HELD);
        }
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
                List.copyOf(lane.getPacks()),
                occupancyModel.requiredLengthForPacks(lane.getPacks()));
    }

    public ReleasedPackGroup releaseGroup() {
        ReleasedPackGroup group = peekReadyGroup();
        assignment.startRelease();
        lane.setRunning(true);
        return group;
    }

    public Optional<Pack> pollPackAtOutfeed() {
        return lane.pollLeadingPackAtOutfeed();
    }

    public Optional<Pack> peekPackAtOutfeed() {
        return lane.peekLeadingPackAtOutfeed();
    }

    public void completeReleaseIfEmpty() {
        if (assignment.getState() == PrlState.RELEASING && lane.isEmpty()) {
            clear();
        }
    }

    private void clear() {
        indexedDistance = 0f;
        remainingControlledTravelDistance = 0f;
        lane.setRunning(false);
        assignment.clear();
    }

    private void queueControlledTravel(float distance) {
        if (distance <= 0f) {
            return;
        }
        remainingControlledTravelDistance += distance;
    }

    private void queueTravelToReleasePosition() {
        Optional<LinearLaneEntrySnapshot> leadingEntry = lane.getLeadingEntry();
        if (leadingEntry.isEmpty()) {
            return;
        }
        float targetFrontDistance = lane.getUsableLength() - releaseLeadClearance;
        float additionalDistance = targetFrontDistance - leadingEntry.get().frontDistance();
        if (additionalDistance > 0f) {
            queueControlledTravel(additionalDistance);
        }
    }

    private void setLanePackState(Pack.PackMotionState state) {
        for (Pack pack : lane.getPacks()) {
            pack.setState(state);
        }
    }
}
