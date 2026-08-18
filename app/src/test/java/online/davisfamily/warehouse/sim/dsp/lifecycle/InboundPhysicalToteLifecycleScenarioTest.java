package online.davisfamily.warehouse.sim.dsp.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingVisit;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingVisitFactory;
import online.davisfamily.warehouse.sim.dsp.adapting.MapBackedToteLoadPlanRegistry;
import online.davisfamily.warehouse.sim.dsp.io.DspDatasetAssembler;
import online.davisfamily.warehouse.sim.dsp.io.LoadedDspData;
import online.davisfamily.warehouse.sim.dsp.io.TwelveNFieldJson;
import online.davisfamily.warehouse.sim.dsp.io.TwelveNHeaderJson;
import online.davisfamily.warehouse.sim.dsp.io.TwelveNMessageJson;
import online.davisfamily.warehouse.sim.dsp.io.TwelveNMessageKindMapper;
import online.davisfamily.warehouse.sim.dsp.io.TwelveNOrderDetailJson;
import online.davisfamily.warehouse.sim.dsp.io.TwelveNOrderLineJson;
import online.davisfamily.warehouse.sim.dsp.io.TwelveNOrderMapper;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderValidator;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.ProductMasterRecord;
import online.davisfamily.warehouse.sim.dsp.routing.InMemoryProductMasterRepository;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyVisit;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyVisitFactory;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

class InboundPhysicalToteLifecycleScenarioTest {
    private static final String SERVICE_CENTRE_ID = "104";
    private static final String PHARMACY_ID = "0006461";

    private static final String ADAPTED_ORDER_ID = "ADAPTED-ORDER";
    private static final PhysicalToteId ADAPTED_TOTE_ID = new PhysicalToteId("PHYSICAL-ADAPTED-01");
    private static final String FULL_PACK_ORDER_ID = "FULL-PACK-ORDER";
    private static final PhysicalToteId FULL_PACK_TOTE_ID = new PhysicalToteId("PHYSICAL-FULL-01");
    private static final String ASSOCIATED_ORDER_ID = "ASSOCIATED-ORDER";
    private static final OrderSheetKey ASSOCIATED_SHEET = new OrderSheetKey(ASSOCIATED_ORDER_ID, 1);
    private static final PhysicalToteId ASSOCIATED_TOTE_ONE_ID = new PhysicalToteId("PHYSICAL-ASSOC-01");
    private static final PhysicalToteId ASSOCIATED_TOTE_TWO_ID = new PhysicalToteId("PHYSICAL-ASSOC-02");
    private static final String EMPTY_ORDER_ID = "EMPTY-ORDER";
    private static final PhysicalToteId AV02_TOTE_ID = new PhysicalToteId("PHYSICAL-AV02-01");

    private static final String ADAPTED_PRODUCT_ID = "PRODUCT-ADAPTED";
    private static final String FULL_PACK_PRODUCT_ID = "PRODUCT-FULL-PACK";
    private static final String ASSOCIATED_PRODUCT_ONE_ID = "PRODUCT-ASSOC-ONE";
    private static final String ASSOCIATED_PRODUCT_TWO_ID = "PRODUCT-ASSOC-TWO";
    private static final String EMPTY_PRODUCT_ID = "PRODUCT-EMPTY";
    private static final PackDimensions PACK_DIMENSIONS = new PackDimensions(0.18f, 0.08f, 0.05f);

    @Test
    void shouldLoadLogicalSheetsAndDistinctInboundPhysicalTotes() {
        Scenario scenario = scenario();

        assertEquals(List.of(
                        ADAPTED_TOTE_ID,
                        FULL_PACK_TOTE_ID,
                        ASSOCIATED_TOTE_ONE_ID,
                        ASSOCIATED_TOTE_TWO_ID),
                scenario.data().inboundToteManifests().stream()
                        .map(InboundToteManifest::physicalToteId)
                        .toList());
        assertTrue(scenario.data().inboundToteManifests().stream()
                .noneMatch(manifest -> manifest.physicalToteId().value()
                        .equals(manifest.orderSheetKey().orderId())));

        NotionalToteOrder associatedOrder = scenario.order(ASSOCIATED_ORDER_ID);
        List<InboundToteManifest> associatedManifests = scenario.catalog().manifestsFor(ASSOCIATED_SHEET);
        assertEquals(2, associatedOrder.items().size());
        assertEquals(2, associatedManifests.size());
        assertEquals(List.of("ASSOC-LINE-1"), lineReferences(associatedManifests.get(0)));
        assertEquals(List.of("ASSOC-LINE-2"), lineReferences(associatedManifests.get(1)));
        assertTrue(scenario.lifecycle().snapshot().assignments().isEmpty());
        assertTrue(scenario.lifecycle().snapshot().totes().values().stream()
                .allMatch(tote -> tote.role() == PhysicalToteRole.INBOUND_PACK));
    }

    @Test
    void shouldProcessSeveralInboundTotesForOneLogicalSheetSequentially() {
        Scenario scenario = scenario();
        InboundToteLifecycleController lifecycle = scenario.lifecycle();

        lifecycle.activate(ASSOCIATED_TOTE_ONE_ID, Duration.ZERO);
        assertThrows(IllegalStateException.class,
                () -> lifecycle.activate(ASSOCIATED_TOTE_TWO_ID, Duration.ofSeconds(1)));
        lifecycle.advanceToPreP2p(ASSOCIATED_TOTE_ONE_ID, Duration.ofSeconds(2));
        lifecycle.consumeAtP2p(ASSOCIATED_TOTE_ONE_ID, Duration.ofSeconds(3));

        lifecycle.activate(ASSOCIATED_TOTE_TWO_ID, Duration.ofSeconds(4));
        lifecycle.advanceToPreP2p(ASSOCIATED_TOTE_TWO_ID, Duration.ofSeconds(5));
        lifecycle.consumeAtP2p(ASSOCIATED_TOTE_TWO_ID, Duration.ofSeconds(6));

        List<PhysicalToteAssignment> history = lifecycle.snapshot().assignmentHistoryFor(ASSOCIATED_SHEET);
        assertEquals(List.of(
                        ASSOCIATED_TOTE_ONE_ID,
                        ASSOCIATED_TOTE_ONE_ID,
                        ASSOCIATED_TOTE_TWO_ID,
                        ASSOCIATED_TOTE_TWO_ID),
                history.stream().map(PhysicalToteAssignment::physicalToteId).toList());
        assertTrue(history.stream().noneMatch(PhysicalToteAssignment::active));
        assertEquals(PhysicalToteLifecycleState.CONSUMED_AT_P2P,
                lifecycle.snapshot().totes().get(ASSOCIATED_TOTE_ONE_ID).state());
        assertEquals(PhysicalToteLifecycleState.CONSUMED_AT_P2P,
                lifecycle.snapshot().totes().get(ASSOCIATED_TOTE_TWO_ID).state());
    }

    @Test
    void shouldConsumeAdaptedAndFulfilmentTotesAtTheirCorrectBoundaries() {
        Scenario scenario = scenario();
        InboundToteLifecycleController lifecycle = scenario.lifecycle();

        lifecycle.activate(ADAPTED_TOTE_ID, Duration.ZERO);
        lifecycle.consumeAtAdapting(ADAPTED_TOTE_ID, Duration.ofSeconds(1));
        lifecycle.activate(FULL_PACK_TOTE_ID, Duration.ofSeconds(2));
        lifecycle.advanceToPreP2p(FULL_PACK_TOTE_ID, Duration.ofSeconds(3));
        lifecycle.consumeAtP2p(FULL_PACK_TOTE_ID, Duration.ofSeconds(4));

        assertEquals(PhysicalToteLifecycleState.CONSUMED_AT_ADAPTING,
                lifecycle.snapshot().totes().get(ADAPTED_TOTE_ID).state());
        assertEquals(PhysicalToteLifecycleState.CONSUMED_AT_P2P,
                lifecycle.snapshot().totes().get(FULL_PACK_TOTE_ID).state());
        assertTrue(lifecycle.snapshot().totes().values().stream()
                .noneMatch(tote -> tote.role() == PhysicalToteRole.OUTBOUND_BAG));
    }

    @Test
    void shouldAllocateEmptyPhysicalToteOnlyAtAv02() {
        Scenario scenario = scenario();
        NotionalToteOrder emptyOrder = scenario.order(EMPTY_ORDER_ID);

        assertEquals(OrderType.EMPTY, emptyOrder.orderType());
        assertTrue(scenario.catalog().manifestsFor(emptyOrder.orderSheetKey()).isEmpty());
        assertFalse(scenario.lifecycle().snapshot().totes().containsKey(AV02_TOTE_ID));

        Av02ToteLifecycleController av02 = new Av02ToteLifecycleController(
                scenario.ledger(),
                () -> AV02_TOTE_ID);
        PhysicalToteRecord allocated = av02.allocateFor(emptyOrder, Duration.ofSeconds(5));

        assertEquals(PhysicalToteRole.PRE_P2P, allocated.role());
        assertEquals(PhysicalToteLifecycleState.ACTIVE_PRE_P2P, allocated.state());
        assertEquals(AV02_TOTE_ID, scenario.ledger().activeAssignmentFor(emptyOrder.orderSheetKey())
                .orElseThrow().physicalToteId());
        assertTrue(scenario.catalog().findByPhysicalToteId(AV02_TOTE_ID).isEmpty());
    }

    @Test
    void shouldUsePhysicalIdentityForStationVisitsAndLoadPlans() {
        Scenario scenario = scenario();
        NotionalToteOrder adaptedOrder = scenario.order(ADAPTED_ORDER_ID);
        NotionalToteOrder fullPackOrder = scenario.order(FULL_PACK_ORDER_ID);
        AdaptingVisit adaptingVisit = new AdaptingVisitFactory().create(ADAPTED_TOTE_ID, adaptedOrder);
        ThirdPartyVisit thirdPartyVisit = new ThirdPartyVisitFactory(
                new InMemoryProductMasterRepository(scenario.data().products()))
                .create(FULL_PACK_TOTE_ID, fullPackOrder)
                .orElseThrow();

        MapBackedToteLoadPlanRegistry loadPlans = new MapBackedToteLoadPlanRegistry();
        for (InboundToteManifest manifest : scenario.data().inboundToteManifests()) {
            loadPlans.putLoadPlan(new ToteLoadPlan(manifest.physicalToteId(), List.of()));
        }

        assertEquals(ADAPTED_TOTE_ID, adaptingVisit.physicalToteId());
        assertEquals(FULL_PACK_TOTE_ID, thirdPartyVisit.physicalToteId());
        assertEquals(FULL_PACK_ORDER_ID, thirdPartyVisit.orderSheetKey().orderId());
        assertEquals(ASSOCIATED_TOTE_ONE_ID,
                loadPlans.getLoadPlanFor(ASSOCIATED_TOTE_ONE_ID).physicalToteId());
        assertEquals(ASSOCIATED_TOTE_TWO_ID,
                loadPlans.getLoadPlanFor(ASSOCIATED_TOTE_TWO_ID).physicalToteId());
        assertNull(loadPlans.getLoadPlanFor(new PhysicalToteId(ASSOCIATED_ORDER_ID)));
        assertFalse(adaptingVisit.physicalToteId().value().equals(adaptedOrder.orderId()));
        assertFalse(thirdPartyVisit.physicalToteId().value().equals(fullPackOrder.orderId()));
    }

    private static Scenario scenario() {
        List<ProductMasterRecord> products = List.of(
                product(ADAPTED_PRODUCT_ID, Optional.empty()),
                product(FULL_PACK_PRODUCT_ID, Optional.of("Y74")),
                product(ASSOCIATED_PRODUCT_ONE_ID, Optional.empty()),
                product(ASSOCIATED_PRODUCT_TWO_ID, Optional.empty()),
                product(EMPTY_PRODUCT_ID, Optional.empty()));
        List<TwelveNMessageJson> messages = List.of(
                physicalMessage(
                        ADAPTED_ORDER_ID,
                        "001",
                        "02",
                        ADAPTED_TOTE_ID,
                        line("ADAPTED-LINE", "02", ADAPTED_PRODUCT_ID, ASSOCIATED_ORDER_ID)),
                physicalMessage(
                        FULL_PACK_ORDER_ID,
                        "001",
                        "05",
                        FULL_PACK_TOTE_ID,
                        line("FULL-PACK-LINE", "05", FULL_PACK_PRODUCT_ID, FULL_PACK_ORDER_ID)),
                physicalMessage(
                        ASSOCIATED_ORDER_ID,
                        "001",
                        "04",
                        ASSOCIATED_TOTE_ONE_ID,
                        line("ASSOC-LINE-1", "05", ASSOCIATED_PRODUCT_ONE_ID, ASSOCIATED_ORDER_ID)),
                physicalMessage(
                        ASSOCIATED_ORDER_ID,
                        "001",
                        "04",
                        ASSOCIATED_TOTE_TWO_ID,
                        line("ASSOC-LINE-2", "05", ASSOCIATED_PRODUCT_TWO_ID, ASSOCIATED_ORDER_ID)),
                emptyMessage());
        TwelveNMessageKindMapper kindMapper = new TwelveNMessageKindMapper();
        LoadedDspData data = new DspDatasetAssembler(
                kindMapper,
                new TwelveNOrderMapper(kindMapper),
                new DspOrderValidator())
                .assemble(products, messages);
        InboundToteManifestCatalog catalog = new InboundToteManifestCatalog(data.inboundToteManifests());
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        return new Scenario(data, catalog, ledger, new InboundToteLifecycleController(ledger, catalog));
    }

    private static ProductMasterRecord product(String productId, Optional<String> thirdPartyLocation) {
        return new ProductMasterRecord(
                productId,
                "Product " + productId,
                thirdPartyLocation,
                Optional.of(PACK_DIMENSIONS));
    }

    private static TwelveNMessageJson physicalMessage(
            String orderId,
            String sheetNumber,
            String toteType,
            PhysicalToteId physicalToteId,
            TwelveNOrderLineJson... lines) {
        return message(orderId, sheetNumber, toteType, field(physicalToteId.value()), lines);
    }

    private static TwelveNMessageJson emptyMessage() {
        return message(
                EMPTY_ORDER_ID,
                "001",
                "03",
                null,
                line("EMPTY-LINE", "05", EMPTY_PRODUCT_ID, EMPTY_ORDER_ID));
    }

    private static TwelveNMessageJson message(
            String orderId,
            String sheetNumber,
            String toteType,
            TwelveNFieldJson transportContainer,
            TwelveNOrderLineJson... lines) {
        return new TwelveNMessageJson(
                new TwelveNHeaderJson(null, null, orderId, sheetNumber),
                field(toteType),
                transportContainer,
                field("999"),
                null,
                field(SERVICE_CENTRE_ID),
                new TwelveNOrderDetailJson(
                        lines.length,
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
                        List.of(lines)));
    }

    private static TwelveNOrderLineJson line(
            String lineReference,
            String lineType,
            String productId,
            String referenceOrderId) {
        return new TwelveNOrderLineJson(
                lineReference,
                lineType,
                PHARMACY_ID,
                "PATIENT-1",
                "PRESCRIPTION-1",
                productId,
                "0001",
                "0000",
                referenceOrderId,
                "001",
                "0000");
    }

    private static TwelveNFieldJson field(String payload) {
        return new TwelveNFieldJson(null, null, payload);
    }

    private static List<String> lineReferences(InboundToteManifest manifest) {
        return manifest.items().stream().map(DspOrderItem::lineReference).toList();
    }

    private record Scenario(
            LoadedDspData data,
            InboundToteManifestCatalog catalog,
            PhysicalToteLifecycleLedger ledger,
            InboundToteLifecycleController lifecycle) {

        private NotionalToteOrder order(String orderId) {
            return data.orders().stream()
                    .filter(order -> order.orderId().equals(orderId))
                    .findFirst()
                    .orElseThrow();
        }
    }
}
