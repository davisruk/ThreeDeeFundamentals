package online.davisfamily.warehouse.sim.dsp.thirdparty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.adapting.MapBackedToteLoadPlanRegistry;
import online.davisfamily.warehouse.sim.dsp.bagging.DspPackPlanFactory;
import online.davisfamily.warehouse.sim.dsp.bagging.PackProvenanceRegistry;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.ProductMasterRecord;
import online.davisfamily.warehouse.sim.dsp.routing.InMemoryProductMasterRepository;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlanProvider;

class ThirdPartyPickFlowTest {

    @Test
    void shouldRegisterThirdPartyPackAgainstVisitSourceSheet() {
        MapBackedToteLoadPlanRegistry registry = registryWithPlan(
                "tote-order-1",
                List.of(pack("existing-pack", "existing-bag", dimensions(0.1f))));
        PackProvenanceRegistry provenanceRegistry = new PackProvenanceRegistry();
        ThirdPartyArea area = area();
        ThirdPartyAreaController controller = controller(area, registry, provenanceRegistry);
        ThirdPartyVisit visit = visit("order-1", OrderType.FULL_PACK, ThirdPartyWorkType.DIRECT_FULFILMENT, 2);

        area.submitVisit(visit);
        controller.update(1d);

        ToteLoadPlan updated = registry.getLoadPlanFor("tote-order-1");
        assertEquals(List.of("existing-pack", "pack-line-order-1-1", "pack-line-order-1-2"),
                updated.getPackPlans().stream().map(PackPlan::packId).toList());
        assertEquals(dimensions(0.25f), updated.getPackPlans().get(1).dimensions());
        assertEquals(List.of("existing-bag", "order-1", "order-1"),
                updated.getPackPlans().stream().map(PackPlan::correlationId).toList());
        ThirdPartyVisit completedVisit = controller.completionForTote(new PhysicalToteId("tote-order-1"))
                .orElseThrow().visit();
        assertEquals("Y74", completedVisit.lineWork().getFirst().binLocation());
        assertEquals("order-1", completedVisit.orderSheetKey().orderId());
        assertEquals(new PhysicalToteId("tote-order-1"), completedVisit.physicalToteId());
        var provenance = provenanceRegistry.find("pack-line-order-1-1").orElseThrow();
        assertEquals(new OrderSheetKey("order-1", 1), provenance.sourceOrderSheetKey());
        assertEquals("SC-1", provenance.serviceCentreId());
        assertEquals("patient-order-1", provenance.patientId());
        assertEquals("prescription-order-1", provenance.prescriptionId());
    }

    @Test
    void shouldPopulateAnInitiallyEmptyThirdPartyOnlyTotePlan() {
        MapBackedToteLoadPlanRegistry registry = registryWithPlan("tote-order-2", List.of());
        ThirdPartyArea area = area();
        ThirdPartyAreaController controller = controller(area, registry);

        area.submitVisit(visit("order-2", OrderType.EMPTY, ThirdPartyWorkType.DIRECT_FULFILMENT, 1));
        controller.update(1d);

        ToteLoadPlanProvider provider = registry;
        assertEquals(List.of("pack-line-order-2-1"), provider.getLoadPlanFor("tote-order-2")
                .getPackPlans().stream().map(PackPlan::packId).toList());
    }

    @Test
    void shouldAddAdaptedPreparationWithoutPublishingOtherRuntimeState() {
        MapBackedToteLoadPlanRegistry registry = registryWithPlan("tote-order-3", List.of());
        ThirdPartyArea area = area();
        ThirdPartyAreaController controller = controller(area, registry);

        area.submitVisit(visit("order-3", OrderType.ADAPTED, ThirdPartyWorkType.ADAPTED_PREPARATION, 1));
        controller.update(1d);

        assertEquals(1, registry.getLoadPlanFor("tote-order-3").getPackPlans().size());
        assertEquals(ThirdPartyWorkType.ADAPTED_PREPARATION,
                controller.completionForTote(new PhysicalToteId("tote-order-3")).orElseThrow()
                        .visit().lineWork().getFirst().workType());
        assertEquals(List.of("line-order-3"), controller.completedLineReferences().stream().toList());
    }

    @Test
    void shouldNotApplyARepeatedVisitCompletionTwice() {
        MapBackedToteLoadPlanRegistry registry = registryWithPlan("tote-order-4", List.of());
        ThirdPartyArea area = area();
        ThirdPartyAreaController controller = controller(area, registry);
        ThirdPartyVisit visit = visit("order-4", OrderType.FULL_PACK, ThirdPartyWorkType.DIRECT_FULFILMENT, 1);

        area.submitVisit(visit);
        controller.update(1d);
        area.submitVisit(visit);
        controller.update(1d);

        assertEquals(1, registry.getLoadPlanFor("tote-order-4").getPackPlans().size());
        assertEquals(1, controller.completedLineReferences().size());
    }

    @Test
    void shouldNotCreateProvenanceForMissingPhysicalPack() {
        ProductMasterRecord product = new ProductMasterRecord(
                "product-1", "Third Party Product", Optional.of("Y74"), Optional.empty());
        PackProvenanceRegistry provenanceRegistry = new PackProvenanceRegistry();
        ProductMasterThirdPartyPackPlanFactory factory = new ProductMasterThirdPartyPackPlanFactory(
                new InMemoryProductMasterRepository(List.of(product)),
                (visit, lineWork) -> visit.orderSheetKey().orderId(),
                new DspPackPlanFactory(provenanceRegistry));

        assertThrows(
                IllegalStateException.class,
                () -> factory.createPackPlan(
                        visit("order-5", OrderType.FULL_PACK, ThirdPartyWorkType.DIRECT_FULFILMENT, 1),
                        lineWork("order-5", ThirdPartyWorkType.DIRECT_FULFILMENT, 1),
                        1));
        assertEquals(0, provenanceRegistry.snapshot().provenanceByPackId().size());
    }

    private ThirdPartyAreaController controller(
            ThirdPartyArea area,
            MapBackedToteLoadPlanRegistry registry) {
        return controller(area, registry, new PackProvenanceRegistry());
    }

    private ThirdPartyAreaController controller(
            ThirdPartyArea area,
            MapBackedToteLoadPlanRegistry registry,
            PackProvenanceRegistry provenanceRegistry) {
        ProductMasterRecord product = new ProductMasterRecord(
                "product-1", "Third Party Product", Optional.of("Y74"), Optional.of(dimensions(0.25f)));
        return new ThirdPartyAreaController(
                area,
                registry,
                new ProductMasterThirdPartyPackPlanFactory(
                        new InMemoryProductMasterRepository(List.of(product)),
                        (visit, lineWork) -> visit.orderSheetKey().orderId(),
                        new DspPackPlanFactory(provenanceRegistry)));
    }

    private ThirdPartyArea area() {
        return new ThirdPartyArea(new ThirdPartyAreaConfig(1, 1, 1d));
    }

    private MapBackedToteLoadPlanRegistry registryWithPlan(String toteId, List<PackPlan> packPlans) {
        MapBackedToteLoadPlanRegistry registry = new MapBackedToteLoadPlanRegistry();
        registry.putLoadPlan(new ToteLoadPlan(toteId, packPlans));
        return registry;
    }

    private ThirdPartyVisit visit(
            String orderId,
            OrderType orderType,
            ThirdPartyWorkType workType,
            int quantity) {
        return new ThirdPartyVisit(
                new PhysicalToteId("tote-" + orderId),
                new ThirdPartyVisitPlan(
                        new OrderSheetKey(orderId, 1),
                        "SC-1",
                        orderType,
                        List.of(lineWork(orderId, workType, quantity))));
    }

    private ThirdPartyLineWork lineWork(String orderId, ThirdPartyWorkType workType, int quantity) {
        return new ThirdPartyLineWork(
                new DspOrderItem(
                        "line-" + orderId,
                        "product-1",
                        quantity,
                        "pharmacy-1",
                        "patient-" + orderId,
                        "prescription-" + orderId,
                        workType == ThirdPartyWorkType.ADAPTED_PREPARATION
                                ? DspOrderLineType.ADAPTED
                                : DspOrderLineType.FULL_PACK,
                        orderId,
                        1,
                        0),
                quantity,
                "Y74",
                workType);
    }

    private PackPlan pack(String packId, String correlationId, PackDimensions dimensions) {
        return new PackPlan(packId, correlationId, dimensions);
    }

    private PackDimensions dimensions(float length) {
        return new PackDimensions(length, 0.1f, 0.05f);
    }
}
