package online.davisfamily.warehouse.sim.dsp.osr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.io.DspDatasetLoadReport;
import online.davisfamily.warehouse.sim.dsp.io.LoadedDspData;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteLifecycleController;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifestCatalog;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteAssignmentStage;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleLedger;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleState;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRole;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

class OsrLifecycleIntegrationTest {

    @Test
    void shouldRegisterLoadedTotesWithoutTreatingAllAsStoredInOsr() {
        Scenario scenario = scenario();

        assertEquals(3, scenario.lifecycle().snapshot().totes().size());
        assertTrue(scenario.lifecycle().snapshot().totes().containsKey(scenario.laterManifest().physicalToteId()));
        assertTrue(scenario.lifecycle().snapshot().totes().values().stream()
                .allMatch(tote -> tote.role() == PhysicalToteRole.INBOUND_PACK
                        && tote.state() == PhysicalToteLifecycleState.INBOUND_PACK_TOTE));
        assertTrue(scenario.lifecycle().snapshot().assignments().isEmpty());
        assertEquals(2, scenario.bootstrap().inventorySnapshot().occupancy());
        assertFalse(scenario.bootstrap().inventorySnapshot().contains(
                scenario.laterManifest().physicalToteId()));
    }

    @Test
    void shouldKeepCandidateInspectionFreeOfInventoryMutation() {
        Scenario scenario = scenario();
        OsrInventorySnapshot beforeInspection = scenario.bootstrap().inventorySnapshot();

        InboundToteManifest candidate = beforeInspection.findStored(
                scenario.firstSharedManifest().physicalToteId()).orElseThrow();
        beforeInspection.storedTotesFor(candidate.orderSheetKey());
        beforeInspection.occupancyByServiceCentre();
        beforeInspection.occupancyByOrderType();

        assertEquals(beforeInspection, scenario.bootstrap().inventorySnapshot());
        assertEquals(2, scenario.bootstrap().inventorySnapshot().occupancy());
        assertTrue(scenario.lifecycle().snapshot().assignments().isEmpty());
    }

    @Test
    void shouldActivateExactPhysicalToteAfterCommittedOsrDeparture() {
        Scenario scenario = scenario();
        PhysicalToteId toteId = scenario.firstSharedManifest().physicalToteId();

        scenario.bootstrap().inventory().recordDeparture(toteId);

        assertEquals(1, scenario.bootstrap().inventorySnapshot().occupancy());
        assertTrue(scenario.lifecycle().snapshot().activeAssignmentFor(
                scenario.sharedSheet()).isEmpty());

        scenario.lifecycle().activate(toteId, Duration.ofSeconds(1));

        var assignment = scenario.lifecycle().snapshot()
                .activeAssignmentFor(scenario.sharedSheet())
                .orElseThrow();
        assertEquals(toteId, assignment.physicalToteId());
        assertEquals(PhysicalToteAssignmentStage.INBOUND_PACK, assignment.stage());
        assertTrue(scenario.bootstrap().inventorySnapshot().hasDeparted(toteId));
    }

    @Test
    void shouldPreserveOneActivePhysicalManifestPerLogicalSheet() {
        Scenario scenario = scenario();
        scenario.bootstrap().inventory().recordDeparture(
                scenario.firstSharedManifest().physicalToteId());
        scenario.lifecycle().activate(
                scenario.firstSharedManifest().physicalToteId(), Duration.ofSeconds(1));

        assertThrows(
                IllegalStateException.class,
                () -> scenario.lifecycle().activate(
                        scenario.secondSharedManifest().physicalToteId(), Duration.ofSeconds(2)));

        assertEquals(
                scenario.firstSharedManifest().physicalToteId(),
                scenario.lifecycle().snapshot().activeAssignmentFor(scenario.sharedSheet())
                        .orElseThrow().physicalToteId());
        assertTrue(scenario.bootstrap().inventorySnapshot().contains(
                scenario.secondSharedManifest().physicalToteId()));
    }

    @Test
    void shouldKeepAuthorizedEmptyOrderManifestFree() {
        Scenario scenario = scenario();

        assertEquals(Set.of(scenario.emptyOrder().orderSheetKey()),
                scenario.bootstrap().authorizedEmptyOrderSheetKeys());
        assertTrue(scenario.catalog().manifestsFor(scenario.emptyOrder().orderSheetKey()).isEmpty());
        assertTrue(scenario.ledger().activeAssignmentFor(
                scenario.emptyOrder().orderSheetKey()).isEmpty());
        assertTrue(scenario.ledger().snapshot().totes().keySet().stream()
                .noneMatch(toteId -> toteId.value().equals(scenario.emptyOrder().orderId())));
        assertTrue(scenario.bootstrap().inventorySnapshot().storedTotes().stream()
                .noneMatch(manifest -> manifest.orderType() == OrderType.EMPTY));
    }

    private static Scenario scenario() {
        OrderSheetKey sharedSheet = new OrderSheetKey("associated-1", 1);
        DspOrderItem firstItem = item("line-1", sharedSheet.orderId());
        DspOrderItem secondItem = item("line-2", sharedSheet.orderId());
        InboundToteManifest firstSharedManifest = manifest(
                "tote-1", sharedSheet, OrderType.ASSOCIATED, "104", firstItem, 1);
        InboundToteManifest secondSharedManifest = manifest(
                "tote-2", sharedSheet, OrderType.ASSOCIATED, "104", secondItem, 2);
        OrderSheetKey laterSheet = new OrderSheetKey("full-pack-later", 1);
        DspOrderItem laterItem = item("line-3", laterSheet.orderId());
        InboundToteManifest laterManifest = manifest(
                "tote-3", laterSheet, OrderType.FULL_PACK, "116", laterItem, 3);
        NotionalToteOrder emptyOrder = order(
                "empty-1", OrderType.EMPTY, "104", List.of(item("line-empty", "empty-1")), 4);
        NotionalToteOrder associatedOrder = order(
                sharedSheet.orderId(),
                OrderType.ASSOCIATED,
                "104",
                List.of(firstItem, secondItem),
                1);
        NotionalToteOrder laterOrder = order(
                laterSheet.orderId(), OrderType.FULL_PACK, "116", List.of(laterItem), 3);
        LoadedDspData data = new LoadedDspData(
                List.of(),
                List.of(associatedOrder, laterOrder, emptyOrder),
                List.of(),
                Set.of(),
                Set.of(),
                List.of(firstSharedManifest, secondSharedManifest, laterManifest),
                DspDatasetLoadReport.empty());
        InboundToteManifestCatalog catalog = new InboundToteManifestCatalog(
                data.inboundToteManifests());
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        InboundToteLifecycleController lifecycle = new InboundToteLifecycleController(
                ledger, catalog);
        OsrBootstrapState bootstrap = new OsrInventoryBootstrapFactory().create(
                data, new OsrInventoryConfig(5, List.of("104")));
        return new Scenario(
                sharedSheet,
                firstSharedManifest,
                secondSharedManifest,
                laterManifest,
                emptyOrder,
                catalog,
                ledger,
                lifecycle,
                bootstrap);
    }

    private static NotionalToteOrder order(
            String orderId,
            OrderType orderType,
            String serviceCentreId,
            List<DspOrderItem> items,
            long sequenceNumber) {
        return new NotionalToteOrder(
                orderId,
                "notional-" + orderId,
                serviceCentreId,
                1,
                orderType,
                items,
                sequenceNumber);
    }

    private static InboundToteManifest manifest(
            String physicalToteId,
            OrderSheetKey orderSheetKey,
            OrderType orderType,
            String serviceCentreId,
            DspOrderItem item,
            long sequenceNumber) {
        return new InboundToteManifest(
                new PhysicalToteId(physicalToteId),
                orderSheetKey,
                orderType,
                serviceCentreId,
                List.of(item),
                sequenceNumber);
    }

    private static DspOrderItem item(String lineReference, String referenceOrderId) {
        return new DspOrderItem(
                lineReference,
                "product-" + lineReference,
                1,
                "pharmacy-1",
                "patient-1",
                "prescription-" + lineReference,
                DspOrderLineType.FULL_PACK,
                referenceOrderId,
                1,
                1);
    }

    private record Scenario(
            OrderSheetKey sharedSheet,
            InboundToteManifest firstSharedManifest,
            InboundToteManifest secondSharedManifest,
            InboundToteManifest laterManifest,
            NotionalToteOrder emptyOrder,
            InboundToteManifestCatalog catalog,
            PhysicalToteLifecycleLedger ledger,
            InboundToteLifecycleController lifecycle,
            OsrBootstrapState bootstrap) {
    }
}
