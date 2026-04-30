package online.davisfamily.warehouse.sim.totebag.conveyor;

import online.davisfamily.warehouse.sim.totebag.plan.*;
import online.davisfamily.warehouse.sim.totebag.pack.*;
import online.davisfamily.warehouse.sim.totebag.machine.*;
import online.davisfamily.warehouse.sim.totebag.conveyor.*;
import online.davisfamily.warehouse.sim.totebag.transfer.*;
import online.davisfamily.warehouse.sim.totebag.device.*;
import online.davisfamily.warehouse.sim.totebag.assignment.*;
import online.davisfamily.warehouse.sim.totebag.control.*;

import java.util.List;
import java.util.Optional;

public class PdcConveyor {
    private final String id;
    private final LinearConveyorLane lane;
    private boolean running = true;

    public PdcConveyor(String id, ConveyorOccupancyModel occupancyModel, float beltSpeedMetersPerSecond) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (occupancyModel == null) {
            throw new IllegalArgumentException("occupancyModel must not be null");
        }
        if (beltSpeedMetersPerSecond < 0f) {
            throw new IllegalArgumentException("beltSpeedMetersPerSecond must be >= 0");
        }
        this.id = id;
        this.lane = new LinearConveyorLane(id + "_lane", occupancyModel.getUsableLength(), occupancyModel.getMinimumGap(), beltSpeedMetersPerSecond);
    }

    public String getId() {
        return id;
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    public void update(double dtSeconds) {
        if (!running) {
            setLanePackState(Pack.PackMotionState.HELD);
            return;
        }
        lane.advance(dtSeconds);
        setLanePackState(Pack.PackMotionState.MOVING);
    }

    public boolean canAcceptIncomingPack(Pack pack) {
        return lane.canAcceptAtInfeed(pack);
    }

    public void acceptIncomingPack(Pack pack) {
        lane.acceptAtInfeed(pack);
        pack.setState(Pack.PackMotionState.MOVING);
    }

    public boolean canAcceptIncomingPackAtFrontDistance(Pack pack, float frontDistance) {
        return lane.canAcceptAtFrontDistance(pack, frontDistance);
    }

    public void acceptIncomingPackAtFrontDistance(Pack pack, float frontDistance) {
        lane.acceptAtFrontDistance(pack, frontDistance);
        pack.setState(Pack.PackMotionState.MOVING);
    }

    public Optional<Float> divertPack(Pack pack) {
        Optional<Float> frontDistance = lane.getFrontDistanceFor(pack);
        if (frontDistance.isEmpty()) {
            return Optional.empty();
        }
        if (!lane.removePack(pack)) {
            return Optional.empty();
        }
        pack.setState(Pack.PackMotionState.DIVERTING);
        return frontDistance;
    }

    public List<LinearLaneEntrySnapshot> getLaneEntries() {
        return lane.getEntrySnapshots();
    }

    public Optional<Pack> peekLeadingPackAtOutfeed() {
        return lane.peekLeadingPackAtOutfeed();
    }

    public Optional<Pack> pollLeadingPackAtOutfeed() {
        return lane.pollLeadingPackAtOutfeed();
    }

    public float getUsableLength() {
        return lane.getUsableLength();
    }

    public float getSpeedMetersPerSecond() {
        return lane.getSpeedMetersPerSecond();
    }

    private void setLanePackState(Pack.PackMotionState state) {
        for (Pack pack : lane.getPacks()) {
            pack.setState(state);
        }
    }
}
