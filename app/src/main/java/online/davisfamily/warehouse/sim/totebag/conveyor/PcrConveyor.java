package online.davisfamily.warehouse.sim.totebag.conveyor;

import online.davisfamily.warehouse.sim.totebag.plan.*;
import online.davisfamily.warehouse.sim.totebag.pack.*;
import online.davisfamily.warehouse.sim.totebag.machine.*;
import online.davisfamily.warehouse.sim.totebag.conveyor.*;
import online.davisfamily.warehouse.sim.totebag.transfer.*;
import online.davisfamily.warehouse.sim.totebag.device.*;
import online.davisfamily.warehouse.sim.totebag.assignment.*;
import online.davisfamily.warehouse.sim.totebag.control.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.objects.SimObject;

public class PcrConveyor implements SimObject {
    private static final class ActiveGroup {
        private final ReleasedPackGroup group;
        private int acceptedPackCount;
        private int transferredPackCount;

        private ActiveGroup(ReleasedPackGroup group) {
            this.group = group;
        }
    }

    private final String id;
    private final ConveyorOccupancyModel occupancyModel;
    private final LinearConveyorLane lane;
    private final List<ActiveGroup> travellingGroups = new ArrayList<>();

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
        float speedMetersPerSecond = travelDurationSeconds == 0d
                ? occupancyModel.getUsableLength()
                : occupancyModel.getUsableLength() / (float) travelDurationSeconds;
        this.lane = new LinearConveyorLane(id + "_lane", occupancyModel.getUsableLength(), occupancyModel.getMinimumGap(), speedMetersPerSecond);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void update(SimulationContext context, double dtSeconds) {
        lane.setRunning(!lane.isEmpty() || !travellingGroups.isEmpty());
        if (lane.isRunning()) {
            lane.advance(dtSeconds);
            for (Pack pack : lane.getPacks()) {
                pack.setState(Pack.PackMotionState.MOVING);
            }
        }
    }

    public PcrReleaseDecision evaluateRelease(ReleasedPackGroup group) {
        return occupancyModel.evaluateRelease(group.toPackPlans(), getOccupiedLength());
    }

    public void startReceivingGroup(ReleasedPackGroup group) {
        if (group == null) {
            throw new IllegalArgumentException("group must not be null");
        }
        if (!travellingGroups.isEmpty()) {
            throw new IllegalStateException("PCR is already carrying a released group");
        }
        travellingGroups.add(new ActiveGroup(group));
    }

    public boolean canAcceptIncomingPack(Pack pack) {
        return lane.canAcceptAtInfeed(pack);
    }

    public void acceptIncomingPack(Pack pack) {
        acceptIncomingPackAtDistance(pack, pack.getDimensions().length());
    }

    public boolean canAcceptIncomingPackAtDistance(Pack pack, float frontDistance) {
        return lane.canAcceptAtFrontDistance(pack, frontDistance);
    }

    public void acceptIncomingPackAtDistance(Pack pack, float frontDistance) {
        if (!lane.canAcceptAtFrontDistance(pack, frontDistance)) {
            throw new IllegalStateException("PCR entry does not have space for pack " + pack.getId());
        }
        ActiveGroup activeGroup = findTravellingGroupForPack(pack)
                .orElseThrow(() -> new IllegalStateException("No active PCR group for pack " + pack.getId()));
        lane.acceptAtFrontDistance(pack, frontDistance);
        pack.setState(Pack.PackMotionState.MOVING);
        activeGroup.acceptedPackCount++;
    }

    public boolean hasWorkInFlight() {
        return !travellingGroups.isEmpty();
    }

    public float getOccupiedLength() {
        float occupiedLength = 0f;
        for (ActiveGroup travellingGroup : travellingGroups) {
            occupiedLength += travellingGroup.group.requiredLength();
        }
        return occupiedLength;
    }

    public boolean isEmpty() {
        return lane.isEmpty() && travellingGroups.isEmpty();
    }

    public float getUsableLength() {
        return occupancyModel.getUsableLength();
    }

    public boolean isRunning() {
        return lane.isRunning();
    }

    public List<LinearLaneEntrySnapshot> getLaneEntries() {
        return lane.getEntrySnapshots();
    }

    public List<ReleasedPackGroup> getTravellingGroups() {
        List<ReleasedPackGroup> result = new ArrayList<>();
        for (ActiveGroup travellingGroup : travellingGroups) {
            result.add(travellingGroup.group);
        }
        return Collections.unmodifiableList(result);
    }

    public List<ReleasedPackGroup> getReadyGroups() {
        return List.of();
    }

    public Optional<ReleasedPackGroup> peekGroupAtOutfeed() {
        Pack leadingPack = lane.peekLeadingPackAtOutfeed().orElse(null);
        if (leadingPack == null) {
            return Optional.empty();
        }
        return findTravellingGroupForPack(leadingPack).map(group -> group.group);
    }

    public Optional<Pack> pollPackAtOutfeed() {
        Pack leadingPack = lane.pollLeadingPackAtOutfeed().orElse(null);
        if (leadingPack == null) {
            return Optional.empty();
        }

        ActiveGroup activeGroup = findTravellingGroupForPack(leadingPack)
                .orElseThrow(() -> new IllegalStateException("No active PCR group for pack " + leadingPack.getId()));
        activeGroup.transferredPackCount++;
        if (activeGroup.transferredPackCount >= activeGroup.group.packs().size()) {
            travellingGroups.remove(activeGroup);
        }
        return Optional.of(leadingPack);
    }

    private java.util.Optional<ActiveGroup> findTravellingGroupForPack(Pack pack) {
        return travellingGroups.stream()
                .filter(group -> group.group.packs().contains(pack))
                .findFirst();
    }
}
