package online.davisfamily.warehouse.sim.dsp.thirdparty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.adapting.AdaptedLineStore;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingArea;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingAreaController;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingBench;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingBenchCompletion;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingBenchId;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingVisit;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingVisitFactory;
import online.davisfamily.warehouse.sim.dsp.adapting.DefaultCollectedPackPlanFactory;
import online.davisfamily.warehouse.sim.dsp.adapting.MapBackedToteLoadPlanRegistry;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.ProductMasterRecord;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StartLocation;
import online.davisfamily.warehouse.sim.dsp.routing.InMemoryProductMasterRepository;
import online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements;
import online.davisfamily.warehouse.sim.dsp.runtime.DspSchedulerRuntimeState;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspDependencyEvaluator;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspOrderStatus;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

class ThirdPartyAdaptedCollectIntegrationTest {

    private static final String ASSOCIATED_ORDER_ID = "associated-1";
    private static final String LINE_REFERENCE = "adapted-third-party-line";
    private static final String PRODUCT_ID = "third-party-product";
    private static final PackDimensions DIMENSIONS = new PackDimensions(0.18f, 0.08f, 0.05f);

    @Test
    void shouldUpdateAdaptingAndThirdPartyPlansByPhysicalToteId() {
        DspOrderItem sourceLine = adaptedLine(ASSOCIATED_ORDER_ID);
        NotionalToteOrder adaptedOrder = order(
                "adapted-source-1",
                "adapted-tote-1",
                OrderType.ADAPTED,
                sourceLine);
        NotionalToteOrder associatedOrder = order(
                ASSOCIATED_ORDER_ID,
                "associated-tote-1",
                OrderType.ASSOCIATED,
                adaptedLine(ASSOCIATED_ORDER_ID));
        PhysicalToteId adaptedToteId = new PhysicalToteId("adapted-tote-1");
        PhysicalToteId associatedToteId = new PhysicalToteId("associated-tote-1");
        InMemoryProductMasterRepository products = new InMemoryProductMasterRepository(List.of(
                new ProductMasterRecord(
                        PRODUCT_ID,
                        "Third Party adapted product",
                        Optional.of("Y74"),
                        Optional.of(DIMENSIONS))));
        ThirdPartyVisitFactory visitFactory = new ThirdPartyVisitFactory(products);
        MapBackedToteLoadPlanRegistry loadPlans = new MapBackedToteLoadPlanRegistry();
        loadPlans.putLoadPlan(new ToteLoadPlan(adaptedToteId, List.of()));
        loadPlans.putLoadPlan(new ToteLoadPlan(associatedToteId, List.of()));

        ThirdPartyArea thirdPartyArea = new ThirdPartyArea(new ThirdPartyAreaConfig(0, 1, 0d));
        ThirdPartyAreaController thirdPartyController = new ThirdPartyAreaController(
                thirdPartyArea,
                loadPlans,
                new ProductMasterThirdPartyPackPlanFactory(
                        products,
                        (visit, lineWork) -> visit.orderSheetKey().orderId()));
        ThirdPartyVisit adaptedThirdPartyVisit = visitFactory.create(adaptedToteId, adaptedOrder).orElseThrow();

        assertEquals(ThirdPartyWorkType.ADAPTED_PREPARATION,
                adaptedThirdPartyVisit.lineWork().getFirst().workType());
        assertTrue(visitFactory.planFor(associatedOrder).isEmpty());

        thirdPartyArea.submitVisit(adaptedThirdPartyVisit);
        thirdPartyController.update(0d);

        assertEquals(List.of("pack-" + LINE_REFERENCE + "-1"),
                loadPlans.getLoadPlanFor(adaptedToteId).getPackPlans().stream()
                        .map(PackPlan::packId)
                        .toList());

        DspSchedulerOrderState associatedState = new DspSchedulerOrderState(
                associatedOrder,
                new RouteRequirements(false, true, false, true, false, StartLocation.OSR),
                DspOrderStatus.WAITING);
        DspSchedulerRuntimeState runtimeState = new DspSchedulerRuntimeState(new WarehouseSchedulerSnapshot(
                List.of(associatedState),
                Map.of(),
                Set.of(),
                Optional.empty()));
        AdaptedLineStore adaptedLineStore = new AdaptedLineStore();
        AdaptingBench bench = new AdaptingBench("bench-1", adaptedLineStore, 0d);
        AdaptingArea adaptingArea = new AdaptingArea(List.of(bench), 0);
        AdaptingAreaController adaptingController = new AdaptingAreaController(
                adaptingArea,
                runtimeState,
                loadPlans,
                new DefaultCollectedPackPlanFactory(DIMENSIONS));
        AdaptingBenchId benchId = new AdaptingBenchId("bench-1");
        DspDependencyEvaluator dependencyEvaluator = new DspDependencyEvaluator();

        assertFalse(dependencyEvaluator.findBlocks(associatedState, runtimeState.snapshot()).isEmpty());

        adaptingArea.submitVisit(AdaptingVisit.store(
                adaptedToteId,
                adaptedOrder.orderSheetKey(),
                adaptedOrder.serviceCentreId(),
                adaptedOrder.items()));
        bench.startProcessing();
        adaptingController.applyBenchCompletion(benchId).orElseThrow();

        PreparedLineKey preparedLineKey = new PreparedLineKey(ASSOCIATED_ORDER_ID, LINE_REFERENCE);
        assertTrue(runtimeState.snapshot().preparedLineKeys().contains(preparedLineKey));
        assertTrue(dependencyEvaluator.findBlocks(associatedState, runtimeState.snapshot()).isEmpty());

        adaptingArea.submitVisit(new AdaptingVisitFactory().create(associatedToteId, associatedOrder));
        bench.startProcessing();
        AdaptingBenchCompletion collectCompletion = adaptingController.applyBenchCompletion(benchId).orElseThrow();

        assertEquals(PRODUCT_ID, collectCompletion.collectedLines().getFirst().line().productId());
        ToteLoadPlan associatedPlan = loadPlans.getLoadPlanFor(associatedToteId);
        assertEquals(List.of("pack-" + LINE_REFERENCE + "-1"),
                associatedPlan.getPackPlans().stream().map(PackPlan::packId).toList());
        assertEquals(List.of(LINE_REFERENCE),
                associatedPlan.getPackPlans().stream().map(PackPlan::correlationId).toList());
        assertEquals(List.of(DIMENSIONS),
                associatedPlan.getPackPlans().stream().map(PackPlan::dimensions).toList());
        assertFalse(adaptedLineStore.contains(preparedLineKey));
    }

    private static DspOrderItem adaptedLine(String referenceOrderId) {
        return new DspOrderItem(
                LINE_REFERENCE,
                PRODUCT_ID,
                1,
                "0000310",
                DspOrderLineType.ADAPTED,
                referenceOrderId,
                1,
                0);
    }

    private static NotionalToteOrder order(
            String orderId,
            String toteId,
            OrderType orderType,
            DspOrderItem item) {
        return new NotionalToteOrder(
                orderId,
                toteId,
                "SC-1",
                1,
                orderType,
                List.of(item),
                0L);
    }
}
