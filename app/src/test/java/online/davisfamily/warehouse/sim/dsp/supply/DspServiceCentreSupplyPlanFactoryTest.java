package online.davisfamily.warehouse.sim.dsp.supply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.io.DspDatasetLoadReport;
import online.davisfamily.warehouse.sim.dsp.io.LoadedDspData;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.osr.OsrInventoryConfig;

class DspServiceCentreSupplyPlanFactoryTest {
    private final DspServiceCentreSupplyPlanFactory factory = new DspServiceCentreSupplyPlanFactory();

    @Test
    void shouldOrderServiceCentresByDescendingRetainedPriority() {
        NotionalToteOrder lower = order("lower", "sc-lower", OrderType.FULL_PACK, 998, 0);
        NotionalToteOrder higher = order("higher", "sc-higher", OrderType.FULL_PACK, 999, 1);

        DspServiceCentreSupplyPlan plan = factory.create(
                data(List.of(lower, higher), List.of(
                        manifest("tote-lower", lower, OrderType.FULL_PACK, 0),
                        manifest("tote-higher", higher, OrderType.FULL_PACK, 1))),
                new OsrInventoryConfig(10, List.of("sc-higher")));

        assertEquals(List.of("sc-higher", "sc-lower"), plan.batches().stream()
                .map(ServiceCentreSupplyBatch::serviceCentreId)
                .toList());
        assertEquals("sc-higher", plan.findBatch(" sc-higher ").orElseThrow().serviceCentreId());
        assertEquals(List.of("sc-lower"), plan.postStartupBatches().stream()
                .map(ServiceCentreSupplyBatch::serviceCentreId)
                .toList());
    }

    @Test
    void shouldPreserveAdaptedFirstThenCombinedFulfilmentSourceOrder() {
        NotionalToteOrder associated = order(
                "associated", "sc-1", OrderType.ASSOCIATED, 999, 0);
        NotionalToteOrder fullPack = order(
                "full", "sc-1", OrderType.FULL_PACK, 999, 1);
        NotionalToteOrder adapted = order(
                "adapted", "sc-1", OrderType.ADAPTED, 999, 2);

        ServiceCentreSupplyBatch batch = factory.create(
                data(List.of(associated, fullPack, adapted), List.of(
                        manifest("tote-associated", associated, OrderType.ASSOCIATED, 20),
                        manifest("tote-full", fullPack, OrderType.FULL_PACK, 10),
                        manifest("tote-adapted", adapted, OrderType.ADAPTED, 30))),
                new OsrInventoryConfig(10, List.of("sc-1")))
                .batches().getFirst();

        assertEquals(List.of("tote-adapted", "tote-full", "tote-associated"),
                batch.physicalManifests().stream()
                        .map(manifest -> manifest.physicalToteId().value())
                        .toList());
    }

    @Test
    void shouldKeepEveryPhysicalManifestForARepeatedLogicalSheet() {
        NotionalToteOrder order = order("order-1", "sc-1", OrderType.ASSOCIATED, 999, 0);
        InboundToteManifest first = manifest("tote-1", order, OrderType.ASSOCIATED, 0);
        InboundToteManifest second = manifest("tote-2", order, OrderType.ASSOCIATED, 1);

        ServiceCentreSupplyBatch batch = factory.create(
                data(List.of(order), List.of(first, second)),
                new OsrInventoryConfig(10, List.of("sc-1")))
                .batches().getFirst();

        assertEquals(List.of("tote-1", "tote-2"), batch.physicalManifests().stream()
                .map(manifest -> manifest.physicalToteId().value())
                .toList());
        assertEquals(order.orderSheetKey(), batch.physicalManifests().getFirst().orderSheetKey());
    }

    @Test
    void shouldKeepEmptySheetsSeparateFromPhysicalManifests() {
        NotionalToteOrder empty = order("empty-1", "sc-1", OrderType.EMPTY, 999, 0);
        NotionalToteOrder fullPack = order("full-1", "sc-1", OrderType.FULL_PACK, 999, 1);

        ServiceCentreSupplyBatch batch = factory.create(
                data(List.of(empty, fullPack), List.of(
                        manifest("tote-full", fullPack, OrderType.FULL_PACK, 1))),
                new OsrInventoryConfig(10, List.of("sc-1")))
                .batches().getFirst();

        assertEquals(List.of("tote-full"), batch.physicalManifests().stream()
                .map(manifest -> manifest.physicalToteId().value())
                .toList());
        assertEquals(Set.of(empty.orderSheetKey()), batch.emptyOrderSheetKeys());
        assertTrue(batch.emptyOrderSheetKeys().stream().allMatch(key ->
                batch.physicalManifests().stream().noneMatch(manifest -> manifest.orderSheetKey().equals(key))));
    }

    @Test
    void shouldMarkConfiguredStartupBatches() {
        NotionalToteOrder preload = order("preload", "sc-preload", OrderType.FULL_PACK, 999, 0);
        NotionalToteOrder later = order("later", "sc-later", OrderType.FULL_PACK, 998, 1);

        DspServiceCentreSupplyPlan plan = factory.create(
                data(List.of(preload, later), List.of(
                        manifest("tote-preload", preload, OrderType.FULL_PACK, 0),
                        manifest("tote-later", later, OrderType.FULL_PACK, 1))),
                new OsrInventoryConfig(10, List.of("sc-preload")));

        assertTrue(plan.findBatch("sc-preload").orElseThrow().preloadedAtStart());
        assertFalse(plan.findBatch("sc-later").orElseThrow().preloadedAtStart());
    }

    @Test
    void shouldReportInconsistentPrioritiesUsingEarliestOrderPriority() {
        NotionalToteOrder earliest = order("earliest", "sc-1", OrderType.FULL_PACK, 998, 0);
        NotionalToteOrder inconsistent = order("inconsistent", "sc-1", OrderType.FULL_PACK, 997, 1);

        DspServiceCentreSupplyPlan plan = factory.create(
                data(List.of(earliest, inconsistent), List.of()),
                new OsrInventoryConfig(10, List.of("sc-1")));

        ServiceCentreSupplyIssue issue = plan.issues().stream()
                .filter(candidate -> candidate.type() == ServiceCentreSupplyIssueType.INCONSISTENT_PRIORITIES)
                .findFirst()
                .orElseThrow();
        assertEquals(998, plan.batches().getFirst().priority());
        assertEquals(998, issue.priority());
        assertEquals(List.of(997, 998), issue.observedPriorities());
        assertEquals(List.of("sc-1"), issue.serviceCentreIds());
    }

    @Test
    void shouldReportAndDeterministicallyResolveDuplicatePriorities() {
        NotionalToteOrder first = order("first", "sc-b", OrderType.FULL_PACK, 998, 0);
        NotionalToteOrder second = order("second", "sc-a", OrderType.FULL_PACK, 998, 1);

        DspServiceCentreSupplyPlan plan = factory.create(
                data(List.of(first, second), List.of()),
                new OsrInventoryConfig(10, List.of("sc-a", "sc-b")));

        assertEquals(List.of("sc-b", "sc-a"), plan.batches().stream()
                .map(ServiceCentreSupplyBatch::serviceCentreId)
                .toList());
        ServiceCentreSupplyIssue issue = plan.issues().stream()
                .filter(candidate -> candidate.type() == ServiceCentreSupplyIssueType.DUPLICATE_PRIORITY)
                .findFirst()
                .orElseThrow();
        assertEquals(List.of("sc-b", "sc-a"), issue.serviceCentreIds());
        assertEquals(List.of(998), issue.observedPriorities());
    }

    @Test
    void shouldRejectMissingPriorityAndUnknownPreloadCentre() {
        NotionalToteOrder unspecified = order("unspecified", "sc-1", OrderType.FULL_PACK, 0, 0);
        assertThrows(IllegalArgumentException.class,
                () -> factory.create(
                        data(List.of(unspecified), List.of()),
                        new OsrInventoryConfig(10, List.of())));

        NotionalToteOrder specified = order("specified", "sc-1", OrderType.FULL_PACK, 999, 0);
        assertThrows(IllegalArgumentException.class,
                () -> factory.create(
                        data(List.of(specified), List.of()),
                        new OsrInventoryConfig(10, List.of("missing"))));
    }

    private static LoadedDspData data(
            List<NotionalToteOrder> orders,
            List<InboundToteManifest> manifests) {
        return new LoadedDspData(
                List.of(),
                orders,
                List.of(),
                Set.of(),
                Set.of(),
                manifests,
                DspDatasetLoadReport.empty());
    }

    private static NotionalToteOrder order(
            String orderId,
            String serviceCentreId,
            OrderType orderType,
            int priority,
            long sequenceNumber) {
        return new NotionalToteOrder(
                orderId,
                "notional-" + orderId,
                serviceCentreId,
                1,
                orderType,
                List.of(item("line-" + orderId, orderId)),
                priority,
                sequenceNumber);
    }

    private static InboundToteManifest manifest(
            String physicalToteId,
            NotionalToteOrder order,
            OrderType orderType,
            long sourceSequenceNumber) {
        return new InboundToteManifest(
                new PhysicalToteId(physicalToteId),
                order.orderSheetKey(),
                orderType,
                order.serviceCentreId(),
                List.of(item("line-" + physicalToteId, order.orderId())),
                sourceSequenceNumber);
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
}
