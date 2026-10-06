package online.davisfamily.warehouse.sim.totebag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.totebag.conveyor.LinearConveyorLane;
import online.davisfamily.warehouse.sim.totebag.conveyor.LinearLaneEntrySnapshot;
import online.davisfamily.warehouse.sim.totebag.pack.Pack;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;

class LinearConveyorLaneTest {
    private static final float EPSILON = 0.000001f;

    @Test
    void shouldEnforceWholePackGapAtInfeedAndAtArbitraryInsertion() {
        LinearConveyorLane infeedLane = new LinearConveyorLane("infeed", 1f, 0.015f, 1f);
        Pack infeedResident = pack("infeed-resident", 0.100f);
        infeedLane.acceptAtFrontDistance(infeedResident, 0.300f);

        assertFalse(infeedLane.canAcceptAtFrontDistance(pack("too-close", 0.080f), 0.220f));
        assertTrue(infeedLane.canAcceptAtFrontDistance(pack("gap-boundary", 0.080f), 0.185f));

        Pack ahead = pack("ahead", 0.100f);
        Pack behind = pack("behind", 0.100f);
        LinearConveyorLane insertionLane = laneWithTwoResidents(ahead, behind);
        Pack inserted = pack("inserted", 0.070f);
        List<LinearLaneEntrySnapshot> before = insertionLane.getEntrySnapshots();

        assertTrue(insertionLane.canAcceptAtFrontDistance(inserted, 0.385f));
        assertFalse(insertionLane.canAcceptAtFrontDistance(inserted, 0.386f));
        assertFalse(insertionLane.canAcceptAtFrontDistance(inserted, 0.390f));
        assertFalse(insertionLane.canAcceptAtFrontDistance(inserted, 0.380f));
        assertEquals(before, insertionLane.getEntrySnapshots());
        assertSame(ahead, insertionLane.getEntrySnapshots().get(0).pack());
        assertSame(behind, insertionLane.getEntrySnapshots().get(1).pack());

        insertionLane.acceptAtFrontDistance(inserted, 0.385f);
        List<LinearLaneEntrySnapshot> insertedEntries = insertionLane.getEntrySnapshots();
        assertSame(ahead, insertedEntries.get(0).pack());
        assertSame(inserted, insertedEntries.get(1).pack());
        assertSame(behind, insertedEntries.get(2).pack());

        LinearConveyorLane movingInfeedLane = new LinearConveyorLane("moving-infeed", 1f, 0.015f, 1f);
        movingInfeedLane.acceptAtFrontDistance(pack("moving-resident", 0.100f), 0.190f);
        Pack incoming = pack("moving-incoming", 0.080f);
        assertFalse(movingInfeedLane.canAcceptAtInfeed(incoming));
        movingInfeedLane.advanceDistance(0.005f);
        assertTrue(movingInfeedLane.canAcceptAtInfeed(incoming));

        LinearConveyorLane emptyLane = new LinearConveyorLane("empty", 0.100f, 0.015f, 1f);
        assertFalse(emptyLane.canAcceptAtInfeed(null));
        assertFalse(emptyLane.canAcceptAtInfeed(pack("oversized", 0.101f)));
    }

    @Test
    void shouldComputeAdditionalInfeedTravelWithoutMutation() {
        LinearConveyorLane lane = new LinearConveyorLane("travel", 1f, 0.015f, 1f);
        Pack resident = pack("resident", 0.030f);
        Pack incoming = pack("incoming", 0.174f);
        lane.acceptAtFrontDistance(resident, 0.130f);
        List<LinearLaneEntrySnapshot> before = lane.getEntrySnapshots();

        assertEquals(0.089f, lane.additionalTravelRequiredForInfeed(incoming), EPSILON);
        assertEquals(0.089f, lane.additionalTravelRequiredForInfeed(incoming), EPSILON);
        assertFalse(lane.canAcceptAtInfeed(incoming));
        assertEquals(before, lane.getEntrySnapshots());

        assertEquals(0.089f, lane.advanceDistance(0.089f), EPSILON);
        assertTrue(lane.canAcceptAtInfeed(incoming));
        assertEquals(0f, lane.additionalTravelRequiredForInfeed(incoming), EPSILON);

        LinearConveyorLane emptyLane = new LinearConveyorLane("empty-fit", 1f, 0.015f, 1f);
        assertEquals(0f, emptyLane.additionalTravelRequiredForInfeed(pack("empty", 0.174f)), EPSILON);
        assertEquals(Float.POSITIVE_INFINITY, emptyLane.additionalTravelRequiredForInfeed(null));
        assertEquals(Float.POSITIVE_INFINITY,
                emptyLane.additionalTravelRequiredForInfeed(pack("too-large", 1.001f)));

        LinearConveyorLane shortLane = new LinearConveyorLane("short", 0.200f, 0.015f, 1f);
        shortLane.acceptAtFrontDistance(pack("short-resident", 0.030f), 0.130f);
        assertEquals(Float.POSITIVE_INFINITY, shortLane.additionalTravelRequiredForInfeed(incoming));
        assertEquals(0f, shortLane.additionalTravelRequiredForInfeed(pack("already-fits", 0.080f)), EPSILON);
    }

    @Test
    void shouldPreserveOrderBoundsAndGapsThroughAdvanceAndRemoval() {
        LinearConveyorLane lane = new LinearConveyorLane("ordered", 1f, 0.015f, 1f);
        Pack first = pack("first", 0.030f);
        Pack second = pack("second", 0.174f);
        Pack third = pack("third", 0.050f);
        lane.acceptAtFrontDistance(first, 0.300f);
        lane.acceptAtFrontDistance(second, 0.255f);
        lane.acceptAtFrontDistance(third, 0.066f);
        assertLaneGeometry(lane, List.of(first, second, third));

        for (int step = 0; step < 100 && lane.getEntrySnapshots().getFirst().frontDistance() < 1f; step++) {
            assertTrue(lane.advance(0.05d) > 0f);
            assertLaneGeometry(lane, List.of(first, second, third));
        }
        assertEquals(1f, lane.getEntrySnapshots().getFirst().frontDistance(), EPSILON);
        assertEquals(0f, lane.advance(0.05d), EPSILON);
        assertSame(first, lane.pollLeadingPackAtOutfeed().orElseThrow());
        assertEquals(0.045f, lane.advance(0.05d), EPSILON);
        assertLaneGeometry(lane, List.of(second, third));
    }

    @Test
    void shouldUseOnlyLaneEndpointsForRepeatedIntakeQueries() {
        QueryGuardLane lane = new QueryGuardLane("guarded", 10f, 0.015f, 1f);
        List<CountingPack> residents = new ArrayList<>();
        for (int ordinal = 1; ordinal <= 20; ordinal++) {
            CountingPack resident = new CountingPack("resident-" + ordinal, 0.030f);
            residents.add(resident);
            lane.acceptAtFrontDistance(resident, 0.200f * ordinal);
        }
        CountingPack incoming = new CountingPack("incoming", 0.174f);
        List<LinearLaneEntrySnapshot> before = lane.getEntrySnapshots();
        List<Pack> beforePacks = lane.getPacks();
        residents.forEach(CountingPack::resetDimensionReads);
        incoming.resetDimensionReads();

        lane.forbidSnapshotQueries = true;
        for (int i = 0; i < 100; i++) {
            assertFalse(lane.canAcceptAtInfeed(incoming));
            assertEquals(0.019f, lane.additionalTravelRequiredForInfeed(incoming), EPSILON);
        }
        lane.forbidSnapshotQueries = false;

        assertEquals(200, incoming.dimensionReads);
        assertEquals(200, residents.getFirst().dimensionReads);
        for (int i = 1; i < residents.size() - 1; i++) {
            assertEquals(0, residents.get(i).dimensionReads);
        }
        assertEquals(0, residents.getLast().dimensionReads);
        assertEquals(before, lane.getEntrySnapshots());
        List<Pack> afterPacks = lane.getPacks();
        assertEquals(beforePacks.size(), afterPacks.size());
        for (int i = 0; i < beforePacks.size(); i++) {
            assertSame(beforePacks.get(i), afterPacks.get(i));
        }
    }

    private static LinearConveyorLane laneWithTwoResidents(Pack ahead, Pack behind) {
        LinearConveyorLane lane = new LinearConveyorLane("two-residents", 1f, 0.015f, 1f);
        lane.acceptAtFrontDistance(ahead, 0.500f);
        lane.acceptAtFrontDistance(behind, 0.300f);
        return lane;
    }

    private static void assertLaneGeometry(LinearConveyorLane lane, List<Pack> expectedOrder) {
        List<LinearLaneEntrySnapshot> entries = lane.getEntrySnapshots();
        assertEquals(expectedOrder.size(), entries.size());
        for (int i = 0; i < entries.size(); i++) {
            LinearLaneEntrySnapshot entry = entries.get(i);
            assertSame(expectedOrder.get(i), entry.pack());
            float length = entry.pack().getDimensions().length();
            assertTrue(entry.frontDistance() >= length - EPSILON);
            assertTrue(entry.frontDistance() <= lane.getUsableLength() + EPSILON);
            if (i > 0) {
                LinearLaneEntrySnapshot ahead = entries.get(i - 1);
                assertTrue(entry.frontDistance() <= ahead.rearDistance() - lane.getMinimumGap() + EPSILON);
            }
        }
    }

    private static Pack pack(String id, float length) {
        return new Pack(id, "bag-a", dimensions(length));
    }

    private static PackDimensions dimensions(float length) {
        return new PackDimensions(length, 0.050f, 0.040f);
    }

    private static final class CountingPack extends Pack {
        private int dimensionReads;

        private CountingPack(String id, float length) {
            super(id, "bag-a", dimensions(length));
        }

        @Override
        public PackDimensions getDimensions() {
            dimensionReads++;
            return super.getDimensions();
        }

        private void resetDimensionReads() {
            dimensionReads = 0;
        }
    }

    private static final class QueryGuardLane extends LinearConveyorLane {
        private boolean forbidSnapshotQueries;

        private QueryGuardLane(String id, float length, float gap, float speed) {
            super(id, length, gap, speed);
        }

        @Override
        public List<Pack> getPacks() {
            rejectSnapshotQuery();
            return super.getPacks();
        }

        @Override
        public List<LinearLaneEntrySnapshot> getEntrySnapshots() {
            rejectSnapshotQuery();
            return super.getEntrySnapshots();
        }

        @Override
        public java.util.Optional<LinearLaneEntrySnapshot> getLeadingEntry() {
            rejectSnapshotQuery();
            return super.getLeadingEntry();
        }

        @Override
        public java.util.Optional<LinearLaneEntrySnapshot> getTrailingEntry() {
            rejectSnapshotQuery();
            return super.getTrailingEntry();
        }

        @Override
        public java.util.Optional<Float> getFrontDistanceFor(Pack pack) {
            rejectSnapshotQuery();
            return super.getFrontDistanceFor(pack);
        }

        private void rejectSnapshotQuery() {
            if (forbidSnapshotQueries) {
                throw new AssertionError("Intake query must use lane endpoints directly");
            }
        }
    }
}
