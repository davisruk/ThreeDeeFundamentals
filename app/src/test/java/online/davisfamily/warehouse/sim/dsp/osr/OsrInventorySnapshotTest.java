package online.davisfamily.warehouse.sim.dsp.osr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

class OsrInventorySnapshotTest {

    @Test
    void shouldExposeOccupancyCapacityAndPhysicalToteLookups() {
        InboundToteManifest first = manifest("tote-1", "order-1", 1, OrderType.FULL_PACK, "104", 1);
        InboundToteManifest second = manifest("tote-2", "order-2", 1, OrderType.ADAPTED, "108", 2);
        OsrInventorySnapshot snapshot = new OsrInventorySnapshot(3, List.of(first, second), List.of());

        assertEquals(2, snapshot.occupancy());
        assertEquals(1, snapshot.remainingCapacity());
        assertFalse(snapshot.full());
        assertTrue(snapshot.contains(first.physicalToteId()));
        assertEquals(second, snapshot.findStored(second.physicalToteId()).orElseThrow());
        assertFalse(snapshot.contains(new PhysicalToteId("unknown")));
        assertTrue(new OsrInventorySnapshot(2, List.of(first, second), List.of()).full());
    }

    @Test
    void shouldGroupStoredTotesByLogicalSheetServiceCentreAndOrderType() {
        OrderSheetKey sharedSheet = new OrderSheetKey("order-1", 1);
        InboundToteManifest first = manifest("tote-1", sharedSheet, OrderType.ASSOCIATED, "108", 1);
        InboundToteManifest second = manifest("tote-2", sharedSheet, OrderType.ASSOCIATED, "108", 2);
        InboundToteManifest third = manifest("tote-3", "order-2", 1, OrderType.ADAPTED, "104", 3);
        OsrInventorySnapshot snapshot = new OsrInventorySnapshot(
                5, List.of(first, second, third), List.of());

        assertEquals(List.of(first, second), snapshot.storedTotesFor(sharedSheet));
        assertEquals(List.of(first, second), snapshot.storedTotesForServiceCentre(" 108 "));
        assertEquals(List.of("108", "104"), List.copyOf(snapshot.occupancyByServiceCentre().keySet()));
        assertEquals(Map.of("108", 2, "104", 1), snapshot.occupancyByServiceCentre());
        assertEquals(List.of(OrderType.ASSOCIATED, OrderType.ADAPTED),
                List.copyOf(snapshot.occupancyByOrderType().keySet()));
        assertEquals(Map.of(OrderType.ASSOCIATED, 2, OrderType.ADAPTED, 1),
                snapshot.occupancyByOrderType());
    }

    @Test
    void shouldDistinguishStoredAndDepartedPhysicalTotes() {
        InboundToteManifest stored = manifest("tote-1", "order-1", 1, OrderType.FULL_PACK, "104", 1);
        InboundToteManifest departed = manifest("tote-2", "order-2", 1, OrderType.FULL_PACK, "104", 2);
        OsrInventorySnapshot snapshot = new OsrInventorySnapshot(
                2, List.of(stored), List.of(departed));

        assertTrue(snapshot.contains(stored.physicalToteId()));
        assertFalse(snapshot.hasDeparted(stored.physicalToteId()));
        assertFalse(snapshot.contains(departed.physicalToteId()));
        assertTrue(snapshot.hasDeparted(departed.physicalToteId()));
        assertEquals(1, snapshot.occupancy());
    }

    @Test
    void shouldRejectCapacityDuplicateIdentityOrNullContentViolations() {
        InboundToteManifest first = manifest("tote-1", "order-1", 1, OrderType.FULL_PACK, "104", 1);
        InboundToteManifest duplicate = manifest("tote-1", "order-2", 1, OrderType.ADAPTED, "108", 2);
        InboundToteManifest second = manifest("tote-2", "order-2", 1, OrderType.ADAPTED, "108", 2);
        ArrayList<InboundToteManifest> withNull = new ArrayList<>();
        withNull.add(null);

        assertThrows(IllegalArgumentException.class,
                () -> new OsrInventorySnapshot(0, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new OsrInventorySnapshot(1, null, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new OsrInventorySnapshot(1, List.of(), null));
        assertThrows(IllegalArgumentException.class,
                () -> new OsrInventorySnapshot(1, withNull, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new OsrInventorySnapshot(1, List.of(first, second), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new OsrInventorySnapshot(2, List.of(first, duplicate), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new OsrInventorySnapshot(2, List.of(first), List.of(duplicate)));
        assertThrows(IllegalArgumentException.class, () -> new OsrInventorySnapshot(
                2, List.of(first), List.of()).contains(null));
        assertThrows(IllegalArgumentException.class, () -> new OsrInventorySnapshot(
                2, List.of(first), List.of()).storedTotesForServiceCentre(" "));
    }

    @Test
    void shouldExposeImmutableDeterministicCollections() {
        InboundToteManifest first = manifest("tote-1", "order-1", 1, OrderType.FULL_PACK, "108", 1);
        InboundToteManifest second = manifest("tote-2", "order-2", 1, OrderType.ADAPTED, "104", 2);
        ArrayList<InboundToteManifest> source = new ArrayList<>(List.of(first, second));
        OsrInventorySnapshot snapshot = new OsrInventorySnapshot(3, source, List.of());
        source.clear();

        assertEquals(List.of(first, second), snapshot.storedTotes());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.storedTotes().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.storedTotesForServiceCentre("108").clear());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.occupancyByServiceCentre().put("116", 1));
    }

    private static InboundToteManifest manifest(
            String physicalToteId,
            String orderId,
            int sheetNumber,
            OrderType orderType,
            String serviceCentreId,
            long sequenceNumber) {
        return manifest(
                physicalToteId,
                new OrderSheetKey(orderId, sheetNumber),
                orderType,
                serviceCentreId,
                sequenceNumber);
    }

    private static InboundToteManifest manifest(
            String physicalToteId,
            OrderSheetKey orderSheetKey,
            OrderType orderType,
            String serviceCentreId,
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
                orderSheetKey.orderId(),
                1,
                1);
        return new InboundToteManifest(
                new PhysicalToteId(physicalToteId),
                orderSheetKey,
                orderType,
                serviceCentreId,
                List.of(item),
                sequenceNumber);
    }
}
