package online.davisfamily.warehouse.sim.dsp.osr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

class OsrPhysicalInventoryTest {

    @Test
    void shouldCountOnlySuccessfulDeparturesByCentreAndKeepHistoryDuringMigration() {
        OsrPhysicalInventory inventory = inventory(2);
        InboundToteManifest first = manifest("first", "order-1", 0);
        InboundToteManifest input = manifest("second", "order-2", 1);
        InboundToteManifest second = new InboundToteManifest(input.physicalToteId(), input.orderSheetKey(),
                input.orderType(), "108", input.items(), input.sourceSequenceNumber());
        InboundToteManifest third = manifest("third", "order-3", 2);
        assertEquals(0L, inventory.snapshot().departedToteCount());
        assertTrue(inventory.snapshot().departedToteCountsByServiceCentre().isEmpty());
        inventory.storeAll(List.of(first, second));
        OsrInventorySnapshot stored = inventory.snapshot();
        assertEquals(0L, stored.departedToteCount());
        assertThrows(IllegalStateException.class, () -> inventory.store(third));
        assertSame(stored, inventory.snapshot());

        assertSame(second, inventory.recordDeparture(second.physicalToteId()));
        OsrInventorySnapshot departed = inventory.snapshot();
        assertEquals(1L, departed.departedToteCount());
        assertEquals(Map.of("108", 1L), departed.departedToteCountsByServiceCentre());
        assertEquals(List.of(second), departed.departedTotes());
        assertEquals(0L, stored.departedToteCount());
        assertThrows(UnsupportedOperationException.class, () -> departed.departedToteCountsByServiceCentre().clear());
        assertThrows(IllegalStateException.class, () -> inventory.recordDeparture(second.physicalToteId()));
        assertThrows(IllegalStateException.class, () -> inventory.recordDeparture(new PhysicalToteId("unknown")));
        assertSame(departed, inventory.snapshot());
        inventory.store(third);
        assertEquals(1L, inventory.snapshot().departedToteCount());
        inventory.recordDeparture(first.physicalToteId());
        inventory.recordDeparture(third.physicalToteId());
        assertEquals(3L, inventory.snapshot().departedToteCount());
        assertEquals(Map.of("108", 1L, "104", 2L), inventory.snapshot().departedToteCountsByServiceCentre());
        assertEquals(List.of(second, first, third), inventory.snapshot().departedTotes());
        assertEquals(1L, departed.departedToteCount());
        assertSame(inventory.snapshot(), inventory.snapshot());
    }

    @Test
    void shouldDeriveFixtureCountsOnceAndAcceptIndependentLongCountersInSnapshots() {
        InboundToteManifest first = manifest("fixture", "fixture-order", 0);
        OsrInventorySnapshot fixture = new OsrInventorySnapshot(1, List.of(), List.of(first));
        assertEquals(1L, fixture.departedToteCount());
        assertEquals(Map.of("104", 1L), fixture.departedToteCountsByServiceCentre());
        Map<String, Long> counts = new LinkedHashMap<>(Map.of("104", 3_000_000_000L));
        OsrInventorySnapshot scalar = new OsrInventorySnapshot(1, List.of(), List.of(), 3_000_000_000L, counts);
        counts.clear();
        assertEquals(3_000_000_000L, scalar.departedToteCount());
        assertEquals(Map.of("104", 3_000_000_000L), scalar.departedToteCountsByServiceCentre());
        assertTrue(scalar.departedTotes().isEmpty());
        OsrInventorySnapshot equivalent = new OsrInventorySnapshot(1, List.of(), List.of(),
                3_000_000_000L, Map.of("104", 3_000_000_000L));
        assertEquals(scalar, equivalent);
        assertEquals(scalar.hashCode(), equivalent.hashCode());
        assertNotEquals(scalar, new OsrInventorySnapshot(1, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new OsrInventorySnapshot(1, List.of(), List.of(), -1, Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new OsrInventorySnapshot(1, List.of(), List.of(), 1, Map.of("104", -1L)));
        assertThrows(IllegalArgumentException.class,
                () -> new OsrInventorySnapshot(1, List.of(), List.of(), 1, Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new OsrInventorySnapshot(1, List.of(), List.of(), 0, null));
    }

    @Test
    void shouldStorePhysicalTotesAndPreserveAdmissionOrder() {
        OsrPhysicalInventory inventory = inventory(3);
        InboundToteManifest first = manifest("tote-1", "order-1", 1);
        InboundToteManifest second = manifest("tote-2", "order-2", 2);

        inventory.store(first);
        inventory.store(second);

        assertEquals(List.of(first, second), inventory.snapshot().storedTotes());
        assertEquals(2, inventory.snapshot().occupancy());
    }

    @Test
    void shouldStoreBatchAtomicallyWithinCapacity() {
        OsrPhysicalInventory inventory = inventory(3);
        InboundToteManifest first = manifest("tote-1", "order-1", 1);
        InboundToteManifest second = manifest("tote-2", "order-2", 2);

        inventory.storeAll(List.of(first, second));

        assertEquals(List.of(first, second), inventory.snapshot().storedTotes());
        assertEquals(1, inventory.snapshot().remainingCapacity());
    }

    @Test
    void shouldLeaveInventoryUnchangedWhenBatchWouldOverflowOrDuplicate() {
        OsrPhysicalInventory inventory = inventory(2);
        InboundToteManifest existing = manifest("tote-1", "order-1", 1);
        InboundToteManifest second = manifest("tote-2", "order-2", 2);
        InboundToteManifest third = manifest("tote-3", "order-3", 3);
        inventory.store(existing);

        assertThrows(IllegalStateException.class,
                () -> inventory.storeAll(List.of(second, third)));
        assertEquals(List.of(existing), inventory.snapshot().storedTotes());

        InboundToteManifest duplicateSecond = manifest("tote-2", "order-4", 4);
        assertThrows(IllegalArgumentException.class,
                () -> inventory.storeAll(List.of(second, duplicateSecond)));
        assertEquals(List.of(existing), inventory.snapshot().storedTotes());

        assertThrows(IllegalStateException.class,
                () -> inventory.storeAll(List.of(second, existing)));
        assertEquals(List.of(existing), inventory.snapshot().storedTotes());
    }

    @Test
    void shouldRecordPhysicalDepartureAndFreeOneCapacitySlot() {
        OsrPhysicalInventory inventory = inventory(2);
        InboundToteManifest first = manifest("tote-1", "order-1", 1);
        InboundToteManifest second = manifest("tote-2", "order-2", 2);
        inventory.storeAll(List.of(first, second));

        InboundToteManifest departed = inventory.recordDeparture(first.physicalToteId());

        assertEquals(first, departed);
        assertEquals(List.of(second), inventory.snapshot().storedTotes());
        assertEquals(List.of(first), inventory.snapshot().departedTotes());
        assertEquals(1, inventory.snapshot().remainingCapacity());
        assertFalse(inventory.snapshot().contains(first.physicalToteId()));
        assertTrue(inventory.snapshot().hasDeparted(first.physicalToteId()));
    }

    @Test
    void shouldRejectUnknownDepartureOrReadmissionAfterDeparture() {
        OsrPhysicalInventory inventory = inventory(2);
        InboundToteManifest manifest = manifest("tote-1", "order-1", 1);
        inventory.store(manifest);
        inventory.recordDeparture(manifest.physicalToteId());

        assertThrows(IllegalArgumentException.class, () -> inventory.recordDeparture(null));
        assertThrows(IllegalStateException.class,
                () -> inventory.recordDeparture(new PhysicalToteId("unknown")));
        assertThrows(IllegalStateException.class, () -> inventory.recordDeparture(manifest.physicalToteId()));
        assertThrows(IllegalStateException.class, () -> inventory.store(manifest));
        assertTrue(inventory.snapshot().storedTotes().isEmpty());
        assertEquals(List.of(manifest), inventory.snapshot().departedTotes());
    }

    @Test
    void shouldReturnImmutablePointInTimeSnapshots() {
        OsrPhysicalInventory inventory = inventory(3);
        InboundToteManifest first = manifest("tote-1", "order-1", 1);
        InboundToteManifest second = manifest("tote-2", "order-2", 2);
        inventory.store(first);
        OsrInventorySnapshot beforeSecondStore = inventory.snapshot();

        inventory.store(second);
        OsrInventorySnapshot afterSecondStore = inventory.snapshot();
        inventory.recordDeparture(first.physicalToteId());

        assertEquals(List.of(first), beforeSecondStore.storedTotes());
        assertEquals(List.of(first, second), afterSecondStore.storedTotes());
        assertTrue(beforeSecondStore.departedTotes().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> beforeSecondStore.storedTotes().clear());

        ArrayList<InboundToteManifest> withNull = new ArrayList<>();
        withNull.add(null);
        assertThrows(IllegalArgumentException.class, () -> inventory.storeAll(null));
        assertThrows(IllegalArgumentException.class, () -> inventory.storeAll(withNull));
        assertThrows(IllegalArgumentException.class, () -> new OsrPhysicalInventory(null));
    }

    @Test
    void shouldReuseSnapshotUntilARealInventoryMutation() {
        OsrPhysicalInventory inventory = inventory(2);
        InboundToteManifest first = manifest("tote-1", "order-1", 1);
        InboundToteManifest second = manifest("tote-2", "order-2", 2);
        InboundToteManifest third = manifest("tote-3", "order-3", 3);

        OsrInventorySnapshot initial = inventory.snapshot();
        assertSame(initial, inventory.snapshot());

        inventory.storeAll(List.of());
        assertSame(initial, inventory.snapshot());

        inventory.store(first);
        OsrInventorySnapshot afterFirstStore = inventory.snapshot();
        assertNotSame(initial, afterFirstStore);
        assertSame(afterFirstStore, inventory.snapshot());

        assertThrows(IllegalStateException.class,
                () -> inventory.storeAll(List.of(second, third)));
        assertSame(afterFirstStore, inventory.snapshot());

        inventory.store(second);
        OsrInventorySnapshot afterSecondStore = inventory.snapshot();
        assertNotSame(afterFirstStore, afterSecondStore);

        assertThrows(IllegalStateException.class,
                () -> inventory.store(second));
        assertSame(afterSecondStore, inventory.snapshot());

        inventory.recordDeparture(first.physicalToteId());
        OsrInventorySnapshot afterDeparture = inventory.snapshot();
        assertNotSame(afterSecondStore, afterDeparture);
        assertSame(afterDeparture, inventory.snapshot());

        assertThrows(IllegalStateException.class,
                () -> inventory.recordDeparture(first.physicalToteId()));
        assertSame(afterDeparture, inventory.snapshot());
    }

    private static OsrPhysicalInventory inventory(int capacity) {
        return new OsrPhysicalInventory(new OsrInventoryConfig(capacity, List.of("104", "108")));
    }

    private static InboundToteManifest manifest(
            String physicalToteId,
            String orderId,
            long sequenceNumber) {
        String lineReference = "line-" + physicalToteId;
        DspOrderItem item = new DspOrderItem(
                lineReference,
                "product-" + physicalToteId,
                1,
                "pharmacy-1",
                "patient-1",
                "prescription-" + physicalToteId,
                DspOrderLineType.FULL_PACK,
                orderId,
                1,
                1);
        return new InboundToteManifest(
                new PhysicalToteId(physicalToteId),
                new OrderSheetKey(orderId, 1),
                OrderType.FULL_PACK,
                "104",
                List.of(item),
                sequenceNumber);
    }
}
