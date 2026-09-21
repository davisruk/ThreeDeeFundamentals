package online.davisfamily.warehouse.sim.dsp.thirdparty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.bagging.BagPackDemand;
import online.davisfamily.warehouse.sim.dsp.bagging.BagPlanningRequest;
import online.davisfamily.warehouse.sim.dsp.bagging.BagPlanningResult;
import online.davisfamily.warehouse.sim.dsp.bagging.DeterministicBagPlanner;
import online.davisfamily.warehouse.sim.dsp.bagging.MaximumPackCountBagCapacityPolicy;
import online.davisfamily.warehouse.sim.dsp.bagging.PackSourceProvenance;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedPackSlotKey;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;

class PlannedSlotThirdPartyPackCorrelationResolverTest {

    @Test
    void shouldResolveOnlyTheSingleLinePackOrdinal() {
        OrderSheetKey sourceSheet = new OrderSheetKey("source-order", 1);
        DspOrderItem line = new DspOrderItem(
                "line-1",
                "product-1",
                3,
                "pharmacy-1",
                "patient-1",
                "prescription-1",
                DspOrderLineType.FULL_PACK,
                "source-order",
                1,
                2);
        PackSourceProvenance provenance = new PackSourceProvenance(
                sourceSheet,
                line.lineReference(),
                line.productId(),
                "SC-1",
                line.pharmacyId(),
                line.patientId(),
                line.prescriptionId());
        BagPlanningResult result = new DeterministicBagPlanner(
                new MaximumPackCountBagCapacityPolicy(10)).plan(new BagPlanningRequest(
                        List.of(new BagPackDemand(
                                new PlannedPackSlotKey(sourceSheet, line.lineReference(), 1),
                                "pack-line-1-1",
                                new PackDimensions(0.2f, 0.1f, 0.08f),
                                provenance,
                                sourceSheet,
                                Optional.empty())),
                        List.of()));
        ThirdPartyVisit visit = new ThirdPartyVisit(
                new PhysicalToteId("tote-1"),
                new ThirdPartyVisitPlan(
                        sourceSheet,
                        "SC-1",
                        OrderType.FULL_PACK,
                        List.of(new ThirdPartyLineWork(line, "Y74", ThirdPartyWorkType.DIRECT_FULFILMENT))));
        PlannedSlotThirdPartyPackCorrelationResolver resolver =
                new PlannedSlotThirdPartyPackCorrelationResolver(result);

        assertEquals("prescription-1/bag-1", resolver.resolve(visit, visit.lineWork().getFirst(), 1));
        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve(visit, visit.lineWork().getFirst(), 0));
        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve(visit, visit.lineWork().getFirst(), 2));
    }
}
