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
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

public class LinearConveyorLane {
    private static final class LaneEntry {
        private final Pack pack;
        private float frontDistance;

        private LaneEntry(Pack pack, float frontDistance) {
            this.pack = pack;
            this.frontDistance = frontDistance;
        }

        private float rearDistance() {
            return frontDistance - pack.getDimensions().length();
        }
    }

    private final String id;
    private final float usableLength;
    private final float minimumGap;
    private final float speedMetersPerSecond;
    private final List<LaneEntry> entries = new ArrayList<>();
    private boolean running;

    public LinearConveyorLane(String id, float usableLength, float minimumGap, float speedMetersPerSecond) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (usableLength <= 0f) {
            throw new IllegalArgumentException("usableLength must be > 0");
        }
        if (minimumGap < 0f) {
            throw new IllegalArgumentException("minimumGap must be >= 0");
        }
        if (speedMetersPerSecond < 0f) {
            throw new IllegalArgumentException("speedMetersPerSecond must be >= 0");
        }
        this.id = id;
        this.usableLength = usableLength;
        this.minimumGap = minimumGap;
        this.speedMetersPerSecond = speedMetersPerSecond;
    }

    public String getId() {
        return id;
    }

    public float getUsableLength() {
        return usableLength;
    }

    public float getMinimumGap() {
        return minimumGap;
    }

    public float getSpeedMetersPerSecond() {
        return speedMetersPerSecond;
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int getPackCount() {
        return entries.size();
    }

    public List<Pack> getPacks() {
        List<Pack> packs = new ArrayList<>();
        for (LaneEntry entry : entries) {
            packs.add(entry.pack);
        }
        return Collections.unmodifiableList(packs);
    }

    public List<LinearLaneEntrySnapshot> getEntrySnapshots() {
        List<LinearLaneEntrySnapshot> snapshots = new ArrayList<>();
        for (LaneEntry entry : entries) {
            snapshots.add(new LinearLaneEntrySnapshot(entry.pack, entry.frontDistance));
        }
        return Collections.unmodifiableList(snapshots);
    }

    public Optional<LinearLaneEntrySnapshot> getLeadingEntry() {
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        LaneEntry leader = entries.getFirst();
        return Optional.of(new LinearLaneEntrySnapshot(leader.pack, leader.frontDistance));
    }

    public Optional<LinearLaneEntrySnapshot> getTrailingEntry() {
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        LaneEntry trailing = entries.getLast();
        return Optional.of(new LinearLaneEntrySnapshot(trailing.pack, trailing.frontDistance));
    }

    public Optional<Float> getFrontDistanceFor(Pack pack) {
        if (pack == null) {
            return Optional.empty();
        }
        for (LaneEntry entry : entries) {
            if (entry.pack.equals(pack)) {
                return Optional.of(entry.frontDistance);
            }
        }
        return Optional.empty();
    }

    public boolean canAcceptAtInfeed(Pack pack) {
        if (pack == null) {
            return false;
        }
        return canAcceptAtFrontDistance(pack, pack.getDimensions().length());
    }

    public boolean canAcceptAtFrontDistance(Pack pack, float candidateFrontDistance) {
        if (pack == null) {
            return false;
        }
        if (candidateFrontDistance < pack.getDimensions().length() || candidateFrontDistance > usableLength) {
            return false;
        }
        LaneEntry ahead = null;
        LaneEntry behind = null;
        for (LaneEntry entry : entries) {
            if (entry.frontDistance > candidateFrontDistance) {
                ahead = entry;
                continue;
            }
            behind = entry;
            break;
        }

        if (ahead != null) {
            float maxFrontDistance = ahead.rearDistance() - minimumGap + pack.getDimensions().length();
            if (candidateFrontDistance > maxFrontDistance) {
                return false;
            }
        }

        if (behind != null) {
            float minFrontDistance = behind.frontDistance + minimumGap + pack.getDimensions().length();
            if (candidateFrontDistance < minFrontDistance) {
                return false;
            }
        }
        return true;
    }

    public void acceptAtInfeed(Pack pack) {
        acceptAtFrontDistance(pack, pack.getDimensions().length());
    }

    public void acceptAtFrontDistance(Pack pack, float frontDistance) {
        if (!canAcceptAtFrontDistance(pack, frontDistance)) {
            throw new IllegalStateException("Lane infeed does not have space for pack " + pack.getId());
        }
        entries.add(new LaneEntry(pack, frontDistance));
        sortEntries();
    }

    public float advance(double dtSeconds) {
        return advanceDistance(speedMetersPerSecond * (float) Math.max(0d, dtSeconds));
    }

    public float advanceDistance(float requestedDistance) {
        if (requestedDistance <= 0f || entries.isEmpty()) {
            return 0f;
        }
        float appliedDistance = requestedDistance;
        LaneEntry leading = entries.getFirst();
        if (leading.frontDistance + appliedDistance > usableLength) {
            appliedDistance = usableLength - leading.frontDistance;
        }
        if (appliedDistance <= 0f) {
            return 0f;
        }
        for (LaneEntry entry : entries) {
            entry.frontDistance += appliedDistance;
        }
        clampSpacingFromFront();
        return appliedDistance;
    }

    public Optional<Pack> pollLeadingPackAtOutfeed() {
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        LaneEntry leader = entries.getFirst();
        if (leader.frontDistance < usableLength) {
            return Optional.empty();
        }
        entries.removeFirst();
        return Optional.of(leader.pack);
    }

    public Optional<Pack> peekLeadingPackAtOutfeed() {
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        LaneEntry leader = entries.getFirst();
        if (leader.frontDistance < usableLength) {
            return Optional.empty();
        }
        return Optional.of(leader.pack);
    }

    public void removePacks(List<Pack> packs) {
        if (packs == null || packs.isEmpty()) {
            return;
        }
        Iterator<LaneEntry> iterator = entries.iterator();
        while (iterator.hasNext()) {
            LaneEntry entry = iterator.next();
            if (packs.contains(entry.pack)) {
                iterator.remove();
            }
        }
        sortEntries();
    }

    public boolean removePack(Pack pack) {
        if (pack == null) {
            return false;
        }
        Iterator<LaneEntry> iterator = entries.iterator();
        while (iterator.hasNext()) {
            LaneEntry entry = iterator.next();
            if (entry.pack.equals(pack)) {
                iterator.remove();
                sortEntries();
                return true;
            }
        }
        return false;
    }

    private void clampSpacingFromFront() {
        LaneEntry previous = null;
        for (LaneEntry entry : entries) {
            entry.frontDistance = Math.min(entry.frontDistance, usableLength);
            if (previous != null) {
                float maxFrontDistance = previous.rearDistance() - minimumGap + entry.pack.getDimensions().length();
                entry.frontDistance = Math.min(entry.frontDistance, maxFrontDistance);
            }
            previous = entry;
        }
        sortEntries();
    }

    private void sortEntries() {
        entries.sort((left, right) -> Float.compare(right.frontDistance, left.frontDistance));
    }
}
