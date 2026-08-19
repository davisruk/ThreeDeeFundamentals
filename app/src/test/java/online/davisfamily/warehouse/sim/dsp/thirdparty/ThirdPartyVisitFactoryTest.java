package online.davisfamily.warehouse.sim.dsp.thirdparty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.ProductMasterRecord;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.routing.InMemoryProductMasterRepository;

class ThirdPartyVisitFactoryTest {

    @Test
    void shouldCreateThirdPartyPlanWithoutPhysicalTote() {
        ThirdPartyVisitFactory factory = factory(
                product("third-party", "Y74"),
                product("regular", null),
                product("complete", "Y75"));
        NotionalToteOrder order = order(
                OrderType.ADAPTED,
                line("line-1", "third-party", DspOrderLineType.ADAPTED, 3, 1),
                line("line-2", "regular", DspOrderLineType.ADAPTED, 1, 0),
                line("line-3", "complete", DspOrderLineType.ADAPTED, 1, 1));

        ThirdPartyVisitPlan plan = factory.planFor(order).orElseThrow();

        assertEquals(order.orderSheetKey(), plan.orderSheetKey());
        assertEquals(order.serviceCentreId(), plan.serviceCentreId());
        assertEquals(OrderType.ADAPTED, plan.orderType());
        assertEquals(2, plan.outstandingPackCount());
        assertEquals(List.of(new ThirdPartyLineWork(
                order.items().getFirst(),
                2,
                "Y74",
                ThirdPartyWorkType.ADAPTED_PREPARATION)), plan.lineWork());
        assertThrows(UnsupportedOperationException.class, () -> plan.lineWork().clear());
    }

    @Test
    void shouldCreateThirdPartyVisitWithExplicitPhysicalTote() {
        ThirdPartyVisitFactory factory = factory(product("third-party", "Y74"));
        NotionalToteOrder order = order(
                OrderType.FULL_PACK,
                line("line-1", "third-party", DspOrderLineType.FULL_PACK, 1, 0));
        PhysicalToteId physicalToteId = new PhysicalToteId("physical-90864875");

        ThirdPartyVisit visit = factory.create(physicalToteId, order).orElseThrow();

        assertEquals(physicalToteId, visit.physicalToteId());
        assertEquals(order.orderSheetKey(), visit.orderSheetKey());
    }

    @Test
    void shouldSelectDirectFullPackLinesForEveryFulfilmentOrderType() {
        ThirdPartyVisitFactory factory = factory(product("third-party", "Y74"));

        for (OrderType orderType : List.of(OrderType.FULL_PACK, OrderType.ASSOCIATED, OrderType.EMPTY)) {
            ThirdPartyLineWork lineWork = factory.planFor(order(
                    orderType,
                    line("line-1", "third-party", DspOrderLineType.FULL_PACK, 2, 0)))
                    .orElseThrow()
                    .lineWork()
                    .getFirst();

            assertEquals(ThirdPartyWorkType.DIRECT_FULFILMENT, lineWork.workType());
            assertEquals(2, lineWork.outstandingQuantity());
        }
    }

    @Test
    void shouldNotRepickAdaptedDependencyFromAssociatedOrder() {
        ThirdPartyVisitFactory factory = factory(
                product("direct", "Y74"),
                product("adapted", "Y75"));
        NotionalToteOrder mixedOrder = order(
                OrderType.ASSOCIATED,
                line("direct-line", "direct", DspOrderLineType.FULL_PACK, 1, 0),
                line("adapted-line", "adapted", DspOrderLineType.ADAPTED, 1, 0));

        ThirdPartyVisitPlan visit = factory.planFor(mixedOrder).orElseThrow();

        assertEquals(List.of("direct-line"), visit.lineWork().stream()
                .map(ThirdPartyLineWork::lineReference)
                .toList());
        assertTrue(factory.planFor(order(
                OrderType.EMPTY,
                line("adapted-line", "adapted", DspOrderLineType.ADAPTED, 1, 0))).isEmpty());
    }

    @Test
    void shouldReturnNoVisitWhenThirdPartyWorkIsAlreadyComplete() {
        ThirdPartyVisitFactory factory = factory(product("third-party", "Y74"));

        assertTrue(factory.planFor(order(
                OrderType.FULL_PACK,
                line("line-1", "third-party", DspOrderLineType.FULL_PACK, 1, 1))).isEmpty());
    }

    @Test
    void shouldRejectManualLinesAndMissingProductMasterData() {
        ThirdPartyVisitFactory factory = factory(product("manual", null));

        IllegalArgumentException manualException = assertThrows(
                IllegalArgumentException.class,
                () -> factory.planFor(order(
                        OrderType.ASSOCIATED,
                        line("manual-line", "manual", DspOrderLineType.MANUAL, 1, 0))));
        assertTrue(manualException.getMessage().contains("MANUAL line manual-line"));

        IllegalArgumentException missingException = assertThrows(
                IllegalArgumentException.class,
                () -> factory.planFor(order(
                        OrderType.FULL_PACK,
                        line("missing-line", "missing", DspOrderLineType.FULL_PACK, 1, 0))));
        assertTrue(missingException.getMessage().contains("No product master data for missing"));
    }

    private static ThirdPartyVisitFactory factory(ProductMasterRecord... products) {
        return new ThirdPartyVisitFactory(new InMemoryProductMasterRepository(List.of(products)));
    }

    private static NotionalToteOrder order(OrderType orderType, DspOrderItem... lines) {
        return new NotionalToteOrder(
                "order-1",
                "notional-1",
                "sc-1",
                1,
                orderType,
                List.of(lines),
                0);
    }

    private static DspOrderItem line(
            String lineReference,
            String productId,
            DspOrderLineType lineType,
            int quantity,
            int numberOfPacksPicked) {
        return new DspOrderItem(
                lineReference,
                productId,
                quantity,
                "0006515",
                lineType,
                "target-order-1",
                1,
                numberOfPacksPicked);
    }

    private static ProductMasterRecord product(String productId, String thirdPartyLocation) {
        return new ProductMasterRecord(
                productId,
                "Product " + productId,
                Optional.ofNullable(thirdPartyLocation),
                Optional.empty());
    }
}
