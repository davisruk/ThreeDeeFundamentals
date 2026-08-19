package online.davisfamily.warehouse.sim.dsp.osr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.io.DspDatasetLoadReport;
import online.davisfamily.warehouse.sim.dsp.io.LoadedDspData;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

class OsrInventoryBootstrapFactoryTest {
    private final OsrInventoryBootstrapFactory factory = new OsrInventoryBootstrapFactory();

    @Test
    void shouldPreloadAllPhysicalTotesForLetchworthAndSwansea() {
        InboundToteManifest adapted = manifest("tote-a", "adapted-1", OrderType.ADAPTED, "104", 1);
        InboundToteManifest fullPack = manifest("tote-f", "full-1", OrderType.FULL_PACK, "104", 2);
        InboundToteManifest associated = manifest("tote-s", "associated-1", OrderType.ASSOCIATED, "108", 3);
        InboundToteManifest later = manifest("tote-l", "later-1", OrderType.FULL_PACK, "116", 4);

        OsrBootstrapState state = factory.create(
                data(List.of(), List.of(adapted, fullPack, associated, later)),
                OsrInventoryConfig.productionBaseline());

        assertEquals(List.of(adapted, fullPack, associated), state.inventorySnapshot().storedTotes());
        assertEquals(3, state.inventorySnapshot().occupancy());
    }

    @Test
    void shouldPreserveDatasetManifestOrderAcrossPreloadedServiceCentres() {
        InboundToteManifest first = manifest("tote-1", "order-1", OrderType.FULL_PACK, "108", 1);
        InboundToteManifest ignored = manifest("tote-2", "order-2", OrderType.FULL_PACK, "116", 2);
        InboundToteManifest second = manifest("tote-3", "order-3", OrderType.ADAPTED, "104", 3);
        InboundToteManifest third = manifest("tote-4", "order-4", OrderType.ASSOCIATED, "108", 4);

        OsrBootstrapState state = factory.create(
                data(List.of(), List.of(first, ignored, second, third)),
                OsrInventoryConfig.productionBaseline());

        assertEquals(List.of(first, second, third), state.inventorySnapshot().storedTotes());
    }

    @Test
    void shouldIncludeSeveralPhysicalTotesForOneLogicalSheet() {
        OrderSheetKey sharedSheet = new OrderSheetKey("associated-1", 1);
        InboundToteManifest first = manifest("tote-1", sharedSheet, OrderType.ASSOCIATED, "104", 1);
        InboundToteManifest second = manifest("tote-2", sharedSheet, OrderType.ASSOCIATED, "104", 2);

        OsrBootstrapState state = factory.create(
                data(List.of(), List.of(first, second)),
                OsrInventoryConfig.productionBaseline());

        assertEquals(2, state.inventorySnapshot().occupancy());
        assertEquals(List.of(first, second), state.inventorySnapshot().storedTotesFor(sharedSheet));
    }

    @Test
    void shouldAuthorizeEmptyOrdersWithoutIncreasingOccupancy() {
        NotionalToteOrder firstEmpty = order("empty-1", OrderType.EMPTY, "104", 1);
        NotionalToteOrder secondEmpty = order("empty-2", OrderType.EMPTY, "108", 2);
        InboundToteManifest physical = manifest("tote-1", "full-1", OrderType.FULL_PACK, "104", 3);

        OsrBootstrapState state = factory.create(
                data(List.of(firstEmpty, secondEmpty), List.of(physical)),
                OsrInventoryConfig.productionBaseline());

        assertEquals(1, state.inventorySnapshot().occupancy());
        assertEquals(
                List.of(firstEmpty.orderSheetKey(), secondEmpty.orderSheetKey()),
                List.copyOf(state.authorizedEmptyOrderSheetKeys()));
        assertTrue(state.inventorySnapshot().storedTotes().stream()
                .noneMatch(manifest -> manifest.orderType() == OrderType.EMPTY));
    }

    @Test
    void shouldLeaveLaterServiceCentresOutsideInitialInventory() {
        InboundToteManifest preload = manifest("tote-1", "full-1", OrderType.FULL_PACK, "104", 1);
        InboundToteManifest later = manifest("tote-2", "full-2", OrderType.FULL_PACK, "116", 2);
        NotionalToteOrder laterEmpty = order("empty-later", OrderType.EMPTY, "116", 3);

        OsrBootstrapState state = factory.create(
                data(List.of(laterEmpty), List.of(preload, later)),
                OsrInventoryConfig.productionBaseline());

        assertEquals(List.of(preload), state.inventorySnapshot().storedTotes());
        assertTrue(state.authorizedEmptyOrderSheetKeys().isEmpty());
        assertTrue(state.inventorySnapshot().findStored(later.physicalToteId()).isEmpty());
    }

    @Test
    void shouldFailClearlyWhenInitialPreloadExceedsCapacity() {
        InboundToteManifest first = manifest("tote-1", "order-1", OrderType.FULL_PACK, "104", 1);
        InboundToteManifest second = manifest("tote-2", "order-2", OrderType.FULL_PACK, "108", 2);
        LoadedDspData data = data(List.of(), List.of(first, second));

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> factory.create(data, new OsrInventoryConfig(1, List.of("104", "108"))));

        assertTrue(failure.getMessage().contains("selected=2"));
        assertTrue(failure.getMessage().contains("capacity=1"));
        assertThrows(IllegalArgumentException.class,
                () -> factory.create(null, OsrInventoryConfig.productionBaseline()));
        assertThrows(IllegalArgumentException.class, () -> factory.create(data, null));
    }

    private static LoadedDspData data(
            List<NotionalToteOrder> orders,
            List<InboundToteManifest> manifests) {
        return new LoadedDspData(
                List.of(),
                orders,
                List.of(),
                Set.of(),
                Set.of(),
                manifests,
                DspDatasetLoadReport.empty());
    }

    private static NotionalToteOrder order(
            String orderId,
            OrderType orderType,
            String serviceCentreId,
            long sequenceNumber) {
        DspOrderItem item = item("line-" + orderId, orderId);
        return new NotionalToteOrder(
                orderId,
                "notional-" + orderId,
                serviceCentreId,
                1,
                orderType,
                List.of(item),
                sequenceNumber);
    }

    private static InboundToteManifest manifest(
            String physicalToteId,
            String orderId,
            OrderType orderType,
            String serviceCentreId,
            long sequenceNumber) {
        return manifest(
                physicalToteId,
                new OrderSheetKey(orderId, 1),
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
        DspOrderItem item = item("line-" + physicalToteId, orderSheetKey.orderId());
        return new InboundToteManifest(
                new PhysicalToteId(physicalToteId),
                orderSheetKey,
                orderType,
                serviceCentreId,
                List.of(item),
                sequenceNumber);
    }

    private static DspOrderItem item(String lineReference, String referenceOrderId) {
        return new DspOrderItem(
                lineReference,
                "product-" + lineReference,
                1,
                "pharmacy-1",
                "patient-1",
                "prescription-" + lineReference,
                DspOrderLineType.FULL_PACK,
                referenceOrderId,
                1,
                1);
    }
}
