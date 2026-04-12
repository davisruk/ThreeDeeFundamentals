package online.davisfamily.warehouse.sim.totebag;

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
        if (!lane.canAcceptAtInfeed(pack)) {
            throw new IllegalStateException("PCR infeed does not have space for pack " + pack.getId());
        }
        ActiveGroup activeGroup = findTravellingGroupForPack(pack)
                .orElseThrow(() -> new IllegalStateException("No active PCR group for pack " + pack.getId()));
        lane.acceptAtInfeed(pack);
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
        return readyForBagging.poll();
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
        return Collections.unmodifiableList(new ArrayList<>(readyForBagging));
    }

    private void markReadyGroups() {
        while (!travellingGroups.isEmpty()) {
            ActiveGroup firstGroup = travellingGroups.getFirst();
            if (firstGroup.acceptedPackCount < firstGroup.group.packs().size()
                    || firstGroup.arrivedPackCount < firstGroup.group.packs().size()) {
                return;
            }
            travellingGroups.removeFirst();
            readyForBagging.add(firstGroup.group);
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
            activeGroup.arrivedPackCount++;
        }
    }

    private java.util.Optional<ActiveGroup> findTravellingGroupForPack(Pack pack) {
        return travellingGroups.stream()
                .filter(group -> group.group.packs().contains(pack))
                .findFirst();
    }
}
