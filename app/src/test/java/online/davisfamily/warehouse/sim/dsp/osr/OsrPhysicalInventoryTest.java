package online.davisfamily.warehouse.sim.dsp.osr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

class OsrPhysicalInventoryTest {

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
