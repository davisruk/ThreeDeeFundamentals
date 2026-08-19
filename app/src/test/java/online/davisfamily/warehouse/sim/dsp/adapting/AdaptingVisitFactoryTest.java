package online.davisfamily.warehouse.sim.dsp.adapting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

class AdaptingVisitFactoryTest {

    @Test
    void shouldCreateAdaptingAdmissionProfileWithoutPhysicalTote() {
        NotionalToteOrder order = adaptedOrder();

        AdaptingVisitProfile profile = new AdaptingVisitFactory().profileFor(order);

        assertEquals(AdaptingVisitType.STORE, profile.visitType());
        assertEquals(order.orderSheetKey(), profile.orderSheetKey());
        assertEquals(order.serviceCentreId(), profile.serviceCentreId());
        assertEquals(order.items(), profile.preparedLines());
        assertEquals(List.of("0000310"), profile.pharmacyIds());
    }

    @Test
    void shouldCreateAdaptingVisitWithExplicitPhysicalTote() {
        NotionalToteOrder order = adaptedOrder();
        PhysicalToteId physicalToteId = new PhysicalToteId("physical-90864875");

        AdaptingVisit visit = new AdaptingVisitFactory().create(physicalToteId, order);

        assertEquals(physicalToteId, visit.physicalToteId());
        assertEquals(order.orderSheetKey(), visit.profile().orderSheetKey());
        assertEquals(order.serviceCentreId(), visit.profile().serviceCentreId());
        assertEquals(order.items(), visit.preparedLines());
    }

    private static NotionalToteOrder adaptedOrder() {
        DspOrderItem line = new DspOrderItem(
                "line-1",
                "product-1",
                1,
                "0000310",
                DspOrderLineType.ADAPTED,
                "associated-1",
                1,
                0);
        return new NotionalToteOrder(
                "adapted-order-1",
                "legacy-notional-1",
                "104",
                1,
                OrderType.ADAPTED,
                List.of(line),
                0);
    }
}
