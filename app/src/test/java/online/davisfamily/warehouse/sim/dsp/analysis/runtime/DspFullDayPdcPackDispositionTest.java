package online.davisfamily.warehouse.sim.dsp.analysis.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingOrderPreparationCatalog;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingTargetSheetCatalog;
import online.davisfamily.warehouse.sim.dsp.bagging.*;
import online.davisfamily.warehouse.sim.dsp.io.LoadedDspData;
import online.davisfamily.warehouse.sim.dsp.model.*;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;
import online.davisfamily.warehouse.sim.totebag.assignment.PrlState;
import online.davisfamily.warehouse.sim.totebag.assignment.ToteToBagAssignmentPlanner;
import online.davisfamily.warehouse.sim.totebag.control.PdcPackDispositionPolicy;
import online.davisfamily.warehouse.sim.totebag.control.ToteToBagFlowController;
import online.davisfamily.warehouse.sim.totebag.conveyor.*;
import online.davisfamily.warehouse.sim.totebag.device.PdcDiversionDevice;
import online.davisfamily.warehouse.sim.totebag.handoff.StoredBagReceiver;
import online.davisfamily.warehouse.sim.totebag.machine.*;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.*;

/** Stops at StoredBagReceiver: Step 4 owns partial-bag outbound allocation. */
class DspFullDayPdcPackDispositionTest {
    private static final OrderSheetKey FIRST = new OrderSheetKey("target", 1);
    private static final OrderSheetKey LATER = new OrderSheetKey("target", 2);
    private static final OrderSheetKey SOURCE = new OrderSheetKey("source", 1);
    private static final PhysicalToteId FIRST_TOTE = new PhysicalToteId("tote-1");
    private static final PhysicalToteId LATER_TOTE = new PhysicalToteId("tote-2");
    private static final BagKey FIRST_BAG = new BagKey("rx-first", 1);
    private static final BagKey PARTIAL = new BagKey("rx-partial", 1);
    private static final BagKey EMPTY = new BagKey("rx-empty", 1);
    private static final PackDimensions DIMENSIONS = new PackDimensions(0.2f, 0.1f, 0.08f);

    @Test
    void sameLineSheetsBypassWrongPacksAndBagOnlyAvailableContents() { exerciseSheets(false); }

    @Test
    void differentLinesNeverAssignThePassingWrongBagToTheFirstLine() { exerciseSheets(true); }

    private static void exerciseSheets(boolean differentLines) {
        DspPreparedPackExceptionLedger ledger = ledger(true);
        CountingPolicy policy = new CountingPolicy(new DspFullDayPdcPackDispositionPolicy(ledger));
        Map<String, Integer> laterCounts = Map.of(PARTIAL.correlationId(), 2, EMPTY.correlationId(), 1);
        Flow first = flow("first", differentLines ? Map.of(FIRST_BAG.correlationId(), 1)
                : Map.of(FIRST_BAG.correlationId(), 1, PARTIAL.correlationId(), 2, EMPTY.correlationId(), 1), policy);
        Flow later = differentLines ? flow("later", laterCounts, policy) : first;
        first.world().update(0.05d);
        if (differentLines) { later.world().update(0.05d); }
        assertEquals(differentLines ? 2 : 3, later.controller().getOutstandingExpectedBagGroupCount());
        assertEquals(PrlState.IDLE, first.prl().getAssignment().getState());

        ledger.commitCollect(ledger.prepareCollect(FIRST, FIRST_TOTE, preparedPacks()));
        first.world().update(0.05d);
        if (differentLines) { later.world().update(0.05d); }
        assertEquals(differentLines ? 1 : 2, later.controller().getOutstandingExpectedBagGroupCount());
        var classification = ledger.snapshot();
        assertEquals(1, policy.classificationEpoch());
        assertEquals(Set.of(EMPTY), ledger.pendingEmptyBagKeys());
        ToteLoadPlan firstPlan = new ToteLoadPlan(FIRST_TOTE, preparedPacks());
        assertTrue(first.controller().canAdmit(firstPlan));
        first.tipper().loadTote(firstPlan);
        advanceUntil(first, () -> first.receiver().getReceivedBags().size() == 1
                && ledger.snapshot().pdcCollectedPackCountByServiceCentreId().getOrDefault("104", 0) == 2
                && first.tipper().isIdle());

        assertEquals(List.of(FIRST_BAG.correlationId()), first.receiver().getCompletedCorrelationIds());
        assertEquals(List.of("pack-a"), first.receiver().getReceivedBags().getFirst().getPackContents()
                .stream().map(PackPlan::packId).toList());
        assertEquals(List.of(FIRST_BAG.correlationId()), first.controller().getReleasedGroups()
                .stream().map(group -> group.correlationId()).toList());
        assertTrue(first.controller().getPdcLaneEntries().isEmpty());
        assertEquals(PrlState.IDLE, first.prl().getAssignment().getState());
        assertEquals(LATER, ledger.misplacedPack("pack-b").orElseThrow().intendedSheet());
        assertEquals(FIRST_TOTE, ledger.misplacedPack("pack-b").orElseThrow().receivingTote());
        assertEquals(PARTIAL, ledger.misplacedPack("pack-b").orElseThrow().bagKey());
        assertSame(classification.missingPhysicalPackIdsByBagKey(), ledger.snapshot().missingPhysicalPackIdsByBagKey());
        assertEquals(Map.of(), classification.pdcCollectedPackCountByServiceCentreId());
        assertEquals(1, policy.classificationEpoch());
        Map<String, Integer> queriesAfterOutfeed = Map.copyOf(policy.countQueries);
        var afterOutfeed = ledger.snapshot();
        for (int i = 0; i < 20; i++) {
            first.world().update(0.05d);
            if (differentLines) { later.world().update(0.05d); }
        }
        assertSame(afterOutfeed, ledger.snapshot());
        assertEquals(queriesAfterOutfeed, policy.countQueries);

        // Later COLLECT changes the general version, not classification or assigned thresholds.
        ledger.commitCollect(ledger.prepareCollect(LATER, LATER_TOTE, List.of()));
        assertEquals(1, policy.classificationEpoch());
        assertFalse(policy.allowEmptyTote(LATER_TOTE.value())); // Direct D is still available.
        ToteLoadPlan laterPlan = new ToteLoadPlan(LATER_TOTE, List.of(pack("pack-d", PARTIAL)));
        assertTrue(later.controller().canAdmit(laterPlan));
        assertEquals(queriesAfterOutfeed, policy.countQueries);
        later.tipper().loadTote(laterPlan);
        advanceUntil(later, () -> later.receiver().getCompletedCorrelationIds().contains(PARTIAL.correlationId())
                && later.tipper().isIdle());
        var partialBag = later.receiver().getReceivedBags().stream()
                .filter(bag -> bag.getCorrelationId().equals(PARTIAL.correlationId())).findFirst().orElseThrow();
        assertEquals(1, partialBag.getPackCount());
        assertEquals(List.of("pack-d"), partialBag.getPackContents().stream().map(PackPlan::packId).toList());
        assertFalse(later.receiver().getCompletedCorrelationIds().contains(EMPTY.correlationId()));
        assertEquals(0, later.controller().getOutstandingExpectedBagGroupCount());
        assertEquals(Map.of("104", 2), ledger.snapshot().pdcCollectedPackCountByServiceCentreId());
        assertEquals(Set.of("pack-b"), ledger.missingPackIdsFor(PARTIAL.correlationId()));
        assertEquals(Set.of("pack-c"), ledger.missingPackIdsFor(EMPTY.correlationId()));
        assertThrows(IllegalStateException.class, () -> policy.collectedAtPdcOutfeed("pack-b"));
    }

    @Test
    void publishedZeroPackWorkBecomesTerminalWithoutArrivalAndExactEmptyLaterToteCompletesTipper() {
        DspPreparedPackExceptionLedger ledger = ledger(false);
        CountingPolicy policy = new CountingPolicy(new DspFullDayPdcPackDispositionPolicy(ledger));
        Flow later = flow("later", Map.of(PARTIAL.correlationId(), 1, EMPTY.correlationId(), 1), policy);
        later.world().update(0.05d);
        assertEquals(2, later.controller().getOutstandingExpectedBagGroupCount());
        assertFalse(policy.allowEmptyTote(LATER_TOTE.value()));
        ledger.commitCollect(ledger.prepareCollect(FIRST, FIRST_TOTE, preparedPacks()));
        later.world().update(0.05d);
        assertEquals(0, later.controller().getOutstandingExpectedBagGroupCount());
        assertEquals(Set.of(PARTIAL, EMPTY), ledger.pendingEmptyBagKeys());
        assertTrue(later.controller().getObservedPacks().isEmpty());
        assertTrue(later.receiver().getReceivedBags().isEmpty());
        ledger.commitCollect(ledger.prepareCollect(LATER, LATER_TOTE, List.of()));
        ToteLoadPlan empty = new ToteLoadPlan(LATER_TOTE, List.of());
        assertTrue(later.controller().canAdmit(empty));
        assertFalse(later.controller().canAdmit(new ToteLoadPlan("unrecorded-tote", List.of())));
        later.tipper().loadTote(empty);
        advanceUntil(later, later.tipper()::isIdle);
        assertNull(later.tipper().getActiveToteId());
        assertTrue(later.controller().getObservedPacks().isEmpty());
        assertTrue(later.controller().getReleasedGroups().isEmpty());
        assertTrue(later.receiver().getReceivedBags().isEmpty());
        assertEquals(0, later.controller().getOutstandingExpectedBagGroupCount());
        assertEquals(PrlState.IDLE, later.prl().getAssignment().getState());
        var snapshot = ledger.snapshot();
        Map<String, Integer> queries = Map.copyOf(policy.countQueries);
        for (int i = 0; i < 20; i++) { later.world().update(0.05d); }
        assertSame(snapshot, ledger.snapshot());
        assertEquals(queries, policy.countQueries);
    }

    @Test
    void pdcOnlyPublicationDoesNotReclassifyAndNewAssignedCorrelationIsCheckedOnce() {
        DspPreparedPackExceptionLedger ledger = ledger(true);
        CountingPolicy policy = new CountingPolicy(new DspFullDayPdcPackDispositionPolicy(ledger));
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put(EMPTY.correlationId(), 1);
        Flow line = flow("later", counts, policy);
        line.world().update(0.05d);
        ledger.commitCollect(ledger.prepareCollect(FIRST, FIRST_TOTE, preparedPacks()));
        line.world().update(0.05d);
        assertEquals(Map.of(EMPTY.correlationId(), 2), policy.countQueries);
        var before = ledger.snapshot();
        policy.collectedAtPdcOutfeed("pack-c");
        var after = ledger.snapshot();
        assertNotSame(before, after);
        assertEquals(before.version() + 1, after.version());
        assertSame(before.firstCollectedSheetByOrderId(), after.firstCollectedSheetByOrderId());
        for (int i = 0; i < 20; i++) { line.world().update(0.05d); }
        assertEquals(Map.of(EMPTY.correlationId(), 2), policy.countQueries);
        assertSame(after, ledger.snapshot());
        counts.put(PARTIAL.correlationId(), 2);
        line.world().update(0.05d);
        line.world().update(0.05d);
        assertEquals(Map.of(EMPTY.correlationId(), 2, PARTIAL.correlationId(), 1), policy.countQueries);
        assertEquals(1, line.controller().getOutstandingExpectedBagGroupCount());
    }

    private static Flow flow(String id, Map<String, Integer> counts, PdcPackDispositionPolicy policy) {
        ToteToBagWorkPlanProvider work = new ToteToBagWorkPlanProvider() {
            @Override public OptionalInt expectedPackCount(String correlation) {
                Integer count = counts.get(correlation);
                return count == null ? OptionalInt.empty() : OptionalInt.of(count);
            }
            @Override public Set<String> expectedCorrelationIds() { return counts.keySet(); }
        };
        TippingMachine tipper = new TippingMachine(id + "-tipper", 0.05d, 0.05d, 0.05d);
        // At 1 m/s, a 0.20 m pack plus the 0.05 m PDC gap needs 0.25 s between releases.
        SortingMachine sorter = new SortingMachine(id + "-sorter", 0.25d);
        PdcConveyor pdc = new PdcConveyor(id + "-pdc", new ConveyorOccupancyModel(2f, 0.05f, 0f), 1f);
        PrlConveyor prl = new PrlConveyor(id + "-prl", 0.1f, new ConveyorOccupancyModel(2f, 0.05f, 0f));
        PcrConveyor pcr = new PcrConveyor(id + "-pcr", new ConveyorOccupancyModel(2f, 0.05f, 0f), 0.1d);
        StoredBagReceiver receiver = new StoredBagReceiver(id + "-receiver");
        BaggingMachine bagger = new BaggingMachine(id + "-bagger", new BagSpec(0.34f, 0.28f, 0.22f),
                0.05d, 0.05d, 0.05d, 0.05d, receiver);
        ToteToBagFlowController controller = new ToteToBagFlowController(work, tipper, sorter, pdc, pcr,
                bagger, new ToteToBagAssignmentPlanner(), List.of(prl),
                List.of(new PdcDiversionDevice(id + "-diverter", prl.getId(), 0d, 0.05d, 0.05d)),
                ignored -> 0.05d, (ignored, pack) -> pack.getDimensions().length(), ignored -> 0.05d,
                (ignored, pack) -> pack.getDimensions().length(), policy);
        SimulationWorld world = new SimulationWorld();
        world.addSimObject(tipper);
        world.addSimObject(sorter);
        world.addSimObject(pcr);
        world.addSimObject(bagger);
        world.addController(controller);
        return new Flow(world, controller, tipper, receiver, prl);
    }

    private static void advanceUntil(Flow flow, BooleanSupplier condition) {
        for (int i = 0; i < 500 && !condition.getAsBoolean(); i++) { flow.world().update(0.05d); }
        assertTrue(condition.getAsBoolean(), "Expected bounded physical flow to complete");
    }

    private record Flow(SimulationWorld world, ToteToBagFlowController controller,
            TippingMachine tipper, StoredBagReceiver receiver, PrlConveyor prl) { }

    private static DspPreparedPackExceptionLedger ledger(boolean withDirect) {
        AdaptingTargetSheetCatalog targets = new AdaptingTargetSheetCatalog(Map.of(
                new PreparedLineKey("target", "A"), FIRST,
                new PreparedLineKey("target", "B"), LATER,
                new PreparedLineKey("target", "C"), LATER));
        LoadedDspData data = new LoadedDspData(List.of(), List.of(
                order("source", 1, OrderType.ADAPTED, List.of(line("A", "target", FIRST_BAG),
                        line("B", "target", PARTIAL), line("C", "target", EMPTY))),
                order("target", 1, OrderType.ASSOCIATED, List.of(line("A", "source", FIRST_BAG))),
                order("target", 2, OrderType.ASSOCIATED, List.of(line("B", "source", PARTIAL),
                        line("C", "source", EMPTY)))), List.of(), Set.of());
        PlannedPackSlot direct = slot("D", "pack-d", LATER, LATER, PARTIAL, Optional.of(LATER_TOTE));
        List<PlannedPackSlot> slots = new java.util.ArrayList<>(List.of(
                slot("A", "pack-a", SOURCE, FIRST, FIRST_BAG, Optional.empty()),
                slot("B", "pack-b", SOURCE, LATER, PARTIAL, Optional.empty()),
                slot("C", "pack-c", SOURCE, LATER, EMPTY, Optional.empty())));
        if (withDirect) { slots.add(direct); }
        BagPlanningResult bagPlan = new BagPlanningResult(List.of(
                bag(FIRST_BAG, FIRST, List.of("pack-a")),
                bag(PARTIAL, LATER, withDirect ? List.of("pack-b", "pack-d") : List.of("pack-b")),
                bag(EMPTY, LATER, List.of("pack-c"))),
                withDirect ? List.of(new ToteLoadPlan(LATER_TOTE, List.of(pack("pack-d", PARTIAL)))) : List.of(),
                withDirect ? List.of(new PlannedPackTrace("pack-d", direct.sourceProvenance(),
                        LATER_TOTE, LATER, PARTIAL)) : List.of(), slots,
                List.of(new BagSequencePosition(1, 1), new BagSequencePosition(1, 1), new BagSequencePosition(1, 1)));
        return new DspPreparedPackExceptionLedger(bagPlan, targets, new AdaptingOrderPreparationCatalog(data, targets));
    }

    private static PlannedBag bag(BagKey key, OrderSheetKey sheet, List<String> ids) {
        return new PlannedBag(key, "104", "store", "patient-" + key.prescriptionId(),
                key.prescriptionId(), ids, List.of(sheet));
    }

    private static PlannedPackSlot slot(String line, String id, OrderSheetKey source,
            OrderSheetKey target, BagKey bag, Optional<PhysicalToteId> tote) {
        return new PlannedPackSlot(new PlannedPackSlotKey(source, line, 1), id, DIMENSIONS,
                new PackSourceProvenance(source, line, "product", "104", "store",
                        "patient-" + bag.prescriptionId(), bag.prescriptionId()), target, tote, bag);
    }

    private static NotionalToteOrder order(String id, int sheet, OrderType type, List<DspOrderItem> lines) {
        return new NotionalToteOrder(id, id, "104", sheet, type, lines, 999, sheet);
    }

    private static DspOrderItem line(String reference, String target, BagKey bag) {
        return new DspOrderItem(reference, "product", 1, "store", "patient-" + bag.prescriptionId(),
                bag.prescriptionId(), DspOrderLineType.ADAPTED, target, 1, 0);
    }

    private static PackPlan pack(String id, BagKey bag) { return new PackPlan(id, bag.correlationId(), DIMENSIONS); }

    private static List<PackPlan> preparedPacks() {
        return List.of(pack("pack-a", FIRST_BAG), pack("pack-b", PARTIAL), pack("pack-c", EMPTY));
    }

    private static final class CountingPolicy implements PdcPackDispositionPolicy {
        private final PdcPackDispositionPolicy delegate;
        private final Map<String, Integer> countQueries = new LinkedHashMap<>();
        private CountingPolicy(PdcPackDispositionPolicy delegate) { this.delegate = delegate; }
        @Override public boolean bypassPrl(String id) { return delegate.bypassPrl(id); }
        @Override public int effectivePackCount(String id, int planned) {
            countQueries.merge(id, 1, Integer::sum);
            return delegate.effectivePackCount(id, planned);
        }
        @Override public boolean allowEmptyTote(String id) { return delegate.allowEmptyTote(id); }
        @Override public void collectedAtPdcOutfeed(String id) { delegate.collectedAtPdcOutfeed(id); }
        @Override public boolean deferInitialPrlAssignments() { return delegate.deferInitialPrlAssignments(); }
        @Override public long classificationEpoch() { return delegate.classificationEpoch(); }
    }
}
