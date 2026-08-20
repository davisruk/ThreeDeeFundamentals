package online.davisfamily.warehouse.sim.dsp.supply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.sim.dsp.io.DspDatasetAssembler;
import online.davisfamily.warehouse.sim.dsp.io.LoadedDspData;
import online.davisfamily.warehouse.sim.dsp.io.TwelveNFieldJson;
import online.davisfamily.warehouse.sim.dsp.io.TwelveNHeaderJson;
import online.davisfamily.warehouse.sim.dsp.io.TwelveNMessageJson;
import online.davisfamily.warehouse.sim.dsp.io.TwelveNMessageKindMapper;
import online.davisfamily.warehouse.sim.dsp.io.TwelveNOrderDetailJson;
import online.davisfamily.warehouse.sim.dsp.io.TwelveNOrderLineJson;
import online.davisfamily.warehouse.sim.dsp.io.TwelveNOrderMapper;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderValidator;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.ProductMasterRecord;
import online.davisfamily.warehouse.sim.dsp.osr.OsrBootstrapState;
import online.davisfamily.warehouse.sim.dsp.osr.OsrInventoryConfig;
import online.davisfamily.warehouse.sim.dsp.osr.OsrInventorySnapshot;
import online.davisfamily.warehouse.sim.dsp.osr.OsrInventoryBootstrapFactory;
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClock;
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClockConfig;
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClockController;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;

class DspRateLimitedServiceCentreSupplyScenarioTest {

    private static final LocalDate OPERATING_DATE = LocalDate.of(2026, 8, 19);
    private static final PhysicalToteId PRELOADED_TOTE = new PhysicalToteId("pre-adapted");

    @Test
    void shouldRunPriorityOrderedRateLimitedSupplyFromLoadedData() {
        LoadedDspData data = data();
        Runtime runtime = runtime(data);

        assertEquals(
                List.of("104", "108"),
                runtime.plan().batches().stream()
                        .map(ServiceCentreSupplyBatch::serviceCentreId)
                        .toList());
        assertEquals(999, order(data, "adapted-pre").orderPriority());
        assertEquals(998, order(data, "adapted-later").orderPriority());
        assertEquals(1, runtime.bootstrap().inventorySnapshot().occupancy());

        runtime.world().update(1d);
        assertEquals(
                ServiceCentreAuthorizationState.HELD_UPSTREAM,
                serviceCentre(runtime.supplyController().snapshot(), "108")
                        .authorizationState());

        runtime.bootstrap().inventory().recordDeparture(PRELOADED_TOTE);
        runtime.world().update(0d);

        DspSupplySnapshot authorized = runtime.supplyController().snapshot();
        assertEquals("108", authorized.activeInboundServiceCentreId().orElseThrow());
        assertTrue(authorized.authorizedEmptyOrderSheetKeys().stream()
                .anyMatch(key -> key.orderId().equals("empty-later")));
        assertEquals(Optional.of(Duration.ofSeconds(4)),
                authorized.nextPhysicalAdmissionElapsedTime());

        runtime.world().update(3d);
        assertStored(runtime, "later-adapted");
        runtime.world().update(3d);
        assertStored(runtime, "later-full");
        assertEquals(
                List.of("later-adapted", "later-full"),
                runtime.bootstrap().inventorySnapshot().storedTotes().stream()
                        .map(manifest -> manifest.physicalToteId().value())
                        .toList());

        runtime.world().update(3d);
        assertEquals(
                PhysicalToteSupplyState.BLOCKED_BY_OSR_CAPACITY,
                physicalTote(runtime.supplyController().snapshot(), "later-associated-1").state());

        runtime.bootstrap().inventory().recordDeparture(new PhysicalToteId("later-adapted"));
        runtime.world().update(0d);
        assertStored(runtime, "later-associated-1");
        assertEquals(Optional.of(Duration.ofSeconds(13)),
                runtime.supplyController().snapshot().nextPhysicalAdmissionElapsedTime());

        runtime.bootstrap().inventory().recordDeparture(new PhysicalToteId("later-full"));
        runtime.world().update(3d);
        DspSupplySnapshot complete = runtime.supplyController().snapshot();
        assertStored(runtime, "later-associated-2");
        assertEquals(
                ServiceCentreAuthorizationState.SUPPLY_COMPLETE,
                serviceCentre(complete, "108").authorizationState());
        assertTrue(complete.activeInboundServiceCentreId().isEmpty());
        assertTrue(complete.nextPhysicalAdmissionElapsedTime().isEmpty());
        assertTrue(runtime.bootstrap().inventorySnapshot().occupancy()
                <= runtime.bootstrap().inventorySnapshot().capacity());
    }

    @Test
    void shouldRestartSupplyDeterministicallyWhenRuntimeIsReconstructed() {
        LoadedDspData data = data();
        Runtime initial = runtime(data);
        DspSupplySnapshot expectedSupply = initial.supplyController().snapshot();
        OsrInventorySnapshot expectedInventory = initial.bootstrap().inventorySnapshot();

        Runtime progressed = runtime(data);
        progressed.world().update(1d);
        progressed.bootstrap().inventory().recordDeparture(PRELOADED_TOTE);
        progressed.world().update(0d);
        assertEquals(
                ServiceCentreAuthorizationState.AUTHORIZED,
                serviceCentre(progressed.supplyController().snapshot(), "108")
                        .authorizationState());

        Runtime reset = runtime(data);
        assertEquals(expectedInventory, reset.bootstrap().inventorySnapshot());
        assertEquals(expectedSupply, reset.supplyController().snapshot());
        assertEquals(
                ServiceCentreAuthorizationState.HELD_UPSTREAM,
                serviceCentre(reset.supplyController().snapshot(), "108")
                        .authorizationState());
        assertEquals(
                DspOperationalClockConfig.productionBaseline(OPERATING_DATE)
                        .normalStartDateTime(),
                reset.clockController().snapshot().businessDateTime());
    }

    private static Runtime runtime(LoadedDspData data) {
        OsrInventoryConfig inventoryConfig = new OsrInventoryConfig(2, List.of("104"));
        OsrBootstrapState bootstrap = new OsrInventoryBootstrapFactory().create(
                data,
                inventoryConfig);
        DspServiceCentreSupplyPlan plan = new DspServiceCentreSupplyPlanFactory().create(
                data,
                inventoryConfig);
        DspServiceCentreSupplyCoordinator coordinator = new DspServiceCentreSupplyCoordinator(
                plan,
                new ServiceCentreSupplyConfig(0),
                FixedIntervalInboundToteArrivalPolicy.peak(),
                bootstrap);
        DspOperationalClockController clockController = new DspOperationalClockController(
                new DspOperationalClock(
                        DspOperationalClockConfig.productionBaseline(OPERATING_DATE)));
        DspServiceCentreSupplyController supplyController = new DspServiceCentreSupplyController(
                clockController::snapshot,
                coordinator);
        SimulationWorld world = new SimulationWorld();
        world.addController(clockController);
        world.addController(supplyController);
        return new Runtime(
                data,
                bootstrap,
                plan,
                clockController,
                supplyController,
                world);
    }

    private static LoadedDspData data() {
        TwelveNMessageKindMapper kindMapper = new TwelveNMessageKindMapper();
        List<TwelveNMessageJson> messages = List.of(
                physicalMessage(
                        "adapted-pre",
                        "02",
                        "pre-adapted",
                        "104",
                        999,
                        line("pre-line", "02", "product-adapted", "adapted-pre")),
                emptyMessage("empty-pre", "104", 999, "product-empty-pre"),
                physicalMessage(
                        "adapted-later",
                        "02",
                        "later-adapted",
                        "108",
                        998,
                        line("adapted-line", "02", "product-adapted", "adapted-later")),
                physicalMessage(
                        "full-later",
                        "05",
                        "later-full",
                        "108",
                        998,
                        line("full-line", "05", "product-full", "full-later")),
                physicalMessage(
                        "associated-later",
                        "04",
                        "later-associated-1",
                        "108",
                        998,
                        line("associated-line-1", "05", "product-associated-1", "associated-later")),
                physicalMessage(
                        "associated-later",
                        "04",
                        "later-associated-2",
                        "108",
                        998,
                        line("associated-line-2", "05", "product-associated-2", "associated-later")),
                emptyMessage("empty-later", "108", 998, "product-empty-later"));
        List<ProductMasterRecord> products = List.of(
                product("product-adapted"),
                product("product-full"),
                product("product-associated-1"),
                product("product-associated-2"),
                product("product-empty-pre"),
                product("product-empty-later"));
        return new DspDatasetAssembler(
                kindMapper,
                new TwelveNOrderMapper(kindMapper),
                new DspOrderValidator())
                .assemble(products, messages);
    }

    private static TwelveNMessageJson physicalMessage(
            String orderId,
            String toteType,
            String physicalToteId,
            String serviceCentreId,
            int orderPriority,
            TwelveNOrderLineJson line) {
        return message(
                orderId,
                toteType,
                field(physicalToteId),
                serviceCentreId,
                orderPriority,
                line);
    }

    private static TwelveNMessageJson emptyMessage(
            String orderId,
            String serviceCentreId,
            int orderPriority,
            String productId) {
        return message(
                orderId,
                "03",
                null,
                serviceCentreId,
                orderPriority,
                line("line-" + orderId, "05", productId, orderId));
    }

    private static TwelveNMessageJson message(
            String orderId,
            String toteType,
            TwelveNFieldJson transportContainer,
            String serviceCentreId,
            int orderPriority,
            TwelveNOrderLineJson line) {
        return new TwelveNMessageJson(
                new TwelveNHeaderJson(null, null, orderId, "001"),
                field(toteType),
                transportContainer,
                field(Integer.toString(orderPriority)),
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
            String referenceOrderId) {
        return new TwelveNOrderLineJson(
                lineReference,
                lineType,
                "pharmacy-1",
                "patient-1",
                "prescription-" + lineReference,
                productId,
                "0001",
                "0000",
                referenceOrderId,
                "001",
                "0001");
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

    private static NotionalToteOrder order(LoadedDspData data, String orderId) {
        return data.orders().stream()
                .filter(order -> order.orderId().equals(orderId))
                .findFirst()
                .orElseThrow();
    }

    private static void assertStored(Runtime runtime, String physicalToteId) {
        assertTrue(runtime.bootstrap().inventorySnapshot()
                .contains(new PhysicalToteId(physicalToteId)));
    }

    private static ServiceCentreSupplySnapshot serviceCentre(
            DspSupplySnapshot snapshot,
            String serviceCentreId) {
        return snapshot.serviceCentres().stream()
                .filter(candidate -> candidate.serviceCentreId().equals(serviceCentreId))
                .findFirst()
                .orElseThrow();
    }

    private static PhysicalToteSupplySnapshot physicalTote(
            DspSupplySnapshot snapshot,
            String physicalToteId) {
        return snapshot.serviceCentres().stream()
                .flatMap(serviceCentre -> serviceCentre.physicalTotes().stream())
                .filter(tote -> tote.physicalToteId().value().equals(physicalToteId))
                .findFirst()
                .orElseThrow();
    }

    private record Runtime(
            LoadedDspData data,
            OsrBootstrapState bootstrap,
            DspServiceCentreSupplyPlan plan,
            DspOperationalClockController clockController,
            DspServiceCentreSupplyController supplyController,
            SimulationWorld world) {
    }

}
