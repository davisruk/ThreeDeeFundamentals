package online.davisfamily.warehouse.sim.totebag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.totebag.assignment.PrlAssignmentPlan;
import online.davisfamily.warehouse.sim.totebag.assignment.PrlState;
import online.davisfamily.warehouse.sim.totebag.conveyor.ConveyorOccupancyModel;
import online.davisfamily.warehouse.sim.totebag.conveyor.LinearLaneEntrySnapshot;
import online.davisfamily.warehouse.sim.totebag.conveyor.PrlConveyor;
import online.davisfamily.warehouse.sim.totebag.pack.Pack;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.transfer.ReleasedPackGroup;

class PrlConveyorTest {
    private static final float EPSILON = 0.000001f;

    @Test
    void shouldRequestOnlyTheMissingTravelForThirtyThenOneHundredSeventyFourMillimetres() {
        PrlConveyor prl = new PrlConveyor(
                "prl-1", 0.100f, new ConveyorOccupancyModel(1.8f, 0.015f, 0f), 1.8f);
        prl.assign(new PrlAssignmentPlan("prl-1", "bag-a", 2));
        Pack first = pack("short", "bag-a", 0.030f);
        Pack longPack = pack("long", "bag-a", 0.174f);
        prl.acceptPack(first);
        prl.update(1d);
        assertEquals(0.130f, positionOf(prl, first), EPSILON);

        List<LinearLaneEntrySnapshot> before = prl.getLaneEntries();
        for (int i = 0; i < 100; i++) {
            assertFalse(prl.accepts(longPack));
        }
        assertEquals(before, prl.getLaneEntries());
        assertEquals(1, prl.getAssignment().getReceivedPackCount());
        assertEquals(PrlState.ACCUMULATING, prl.getAssignment().getState());

        for (int i = 0; i < 100; i++) {
            assertTrue(prl.requestInfeedSpaceFor(longPack));
        }
        assertEquals(before, prl.getLaneEntries());
        assertEquals(0.100f, prl.getIndexedDistance(), EPSILON);
        assertEquals(1, prl.getAssignment().getReceivedPackCount());
        assertEquals(PrlState.ACCUMULATING, prl.getAssignment().getState());

        for (int i = 0; i < 100 && !prl.accepts(longPack); i++) {
            prl.update(0.05d);
        }
        assertTrue(prl.accepts(longPack));
        prl.update(0.05d);
        assertEquals(0.189f, prl.getIndexedDistance(), EPSILON);
        prl.acceptPack(longPack);

        assertEquals(List.of("short", "long"), prl.getAssignment().getReceivedPackIds());
        assertEquals(2, prl.getAssignment().getReceivedPackCount());
        assertEquals(PrlState.READY_TO_RELEASE, prl.getAssignment().getState());
        List<LinearLaneEntrySnapshot> accepted = prl.getLaneEntries();
        assertSame(first, accepted.get(0).pack());
        assertSame(longPack, accepted.get(1).pack());
        assertTrue(accepted.get(1).frontDistance()
                <= accepted.get(0).rearDistance() - 0.015f + EPSILON);

        ReleasedPackGroup group = prl.releaseGroup();
        assertEquals("bag-a", group.correlationId());
        assertEquals("prl-1", group.sourcePrlId());
        List<Pack> released = new ArrayList<>();
        for (int i = 0; i < 100 && prl.getAssignment().getState() == PrlState.RELEASING; i++) {
            prl.update(0.05d);
            Pack outfeed;
            while ((outfeed = prl.pollPackAtOutfeed().orElse(null)) != null) {
                released.add(outfeed);
            }
            prl.completeReleaseIfEmpty();
        }
        assertEquals(PrlState.IDLE, prl.getAssignment().getState());
        assertEquals(List.of(first, longPack), released);
        assertTrue(prl.getPacks().isEmpty());
    }

    @Test
    void shouldSupplementEqualPackIndexingWithoutReplacingFixedIndex() {
        PrlConveyor equalPacks = new PrlConveyor(
                "equal", 0.150f, new ConveyorOccupancyModel(1.8f, 0.050f, 0f), 1.8f);
        equalPacks.assign(new PrlAssignmentPlan("equal", "bag-a", 2));
        Pack first = pack("equal-first", "bag-a", 0.200f);
        Pack second = pack("equal-second", "bag-a", 0.200f);
        equalPacks.acceptPack(first);
        equalPacks.update(1d);
        assertFalse(equalPacks.accepts(second));
        assertTrue(equalPacks.requestInfeedSpaceFor(second));
        for (int i = 0; i < 100 && !equalPacks.accepts(second); i++) {
            equalPacks.update(0.05d);
        }
        assertTrue(equalPacks.accepts(second));
        equalPacks.update(0.05d);
        assertEquals(0.250f, equalPacks.getIndexedDistance(), EPSILON);

        PrlConveyor alreadyFits = new PrlConveyor(
                "already-fits", 0.100f, new ConveyorOccupancyModel(1.8f, 0.015f, 0f), 1.8f);
        alreadyFits.assign(new PrlAssignmentPlan("already-fits", "bag-a", 2));
        alreadyFits.acceptPack(pack("fits-first", "bag-a", 0.030f));
        alreadyFits.update(1d);
        float indexedBeforeRequest = alreadyFits.getIndexedDistance();
        assertTrue(alreadyFits.requestInfeedSpaceFor(pack("fits-next", "bag-a", 0.020f)));
        assertEquals(indexedBeforeRequest, alreadyFits.getIndexedDistance(), EPSILON);

        PrlConveyor coalesced = new PrlConveyor(
                "coalesced", 0.100f, new ConveyorOccupancyModel(1.8f, 0.015f, 0f), 1.8f);
        coalesced.assign(new PrlAssignmentPlan("coalesced", "bag-a", 2));
        coalesced.acceptPack(pack("coalesced-first", "bag-a", 0.030f));
        Pack coalescedNext = pack("coalesced-next", "bag-a", 0.020f);
        for (int i = 0; i < 100; i++) {
            assertTrue(coalesced.requestInfeedSpaceFor(coalescedNext));
        }
        coalesced.update(1d);
        assertEquals(0.100f, coalesced.getIndexedDistance(), EPSILON);
    }

    @Test
    void shouldKeepImpossibleIntakeAndInvalidRequestsNonMutating() {
        PrlConveyor impossible = new PrlConveyor(
                "short-prl", 0.100f, new ConveyorOccupancyModel(0.200f, 0.015f, 0f), 1.8f);
        impossible.assign(new PrlAssignmentPlan("short-prl", "bag-a", 2));
        Pack first = pack("short-first", "bag-a", 0.030f);
        Pack waiting = pack("too-long", "bag-a", 0.174f);
        impossible.acceptPack(first);
        impossible.update(1d);
        List<LinearLaneEntrySnapshot> impossibleBefore = impossible.getLaneEntries();
        for (int i = 0; i < 100; i++) {
            assertFalse(impossible.requestInfeedSpaceFor(waiting));
            impossible.update(0.05d);
        }
        assertEquals(impossibleBefore, impossible.getLaneEntries());
        assertEquals(0.100f, impossible.getIndexedDistance(), EPSILON);
        assertEquals(1, impossible.getAssignment().getReceivedPackCount());
        assertEquals(PrlState.ACCUMULATING, impossible.getAssignment().getState());

        PrlConveyor assigned = prl("assigned", 1.8f, 0.015f, 0.100f, 1.8f);
        assigned.assign(new PrlAssignmentPlan("assigned", "bag-a", 2));
        List<LinearLaneEntrySnapshot> assignedBefore = assigned.getLaneEntries();
        assertFalse(assigned.requestInfeedSpaceFor(null));
        assertFalse(assigned.requestInfeedSpaceFor(pack("wrong", "bag-b", 0.030f)));
        assertEquals(PrlState.ASSIGNED, assigned.getAssignment().getState());
        assertEquals(0, assigned.getAssignment().getReceivedPackCount());
        assertEquals(assignedBefore, assigned.getLaneEntries());

        PrlConveyor idle = prl("idle", 1.8f, 0.015f, 0.100f, 1.8f);
        assertFalse(idle.requestInfeedSpaceFor(pack("idle-pack", "bag-a", 0.030f)));
        assertEquals(PrlState.IDLE, idle.getAssignment().getState());
        assertEquals(0, idle.getAssignment().getReceivedPackCount());

        PrlConveyor ready = prl("ready", 1.8f, 0.015f, 0f, 1.8f);
        ready.assign(new PrlAssignmentPlan("ready", "bag-a", 1));
        Pack readyPack = pack("ready-pack", "bag-a", 0.030f);
        ready.acceptPack(readyPack);
        assertFalse(ready.requestInfeedSpaceFor(readyPack));
        assertEquals(PrlState.READY_TO_RELEASE, ready.getAssignment().getState());
        assertEquals(1, ready.getAssignment().getReceivedPackCount());
        assertSame(readyPack, ready.getPacks().getFirst());

        PrlConveyor releasing = prl("releasing", 1.8f, 0.015f, 0f, 1.8f);
        releasing.assign(new PrlAssignmentPlan("releasing", "bag-a", 1));
        Pack releasingPack = pack("releasing-pack", "bag-a", 0.030f);
        releasing.acceptPack(releasingPack);
        releasing.releaseGroup();
        assertFalse(releasing.requestInfeedSpaceFor(releasingPack));
        assertEquals(PrlState.RELEASING, releasing.getAssignment().getState());
        assertEquals(1, releasing.getAssignment().getReceivedPackCount());
        assertSame(releasingPack, releasing.getPacks().getFirst());

        PrlConveyor oversized = prl("oversized", 0.200f, 0.015f, 0f, 1.8f);
        oversized.assign(new PrlAssignmentPlan("oversized", "bag-a", 1));
        assertFalse(oversized.requestInfeedSpaceFor(pack("oversized-pack", "bag-a", 0.201f)));
        assertEquals(PrlState.ASSIGNED, oversized.getAssignment().getState());
        assertEquals(0, oversized.getAssignment().getReceivedPackCount());

        PrlConveyor zeroSpeedBlocked = prl("zero-blocked", 0.200f, 0.015f, 0.100f, 0f);
        zeroSpeedBlocked.assign(new PrlAssignmentPlan("zero-blocked", "bag-a", 2));
        Pack zeroSpeedFirst = pack("zero-first", "bag-a", 0.030f);
        zeroSpeedBlocked.acceptPack(zeroSpeedFirst);
        zeroSpeedBlocked.update(1d);
        List<LinearLaneEntrySnapshot> zeroSpeedBefore = zeroSpeedBlocked.getLaneEntries();
        assertFalse(zeroSpeedBlocked.requestInfeedSpaceFor(pack("zero-long", "bag-a", 0.174f)));
        assertEquals(zeroSpeedBefore, zeroSpeedBlocked.getLaneEntries());
        assertEquals(1, zeroSpeedBlocked.getAssignment().getReceivedPackCount());
        assertEquals(0f, zeroSpeedBlocked.getIndexedDistance(), EPSILON);

        PrlConveyor zeroSpeedAvailable = prl("zero-available", 1.8f, 0.015f, 0.100f, 0f);
        zeroSpeedAvailable.assign(new PrlAssignmentPlan("zero-available", "bag-a", 2));
        List<LinearLaneEntrySnapshot> zeroAvailableBefore = zeroSpeedAvailable.getLaneEntries();
        assertTrue(zeroSpeedAvailable.requestInfeedSpaceFor(pack("zero-available-pack", "bag-a", 0.020f)));
        assertEquals(zeroAvailableBefore, zeroSpeedAvailable.getLaneEntries());
        assertEquals(0f, zeroSpeedAvailable.getIndexedDistance(), EPSILON);
        assertEquals(PrlState.ASSIGNED, zeroSpeedAvailable.getAssignment().getState());
    }

    private static float positionOf(PrlConveyor prl, Pack pack) {
        return prl.getLaneEntries().stream()
                .filter(entry -> entry.pack() == pack)
                .findFirst()
                .orElseThrow()
                .frontDistance();
    }

    private static PrlConveyor prl(String id, float laneLength, float gap, float index, float speed) {
        return new PrlConveyor(id, index, new ConveyorOccupancyModel(laneLength, gap, 0f), speed);
    }

    private static Pack pack(String id, String correlation, float length) {
        return new Pack(id, correlation, new PackDimensions(length, 0.050f, 0.040f));
    }
}
