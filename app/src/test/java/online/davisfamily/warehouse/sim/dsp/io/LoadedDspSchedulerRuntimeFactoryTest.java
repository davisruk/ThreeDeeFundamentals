package online.davisfamily.warehouse.sim.dsp.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.ProductCategory;
import online.davisfamily.warehouse.sim.dsp.model.ProductMasterRecord;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.routing.DspRouteDeriver;
import online.davisfamily.warehouse.sim.dsp.routing.InMemoryProductMasterRepository;
import online.davisfamily.warehouse.sim.dsp.runtime.DspSchedulerRuntimeState;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspOrderStatus;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationCapacity;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationSnapshot;

class LoadedDspSchedulerRuntimeFactoryTest {

    @Test
    void shouldCreateRuntimeStateFromLoadedDispatchOrders() {
        LoadedDspData data = new LoadedDspData(
                List.of(new ProductMasterRecord("9114", ProductCategory.AUTOMATED, false)),
                List.of(dispatchOrder("order-1", OrderType.FULL_PACK, "9114", "0006461", 0L)),
                List.of(),
                Set.of());
        LoadedDspSchedulerRuntimeFactory factory = factoryWithProducts(data.products());

        DspSchedulerRuntimeState runtimeState = factory.createRuntimeState(
                data,
                Map.of(StationType.P2P, openAdmission(StationType.P2P)),
                Optional.of("104"));

        assertEquals(1, runtimeState.snapshot().orderStates().size());
        assertEquals(DspOrderStatus.WAITING, runtimeState.snapshot().orderStates().getFirst().status());
        assertEquals(OrderType.FULL_PACK, runtimeState.snapshot().orderStates().getFirst().order().orderType());
        assertTrue(runtimeState.snapshot().orderStates().getFirst().routeRequirements().requiresP2p());
        assertEquals(Optional.of("104"), runtimeState.snapshot().activeServiceCentreId());
    }

    @Test
    void shouldUseProductMasterCategoriesForRouteDerivation() {
        LoadedDspData data = new LoadedDspData(
                List.of(
                        new ProductMasterRecord("manual", ProductCategory.MANUAL, false),
                        new ProductMasterRecord("sortable", ProductCategory.SORTABLE, false),
                        new ProductMasterRecord("third-party", ProductCategory.AUTOMATED, true)),
                List.of(new NotionalToteOrder(
                        "order-1",
                        "order-1",
                        "104",
                        1,
                        OrderType.ASSOCIATED,
                        List.of(
                                new DspOrderItem("line-1", "manual", 1, "0006515", DspOrderLineType.MANUAL, "order-1", 1, 0),
                                new DspOrderItem("line-2", "sortable", 1, "0006515", DspOrderLineType.ADAPTED, "order-1", 1, 0),
                                new DspOrderItem("line-3", "third-party", 1, "0006515", DspOrderLineType.FULL_PACK, "order-1", 1, 0)),
                        0L)),
                List.of(),
                Set.of());
        LoadedDspSchedulerRuntimeFactory factory = factoryWithProducts(data.products());

        DspSchedulerRuntimeState runtimeState = factory.createRuntimeState(data, Map.of(), Optional.empty());

        var requirements = runtimeState.snapshot().orderStates().getFirst().routeRequirements();
        assertTrue(requirements.requiresManual());
        assertTrue(requirements.requiresSortable());
        assertTrue(requirements.requiresThirdParty());
        assertTrue(requirements.requiresManualMerge());
    }

    @Test
    void shouldCarryPreparedLineKeysIntoInitialSnapshot() {
        Set<PreparedLineKey> preparedLineKeys = Set.of(
                new PreparedLineKey("target-1", 1, "line-1", DspOrderLineType.ADAPTED),
                new PreparedLineKey("target-2", 1, "line-2", DspOrderLineType.MANUAL));
        LoadedDspData data = new LoadedDspData(
                List.of(new ProductMasterRecord("9114", ProductCategory.AUTOMATED, false)),
                List.of(dispatchOrder("order-1", OrderType.FULL_PACK, "9114", "0006461", 0L)),
                List.of(),
                preparedLineKeys);
        LoadedDspSchedulerRuntimeFactory factory = factoryWithProducts(data.products());

        DspSchedulerRuntimeState runtimeState = factory.createRuntimeState(data, Map.of(), Optional.empty());

        assertEquals(preparedLineKeys, runtimeState.snapshot().preparedLineKeys());
    }

    @Test
    void shouldFailWhenProductMasterDataIsMissingForLoadedDispatchOrder() {
        LoadedDspData data = new LoadedDspData(
                List.of(new ProductMasterRecord("known", ProductCategory.AUTOMATED, false)),
                List.of(dispatchOrder("order-1", OrderType.FULL_PACK, "missing", "0006461", 0L)),
                List.of(),
                Set.of());
        LoadedDspSchedulerRuntimeFactory factory = factoryWithProducts(data.products());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> factory.createRuntimeState(data, Map.of(), Optional.empty()));

        assertTrue(exception.getMessage().contains("No product master data for missing"));
    }

    private static LoadedDspSchedulerRuntimeFactory factoryWithProducts(List<ProductMasterRecord> products) {
        return new LoadedDspSchedulerRuntimeFactory(
                new DspRouteDeriver(new InMemoryProductMasterRepository(products)));
    }

    private static NotionalToteOrder dispatchOrder(
            String orderId,
            OrderType orderType,
            String productId,
            String pharmacyId,
            long sequenceNumber) {
        return new NotionalToteOrder(
                orderId,
                orderId,
                "104",
                1,
                orderType,
                List.of(new DspOrderItem(
                        "line-" + orderId,
                        productId,
                        1,
                        pharmacyId,
                        DspOrderLineType.FULL_PACK,
                        orderId,
                        1,
                        0)),
                sequenceNumber);
    }

    private static StationAdmissionSnapshot openAdmission(StationType stationType) {
        return new StationAdmissionSnapshot(
                stationType,
                new StationCapacity(1, 10),
                new StationSnapshot(stationType, 0, 0),
                true,
                "");
    }
}
