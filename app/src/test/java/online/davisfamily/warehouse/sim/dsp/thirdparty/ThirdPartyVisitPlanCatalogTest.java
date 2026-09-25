package online.davisfamily.warehouse.sim.dsp.thirdparty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.ProductMasterRecord;
import online.davisfamily.warehouse.sim.dsp.routing.InMemoryProductMasterRepository;

class ThirdPartyVisitPlanCatalogTest {
    @Test
    void derivesEachOrderOnceAndReusesExactOrderedPlansAndEmptyResults() {
        CountingFactory factory = new CountingFactory();
        NotionalToteOrder first = order("first", "third", "plain", "third");
        NotionalToteOrder second = order("second", "plain");
        List<NotionalToteOrder> mutableOrders = new ArrayList<>(List.of(first, second));

        ThirdPartyVisitPlanCatalog catalog = new ThirdPartyVisitPlanCatalog(mutableOrders, factory);
        mutableOrders.clear();
        assertEquals(List.of(first, second), factory.seen);
        Optional<ThirdPartyVisitPlan> firstPlan = catalog.planFor(first);
        assertSame(firstPlan, catalog.planFor(first));
        assertSame(firstPlan, catalog.planFor(equalCopy(first)));
        assertEquals(List.of("first-line-0", "first-line-2"), firstPlan.orElseThrow()
                .lineWork().stream().map(ThirdPartyLineWork::lineReference).toList());
        assertThrows(UnsupportedOperationException.class, () -> firstPlan.orElseThrow().lineWork().clear());
        Optional<ThirdPartyVisitPlan> empty = catalog.planFor(second);
        assertTrue(empty.isEmpty());
        assertSame(empty, catalog.planFor(second));
        assertEquals(2, factory.seen.size());
    }

    @Test
    void rejectsInvalidInputsAndDoesNotPublishPartialConstruction() {
        CountingFactory factory = new CountingFactory();
        NotionalToteOrder first = order("first", "third");
        NotionalToteOrder altered = new NotionalToteOrder(first.orderId(), "different-tote",
                first.serviceCentreId(), first.sheetNumber(), first.orderType(), first.items(),
                first.orderPriority(), first.sequenceNumber());
        ThirdPartyVisitPlanCatalog catalog = new ThirdPartyVisitPlanCatalog(List.of(first), factory);
        assertThrows(IllegalArgumentException.class, () -> catalog.planFor(null));
        assertThrows(IllegalArgumentException.class, () -> catalog.planFor(order("unknown", "third")));
        assertThrows(IllegalArgumentException.class, () -> catalog.planFor(altered));
        assertThrows(IllegalArgumentException.class, () -> new ThirdPartyVisitPlanCatalog(null, factory));
        assertThrows(IllegalArgumentException.class, () -> new ThirdPartyVisitPlanCatalog(List.of(first), null));
        assertThrows(IllegalArgumentException.class, () -> new ThirdPartyVisitPlanCatalog(
                java.util.Arrays.asList(first, null), factory));
        assertThrows(IllegalArgumentException.class, () -> new ThirdPartyVisitPlanCatalog(
                List.of(first, altered), factory));

        ThirdPartyVisitFactory nullResult = new ThirdPartyVisitFactory(factory.products()) {
            @Override public Optional<ThirdPartyVisitPlan> planFor(NotionalToteOrder order) { return null; }
        };
        assertThrows(IllegalArgumentException.class, () -> new ThirdPartyVisitPlanCatalog(
                List.of(first), nullResult));
        for (int mismatch = 0; mismatch < 3; mismatch++) {
            final int field = mismatch;
            ThirdPartyVisitFactory wrongPlan = new ThirdPartyVisitFactory(factory.products()) {
                @Override public Optional<ThirdPartyVisitPlan> planFor(NotionalToteOrder order) {
                    ThirdPartyVisitPlan valid = super.planFor(order).orElseThrow();
                    return Optional.of(new ThirdPartyVisitPlan(
                            field == 0 ? new OrderSheetKey("other", 1) : valid.orderSheetKey(),
                            field == 1 ? "other" : valid.serviceCentreId(),
                            field == 2 ? OrderType.ADAPTED : valid.orderType(), valid.lineWork()));
                }
            };
            assertThrows(IllegalArgumentException.class, () -> new ThirdPartyVisitPlanCatalog(
                    List.of(first), wrongPlan));
        }

        ThirdPartyVisitFactory throwsOnSecond = new ThirdPartyVisitFactory(factory.products()) {
            @Override public Optional<ThirdPartyVisitPlan> planFor(NotionalToteOrder order) {
                if (order.orderId().equals("second")) { throw new IllegalStateException("second failed"); }
                return super.planFor(order);
            }
        };
        assertThrows(IllegalStateException.class, () -> new ThirdPartyVisitPlanCatalog(
                List.of(first, order("second", "third")), throwsOnSecond));
    }

    private static NotionalToteOrder equalCopy(NotionalToteOrder order) {
        return new NotionalToteOrder(order.orderId(), order.notionalToteId(), order.serviceCentreId(),
                order.sheetNumber(), order.orderType(), new ArrayList<>(order.items()),
                order.orderPriority(), order.sequenceNumber());
    }

    private static NotionalToteOrder order(String id, String... products) {
        List<DspOrderItem> lines = new ArrayList<>();
        for (int index = 0; index < products.length; index++) {
            lines.add(new DspOrderItem(id + "-line-" + index, products[index], 1,
                    "0000310", DspOrderLineType.FULL_PACK, id + "-prescription", 1, 0));
        }
        return new NotionalToteOrder(id, "tote-" + id, "104", 1, OrderType.FULL_PACK, lines, 0L);
    }

    private static final class CountingFactory extends ThirdPartyVisitFactory {
        private final List<NotionalToteOrder> seen = new ArrayList<>();

        private CountingFactory() { super(products()); }

        private static InMemoryProductMasterRepository products() {
            return new InMemoryProductMasterRepository(List.of(
                    new ProductMasterRecord("third", "Third", Optional.of("Y74"), Optional.empty()),
                    new ProductMasterRecord("plain", "Plain", Optional.empty(), Optional.empty())));
        }

        @Override public Optional<ThirdPartyVisitPlan> planFor(NotionalToteOrder order) {
            seen.add(order);
            return super.planFor(order);
        }
    }
}
