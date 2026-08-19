package online.davisfamily.warehouse.sim.dsp.osr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

class OsrMultiManifestInventoryTest {
    private static final OrderSheetKey SHARED_SHEET = new OrderSheetKey("associated-order", 1);

    @Test
    void shouldCountEachPhysicalManifestForOneLogicalSheet() {
        InboundToteManifest first = manifest("physical-tote-1", "line-1", 1);
        InboundToteManifest second = manifest("physical-tote-2", "line-2", 2);
        OsrPhysicalInventory inventory = inventory(first, second);

        OsrInventorySnapshot snapshot = inventory.snapshot();

        assertEquals(2, snapshot.occupancy());
        assertEquals(List.of(first, second), snapshot.storedTotesFor(SHARED_SHEET));
        assertEquals(2, snapshot.occupancyByOrderType().get(OrderType.ASSOCIATED));
    }

    @Test
    void shouldKeepRemainingManifestStoredAfterSiblingDeparts() {
        InboundToteManifest first = manifest("physical-tote-1", "line-1", 1);
        InboundToteManifest second = manifest("physical-tote-2", "line-2", 2);
        OsrPhysicalInventory inventory = inventory(first, second);

        inventory.recordDeparture(first.physicalToteId());
        OsrInventorySnapshot snapshot = inventory.snapshot();

        assertEquals(1, snapshot.occupancy());
        assertEquals(List.of(second), snapshot.storedTotesFor(SHARED_SHEET));
        assertEquals(List.of(first), snapshot.departedTotes());
        assertTrue(snapshot.hasDeparted(first.physicalToteId()));
        assertTrue(snapshot.contains(second.physicalToteId()));
        assertEquals(SHARED_SHEET, snapshot.departedTotes().getFirst().orderSheetKey());
        assertEquals(SHARED_SHEET, snapshot.storedTotes().getFirst().orderSheetKey());
    }

    @Test
    void shouldNeverUseLogicalSheetIdentityAsPhysicalInventoryIdentity() {
        InboundToteManifest first = manifest("physical-tote-1", "line-1", 1);
        InboundToteManifest second = manifest("physical-tote-2", "line-2", 2);
        OsrPhysicalInventory inventory = inventory(first, second);
        PhysicalToteId logicalOrderAsPhysicalId = new PhysicalToteId(SHARED_SHEET.orderId());

        assertNotEquals(logicalOrderAsPhysicalId, first.physicalToteId());
        assertNotEquals(logicalOrderAsPhysicalId, second.physicalToteId());
        assertFalse(inventory.snapshot().contains(logicalOrderAsPhysicalId));
        assertEquals(
                List.of(SHARED_SHEET, SHARED_SHEET),
                inventory.snapshot().storedTotes().stream()
                        .map(InboundToteManifest::orderSheetKey)
                        .toList());
        assertEquals(
                List.of(first.physicalToteId(), second.physicalToteId()),
                inventory.snapshot().storedTotes().stream()
                        .map(InboundToteManifest::physicalToteId)
                        .toList());
    }

    private static OsrPhysicalInventory inventory(InboundToteManifest... manifests) {
        OsrPhysicalInventory inventory = new OsrPhysicalInventory(
                new OsrInventoryConfig(4, List.of("104")));
        inventory.storeAll(List.of(manifests));
        return inventory;
    }

    private static InboundToteManifest manifest(
            String physicalToteId,
            String lineReference,
            long sequenceNumber) {
        DspOrderItem item = new DspOrderItem(
                lineReference,
                "product-" + lineReference,
                1,
                "pharmacy-1",
                "patient-1",
                "prescription-1",
                DspOrderLineType.FULL_PACK,
                SHARED_SHEET.orderId(),
                1,
                1);
        return new InboundToteManifest(
                new PhysicalToteId(physicalToteId),
                SHARED_SHEET,
                OrderType.ASSOCIATED,
                "104",
                List.of(item),
                sequenceNumber);
    }
}
