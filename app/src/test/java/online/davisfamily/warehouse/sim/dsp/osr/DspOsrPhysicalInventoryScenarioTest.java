package online.davisfamily.warehouse.sim.dsp.osr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.io.DspDatasetAssembler;
import online.davisfamily.warehouse.sim.dsp.io.LoadedDspData;
import online.davisfamily.warehouse.sim.dsp.io.TwelveNFieldJson;
import online.davisfamily.warehouse.sim.dsp.io.TwelveNHeaderJson;
import online.davisfamily.warehouse.sim.dsp.io.TwelveNMessageJson;
import online.davisfamily.warehouse.sim.dsp.io.TwelveNMessageKindMapper;
import online.davisfamily.warehouse.sim.dsp.io.TwelveNOrderDetailJson;
import online.davisfamily.warehouse.sim.dsp.io.TwelveNOrderLineJson;
import online.davisfamily.warehouse.sim.dsp.io.TwelveNOrderMapper;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderValidator;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.ProductMasterRecord;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;

class DspOsrPhysicalInventoryScenarioTest {
    private static final OrderSheetKey ASSOCIATED_SHEET = new OrderSheetKey("associated-104", 1);
    private static final PhysicalToteId ASSOCIATED_TOTE_ONE = new PhysicalToteId("tote-associated-1");
    private static final PhysicalToteId ASSOCIATED_TOTE_TWO = new PhysicalToteId("tote-associated-2");
    private static final List<PhysicalToteId> EXPECTED_PRELOAD_IDS = List.of(
            new PhysicalToteId("tote-adapted-104"),
            new PhysicalToteId("tote-full-108"),
            ASSOCIATED_TOTE_ONE,
            new PhysicalToteId("tote-full-104"),
            ASSOCIATED_TOTE_TWO);

    @Test
    void shouldBootstrapConfiguredServiceCentresFromAssembledDataset() {
        Scenario scenario = scenario();

        assertEquals(
                EXPECTED_PRELOAD_IDS,
                scenario.bootstrap().inventorySnapshot().storedTotes().stream()
                        .map(InboundToteManifest::physicalToteId)
                        .toList());
        assertEquals(
                List.of(new OrderSheetKey("empty-104", 1), new OrderSheetKey("empty-108", 1)),
                List.copyOf(scenario.bootstrap().authorizedEmptyOrderSheetKeys()));
        assertFalse(scenario.bootstrap().inventorySnapshot().contains(
                new PhysicalToteId("tote-full-116")));
        assertFalse(scenario.bootstrap().authorizedEmptyOrderSheetKeys().contains(
                new OrderSheetKey("empty-116", 1)));
        assertTrue(scenario.bootstrap().inventorySnapshot().departedTotes().isEmpty());
    }

    @Test
    void shouldCountPhysicalManifestsWithoutCountingEmptyOrdersOrPacks() {
        Scenario scenario = scenario();
        OsrInventorySnapshot snapshot = scenario.bootstrap().inventorySnapshot();
        int preloadedPackQuantity = snapshot.storedTotes().stream()
                .flatMap(manifest -> manifest.items().stream())
                .mapToInt(item -> item.quantity())
                .sum();

        assertEquals(5, snapshot.occupancy());
        assertEquals(1195, snapshot.remainingCapacity());
        assertEquals(7, preloadedPackQuantity);
        assertEquals(2, scenario.bootstrap().authorizedEmptyOrderSheetKeys().size());
        assertEquals(Map.of("104", 4, "108", 1), snapshot.occupancyByServiceCentre());
        assertEquals(List.of("104", "108"), List.copyOf(snapshot.occupancyByServiceCentre().keySet()));
        assertEquals(
                Map.of(OrderType.ADAPTED, 1, OrderType.FULL_PACK, 2, OrderType.ASSOCIATED, 2),
                snapshot.occupancyByOrderType());
        assertEquals(
                List.of(OrderType.ADAPTED, OrderType.FULL_PACK, OrderType.ASSOCIATED),
                List.copyOf(snapshot.occupancyByOrderType().keySet()));
    }

    @Test
    void shouldPreserveLogicalAndPhysicalIdentityAcrossInitialInventory() {
        Scenario scenario = scenario();
        List<InboundToteManifest> associatedManifests = scenario.bootstrap()
                .inventorySnapshot()
                .storedTotesFor(ASSOCIATED_SHEET);

        assertEquals(2, associatedManifests.size());
        assertEquals(
                List.of(ASSOCIATED_TOTE_ONE, ASSOCIATED_TOTE_TWO),
                associatedManifests.stream().map(InboundToteManifest::physicalToteId).toList());
        assertEquals(
                List.of(ASSOCIATED_SHEET, ASSOCIATED_SHEET),
                associatedManifests.stream().map(InboundToteManifest::orderSheetKey).toList());
        assertTrue(associatedManifests.stream().allMatch(
                manifest -> manifest.orderType() == OrderType.ASSOCIATED));
        assertTrue(associatedManifests.stream().noneMatch(
                manifest -> manifest.physicalToteId().value().equals(ASSOCIATED_SHEET.orderId())));
        assertNotEquals(ASSOCIATED_TOTE_ONE, ASSOCIATED_TOTE_TWO);
        assertEquals(EXPECTED_PRELOAD_IDS,
                scenario.data().inboundToteManifests().stream()
                        .filter(manifest -> manifest.serviceCentreId().equals("104")
                                || manifest.serviceCentreId().equals("108"))
                        .map(InboundToteManifest::physicalToteId)
                        .toList());
    }

    @Test
    void shouldRejectAnOverCapacityInitialDatasetAtomically() {
        Scenario scenario = scenario();

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> new OsrInventoryBootstrapFactory().create(
                        scenario.data(),
                        new OsrInventoryConfig(4, List.of("104", "108"))));

        assertTrue(failure.getMessage().contains("selected=5"));
        assertTrue(failure.getMessage().contains("capacity=4"));
        assertEquals(5, scenario.bootstrap().inventorySnapshot().occupancy());
        assertEquals(EXPECTED_PRELOAD_IDS,
                scenario.bootstrap().inventorySnapshot().storedTotes().stream()
                        .map(InboundToteManifest::physicalToteId)
                        .toList());
    }

    private static Scenario scenario() {
        List<TwelveNMessageJson> messages = List.of(
                physicalMessage(
                        "adapted-104", "02", "tote-adapted-104", "104",
                        line("line-adapted", "02", "product-adapted", "associated-104", "0001")),
                physicalMessage(
                        "full-108", "05", "tote-full-108", "108",
                        line("line-full-108", "05", "product-full-108", "full-108", "0001")),
                physicalMessage(
                        ASSOCIATED_SHEET.orderId(), "04", ASSOCIATED_TOTE_ONE.value(), "104",
                        line("line-associated-1", "05", "product-associated-1",
                                ASSOCIATED_SHEET.orderId(), "0001")),
                physicalMessage(
                        "full-116", "05", "tote-full-116", "116",
                        line("line-full-116", "05", "product-full-116", "full-116", "0001")),
                physicalMessage(
                        "full-104", "05", "tote-full-104", "104",
                        line("line-full-104", "05", "product-full-104", "full-104", "0003")),
                physicalMessage(
                        ASSOCIATED_SHEET.orderId(), "04", ASSOCIATED_TOTE_TWO.value(), "104",
                        line("line-associated-2", "05", "product-associated-2",
                                ASSOCIATED_SHEET.orderId(), "0001")),
                emptyMessage("empty-104", "104", "product-empty-104"),
                emptyMessage("empty-108", "108", "product-empty-108"),
                emptyMessage("empty-116", "116", "product-empty-116"));
        List<ProductMasterRecord> products = List.of(
                product("product-adapted"),
                product("product-full-108"),
                product("product-associated-1"),
                product("product-full-116"),
                product("product-full-104"),
                product("product-associated-2"),
                product("product-empty-104"),
                product("product-empty-108"),
                product("product-empty-116"));
        TwelveNMessageKindMapper kindMapper = new TwelveNMessageKindMapper();
        LoadedDspData data = new DspDatasetAssembler(
                kindMapper,
                new TwelveNOrderMapper(kindMapper),
                new DspOrderValidator())
                .assemble(products, messages);
        OsrBootstrapState bootstrap = new OsrInventoryBootstrapFactory().create(
                data, OsrInventoryConfig.productionBaseline());
        return new Scenario(data, bootstrap);
    }

    private static TwelveNMessageJson physicalMessage(
            String orderId,
            String toteType,
            String physicalToteId,
            String serviceCentreId,
            TwelveNOrderLineJson line) {
        return message(orderId, toteType, field(physicalToteId), serviceCentreId, line);
    }

    private static TwelveNMessageJson emptyMessage(
            String orderId,
            String serviceCentreId,
            String productId) {
        return message(
                orderId,
                "03",
                null,
                serviceCentreId,
                line("line-" + orderId, "05", productId, orderId, "0001"));
    }

    private static TwelveNMessageJson message(
            String orderId,
            String toteType,
            TwelveNFieldJson transportContainer,
            String serviceCentreId,
            TwelveNOrderLineJson line) {
        return new TwelveNMessageJson(
                new TwelveNHeaderJson(null, null, orderId, "001"),
                field(toteType),
                transportContainer,
                field("999"),
                null,
                field(serviceCentreId),
                new TwelveNOrderDetailJson(
                        1,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(line)));
    }

    private static TwelveNOrderLineJson line(
            String lineReference,
            String lineType,
            String productId,
            String referenceOrderId,
            String numberOfPacks) {
        return new TwelveNOrderLineJson(
                lineReference,
                lineType,
                "pharmacy-1",
                "patient-1",
                "prescription-" + lineReference,
                productId,
                numberOfPacks,
                "0000",
                referenceOrderId,
                "001",
                numberOfPacks);
    }

    private static TwelveNFieldJson field(String payload) {
        return new TwelveNFieldJson(null, null, payload);
    }

    private static ProductMasterRecord product(String productId) {
        return new ProductMasterRecord(
                productId,
                "Product " + productId,
                Optional.empty(),
                Optional.of(new PackDimensions(0.20f, 0.10f, 0.08f)));
    }

    private record Scenario(
            LoadedDspData data,
            OsrBootstrapState bootstrap) {
    }
}
