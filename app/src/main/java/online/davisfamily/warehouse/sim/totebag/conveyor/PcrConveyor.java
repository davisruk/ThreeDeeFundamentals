package online.davisfamily.warehouse.sim.totebag.conveyor;

import online.davisfamily.warehouse.sim.totebag.plan.*;
import online.davisfamily.warehouse.sim.totebag.pack.*;
import online.davisfamily.warehouse.sim.totebag.machine.*;
import online.davisfamily.warehouse.sim.totebag.conveyor.*;
import online.davisfamily.warehouse.sim.totebag.transfer.*;
import online.davisfamily.warehouse.sim.totebag.device.*;
import online.davisfamily.warehouse.sim.totebag.assignment.*;
import online.davisfamily.warehouse.sim.totebag.control.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.objects.SimObject;

public class PcrConveyor implements SimObject {
    private static final class ActiveGroup {
        private final ReleasedPackGroup group;
        private final List<Pack> arrivedPacks = new ArrayList<>();
        private int acceptedPackCount;
        private int arrivedPackCount;

        private ActiveGroup(ReleasedPackGroup group) {
            this.group = group;
        }
    }

    private final String id;
    private final ConveyorOccupancyModel occupancyModel;
    private final LinearConveyorLane lane;
    private final List<ActiveGroup> travellingGroups = new ArrayList<>();
    private final Queue<ActiveGroup> readyForBagging = new ArrayDeque<>();

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
        transferArrivedPacksFromLane();
        markReadyGroups();
    }

    public PcrReleaseDecision evaluateRelease(ReleasedPackGroup group) {
        return occupancyModel.evaluateRelease(group.toPackPlans(), getOccupiedLength());
    }

    public void startReceivingGroup(ReleasedPackGroup group) {
        PcrReleaseDecision decision = evaluateRelease(group);
        if (!decision.allowed()) {
            throw new IllegalStateException(decision.reason());
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

    public boolean hasReadyGroup() {
        return !readyForBagging.isEmpty();
    }

    public boolean hasWorkInFlight() {
        return !travellingGroups.isEmpty() || !readyForBagging.isEmpty();
    }

    public ReleasedPackGroup pollReadyGroup() {
        ActiveGroup readyGroup = readyForBagging.poll();
        return readyGroup == null ? null : readyGroup.group;
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
        List<LinearLaneEntrySnapshot> snapshots = new ArrayList<>(lane.getEntrySnapshots());
        appendArrivedGroupSnapshots(snapshots, travellingGroups);
        appendArrivedGroupSnapshots(snapshots, readyForBagging);
        return Collections.unmodifiableList(snapshots);
    }

    public List<ReleasedPackGroup> getTravellingGroups() {
        List<ReleasedPackGroup> result = new ArrayList<>();
        for (ActiveGroup travellingGroup : travellingGroups) {
            result.add(travellingGroup.group);
        }
        return Collections.unmodifiableList(result);
    }

    public List<ReleasedPackGroup> getReadyGroups() {
        List<ReleasedPackGroup> readyGroups = new ArrayList<>();
        for (ActiveGroup readyGroup : readyForBagging) {
            readyGroups.add(readyGroup.group);
        }
        return Collections.unmodifiableList(readyGroups);
    }

    private void markReadyGroups() {
        while (!travellingGroups.isEmpty()) {
            ActiveGroup firstGroup = travellingGroups.getFirst();
            if (firstGroup.acceptedPackCount < firstGroup.group.packs().size()
                    || firstGroup.arrivedPackCount < firstGroup.group.packs().size()) {
                return;
            }
            travellingGroups.removeFirst();
            readyForBagging.add(firstGroup);
        }
    }

    private void transferArrivedPacksFromLane() {
        while (true) {
            Pack arrivedPack = lane.peekLeadingPackAtOutfeed().orElse(null);
            if (arrivedPack == null) {
                return;
            }
            Pack removedPack = lane.pollLeadingPackAtOutfeed()
                    .orElseThrow(() -> new IllegalStateException("Expected PCR pack at outfeed"));
            ActiveGroup activeGroup = findTravellingGroupForPack(removedPack)
                    .orElseThrow(() -> new IllegalStateException("No active PCR group for pack " + removedPack.getId()));
            activeGroup.arrivedPacks.add(removedPack);
            activeGroup.arrivedPackCount++;
        }
    }

    private void appendArrivedGroupSnapshots(List<LinearLaneEntrySnapshot> snapshots, Iterable<ActiveGroup> groups) {
        for (ActiveGroup group : groups) {
            float frontDistance = occupancyModel.getUsableLength();
            for (Pack pack : group.arrivedPacks) {
                snapshots.add(new LinearLaneEntrySnapshot(pack, frontDistance));
                frontDistance -= pack.getDimensions().length() + occupancyModel.getMinimumGap();
            }
        }
    }

    private java.util.Optional<ActiveGroup> findTravellingGroupForPack(Pack pack) {
        return travellingGroups.stream()
                .filter(group -> group.group.packs().contains(pack))
                .findFirst();
    }
}
