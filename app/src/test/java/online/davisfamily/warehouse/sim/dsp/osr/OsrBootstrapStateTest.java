package online.davisfamily.warehouse.sim.dsp.osr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

class OsrBootstrapStateTest {

    @Test
    void shouldExposeInventoryAndImmutableAuthorizedEmptySheets() {
        OsrPhysicalInventory inventory = inventory();
        OrderSheetKey second = new OrderSheetKey("empty-2", 1);
        OrderSheetKey first = new OrderSheetKey("empty-1", 1);
        Set<OrderSheetKey> sourceKeys = new LinkedHashSet<>(List.of(second, first));

        OsrBootstrapState state = new OsrBootstrapState(inventory, sourceKeys);
        sourceKeys.clear();

        assertSame(inventory, state.inventory());
        assertEquals(List.of(second, first), List.copyOf(state.authorizedEmptyOrderSheetKeys()));
        assertEquals(inventory.snapshot(), state.inventorySnapshot());
        assertThrows(UnsupportedOperationException.class,
                () -> state.authorizedEmptyOrderSheetKeys().clear());
        assertThrows(IllegalArgumentException.class,
                () -> new OsrBootstrapState(null, Set.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new OsrBootstrapState(inventory, null));

        Set<OrderSheetKey> keysWithNull = new LinkedHashSet<>();
        keysWithNull.add(null);
        assertThrows(IllegalArgumentException.class,
                () -> new OsrBootstrapState(inventory, keysWithNull));
    }

    @Test
    void shouldKeepEmptyAuthorizationSeparateFromPhysicalOccupancy() {
        OsrPhysicalInventory inventory = inventory();
        OrderSheetKey emptySheet = new OrderSheetKey("empty-1", 1);
        OsrBootstrapState state = new OsrBootstrapState(inventory, Set.of(emptySheet));

        assertEquals(0, state.inventorySnapshot().occupancy());
        assertEquals(Set.of(emptySheet), state.authorizedEmptyOrderSheetKeys());

        InboundToteManifest fullPackManifest = manifest();
        inventory.store(fullPackManifest);

        assertEquals(1, state.inventorySnapshot().occupancy());
        assertEquals(List.of(fullPackManifest), state.inventorySnapshot().storedTotes());
        assertEquals(Set.of(emptySheet), state.authorizedEmptyOrderSheetKeys());
    }

    private static OsrPhysicalInventory inventory() {
        return new OsrPhysicalInventory(new OsrInventoryConfig(3, List.of("104", "108")));
    }

    private static InboundToteManifest manifest() {
        OrderSheetKey orderSheetKey = new OrderSheetKey("full-pack-1", 1);
        DspOrderItem item = new DspOrderItem(
                "line-1",
                "product-1",
                1,
                "pharmacy-1",
                "patient-1",
                "prescription-1",
                DspOrderLineType.FULL_PACK,
                orderSheetKey.orderId(),
                1,
                1);
        return new InboundToteManifest(
                new PhysicalToteId("tote-1"),
                orderSheetKey,
                OrderType.FULL_PACK,
                "104",
                List.of(item),
                1);
    }
}
