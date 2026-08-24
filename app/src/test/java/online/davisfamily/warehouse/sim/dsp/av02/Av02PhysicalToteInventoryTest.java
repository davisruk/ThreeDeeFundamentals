package online.davisfamily.warehouse.sim.dsp.av02;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRecord;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRole;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteIdentity;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteSource;

class Av02PhysicalToteInventoryTest {

    @Test
    void shouldStoreAllocatedTotesInFifoOrderAndExposeLookups() {
        Av02PhysicalToteInventory inventory = inventory(3);
        Av02AllocatedTote first = tote("av02-000001", "empty-1", 0);
        Av02AllocatedTote second = tote("av02-000002", "empty-2", 1);

        inventory.store(first);
        inventory.store(second);

        assertEquals(3, inventory.capacity());
        assertEquals(2, inventory.occupancy());
        assertEquals(1, inventory.remainingCapacity());
        assertFalse(inventory.full());
        assertEquals(first, inventory.head().orElseThrow());
        assertEquals(first, inventory.findWaiting(first.physicalToteId()).orElseThrow());
        assertEquals(second, inventory.findWaiting(second.orderSheetKey()).orElseThrow());
        assertEquals(List.of(first, second), inventory.snapshot().waitingTotes());
    }

    @Test
    void shouldRecordHeadDepartureAndFreeCapacity() {
        Av02PhysicalToteInventory inventory = inventory(2);
        Av02AllocatedTote first = tote("av02-000001", "empty-1", 0);
        Av02AllocatedTote second = tote("av02-000002", "empty-2", 1);
        inventory.store(first);
        inventory.store(second);

        Av02AllocatedTote departed = inventory.recordDeparture(first.physicalToteId());

        assertEquals(first, departed);
        assertEquals(second, inventory.head().orElseThrow());
        assertEquals(List.of(second), inventory.snapshot().waitingTotes());
        assertEquals(List.of(first), inventory.snapshot().departedTotes());
        assertEquals(1, inventory.remainingCapacity());
    }

    @Test
    void shouldRejectOverflowAndDuplicateIdentityWithoutMutation() {
        Av02PhysicalToteInventory inventory = inventory(1);
        Av02AllocatedTote existing = tote("av02-000001", "empty-1", 0);
        Av02AllocatedTote overflow = tote("av02-000002", "empty-2", 1);
        inventory.store(existing);
        Av02InventorySnapshot fullSnapshot = inventory.snapshot();

        assertThrows(IllegalStateException.class, () -> inventory.store(overflow));
        assertEquals(fullSnapshot, inventory.snapshot());

        inventory.recordDeparture(existing.physicalToteId());
        Av02InventorySnapshot departedSnapshot = inventory.snapshot();
        assertThrows(IllegalStateException.class, () -> inventory.store(existing));
        assertThrows(IllegalStateException.class,
                () -> inventory.store(tote("av02-000003", "empty-1", 2)));
        assertEquals(departedSnapshot, inventory.snapshot());
    }

    @Test
    void shouldRejectUnknownNonHeadOrRepeatedDepartureWithoutMutation() {
        Av02PhysicalToteInventory inventory = inventory(2);
        Av02AllocatedTote first = tote("av02-000001", "empty-1", 0);
        Av02AllocatedTote second = tote("av02-000002", "empty-2", 1);
        inventory.store(first);
        inventory.store(second);
        Av02InventorySnapshot before = inventory.snapshot();

        assertThrows(IllegalArgumentException.class, () -> inventory.recordDeparture(null));
        assertThrows(IllegalStateException.class,
                () -> inventory.recordDeparture(new PhysicalToteId("unknown")));
        assertThrows(IllegalStateException.class,
                () -> inventory.recordDeparture(second.physicalToteId()));
        assertEquals(before, inventory.snapshot());

        inventory.recordDeparture(first.physicalToteId());
        Av02InventorySnapshot after = inventory.snapshot();
        assertThrows(IllegalStateException.class,
                () -> inventory.recordDeparture(first.physicalToteId()));
        assertEquals(after, inventory.snapshot());
    }

    @Test
    void shouldReturnImmutablePointInTimeSnapshots() {
        Av02PhysicalToteInventory inventory = inventory(2);
        Av02AllocatedTote first = tote("av02-000001", "empty-1", 0);
        Av02AllocatedTote second = tote("av02-000002", "empty-2", 1);
        inventory.store(first);
        Av02InventorySnapshot beforeSecondStore = inventory.snapshot();

        inventory.store(second);
        Av02InventorySnapshot afterSecondStore = inventory.snapshot();
        inventory.recordDeparture(first.physicalToteId());

        assertEquals(List.of(first), beforeSecondStore.waitingTotes());
        assertTrue(beforeSecondStore.departedTotes().isEmpty());
        assertEquals(List.of(first, second), afterSecondStore.waitingTotes());
        assertTrue(afterSecondStore.departedTotes().isEmpty());
        assertThrows(UnsupportedOperationException.class,
                () -> beforeSecondStore.waitingTotes().clear());
        assertThrows(IllegalArgumentException.class, () -> new Av02PhysicalToteInventory(null));
        assertThrows(IllegalArgumentException.class, () -> inventory.store(null));
        assertThrows(IllegalArgumentException.class, () -> inventory.findWaiting((PhysicalToteId) null));
        assertThrows(IllegalArgumentException.class, () -> inventory.findWaiting((OrderSheetKey) null));
    }

    @Test
    void shouldRejectNonEmptyOrNonPreP2pAllocationBeforeInventoryMutation() {
        Av02PhysicalToteInventory inventory = inventory(1);

        assertThrows(IllegalArgumentException.class, () -> new OperationalPhysicalToteIdentity(
                OperationalPhysicalToteSource.AV02,
                new PhysicalToteId("av02-000001"),
                new OrderSheetKey("full-pack-1", 1),
                OrderType.FULL_PACK,
                "104",
                PhysicalToteRole.PRE_P2P,
                0));
        assertThrows(IllegalArgumentException.class, () -> new OperationalPhysicalToteIdentity(
                OperationalPhysicalToteSource.AV02,
                new PhysicalToteId("av02-000001"),
                new OrderSheetKey("empty-1", 1),
                OrderType.EMPTY,
                "104",
                PhysicalToteRole.OUTBOUND_BAG,
                0));
        assertTrue(inventory.snapshot().waitingTotes().isEmpty());
    }

    private static Av02PhysicalToteInventory inventory(int capacity) {
        return new Av02PhysicalToteInventory(new Av02AllocationConfig(capacity));
    }

    private static Av02AllocatedTote tote(
            String physicalToteId,
            String orderId,
            long sourceSequenceNumber) {
        PhysicalToteId id = new PhysicalToteId(physicalToteId);
        OperationalPhysicalToteIdentity identity = new OperationalPhysicalToteIdentity(
                OperationalPhysicalToteSource.AV02,
                id,
                new OrderSheetKey(orderId, 1),
                OrderType.EMPTY,
                "104",
                PhysicalToteRole.PRE_P2P,
                sourceSequenceNumber);
        return new Av02AllocatedTote(identity, PhysicalToteRecord.preP2p(id));
    }
}
