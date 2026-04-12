package online.davisfamily.warehouse.sim.totebag;

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

    public boolean canAcceptAtInfeed(Pack pack) {
        if (pack == null) {
            return false;
        }
        float candidateFrontDistance = pack.getDimensions().length();
        if (entries.isEmpty()) {
            return candidateFrontDistance <= usableLength;
        }
        LaneEntry trailing = entries.getLast();
        return candidateFrontDistance <= trailing.rearDistance() - minimumGap + pack.getDimensions().length();
    }

    public void acceptAtInfeed(Pack pack) {
        if (!canAcceptAtInfeed(pack)) {
            throw new IllegalStateException("Lane infeed does not have space for pack " + pack.getId());
        }
        entries.add(new LaneEntry(pack, pack.getDimensions().length()));
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
