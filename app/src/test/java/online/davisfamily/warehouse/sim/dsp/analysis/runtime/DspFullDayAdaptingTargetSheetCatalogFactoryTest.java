package online.davisfamily.warehouse.sim.dsp.analysis.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingTargetSheetCatalog;
import online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayBagPlanningRequestFactory;
import online.davisfamily.warehouse.sim.dsp.analysis.input.DspFullDayInputPreflight;
import online.davisfamily.warehouse.sim.dsp.analysis.input.DspInputRejectionCatalog;
import online.davisfamily.warehouse.sim.dsp.bagging.BagPlanningResult;
import online.davisfamily.warehouse.sim.dsp.bagging.DeterministicBagPlanner;
import online.davisfamily.warehouse.sim.dsp.bagging.MaximumPackCountBagCapacityPolicy;
import online.davisfamily.warehouse.sim.dsp.io.DspDatasetLoadReport;
import online.davisfamily.warehouse.sim.dsp.io.LoadedDspData;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.ProductMasterRecord;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;

class DspFullDayAdaptingTargetSheetCatalogFactoryTest {
    private final DspFullDayAdaptingTargetSheetCatalogFactory factory =
            new DspFullDayAdaptingTargetSheetCatalogFactory();

    @Test
    void shouldMapOneSourceSheetToTwoAssociatedSheetsByPlannedSlot() {
        LoadedDspData executable = executable(twoSheetOrders(OrderType.ASSOCIATED));
        AdaptingTargetSheetCatalog catalog = factory.create(executable, plan(executable));

        assertEquals(new OrderSheetKey("target", 1),
                catalog.requireTargetSheet(new PreparedLineKey("target", "line-a")));
        assertEquals(new OrderSheetKey("target", 2),
                catalog.requireTargetSheet(new PreparedLineKey("target", "line-b")));
    }

    @Test
    void shouldMapPreparedLineToEmptyTargetSheet() {
        LoadedDspData executable = executable(twoSheetOrders(OrderType.EMPTY));
        AdaptingTargetSheetCatalog catalog = factory.create(executable, plan(executable));

        assertEquals(new OrderSheetKey("target", 2),
                catalog.requireTargetSheet(new PreparedLineKey("target", "line-b")));
    }

    @Test
    void shouldFailWithoutReturningCatalogForMissingPlannedSlot() {
        List<NotionalToteOrder> orders = twoSheetOrders(OrderType.ASSOCIATED);
        LoadedDspData executable = executable(orders);
        List<NotionalToteOrder> oneLineOrders = List.of(
                order("source", 1, OrderType.ADAPTED,
                        List.of(sourceLine("line-a")), 0),
                order("target", 1, OrderType.ASSOCIATED,
                        List.of(aliasLine("line-a")), 1));
        BagPlanningResult incompletePlan = plan(executable(oneLineOrders));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> factory.create(executable, incompletePlan));
        assertTrue(exception.getMessage().contains("line-b"));
    }

    @Test
    void shouldFailWithoutReturningCatalogForMismatchedPlannedSource() {
        LoadedDspData executable = executable(twoSheetOrders(OrderType.ASSOCIATED));
        BagPlanningResult bagPlan = plan(executable);
        List<NotionalToteOrder> changedOrders = new ArrayList<>(executable.orders());
        changedOrders.set(0, order("source", 1, OrderType.ADAPTED,
                List.of(new DspOrderItem("line-a", "different-product", 1, "pharmacy", "patient",
                                "rx-line-a", DspOrderLineType.ADAPTED, "target", 1, 0),
                        sourceLine("line-b")), 0));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> factory.create(data(changedOrders), bagPlan));
        assertTrue(exception.getMessage().contains("source"));
    }

    @Test
    void shouldFailWithoutReturningCatalogForDuplicatePreparedKey() {
        LoadedDspData executable = executable(twoSheetOrders(OrderType.ASSOCIATED));
        BagPlanningResult bagPlan = plan(executable);
        List<NotionalToteOrder> duplicateOrders = new ArrayList<>(executable.orders());
        duplicateOrders.add(order("second-source", 1, OrderType.ADAPTED,
                List.of(sourceLine("line-a")), 3));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> factory.create(data(duplicateOrders), bagPlan));
        assertTrue(exception.getMessage().contains("Duplicate executable prepared line"));
    }

    private static List<NotionalToteOrder> twoSheetOrders(OrderType secondTargetType) {
        return List.of(
                order("source", 1, OrderType.ADAPTED,
                        List.of(sourceLine("line-a"), sourceLine("line-b")), 0),
                order("target", 1, OrderType.ASSOCIATED,
                        List.of(aliasLine("line-a")), 1),
                order("target", 2, secondTargetType,
                        List.of(aliasLine("line-b")), 2));
    }

    private static LoadedDspData executable(List<NotionalToteOrder> orders) {
        return new DspFullDayInputPreflight().project(
                data(orders), DspInputRejectionCatalog.empty()).executableData();
    }

    private static BagPlanningResult plan(LoadedDspData data) {
        return new DeterministicBagPlanner(new MaximumPackCountBagCapacityPolicy(10))
                .plan(new DspFullDayBagPlanningRequestFactory().create(data));
    }

    private static LoadedDspData data(List<NotionalToteOrder> orders) {
        List<DspOrderItem> preparedLines = orders.stream()
                .filter(order -> order.orderType() == OrderType.ADAPTED)
                .flatMap(order -> order.items().stream())
                .toList();
        Set<PreparedLineKey> keys = preparedLines.stream()
                .map(PreparedLineKey::forPreparedLine)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        return new LoadedDspData(
                List.of(product("product"), product("different-product")),
                orders, preparedLines, keys, Set.of(), List.of(), DspDatasetLoadReport.empty());
    }

    private static NotionalToteOrder order(String orderId, int sheetNumber, OrderType type,
            List<DspOrderItem> lines, long sequenceNumber) {
        return new NotionalToteOrder(orderId, orderId, "104", sheetNumber,
                type, lines, 999, sequenceNumber);
    }

    private static DspOrderItem sourceLine(String lineReference) {
        return line(lineReference, "target");
    }

    private static DspOrderItem aliasLine(String lineReference) {
        return line(lineReference, "source");
    }

    private static DspOrderItem line(String lineReference, String referenceOrderId) {
        return new DspOrderItem(lineReference, "product", 1, "pharmacy", "patient",
                "rx-" + lineReference, DspOrderLineType.ADAPTED, referenceOrderId, 1, 0);
    }

    private static ProductMasterRecord product(String productId) {
        return new ProductMasterRecord(productId, "Product " + productId,
                Optional.empty(), Optional.of(new PackDimensions(0.20f, 0.10f, 0.08f)));
    }
}
