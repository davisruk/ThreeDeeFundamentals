package online.davisfamily.warehouse.sim.dsp.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

class InboundToteManifestTest {

    @Test
    void shouldRetainPhysicalToteAndLogicalSheetAsDifferentIdentities() {
        InboundToteManifest manifest = manifest(
                "90864875",
                new OrderSheetKey("TOTE0007168406", 22),
                OrderType.ADAPTED,
                List.of(item("line-1")),
                7);

        assertEquals("90864875", manifest.physicalToteId().value());
        assertEquals(new OrderSheetKey("TOTE0007168406", 22), manifest.orderSheetKey());
        assertNotEquals(manifest.orderSheetKey().orderId(), manifest.physicalToteId().value());
        assertEquals("116", manifest.serviceCentreId());
        assertEquals(7, manifest.sourceSequenceNumber());
    }

    @Test
    void shouldRejectEmptyManifestAndDuplicateLineReferences() {
        assertThrows(IllegalArgumentException.class,
                () -> manifest("tote-1", new OrderSheetKey("order-1", 1), OrderType.FULL_PACK, List.of(), 0));
        assertThrows(IllegalArgumentException.class,
                () -> manifest(
                        "tote-1",
                        new OrderSheetKey("order-1", 1),
                        OrderType.FULL_PACK,
                        List.of(item("line-1"), item("line-1")),
                        0));

        ArrayList<DspOrderItem> itemsWithNull = new ArrayList<>();
        itemsWithNull.add(null);
        assertThrows(IllegalArgumentException.class,
                () -> manifest(
                        "tote-1",
                        new OrderSheetKey("order-1", 1),
                        OrderType.FULL_PACK,
                        itemsWithNull,
                        0));
    }

    @Test
    void shouldRejectEmptyOrderTypeManifest() {
        assertThrows(IllegalArgumentException.class,
                () -> manifest(
                        "tote-1",
                        new OrderSheetKey("order-1", 1),
                        OrderType.EMPTY,
                        List.of(item("line-1")),
                        0));
    }

    @Test
    void shouldReplaceItemsWithoutChangingManifestIdentity() {
        InboundToteManifest original = manifest(
                "tote-1",
                new OrderSheetKey("order-1", 1),
                OrderType.ASSOCIATED,
                List.of(item("line-1"), item("line-2")),
                3);

        InboundToteManifest retained = original.withItems(List.of(item("line-2")));

        assertEquals(original.physicalToteId(), retained.physicalToteId());
        assertEquals(original.orderSheetKey(), retained.orderSheetKey());
        assertEquals(original.orderType(), retained.orderType());
        assertEquals(original.serviceCentreId(), retained.serviceCentreId());
        assertEquals(original.sourceSequenceNumber(), retained.sourceSequenceNumber());
        assertEquals(List.of("line-2"), retained.items().stream().map(DspOrderItem::lineReference).toList());
    }

    @Test
    void shouldCatalogSeveralPhysicalTotesForOneLogicalSheet() {
        OrderSheetKey orderSheetKey = new OrderSheetKey("order-1", 1);
        InboundToteManifest first = manifest(
                "tote-1", orderSheetKey, OrderType.ASSOCIATED, List.of(item("line-1")), 0);
        InboundToteManifest second = manifest(
                "tote-2", orderSheetKey, OrderType.ASSOCIATED, List.of(item("line-2")), 1);

        InboundToteManifestCatalog catalog = new InboundToteManifestCatalog(List.of(first, second));

        assertEquals(List.of(first, second), catalog.manifests());
        assertEquals(List.of(first, second), catalog.manifestsFor(orderSheetKey));
        assertEquals(second, catalog.findByPhysicalToteId(second.physicalToteId()).orElseThrow());
    }

    @Test
    void shouldRejectDuplicatePhysicalToteIds() {
        InboundToteManifest first = manifest(
                "tote-1",
                new OrderSheetKey("order-1", 1),
                OrderType.FULL_PACK,
                List.of(item("line-1")),
                0);
        InboundToteManifest second = manifest(
                "tote-1",
                new OrderSheetKey("order-2", 1),
                OrderType.FULL_PACK,
                List.of(item("line-2")),
                1);

        assertThrows(IllegalArgumentException.class,
                () -> new InboundToteManifestCatalog(List.of(first, second)));
    }

    @Test
    void shouldDefensivelyCopyManifestCollections() {
        ArrayList<DspOrderItem> sourceItems = new ArrayList<>();
        sourceItems.add(item("line-1"));
        InboundToteManifest manifest = manifest(
                "tote-1",
                new OrderSheetKey("order-1", 1),
                OrderType.FULL_PACK,
                sourceItems,
                0);
        ArrayList<InboundToteManifest> sourceManifests = new ArrayList<>();
        sourceManifests.add(manifest);
        InboundToteManifestCatalog catalog = new InboundToteManifestCatalog(sourceManifests);

        sourceItems.add(item("line-2"));
        sourceManifests.clear();

        assertEquals(1, manifest.items().size());
        assertEquals(List.of(manifest), catalog.manifests());
        assertThrows(UnsupportedOperationException.class, () -> manifest.items().clear());
        assertThrows(UnsupportedOperationException.class, () -> catalog.manifests().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> catalog.manifestsFor(manifest.orderSheetKey()).clear());
    }

    private static InboundToteManifest manifest(
            String physicalToteId,
            OrderSheetKey orderSheetKey,
            OrderType orderType,
            List<DspOrderItem> items,
            long sequenceNumber) {
        return new InboundToteManifest(
                new PhysicalToteId(physicalToteId),
                orderSheetKey,
                orderType,
                " 116 ",
                items,
                sequenceNumber);
    }

    private static DspOrderItem item(String lineReference) {
        return new DspOrderItem(lineReference, "product-1", 1);
    }
}
