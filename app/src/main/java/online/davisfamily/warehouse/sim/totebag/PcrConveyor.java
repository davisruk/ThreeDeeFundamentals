package online.davisfamily.warehouse.sim.totebag;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.objects.SimObject;

public class PcrConveyor implements SimObject {
    private static final class TravellingGroup {
        private final ReleasedPackGroup group;
        private double remainingTravelSeconds;

        private TravellingGroup(ReleasedPackGroup group, double remainingTravelSeconds) {
            this.group = group;
            this.remainingTravelSeconds = remainingTravelSeconds;
        }
    }

    private final String id;
    private final ConveyorOccupancyModel occupancyModel;
    private final double travelDurationSeconds;
    private final List<TravellingGroup> travellingGroups = new ArrayList<>();
    private final Queue<ReleasedPackGroup> readyForBagging = new ArrayDeque<>();

    public PcrConveyor(String id, ConveyorOccupancyModel occupancyModel, double travelDurationSeconds) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (occupancyModel == null) {
            throw new IllegalArgumentException("occupancyModel must not be null");
        }
        if (travelDurationSeconds < 0d) {
            throw new IllegalArgumentException("travelDurationSeconds must be >= 0");
        }
        this.id = id;
        this.occupancyModel = occupancyModel;
        this.travelDurationSeconds = travelDurationSeconds;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void update(SimulationContext context, double dtSeconds) {
        Iterator<TravellingGroup> iterator = travellingGroups.iterator();
        while (iterator.hasNext()) {
            TravellingGroup travellingGroup = iterator.next();
            travellingGroup.remainingTravelSeconds = Math.max(0d, travellingGroup.remainingTravelSeconds - dtSeconds);
            if (travellingGroup.remainingTravelSeconds <= 0d) {
                readyForBagging.add(travellingGroup.group);
                iterator.remove();
            }
        }
    }

    public PcrReleaseDecision evaluateRelease(ReleasedPackGroup group) {
        return occupancyModel.evaluateRelease(group.toPackPlans(), getOccupiedLength());
    }

    public void accept(ReleasedPackGroup group) {
        PcrReleaseDecision decision = evaluateRelease(group);
        if (!decision.allowed()) {
            throw new IllegalStateException(decision.reason());
        }
        for (Pack pack : group.packs()) {
            pack.setState(Pack.PackMotionState.MOVING);
        }
        travellingGroups.add(new TravellingGroup(group, travelDurationSeconds));
    }

    public boolean hasReadyGroup() {
        return !readyForBagging.isEmpty();
    }

    public ReleasedPackGroup pollReadyGroup() {
        return readyForBagging.poll();
    }

    public float getOccupiedLength() {
        float occupiedLength = 0f;
        for (TravellingGroup travellingGroup : travellingGroups) {
            occupiedLength += travellingGroup.group.requiredLength();
        }
        for (ReleasedPackGroup group : readyForBagging) {
            occupiedLength += group.requiredLength();
        }
        return occupiedLength;
    }

    public boolean isEmpty() {
        return travellingGroups.isEmpty() && readyForBagging.isEmpty();
    }

    public float getUsableLength() {
        return occupancyModel.getUsableLength();
    }

    public List<ReleasedPackGroup> getTravellingGroups() {
        List<ReleasedPackGroup> result = new ArrayList<>();
        for (TravellingGroup travellingGroup : travellingGroups) {
            result.add(travellingGroup.group);
        }
        return Collections.unmodifiableList(result);
    }

    public List<ReleasedPackGroup> getReadyGroups() {
        return Collections.unmodifiableList(new ArrayList<>(readyForBagging));
    }
}
