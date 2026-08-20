package online.davisfamily.warehouse.sim.dsp.bagging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.adapting.AdaptedLineRecord;
import online.davisfamily.warehouse.sim.dsp.adapting.DefaultCollectedPackPlanFactory;
import online.davisfamily.warehouse.sim.dsp.io.MappedTwelveNOrder;
import online.davisfamily.warehouse.sim.dsp.io.TwelveNDatasetLoader;
import online.davisfamily.warehouse.sim.dsp.io.TwelveNMessageKindMapper;
import online.davisfamily.warehouse.sim.dsp.io.TwelveNOrderMapper;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.ProductMasterRecord;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.routing.InMemoryProductMasterRepository;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ProductMasterThirdPartyPackPlanFactory;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyLineWork;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyVisit;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyVisitPlan;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyWorkType;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteToBagBatchPlan;

class DspBagPlanningProvenanceScenarioTest {
    private static final String SERVICE_CENTRE = "104";
    private static final String PHARMACY = "0006461";
    private static final String PATIENT = "patient-1";
    private static final String FIT_PRESCRIPTION = "prescription-fit";
    private static final String OVERFLOW_PRESCRIPTION = "prescription-overflow";
    private static final String ADAPTED_PRESCRIPTION = "prescription-adapted";
    private static final String MISSING_PACK_ID = "pack-line-missing-1";
    private static final PackDimensions DIMENSIONS = new PackDimensions(0.20f, 0.10f, 0.08f);

    @Test
    void shouldPlanBagsByPrescriptionWithDeterministicOverflow() {
        Scenario scenario = createScenario();

        assertEquals(
                List.of(FIT_PRESCRIPTION, OVERFLOW_PRESCRIPTION, FIT_PRESCRIPTION),
                scenario.fullPackOrder().items().stream().map(DspOrderItem::prescriptionId).toList());
        assertEquals(List.of(PATIENT, PATIENT, PATIENT),
                scenario.fullPackOrder().items().stream().map(DspOrderItem::patientId).toList());
        assertEquals(
                List.of(
                        new BagKey(FIT_PRESCRIPTION, 1),
                        new BagKey(OVERFLOW_PRESCRIPTION, 1),
                        new BagKey(OVERFLOW_PRESCRIPTION, 2),
                        new BagKey(ADAPTED_PRESCRIPTION, 1)),
                scenario.result().plannedBags().stream().map(PlannedBag::bagKey).toList());
        assertEquals(List.of("pack-line-overflow-1", "pack-line-overflow-2"),
                scenario.result().findBag(new BagKey(OVERFLOW_PRESCRIPTION, 1))
                        .orElseThrow().physicalPackIds());
        assertEquals(List.of("pack-line-overflow-3"),
                scenario.result().findBag(new BagKey(OVERFLOW_PRESCRIPTION, 2))
                        .orElseThrow().physicalPackIds());
    }

    @Test
    void shouldTraceAdaptedAndThirdPartyPacksToSourceLines() {
        Scenario scenario = createScenario();
        PlannedPackTrace thirdPartyTrace = scenario.result()
                .findPackTrace("pack-line-fit-1")
                .orElseThrow();
        PlannedPackTrace adaptedTrace = scenario.result()
                .findPackTrace("pack-line-adapted-1")
                .orElseThrow();

        assertEquals(scenario.fullPackOrder().orderSheetKey(),
                thirdPartyTrace.sourceProvenance().sourceOrderSheetKey());
        assertEquals(scenario.fullPackOrder().orderSheetKey(), thirdPartyTrace.fulfilmentOrderSheetKey());
        assertEquals(new PhysicalToteId("full-tote-1"), thirdPartyTrace.inputPhysicalToteId());
        assertEquals(new BagKey(FIT_PRESCRIPTION, 1), thirdPartyTrace.bagKey());

        assertEquals(scenario.adaptedSourceOrder().orderSheetKey(),
                adaptedTrace.sourceProvenance().sourceOrderSheetKey());
        assertEquals(new OrderSheetKey("associated-order-1", 1), adaptedTrace.fulfilmentOrderSheetKey());
        assertEquals(new PhysicalToteId("associated-tote-1"), adaptedTrace.inputPhysicalToteId());
        assertEquals(new BagKey(ADAPTED_PRESCRIPTION, 1), adaptedTrace.bagKey());

        for (PlannedPackTrace trace : scenario.result().packTraces()) {
            assertEquals(trace.sourceProvenance(),
                    scenario.provenanceRegistry().find(trace.physicalPackId()).orElseThrow());
        }
    }

    @Test
    void shouldKeepMissingLogicalLineSeparateFromPhysicalPackPlans() {
        Scenario scenario = createScenario();

        assertTrue(scenario.fullPackOrder().items().stream()
                .anyMatch(line -> line.lineReference().equals("line-missing")));
        assertFalse(scenario.provenanceRegistry().find(MISSING_PACK_ID).isPresent());
        assertFalse(scenario.result().findPackTrace(MISSING_PACK_ID).isPresent());
        assertEquals(5, scenario.result().packTraces().size());
        assertEquals(List.of("pack-line-fit-1"),
                scenario.result().findBag(new BagKey(FIT_PRESCRIPTION, 1))
                        .orElseThrow().physicalPackIds());
    }

    @Test
    void shouldProduceStableP2pCorrelationsFromBagKeys() {
        Scenario scenario = createScenario();
        BagPlanningResult repeated = scenario.planner().plan(scenario.request());

        assertEquals(scenario.result().plannedBags(), repeated.plannedBags());
        assertEquals(scenario.result().packTraces(), repeated.packTraces());
        assertEquals(p2pPackPlans(scenario.result()), p2pPackPlans(repeated));
        assertEquals(
                List.of("full-tote-1", "associated-tote-1"),
                scenario.result().p2pToteLoadPlans().stream()
                        .map(loadPlan -> loadPlan.physicalToteId().value())
                        .toList());

        ToteToBagBatchPlan batchPlan = ToteToBagBatchPlan.fromToteLoadPlans(
                scenario.result().p2pToteLoadPlans());
        for (PlannedBag bag : scenario.result().plannedBags()) {
            assertEquals(
                    bag.physicalPackIds().size(),
                    batchPlan.expectedPackCountFor(bag.bagKey().correlationId()));
        }
    }

    private static Scenario createScenario() {
        TwelveNOrderMapper mapper = new TwelveNOrderMapper(new TwelveNMessageKindMapper());
        TwelveNDatasetLoader loader = new TwelveNDatasetLoader();
        MappedTwelveNOrder mappedFullPack = mapper.map(loader.loadString(fullPackJson()), 1L);
        MappedTwelveNOrder mappedAdapted = mapper.map(loader.loadString(adaptedJson()), 2L);
        NotionalToteOrder fullPackOrder = mappedFullPack.order();
        NotionalToteOrder adaptedSourceOrder = mappedAdapted.order();

        PackProvenanceRegistry provenanceRegistry = new PackProvenanceRegistry();
        DspPackPlanFactory packPlanFactory = new DspPackPlanFactory(provenanceRegistry);
        List<PackPlan> fullPackPlans = new ArrayList<>();

        DspOrderItem thirdPartyLine = line(fullPackOrder, "line-fit");
        ProductMasterRecord thirdPartyProduct = new ProductMasterRecord(
                thirdPartyLine.productId(),
                "Third Party product",
                Optional.of("Y74"),
                Optional.of(DIMENSIONS));
        ProductMasterThirdPartyPackPlanFactory thirdPartyFactory =
                new ProductMasterThirdPartyPackPlanFactory(
                        new InMemoryProductMasterRepository(List.of(thirdPartyProduct)),
                        (visit, lineWork) -> lineWork.lineReference(),
                        packPlanFactory);
        ThirdPartyLineWork thirdPartyWork = new ThirdPartyLineWork(
                thirdPartyLine, 1, "Y74", ThirdPartyWorkType.DIRECT_FULFILMENT);
        ThirdPartyVisit thirdPartyVisit = new ThirdPartyVisit(
                new PhysicalToteId("full-tote-1"),
                new ThirdPartyVisitPlan(
                        fullPackOrder.orderSheetKey(),
                        fullPackOrder.serviceCentreId(),
                        OrderType.FULL_PACK,
                        List.of(thirdPartyWork)));
        fullPackPlans.add(thirdPartyFactory.createPackPlan(thirdPartyVisit, thirdPartyWork, 1));

        DspOrderItem overflowLine = line(fullPackOrder, "line-overflow");
        for (int ordinal = 1; ordinal <= overflowLine.quantity(); ordinal++) {
            fullPackPlans.add(packPlanFactory.createPackPlan(
                    "pack-" + overflowLine.lineReference() + "-" + ordinal,
                    overflowLine.lineReference(),
                    DIMENSIONS,
                    provenance(fullPackOrder, overflowLine)));
        }

        DspOrderItem adaptedLine = line(adaptedSourceOrder, "line-adapted");
        AdaptedLineRecord adaptedRecord = AdaptedLineRecord.fromPreparedLine(
                adaptedLine,
                adaptedSourceOrder.orderSheetKey(),
                adaptedSourceOrder.serviceCentreId());
        List<PackPlan> collectedPlans = new DefaultCollectedPackPlanFactory(DIMENSIONS, packPlanFactory)
                .createPackPlans(List.of(adaptedRecord));

        BagPlanningRequest request = new BagPlanningRequest(List.of(
                new BagPlanningTote(
                        fullPackOrder.orderSheetKey(),
                        SERVICE_CENTRE,
                        new ToteLoadPlan("full-tote-1", fullPackPlans)),
                new BagPlanningTote(
                        new OrderSheetKey("associated-order-1", 1),
                        SERVICE_CENTRE,
                        new ToteLoadPlan("associated-tote-1", collectedPlans))));
        DeterministicBagPlanner planner = new DeterministicBagPlanner(
                new MaximumPackCountBagCapacityPolicy(2),
                provenanceRegistry.snapshot());

        return new Scenario(
                fullPackOrder,
                adaptedSourceOrder,
                provenanceRegistry,
                request,
                planner,
                planner.plan(request));
    }

    private static PackSourceProvenance provenance(NotionalToteOrder order, DspOrderItem line) {
        return new PackSourceProvenance(
                order.orderSheetKey(),
                line.lineReference(),
                line.productId(),
                order.serviceCentreId(),
                line.pharmacyId(),
                line.patientId(),
                line.prescriptionId());
    }

    private static DspOrderItem line(NotionalToteOrder order, String lineReference) {
        return order.items().stream()
                .filter(line -> line.lineReference().equals(lineReference))
                .findFirst()
                .orElseThrow();
    }

    private static List<List<PackPlan>> p2pPackPlans(BagPlanningResult result) {
        return result.p2pToteLoadPlans().stream().map(ToteLoadPlan::getPackPlans).toList();
    }

    private static String fullPackJson() {
        return """
                {
                  "header": {"orderId":"full-order-1","sheetNumber":"001"},
                  "toteIdentifier": {"payload":"05"},
                  "orderPriority": {"payload":"999"},
                  "transportContainer": {"payload":"full-tote-1"},
                  "serviceCentre": {"payload":"104"},
                  "orderDetail": {
                    "numberOfOrderLines": 3,
                    "orderLines": [
                      {
                        "orderLineNumber":"line-fit",
                        "orderLineType":"05",
                        "pharmacyId":"0006461",
                        "patientId":"patient-1",
                        "prescriptionId":"prescription-fit",
                        "productId":"product-third-party",
                        "numberOfPacks":"0001",
                        "referenceOrderId":"full-order-1",
                        "referenceSheetNumber":"001",
                        "numberOfPacksPicked":"0001"
                      },
                      {
                        "orderLineNumber":"line-overflow",
                        "orderLineType":"05",
                        "pharmacyId":"0006461",
                        "patientId":"patient-1",
                        "prescriptionId":"prescription-overflow",
                        "productId":"product-regular",
                        "numberOfPacks":"0003",
                        "referenceOrderId":"full-order-1",
                        "referenceSheetNumber":"001",
                        "numberOfPacksPicked":"0003"
                      },
                      {
                        "orderLineNumber":"line-missing",
                        "orderLineType":"05",
                        "pharmacyId":"0006461",
                        "patientId":"patient-1",
                        "prescriptionId":"prescription-fit",
                        "productId":"product-missing",
                        "numberOfPacks":"0001",
                        "referenceOrderId":"full-order-1",
                        "referenceSheetNumber":"001",
                        "numberOfPacksPicked":"0000"
                      }
                    ]
                  }
                }
                """;
    }

    private static String adaptedJson() {
        return """
                {
                  "header": {"orderId":"adapted-source-1","sheetNumber":"002"},
                  "toteIdentifier": {"payload":"02"},
                  "orderPriority": {"payload":"999"},
                  "transportContainer": {"payload":"adapted-tote-1"},
                  "serviceCentre": {"payload":"104"},
                  "orderDetail": {
                    "numberOfOrderLines": 1,
                    "orderLines": [
                      {
                        "orderLineNumber":"line-adapted",
                        "orderLineType":"02",
                        "pharmacyId":"0006461",
                        "patientId":"patient-2",
                        "prescriptionId":"prescription-adapted",
                        "productId":"product-adapted",
                        "numberOfPacks":"0001",
                        "referenceOrderId":"associated-order-1",
                        "referenceSheetNumber":"001",
                        "numberOfPacksPicked":"0001"
                      }
                    ]
                  }
                }
                """;
    }

    private record Scenario(
            NotionalToteOrder fullPackOrder,
            NotionalToteOrder adaptedSourceOrder,
            PackProvenanceRegistry provenanceRegistry,
            BagPlanningRequest request,
            DeterministicBagPlanner planner,
            BagPlanningResult result) {
    }
}
