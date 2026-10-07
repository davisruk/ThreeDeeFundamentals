package online.davisfamily.warehouse.sim.dsp.analysis.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingOrderPreparationCatalog;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingTargetSheetCatalog;
import online.davisfamily.warehouse.sim.dsp.bagging.BagKey;
import online.davisfamily.warehouse.sim.dsp.bagging.BagPlanningResult;
import online.davisfamily.warehouse.sim.dsp.bagging.BagSequencePosition;
import online.davisfamily.warehouse.sim.dsp.bagging.PackSourceProvenance;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedBag;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedPackSlot;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedPackSlotKey;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedPackTrace;
import online.davisfamily.warehouse.sim.dsp.io.LoadedDspData;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

class DspPreparedPackExceptionLedgerTest {
    private static final OrderSheetKey FIRST = new OrderSheetKey("target", 1);
    private static final OrderSheetKey LATER = new OrderSheetKey("target", 2);
    private static final PhysicalToteId FIRST_TOTE = new PhysicalToteId("tote-1");
    private static final PhysicalToteId LATER_TOTE = new PhysicalToteId("tote-2");
    private static final PackDimensions DIMENSIONS = new PackDimensions(0.2f, 0.1f, 0.08f);
    private static final BagKey PARTIAL = new BagKey("rx-partial", 1);
    private static final BagKey EMPTY = new BagKey("rx-empty", 1);
    private static final BagKey FIRST_BAG = new BagKey("rx-first", 1);

    @Test
    void outfeedPublishesOnceWithoutCopyingClassificationOrMutatingPriorCounts() {
        DspPreparedPackExceptionLedger ledger = fixture();
        ledger.commitCollect(ledger.prepareCollect(FIRST, FIRST_TOTE, allPacks()));
        var before = ledger.snapshot();
        ledger.confirmPdcCollection("pack-b");
        var first = ledger.snapshot();
        ledger.confirmPdcCollection("pack-c");
        var second = ledger.snapshot();

        assertEquals(before.version() + 1, first.version());
        assertEquals(before.version() + 2, second.version());
        assertSame(before.missingPhysicalPackIdsByBagKey(), second.missingPhysicalPackIdsByBagKey());
        assertSame(before.pendingEmptyBagKeys(), second.pendingEmptyBagKeys());
        assertSame(before.missingPackCountByServiceCentreId(), second.missingPackCountByServiceCentreId());
        assertSame(before.firstCollectedSheetByOrderId(), second.firstCollectedSheetByOrderId());
        assertEquals(Map.of(), before.pdcCollectedPackCountByServiceCentreId());
        assertEquals(Map.of("104", 1), first.pdcCollectedPackCountByServiceCentreId());
        assertEquals(Map.of("104", 2), second.pdcCollectedPackCountByServiceCentreId());
        assertThrows(IllegalStateException.class, () -> ledger.confirmPdcCollection("pack-b"));
        assertThrows(IllegalStateException.class, () -> ledger.confirmPdcCollection(" pack-c "));
        assertThrows(IllegalStateException.class, () -> ledger.confirmPdcCollection("unknown"));
        assertSame(second, ledger.snapshot());
        assertEquals(Map.of("104", 2), ledger.snapshot().pdcCollectedPackCountByServiceCentreId());
    }

    @Test
    void publishesFirstCollectOnlyAtCommitWithExactPackBagToteAndCentreIdentity() {
        DspPreparedPackExceptionLedger ledger = fixture();
        var initial = ledger.snapshot();
        var decision = ledger.prepareCollect(FIRST, FIRST_TOTE, allPacks());
        assertSame(initial, ledger.snapshot());
        assertFalse(ledger.isMisplaced("pack-b"));
        assertEquals(Map.of(), ledger.snapshot().firstCollectedSheetByOrderId());

        ledger.commitCollect(decision);
        var published = ledger.snapshot();
        assertEquals(Map.of("target", FIRST), published.firstCollectedSheetByOrderId());
        assertEquals(2, published.missingPackCountByServiceCentreId().get("104").intValue());
        assertEquals(Set.of("pack-b"), ledger.missingPackIdsFor(PARTIAL.correlationId()));
        assertEquals(1, ledger.effectivePackCount(PARTIAL.correlationId(), 2));
        assertEquals(0, ledger.effectivePackCount(EMPTY.correlationId(), 1));
        assertEquals(Set.of(EMPTY), ledger.pendingEmptyBagKeys());
        var misplaced = ledger.misplacedPack("pack-b").orElseThrow();
        assertEquals(LATER, misplaced.intendedSheet());
        assertEquals(FIRST_TOTE, misplaced.receivingTote());
        assertEquals(PARTIAL.correlationId(), misplaced.correlationId());
        assertEquals("104", misplaced.serviceCentreId());
        assertEquals("store-1", misplaced.storeId());
        assertSame(published, ledger.snapshot());

        ledger.commitCollect(ledger.prepareCollect(LATER, LATER_TOTE, List.of()));
        assertFalse(ledger.allowEmptyTote(LATER_TOTE.value())); // Direct pack D still belongs to this tote.
        assertFalse(ledger.allowEmptyTote("unknown"));
        assertSame(ledger.snapshot(), ledger.snapshot());
        ledger.confirmPdcCollection("pack-b");
        ledger.confirmPdcCollection("pack-c");
        assertEquals(2, ledger.snapshot().pdcCollectedPackCountByServiceCentreId().get("104").intValue());
        assertEquals(2, ledger.snapshot().missingPackCountByServiceCentreId().get("104").intValue());
        assertThrows(IllegalStateException.class, () -> ledger.confirmPdcCollection("pack-b"));
    }

    @Test
    void rejectsWrongSheetUnknownDuplicateAndIncompletePreviewWithoutMutation() {
        DspPreparedPackExceptionLedger ledger = fixture();
        var original = ledger.snapshot();
        assertThrows(IllegalStateException.class,
                () -> ledger.prepareCollect(LATER, LATER_TOTE, List.of()));
        assertThrows(IllegalStateException.class,
                () -> ledger.prepareCollect(FIRST, FIRST_TOTE, List.of(pack("pack-a", FIRST_BAG))));
        assertThrows(IllegalStateException.class,
                () -> ledger.prepareCollect(FIRST, FIRST_TOTE, List.of(
                        pack("pack-a", FIRST_BAG), pack("pack-b", PARTIAL), pack("pack-b", PARTIAL))));
        assertThrows(IllegalStateException.class,
                () -> ledger.prepareCollect(FIRST, FIRST_TOTE, List.of(
                        pack("pack-a", FIRST_BAG), pack("pack-b", PARTIAL), pack("alien", EMPTY))));
        assertThrows(IllegalStateException.class,
                () -> ledger.prepareCollect(FIRST, FIRST_TOTE, List.of(
                        pack("pack-a", FIRST_BAG), pack("pack-b", EMPTY), pack("pack-c", EMPTY))));
        assertThrows(IllegalStateException.class, () -> ledger.confirmPdcCollection("pack-a"));
        assertSame(original, ledger.snapshot());
        assertFalse(ledger.allowEmptyTote(LATER_TOTE.value()));
    }

    @Test
    void rejectsDimensionMismatchWithExactDetailsWithoutPublishingCollect() {
        DspPreparedPackExceptionLedger ledger = fixture();
        var initial = ledger.snapshot();
        PackDimensions actual = new PackDimensions(0.07f, 0.034f, 0.027f);
        var failure = assertThrows(IllegalStateException.class,
                () -> ledger.prepareCollect(FIRST, FIRST_TOTE, List.of(
                        pack("pack-a", FIRST_BAG),
                        new PackPlan("pack-b", PARTIAL.correlationId(), actual),
                        pack("pack-c", EMPTY))));

        assertEquals("Collected pack dimensions do not match planned slot: pack-b"
                + " expected=" + DIMENSIONS + " actual=" + actual, failure.getMessage());
        assertSame(initial, ledger.snapshot());
        assertFalse(ledger.isMisplaced("pack-b"));
        assertEquals(Map.of(), ledger.snapshot().firstCollectedSheetByOrderId());
        assertEquals(Map.of(), ledger.snapshot().missingPhysicalPackIdsByBagKey());
        ledger.commitCollect(ledger.prepareCollect(FIRST, FIRST_TOTE, allPacks()));
        assertTrue(ledger.isMisplaced("pack-b"));
    }

    @Test
    void rejectsStaleDecisionAndNonemptyLaterCollect() {
        DspPreparedPackExceptionLedger ledger = fixture();
        var decision = ledger.prepareCollect(FIRST, FIRST_TOTE, allPacks());
        ledger.commitCollect(decision);
        var published = ledger.snapshot();
        assertThrows(IllegalStateException.class, () -> ledger.commitCollect(decision));
        assertThrows(IllegalStateException.class,
                () -> ledger.prepareCollect(LATER, LATER_TOTE, List.of(pack("pack-b", PARTIAL))));
        assertSame(published, ledger.snapshot());
    }

    @Test
    void lowestExecutableSheetCanBe002() {
        AdaptingTargetSheetCatalog targetCatalog = new AdaptingTargetSheetCatalog(
                Map.of(new PreparedLineKey("target", "B"), LATER));
        LoadedDspData data = new LoadedDspData(List.of(), List.of(
                order("source", 1, OrderType.ADAPTED, List.of(line("B", "target", "rx-empty"))),
                order("target", 2, OrderType.ASSOCIATED, List.of(line("B", "source", "rx-empty")))),
                List.of(), Set.of());
        BagPlanningResult bagPlan = new BagPlanningResult(
                List.of(new PlannedBag(EMPTY, "104", "store-1", "patient", "rx-empty",
                        List.of("pack-b"), List.of(LATER))),
                List.of(), List.of(), List.of(slot("B", "pack-b", LATER, EMPTY)),
                List.of(new BagSequencePosition(1, 1)));
        DspPreparedPackExceptionLedger ledger = new DspPreparedPackExceptionLedger(
                bagPlan, targetCatalog, new AdaptingOrderPreparationCatalog(data, targetCatalog));

        ledger.commitCollect(ledger.prepareCollect(LATER, LATER_TOTE, List.of(pack("pack-b", EMPTY))));

        assertEquals(LATER, ledger.snapshot().firstCollectedSheetByOrderId().get("target"));
        assertEquals(Map.of(), ledger.snapshot().missingPhysicalPackIdsByBagKey());
    }

    private static DspPreparedPackExceptionLedger fixture() {
        Map<PreparedLineKey, OrderSheetKey> targets = new LinkedHashMap<>();
        targets.put(new PreparedLineKey("target", "A"), FIRST);
        targets.put(new PreparedLineKey("target", "B"), LATER);
        targets.put(new PreparedLineKey("target", "C"), LATER);
        AdaptingTargetSheetCatalog targetCatalog = new AdaptingTargetSheetCatalog(targets);
        LoadedDspData data = new LoadedDspData(List.of(), List.of(
                order("source", 1, OrderType.ADAPTED, List.of(
                        line("A", "target", "rx-partial"),
                        line("B", "target", "rx-partial"),
                        line("C", "target", "rx-empty"))),
                order("target", 1, OrderType.ASSOCIATED, List.of(
                        line("A", "source", "rx-partial"))),
                order("target", 2, OrderType.ASSOCIATED, List.of(
                        line("B", "source", "rx-partial"),
                        line("C", "source", "rx-empty"),
                        new DspOrderItem("D", "product", 1, "store-1", "patient", "rx-partial",
                                DspOrderLineType.FULL_PACK, "target", 1, 0)))), List.of(), Set.of());
        PlannedPackSlot direct = new PlannedPackSlot(
                new PlannedPackSlotKey(LATER, "D", 1), "pack-d", DIMENSIONS,
                new PackSourceProvenance(LATER, "D", "product", "104", "store-1",
                        "patient", PARTIAL.prescriptionId()), LATER, Optional.of(LATER_TOTE), PARTIAL);
        AdaptingOrderPreparationCatalog orderCatalog = new AdaptingOrderPreparationCatalog(data, targetCatalog);
        BagPlanningResult bagPlan = new BagPlanningResult(
                List.of(new PlannedBag(FIRST_BAG, "104", "store-1", "patient", "rx-first",
                                List.of("pack-a"), List.of(FIRST)),
                        new PlannedBag(PARTIAL, "104", "store-1", "patient", "rx-partial",
                                List.of("pack-b", "pack-d"), List.of(LATER)),
                        new PlannedBag(EMPTY, "104", "store-1", "patient", "rx-empty",
                                List.of("pack-c"), List.of(LATER))),
                List.of(new ToteLoadPlan(LATER_TOTE, List.of(pack("pack-d", PARTIAL)))),
                List.of(new PlannedPackTrace("pack-d", direct.sourceProvenance(),
                        LATER_TOTE, LATER, PARTIAL)),
                List.of(slot("A", "pack-a", FIRST, FIRST_BAG),
                        slot("B", "pack-b", LATER, PARTIAL),
                        direct,
                        slot("C", "pack-c", LATER, EMPTY)),
                List.of(new BagSequencePosition(1, 1), new BagSequencePosition(1, 1),
                        new BagSequencePosition(1, 1)));
        return new DspPreparedPackExceptionLedger(bagPlan, targetCatalog, orderCatalog);
    }

    private static PlannedPackSlot slot(String line, String packId, OrderSheetKey sheet, BagKey bag) {
        OrderSheetKey source = new OrderSheetKey("source", 1);
        return new PlannedPackSlot(new PlannedPackSlotKey(source, line, 1), packId, DIMENSIONS,
                new PackSourceProvenance(source, line, "product", "104", "store-1", "patient",
                        bag.prescriptionId()), sheet, Optional.empty(), bag);
    }

    private static List<PackPlan> allPacks() {
        return List.of(pack("pack-a", FIRST_BAG), pack("pack-b", PARTIAL), pack("pack-c", EMPTY));
    }

    private static PackPlan pack(String id, BagKey bag) {
        return new PackPlan(id, bag.correlationId(), DIMENSIONS);
    }

    private static NotionalToteOrder order(String id, int sheet, OrderType type, List<DspOrderItem> lines) {
        return new NotionalToteOrder(id, id, "104", sheet, type, lines, 999, sheet);
    }

    private static DspOrderItem line(String reference, String target, String prescription) {
        return new DspOrderItem(reference, "product", 1, "store-1", "patient", prescription,
                DspOrderLineType.ADAPTED, target, 1, 0);
    }
}
