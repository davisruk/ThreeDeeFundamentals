package online.davisfamily.warehouse.sim.dsp.av02;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRecord;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRole;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteIdentity;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteSource;

class Av02AllocationDomainTest {

    @Test
    void shouldExposeExactlyOsrAndAv02PhysicalSources() {
        assertEquals(
                List.of(OperationalPhysicalToteSource.OSR, OperationalPhysicalToteSource.AV02),
                List.of(OperationalPhysicalToteSource.values()));
    }

    @Test
    void shouldValidateSourceNeutralPhysicalIdentity() {
        OperationalPhysicalToteIdentity av02 = av02Identity("av02-000001", "empty-1", 1, " 104 ", 3);
        OperationalPhysicalToteIdentity osr = new OperationalPhysicalToteIdentity(
                OperationalPhysicalToteSource.OSR,
                id("osr-1"),
                new OrderSheetKey("full-pack-1", 1),
                OrderType.FULL_PACK,
                "108",
                PhysicalToteRole.INBOUND_PACK,
                4);

        assertEquals("104", av02.serviceCentreId());
        assertEquals(OrderType.EMPTY, av02.orderType());
        assertEquals(PhysicalToteRole.PRE_P2P, av02.physicalToteRole());
        assertEquals(OrderType.FULL_PACK, osr.orderType());
        assertEquals(PhysicalToteRole.INBOUND_PACK, osr.physicalToteRole());

        assertThrows(IllegalArgumentException.class, () -> new OperationalPhysicalToteIdentity(
                OperationalPhysicalToteSource.OSR, id("osr-empty"), new OrderSheetKey("empty", 1),
                OrderType.EMPTY, "104", PhysicalToteRole.INBOUND_PACK, 0));
        assertThrows(IllegalArgumentException.class, () -> new OperationalPhysicalToteIdentity(
                OperationalPhysicalToteSource.AV02, id("av02-full"), new OrderSheetKey("full", 1),
                OrderType.FULL_PACK, "104", PhysicalToteRole.PRE_P2P, 0));
        assertThrows(IllegalArgumentException.class, () -> new OperationalPhysicalToteIdentity(
                OperationalPhysicalToteSource.AV02, id("av02-outbound"), new OrderSheetKey("empty", 1),
                OrderType.EMPTY, "104", PhysicalToteRole.OUTBOUND_BAG, 0));
        assertThrows(IllegalArgumentException.class, () -> new OperationalPhysicalToteIdentity(
                OperationalPhysicalToteSource.AV02, id("av02-negative"), new OrderSheetKey("empty", 1),
                OrderType.EMPTY, "104", PhysicalToteRole.PRE_P2P, -1));
    }

    @Test
    void shouldRetainMatchingAv02IdentityAndPreP2pRecord() {
        OperationalPhysicalToteIdentity identity = av02Identity(
                "av02-000001", "empty-1", 1, "104", 0);
        PhysicalToteRecord record = PhysicalToteRecord.preP2p(identity.physicalToteId());

        Av02AllocatedTote allocatedTote = new Av02AllocatedTote(identity, record);

        assertSame(identity, allocatedTote.identity());
        assertSame(record, allocatedTote.physicalTote());
        assertEquals(identity.physicalToteId(), allocatedTote.physicalToteId());
        assertEquals(identity.orderSheetKey(), allocatedTote.orderSheetKey());
        assertEquals("104", allocatedTote.serviceCentreId());

        assertThrows(IllegalArgumentException.class,
                () -> new Av02AllocatedTote(identity, PhysicalToteRecord.preP2p(id("different"))));
    }

    @Test
    void shouldExposeValidatedImmutableInventorySnapshotInInsertionOrder() {
        Av02AllocatedTote first = allocatedTote("av02-000001", "empty-1", 0);
        Av02AllocatedTote second = allocatedTote("av02-000002", "empty-2", 1);
        ArrayList<Av02AllocatedTote> source = new ArrayList<>(List.of(first, second));

        Av02InventorySnapshot snapshot = new Av02InventorySnapshot(3, source, List.of());
        source.clear();

        assertEquals(List.of(first, second), snapshot.waitingTotes());
        assertEquals(2, snapshot.occupancy());
        assertEquals(1, snapshot.remainingCapacity());
        assertFalse(snapshot.full());
        assertEquals(first, snapshot.findWaiting(first.physicalToteId()).orElseThrow());
        assertEquals(second, snapshot.findWaiting(second.orderSheetKey()).orElseThrow());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.waitingTotes().clear());

        assertTrue(new Av02InventorySnapshot(2, List.of(first, second), List.of()).full());
        assertThrows(IllegalArgumentException.class,
                () -> new Av02InventorySnapshot(0, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new Av02InventorySnapshot(1, List.of(first, second), List.of()));
        assertEquals(
                List.of(second, first),
                new Av02InventorySnapshot(2, List.of(second, first), List.of()).waitingTotes());
        assertThrows(IllegalArgumentException.class,
                () -> new Av02InventorySnapshot(2, List.of(first), List.of(first)));
        assertThrows(IllegalArgumentException.class,
                () -> new Av02InventorySnapshot(2, List.of(first),
                        List.of(allocatedTote("av02-000003", "empty-1", 2))));
    }

    @Test
    void shouldUsePositiveBaselineCapacityAndStableMonotonicIds() {
        assertEquals(1, Av02AllocationConfig.productionBaseline().capacity());
        assertThrows(IllegalArgumentException.class, () -> new Av02AllocationConfig(0));

        DeterministicAv02PhysicalToteIdAllocator allocator =
                new DeterministicAv02PhysicalToteIdAllocator();
        assertEquals("av02-000001", allocator.nextPhysicalToteId().value());
        assertEquals("av02-000002", allocator.nextPhysicalToteId().value());
        assertEquals("av02-000003", allocator.nextPhysicalToteId().value());
    }

    private static Av02AllocatedTote allocatedTote(
            String physicalToteId,
            String orderId,
            long sourceSequenceNumber) {
        OperationalPhysicalToteIdentity identity = av02Identity(
                physicalToteId, orderId, 1, "104", sourceSequenceNumber);
        return new Av02AllocatedTote(identity, PhysicalToteRecord.preP2p(identity.physicalToteId()));
    }

    private static OperationalPhysicalToteIdentity av02Identity(
            String physicalToteId,
            String orderId,
            int sheetNumber,
            String serviceCentreId,
            long sourceSequenceNumber) {
        return new OperationalPhysicalToteIdentity(
                OperationalPhysicalToteSource.AV02,
                id(physicalToteId),
                new OrderSheetKey(orderId, sheetNumber),
                OrderType.EMPTY,
                serviceCentreId,
                PhysicalToteRole.PRE_P2P,
                sourceSequenceNumber);
    }

    private static PhysicalToteId id(String value) {
        return new PhysicalToteId(value);
    }
}
