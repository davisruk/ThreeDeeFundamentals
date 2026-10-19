package online.davisfamily.warehouse.sim.dsp.runtime.operational;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import online.davisfamily.warehouse.sim.dsp.av02.Av02AllocatedTote;
import online.davisfamily.warehouse.sim.dsp.bagging.BagKey;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedBag;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifestCatalog;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRecord;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRole;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StartLocation;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteIdentity;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteSource;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineDefinition;
import online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements;
import online.davisfamily.warehouse.sim.dsp.runtime.operational.DspOperationalWorkLedger.EntryCounts;
import online.davisfamily.warehouse.sim.dsp.runtime.operational.DspOperationalWorkSnapshot.CentreTotals;
import online.davisfamily.warehouse.sim.dsp.runtime.operational.DspOperationalWorkSnapshot.LineTotals;
import online.davisfamily.warehouse.sim.dsp.runtime.operational.DspOperationalWorkSnapshot.RemainingCounts;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspOrderStatus;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;

class DspOperationalWorkLedgerTest {
    private static final P2pLineId LINE_1 = new P2pLineId("line-1");
    private static final P2pLineId LINE_2 = new P2pLineId("line-2");
    private static final P2pLineId UNKNOWN_LINE = new P2pLineId("unknown");
    private static final List<P2pLineDefinition> LINES = List.of(
            new P2pLineDefinition(LINE_1, new OperationalRouteDestination(StationType.P2P, "p2p-1")),
            new P2pLineDefinition(LINE_2, new OperationalRouteDestination(StationType.P2P, "p2p-2")));

    @Test
    void initializesMixedWorkInInputOrderWithSeparatePhysicalAndLogicalCounts() {
        Fixture f = fixture();
        DspOperationalWorkSnapshot snapshot = f.ledger.snapshot();

        assertEquals(List.of("104", "108"), new ArrayList<>(snapshot.remainingCountsByServiceCentre().keySet()));
        assertEquals(List.of(LINE_1, LINE_2), new ArrayList<>(snapshot.totalsByLine().keySet()));
        assertEquals(List.of(f.full, f.adapted, f.associated1, f.associated2), snapshot.remainingInboundTotes());
        assertEquals(List.of(f.empty, f.futureEmpty), snapshot.unallocatedEmptyOrders());
        assertTrue(snapshot.liveAv02Totes().isEmpty());
        assertEquals(List.of(f.associated1.physicalToteId(), f.associated2.physicalToteId()),
                snapshot.remainingP2pToteIdsByServiceCentre().get("104"));
        assertEquals(List.of(f.full.physicalToteId()), snapshot.remainingP2pToteIdsByServiceCentre().get("108"));
        assertEquals(new RemainingCounts(2, 1, 3, 1), snapshot.remainingCountsByServiceCentre().get("104"));
        assertEquals(new RemainingCounts(1, 1, 1, 1), snapshot.remainingCountsByServiceCentre().get("108"));
        assertEquals(new CentreTotals(0, 0, 0, 0, 0), snapshot.totalsByServiceCentre().get("104"));
        assertEquals(new LineTotals(0, 0), snapshot.totalsByLine().get(LINE_2));
        assertEquals(2, f.ledger.remainingInboundConsumersForSource(f.associated1.orderSheetKey()));
        assertEquals(1, f.ledger.remainingInboundConsumersForSource(f.empty.order().orderSheetKey()));
        assertEquals(3, f.ledger.remainingConsumersForOrder("target"));
        assertEquals(0, f.ledger.remainingConsumersForOrder("adapted-source"));
        assertEquals(0, f.ledger.remainingConsumersForOrder("full"));
        assertEquals(1, f.ledger.remainingBagsForSource(f.associated1.orderSheetKey()));
        assertEquals(1, f.ledger.remainingBagsForSource(f.empty.order().orderSheetKey()));
        assertEquals(0, f.ledger.remainingBagsForSource(new OrderSheetKey("absent", 1)));
        assertEquals(0, f.ledger.remainingInboundConsumersForSource(new OrderSheetKey("absent", 1)));
        assertEquals(0, f.ledger.remainingConsumersForOrder("absent"));
        assertSame(f.bag104, f.ledger.remainingBag(f.bag104.bagKey()).orElseThrow());
        assertSame(snapshot, f.ledger.snapshot());
    }

    @Test
    void emptyAllocationReplacesIdentityWithoutEndingItsConsumerAndRetiresBothLiveIndexes() {
        Fixture f = fixture();
        DspOperationalWorkSnapshot before = f.ledger.snapshot();
        Av02AllocatedTote allocated = av02(f.empty, "av02-1");
        f.ledger.validateCanAllocateEmpty(allocated.orderSheetKey());
        f.ledger.validateAv02Allocated(allocated);
        assertSame(before, f.ledger.snapshot());
        f.ledger.recordAv02Allocated(allocated);
        DspOperationalWorkSnapshot live = f.ledger.snapshot();
        assertNotSame(before, live);
        assertSame(before.remainingInboundTotes(), live.remainingInboundTotes());
        assertFalse(f.ledger.isUnallocatedEmpty(allocated.orderSheetKey()));
        assertTrue(f.ledger.isUnallocatedEmpty(f.futureEmpty.order().orderSheetKey()));
        assertSame(allocated, f.ledger.findLiveAv02(allocated.physicalToteId()).orElseThrow());
        assertSame(allocated, f.ledger.findLiveAv02(allocated.orderSheetKey()).orElseThrow());
        assertSame(allocated, live.liveAv02Totes().getFirst());
        assertEquals(List.of(f.futureEmpty), live.unallocatedEmptyOrders());
        assertEquals(new RemainingCounts(3, 0, 3, 1), live.remainingCountsByServiceCentre().get("104"));
        assertEquals(1, f.ledger.remainingInboundConsumersForSource(allocated.orderSheetKey()));
        assertEquals(3, f.ledger.remainingConsumersForOrder("target"));
        assertEquals(List.of(f.associated1.physicalToteId(), f.associated2.physicalToteId(), allocated.physicalToteId()),
                live.remainingP2pToteIdsByServiceCentre().get("104"));
        rejected(f.ledger, () -> f.ledger.recordAv02Allocated(allocated));
        rejected(f.ledger, () -> f.ledger.validateCanAllocateEmpty(allocated.orderSheetKey()));
        rejected(f.ledger, () -> f.ledger.recordAv02Allocated(av02(f.futureEmpty, "av02-1")));

        f.ledger.recordInboundConsumed(allocated.physicalToteId(), StationType.P2P, Optional.of(LINE_2));
        DspOperationalWorkSnapshot consumed = f.ledger.snapshot();
        assertSame(live.remainingInboundTotes(), consumed.remainingInboundTotes());
        assertSame(live.unallocatedEmptyOrders(), consumed.unallocatedEmptyOrders());
        assertTrue(consumed.liveAv02Totes().isEmpty());
        assertTrue(f.ledger.findLiveAv02(allocated.physicalToteId()).isEmpty());
        assertTrue(f.ledger.findLiveAv02(allocated.orderSheetKey()).isEmpty());
        assertFalse(f.ledger.isUnallocatedEmpty(allocated.orderSheetKey()));
        assertEquals(0, f.ledger.remainingInboundConsumersForSource(allocated.orderSheetKey()));
        assertEquals(2, f.ledger.remainingConsumersForOrder("target"));
        assertEquals(new RemainingCounts(2, 0, 3, 1), consumed.remainingCountsByServiceCentre().get("104"));
        assertEquals(new CentreTotals(0, 1, 0, 0, 0), consumed.totalsByServiceCentre().get("104"));
        assertEquals(new LineTotals(1, 0), consumed.totalsByLine().get(LINE_2));
        assertSame(f.bag104, f.ledger.remainingBag(f.bag104.bagKey()).orElseThrow());
        rejected(f.ledger, () -> f.ledger.recordInboundConsumed(allocated.physicalToteId(), StationType.P2P,
                Optional.of(LINE_2)));
        rejected(f.ledger, () -> f.ledger.recordAv02Allocated(allocated));
        // No historical barcode guard: a different still-pending physical obligation may reuse the ID.
        f.ledger.recordAv02Allocated(av02(f.futureEmpty, "av02-1"));
        assertEquals("108", f.ledger.findLiveAv02(allocated.physicalToteId()).orElseThrow().serviceCentreId());
    }

    @Test
    void sourceConsumptionCountsActualTerminalStationsWithoutDiscardingDownstreamBagsOrFutureConsumers() {
        Fixture f = fixture();
        DspOperationalWorkSnapshot before = f.ledger.snapshot();
        f.ledger.validateInboundConsumed(f.adapted.physicalToteId(), StationType.ADAPTING, Optional.empty());
        assertSame(before, f.ledger.snapshot());
        f.ledger.recordInboundConsumed(f.adapted.physicalToteId(), StationType.ADAPTING, Optional.empty());
        DspOperationalWorkSnapshot stored = f.ledger.snapshot();
        assertSame(before.remainingP2pToteIdsByServiceCentre(), stored.remainingP2pToteIdsByServiceCentre());
        assertSame(before.unallocatedEmptyOrders(), stored.unallocatedEmptyOrders());
        assertSame(before.liveAv02Totes(), stored.liveAv02Totes());
        assertEquals(3, f.ledger.remainingConsumersForOrder("target"));
        assertEquals(0, f.ledger.remainingInboundConsumersForSource(f.adapted.orderSheetKey()));
        assertEquals(new CentreTotals(1, 0, 0, 0, 0), stored.totalsByServiceCentre().get("104"));
        assertEquals(new RemainingCounts(2, 1, 3, 1), stored.remainingCountsByServiceCentre().get("104"));

        f.ledger.recordInboundConsumed(f.associated1.physicalToteId(), StationType.P2P, Optional.of(LINE_1));
        DspOperationalWorkSnapshot firstTipped = f.ledger.snapshot();
        assertSame(stored.unallocatedEmptyOrders(), firstTipped.unallocatedEmptyOrders());
        assertSame(stored.liveAv02Totes(), firstTipped.liveAv02Totes());
        assertEquals(1, f.ledger.remainingInboundConsumersForSource(f.associated1.orderSheetKey()));
        assertEquals(2, f.ledger.remainingConsumersForOrder("target"));
        assertEquals(List.of(f.associated2.physicalToteId()), firstTipped.remainingP2pToteIdsByServiceCentre().get("104"));
        f.ledger.recordInboundConsumed(f.associated2.physicalToteId(), StationType.P2P, Optional.of(LINE_1));
        f.ledger.recordInboundConsumed(f.full.physicalToteId(), StationType.P2P, Optional.of(LINE_2));
        assertEquals(0, f.ledger.remainingInboundConsumersForSource(f.associated1.orderSheetKey()));
        assertEquals(1, f.ledger.remainingConsumersForOrder("target"));
        assertEquals(new CentreTotals(1, 2, 0, 0, 0), f.ledger.snapshot().totalsByServiceCentre().get("104"));
        assertEquals(new CentreTotals(0, 1, 0, 0, 0), f.ledger.snapshot().totalsByServiceCentre().get("108"));
        assertEquals(new LineTotals(2, 0), f.ledger.snapshot().totalsByLine().get(LINE_1));
        assertEquals(1, f.ledger.remainingBagsForSource(f.associated1.orderSheetKey()));
        assertTrue(f.ledger.snapshot().remainingInboundTotes().isEmpty());
        assertEquals(new RemainingCounts(0, 1, 3, 1), f.ledger.snapshot().remainingCountsByServiceCentre().get("104"));
        rejected(f.ledger, () -> f.ledger.recordInboundConsumed(f.adapted.physicalToteId(),
                StationType.ADAPTING, Optional.empty()));
    }

    @Test
    void missingClassificationIsAReplacementAndAllocationCountsOnlyActualPacksOnce() {
        Fixture f = fixture();
        DspOperationalWorkSnapshot before = f.ledger.snapshot();
        Set<String> missing = new HashSet<>(Set.of("pack-b"));
        f.ledger.validateMissingPacks(f.bag104.bagKey(), missing);
        assertSame(before, f.ledger.snapshot());
        f.ledger.recordMissingPacks(f.bag104.bagKey(), missing);
        missing.add("pack-c");
        DspOperationalWorkSnapshot classified = f.ledger.snapshot();
        assertNotSame(before, classified);
        sameLargeSubviews(before, classified);
        assertEquals(new RemainingCounts(2, 1, 2, 1), classified.remainingCountsByServiceCentre().get("104"));
        f.ledger.recordMissingPacks(f.bag104.bagKey(), Set.of("pack-b"));
        assertSame(classified, f.ledger.snapshot());
        f.ledger.recordMissingPacks(f.bag104.bagKey(), Set.of("pack-a"));
        assertNotSame(classified, f.ledger.snapshot());
        rejected(f.ledger, () -> f.ledger.recordBagAllocated(LINE_1, f.bag104, Set.of("pack-b")));
        f.ledger.recordMissingPacks(f.bag104.bagKey(), Set.of());
        assertEquals(new RemainingCounts(2, 1, 3, 1), f.ledger.snapshot().remainingCountsByServiceCentre().get("104"));
        f.ledger.recordMissingPacks(f.bag104.bagKey(), Set.of("pack-b"));
        DspOperationalWorkSnapshot ready = f.ledger.snapshot();
        f.ledger.validateBagAllocated(LINE_1, f.bag104, Set.of("pack-b"));
        assertSame(ready, f.ledger.snapshot());
        f.ledger.recordBagAllocated(LINE_1, f.bag104, Set.of("pack-b"));
        DspOperationalWorkSnapshot allocated = f.ledger.snapshot();
        sameLargeSubviews(ready, allocated);
        assertEquals(new RemainingCounts(2, 1, 0, 0), allocated.remainingCountsByServiceCentre().get("104"));
        assertEquals(new CentreTotals(0, 0, 1, 2, 1), allocated.totalsByServiceCentre().get("104"));
        assertEquals(new LineTotals(0, 1), allocated.totalsByLine().get(LINE_1));
        assertEquals(0, f.ledger.remainingBagsForSource(f.associated1.orderSheetKey()));
        assertEquals(0, f.ledger.remainingBagsForSource(f.empty.order().orderSheetKey()));
        assertEquals(2, f.ledger.remainingInboundConsumersForSource(f.associated1.orderSheetKey()));
        assertEquals(0, f.ledger.entryCounts().classifiedBags());
        assertTrue(f.ledger.remainingBag(f.bag104.bagKey()).isEmpty());
        assertSame(f.bag108, f.ledger.remainingBag(f.bag108.bagKey()).orElseThrow());
        assertEquals(new RemainingCounts(1, 1, 1, 1), allocated.remainingCountsByServiceCentre().get("108"));
        rejected(f.ledger, () -> f.ledger.recordBagAllocated(LINE_1, f.bag104, Set.of("pack-b")));
        rejected(f.ledger, () -> f.ledger.recordMissingPacks(f.bag104.bagKey(), Set.of()));
        rejected(f.ledger, () -> f.ledger.recordPendingEmptyBag(f.bag104.bagKey()));
        assertEquals(new RemainingCounts(2, 1, 3, 1), before.remainingCountsByServiceCentre().get("104"));
    }

    @Test
    void fullyMissingBagsLeaveExecutableWorkWithoutCountingAnAllocatedPhysicalBag() {
        Fixture f = fixture();
        Set<String> allMissing = Set.of("pack-a", "pack-b", "pack-c");
        f.ledger.recordMissingPacks(f.bag104.bagKey(), allMissing);
        DspOperationalWorkSnapshot classified = f.ledger.snapshot();
        assertEquals(new RemainingCounts(2, 1, 0, 1), classified.remainingCountsByServiceCentre().get("104"));
        rejected(f.ledger, () -> f.ledger.recordBagAllocated(LINE_1, f.bag104, allMissing));
        f.ledger.validatePendingEmptyBag(f.bag104.bagKey());
        assertSame(classified, f.ledger.snapshot());
        f.ledger.recordPendingEmptyBag(f.bag104.bagKey());
        DspOperationalWorkSnapshot removed = f.ledger.snapshot();
        sameLargeSubviews(classified, removed);
        assertEquals(new RemainingCounts(2, 1, 0, 0), removed.remainingCountsByServiceCentre().get("104"));
        assertEquals(new CentreTotals(0, 0, 0, 0, 0), removed.totalsByServiceCentre().get("104"));
        assertEquals(new LineTotals(0, 0), removed.totalsByLine().get(LINE_1));
        assertEquals(0, f.ledger.remainingBagsForSource(f.empty.order().orderSheetKey()));
        assertEquals(3, f.ledger.remainingConsumersForOrder("target"));
        assertEquals(0, f.ledger.entryCounts().classifiedBags());
        rejected(f.ledger, () -> f.ledger.recordPendingEmptyBag(f.bag104.bagKey()));
    }

    @Test
    void rejectedIdentityRouteLineMembershipAndEffectiveCountChangesDoNotMutateWork() {
        Fixture f = fixture();
        for (Executable action : List.<Executable>of(
                () -> f.ledger.recordInboundConsumed(new PhysicalToteId("absent"), StationType.P2P, Optional.of(LINE_1)),
                () -> f.ledger.recordInboundConsumed(f.adapted.physicalToteId(), StationType.P2P, Optional.of(LINE_1)),
                () -> f.ledger.recordInboundConsumed(f.adapted.physicalToteId(), StationType.ADAPTING, Optional.of(LINE_1)),
                () -> f.ledger.recordInboundConsumed(f.full.physicalToteId(), StationType.ADAPTING, Optional.empty()),
                () -> f.ledger.recordInboundConsumed(f.full.physicalToteId(), StationType.P2P, Optional.empty()),
                () -> f.ledger.recordInboundConsumed(f.full.physicalToteId(), StationType.P2P, Optional.of(UNKNOWN_LINE)),
                () -> f.ledger.recordInboundConsumed(f.full.physicalToteId(), StationType.THIRD_PARTY, Optional.of(LINE_1)),
                () -> f.ledger.recordInboundConsumed(null, StationType.P2P, Optional.of(LINE_1)),
                () -> f.ledger.recordInboundConsumed(f.full.physicalToteId(), null, Optional.of(LINE_1)),
                () -> f.ledger.recordInboundConsumed(f.full.physicalToteId(), StationType.P2P, null),
                () -> f.ledger.recordAv02Allocated(av02(f.empty, f.full.physicalToteId().value())),
                () -> f.ledger.recordAv02Allocated(av02(order("target", 2, "wrong-centre", OrderType.EMPTY), "av02")),
                () -> f.ledger.recordAv02Allocated(av02(order("unknown", 1, "104", OrderType.EMPTY), "av02")),
                () -> f.ledger.recordAv02Allocated(null),
                () -> f.ledger.validateCanAllocateEmpty(f.full.orderSheetKey()),
                () -> f.ledger.recordMissingPacks(f.bag104.bagKey(), Set.of("foreign")),
                () -> f.ledger.recordMissingPacks(f.bag104.bagKey(), Set.of("pack-a", "pack-b", "pack-c", "foreign")),
                () -> f.ledger.recordMissingPacks(f.bag104.bagKey(), null),
                () -> f.ledger.recordMissingPacks(f.bag104.bagKey(), new HashSet<>(java.util.Arrays.asList((String) null))),
                () -> f.ledger.recordMissingPacks(new BagKey("unknown", 1), Set.of()),
                () -> f.ledger.recordPendingEmptyBag(f.bag104.bagKey()),
                () -> f.ledger.recordBagAllocated(UNKNOWN_LINE, f.bag104, Set.of()),
                () -> f.ledger.recordBagAllocated(null, f.bag104, Set.of()),
                () -> f.ledger.recordBagAllocated(LINE_1, null, Set.of()),
                () -> f.ledger.recordBagAllocated(LINE_1, f.bag104, Set.of("pack-a")),
                () -> f.ledger.recordBagAllocated(LINE_1, new PlannedBag(f.bag104.bagKey(), "104", "wrong-pharmacy",
                        f.bag104.patientId(), f.bag104.prescriptionId(), f.bag104.physicalPackIds(),
                        f.bag104.owningOrderSheetKeys()), Set.of()))) {
            rejected(f.ledger, action);
        }
        assertEquals(3, f.ledger.remainingConsumersForOrder("target"));
        assertEquals(2, f.ledger.remainingInboundConsumersForSource(f.associated1.orderSheetKey()));
        assertEquals(1, f.ledger.remainingBagsForSource(f.empty.order().orderSheetKey()));
        assertTrue(f.ledger.isUnallocatedEmpty(f.empty.order().orderSheetKey()));
    }

    @Test
    void snapshotsAndNestedViewsAreImmutableAndRetainTheirOldValues() {
        Fixture f = fixture();
        DspOperationalWorkSnapshot before = f.ledger.snapshot();
        assertThrows(UnsupportedOperationException.class, () -> before.remainingInboundTotes().clear());
        assertThrows(UnsupportedOperationException.class, () -> before.unallocatedEmptyOrders().clear());
        assertThrows(UnsupportedOperationException.class, () -> before.liveAv02Totes().add(av02(f.empty, "av02")));
        assertThrows(UnsupportedOperationException.class, () -> before.remainingP2pToteIdsByServiceCentre().clear());
        assertThrows(UnsupportedOperationException.class, () -> before.remainingP2pToteIdsByServiceCentre().get("104").clear());
        assertThrows(UnsupportedOperationException.class, () -> before.remainingCountsByServiceCentre().clear());
        assertThrows(UnsupportedOperationException.class, () -> before.totalsByServiceCentre().clear());
        assertThrows(UnsupportedOperationException.class, () -> before.totalsByLine().clear());
        f.ledger.recordInboundConsumed(f.full.physicalToteId(), StationType.P2P, Optional.of(LINE_2));
        assertEquals(4, before.remainingInboundTotes().size());
        assertEquals(new RemainingCounts(1, 1, 1, 1), before.remainingCountsByServiceCentre().get("108"));
        assertEquals(new LineTotals(0, 0), before.totalsByLine().get(LINE_2));
        DspOperationalWorkSnapshot after = f.ledger.snapshot();
        assertNotSame(before, after);
        assertNotSame(before.remainingInboundTotes(), after.remainingInboundTotes());
        assertNotSame(before.remainingP2pToteIdsByServiceCentre(), after.remainingP2pToteIdsByServiceCentre());
        assertSame(before.unallocatedEmptyOrders(), after.unallocatedEmptyOrders());
        assertSame(before.liveAv02Totes(), after.liveAv02Totes());
        assertSame(after, f.ledger.snapshot());
    }

    @Test
    void validatesInputsAndNonnegativeValueTypesAndKeepsZeroRowsForConfiguredCentresAndLines() {
        DspOperationalWorkLedger empty = ledger(List.of(), List.of(), List.of());
        assertTrue(empty.snapshot().remainingCountsByServiceCentre().isEmpty());
        assertEquals(Map.of(LINE_1, new LineTotals(0, 0), LINE_2, new LineTotals(0, 0)), empty.snapshot().totalsByLine());
        DspSchedulerOrderState full = order("only", 1, "116", OrderType.FULL_PACK);
        InboundToteManifest manifest = manifest(full, "only-tote", 0);
        DspOperationalWorkLedger ledger = ledger(List.of(full), List.of(manifest), List.of());
        ledger.recordInboundConsumed(manifest.physicalToteId(), StationType.P2P, Optional.of(LINE_1));
        assertEquals(new RemainingCounts(0, 0, 0, 0), ledger.snapshot().remainingCountsByServiceCentre().get("116"));
        assertEquals(List.of(), ledger.snapshot().remainingP2pToteIdsByServiceCentre().get("116"));
        for (Executable action : List.<Executable>of(
                () -> new RemainingCounts(-1, 0, 0, 0), () -> new RemainingCounts(0, -1, 0, 0),
                () -> new RemainingCounts(0, 0, -1, 0), () -> new RemainingCounts(0, 0, 0, -1),
                () -> new CentreTotals(-1, 0, 0, 0, 0), () -> new CentreTotals(0, -1, 0, 0, 0),
                () -> new CentreTotals(0, 0, -1, 0, 0), () -> new CentreTotals(0, 0, 0, -1, 0),
                () -> new CentreTotals(0, 0, 0, 0, -1), () -> new LineTotals(-1, 0), () -> new LineTotals(0, -1),
                () -> ledger(null, List.of(), List.of()), () -> ledger(List.of(full, full), List.of(), List.of()),
                () -> ledger(List.of(), List.of(manifest), List.of()),
                () -> ledger(List.of(order("only", 1, "104", OrderType.FULL_PACK)), List.of(manifest), List.of()),
                () -> new DspOperationalWorkLedger(List.of(), null, List.of(), LINES),
                () -> ledger(List.of(), List.of(), null),
                () -> new DspOperationalWorkLedger(List.of(), new InboundToteManifestCatalog(List.of()), List.of(), null),
                () -> new DspOperationalWorkLedger(List.of(), new InboundToteManifestCatalog(List.of()), List.of(),
                        List.of(LINES.getFirst(), LINES.getFirst())))) {
            assertThrows(IllegalArgumentException.class, action);
        }
        PlannedBag bagOnly = bag("bag-only", "999", List.of(new OrderSheetKey("future-source", 1)), "bag-only-pack");
        DspOperationalWorkLedger bagOnlyLedger = ledger(List.of(), List.of(), List.of(bagOnly));
        assertEquals(new RemainingCounts(0, 0, 1, 1), bagOnlyLedger.snapshot().remainingCountsByServiceCentre().get("999"));
        assertThrows(IllegalArgumentException.class, () -> ledger(List.of(), List.of(), List.of(bagOnly, bagOnly)));
    }

    @Test
    void aThousandSmallRetirementsLeaveOnlyLiveSentinelEntriesThenEndCompletelyEmpty() {
        List<DspSchedulerOrderState> orders = new ArrayList<>();
        List<InboundToteManifest> manifests = new ArrayList<>();
        List<PlannedBag> bags = new ArrayList<>();
        DspSchedulerOrderState sentinel = order("sentinel", 1, "108", OrderType.FULL_PACK);
        InboundToteManifest sentinelManifest = manifest(sentinel, "sentinel-tote", 0);
        PlannedBag sentinelBag = bag("sentinel-bag", "108", List.of(sentinel.order().orderSheetKey()), "sentinel-pack");
        orders.add(sentinel);
        manifests.add(sentinelManifest);
        bags.add(sentinelBag);
        for (int i = 0; i < 1_000; i++) {
            DspSchedulerOrderState adapted = order("source-" + i, 1, "104", OrderType.ADAPTED);
            DspSchedulerOrderState associated = order("target-" + i, 1, "104", OrderType.ASSOCIATED);
            DspSchedulerOrderState empty = order("target-" + i, 2, "104", OrderType.EMPTY);
            orders.addAll(List.of(adapted, associated, empty));
            manifests.add(manifest(adapted, "source-tote-" + i, 2L * i + 1));
            manifests.add(manifest(associated, "target-tote-" + i, 2L * i + 2));
            bags.add(bag("partial-" + i, "104", List.of(associated.order().orderSheetKey(), empty.order().orderSheetKey()),
                    "actual-" + i, "missing-" + i));
            bags.add(bag("fully-missing-" + i, "104", List.of(associated.order().orderSheetKey()), "empty-" + i));
        }
        DspOperationalWorkLedger ledger = ledger(orders, manifests, bags);
        for (int i = 0; i < 1_000; i++) {
            ledger.recordInboundConsumed(manifests.get(2 * i + 1).physicalToteId(), StationType.ADAPTING, Optional.empty());
            ledger.recordInboundConsumed(manifests.get(2 * i + 2).physicalToteId(), StationType.P2P, Optional.of(LINE_1));
            Av02AllocatedTote allocated = av02(orders.get(3 * i + 3), "av02-" + i);
            ledger.recordAv02Allocated(allocated);
            ledger.recordInboundConsumed(allocated.physicalToteId(), StationType.P2P, Optional.of(LINE_1));
            PlannedBag partial = bags.get(2 * i + 1);
            ledger.recordMissingPacks(partial.bagKey(), Set.of("missing-" + i));
            ledger.recordBagAllocated(LINE_1, partial, Set.of("missing-" + i));
            PlannedBag fullyMissing = bags.get(2 * i + 2);
            ledger.recordMissingPacks(fullyMissing.bagKey(), Set.of("empty-" + i));
            ledger.recordPendingEmptyBag(fullyMissing.bagKey());

            int pending = 999 - i;
            assertEquals(new EntryCounts(2 * pending + 1, pending, 0, 0, 2 * pending + 1,
                    2 * pending + 1, 3 * pending + 1, pending, 0), ledger.entryCounts());
            DspOperationalWorkSnapshot snapshot = ledger.snapshot();
            assertSame(sentinelManifest, snapshot.remainingInboundTotes().getFirst());
            assertSame(sentinelBag, ledger.remainingBag(sentinelBag.bagKey()).orElseThrow());
            assertEquals(1, ledger.remainingBagsForSource(sentinel.order().orderSheetKey()));
            assertEquals(1, ledger.remainingInboundConsumersForSource(sentinel.order().orderSheetKey()));
            assertEquals(0, ledger.remainingConsumersForOrder("target-" + i));
            assertEquals(0, ledger.remainingInboundConsumersForSource(allocated.orderSheetKey()));
            assertTrue(ledger.findLiveAv02(allocated.physicalToteId()).isEmpty());
            assertTrue(ledger.findLiveAv02(allocated.orderSheetKey()).isEmpty());
            assertTrue(ledger.remainingBag(partial.bagKey()).isEmpty());
            assertEquals(new CentreTotals(i + 1, 2L * (i + 1), i + 1, i + 1, i + 1),
                    snapshot.totalsByServiceCentre().get("104"));
            assertEquals(new RemainingCounts(pending, pending, 3 * pending, 2 * pending),
                    snapshot.remainingCountsByServiceCentre().get("104"));
            assertSame(snapshot, ledger.snapshot());
            rejected(ledger, () -> ledger.recordBagAllocated(LINE_1, partial, Set.of()));
        }
        assertEquals(List.of(sentinelManifest), ledger.snapshot().remainingInboundTotes());
        assertEquals(List.of(sentinelManifest.physicalToteId()), ledger.snapshot().remainingP2pToteIdsByServiceCentre().get("108"));
        assertEquals(List.of(), ledger.snapshot().remainingP2pToteIdsByServiceCentre().get("104"));
        ledger.recordBagAllocated(LINE_2, sentinelBag, Set.of());
        ledger.recordInboundConsumed(sentinelManifest.physicalToteId(), StationType.P2P, Optional.of(LINE_2));
        assertEquals(new EntryCounts(0, 0, 0, 0, 0, 0, 0, 0, 0), ledger.entryCounts());
        assertEquals(new RemainingCounts(0, 0, 0, 0), ledger.snapshot().remainingCountsByServiceCentre().get("104"));
        assertEquals(new RemainingCounts(0, 0, 0, 0), ledger.snapshot().remainingCountsByServiceCentre().get("108"));
        assertEquals(new LineTotals(2_000, 1_000), ledger.snapshot().totalsByLine().get(LINE_1));
        assertEquals(new LineTotals(1, 1), ledger.snapshot().totalsByLine().get(LINE_2));
        DspOperationalWorkSnapshot end = ledger.snapshot();
        for (int i = 0; i < 20; i++) {
            assertSame(end, ledger.snapshot());
        }
    }

    private static void rejected(DspOperationalWorkLedger ledger, Executable action) {
        DspOperationalWorkSnapshot before = ledger.snapshot();
        EntryCounts entries = ledger.entryCounts();
        assertThrows(IllegalArgumentException.class, action);
        assertSame(before, ledger.snapshot());
        assertEquals(entries, ledger.entryCounts());
    }

    private static void sameLargeSubviews(DspOperationalWorkSnapshot before, DspOperationalWorkSnapshot after) {
        assertSame(before.remainingInboundTotes(), after.remainingInboundTotes());
        assertSame(before.unallocatedEmptyOrders(), after.unallocatedEmptyOrders());
        assertSame(before.liveAv02Totes(), after.liveAv02Totes());
        assertSame(before.remainingP2pToteIdsByServiceCentre(), after.remainingP2pToteIdsByServiceCentre());
    }

    private static Fixture fixture() {
        DspSchedulerOrderState adapted = order("adapted-source", 1, "104", OrderType.ADAPTED);
        DspSchedulerOrderState full = order("full", 1, "108", OrderType.FULL_PACK);
        DspSchedulerOrderState associated = order("target", 1, "104", OrderType.ASSOCIATED);
        DspSchedulerOrderState empty = order("target", 2, "104", OrderType.EMPTY);
        DspSchedulerOrderState futureEmpty = order("future", 1, "108", OrderType.EMPTY);
        InboundToteManifest fullManifest = manifest(full, "full-tote", 0);
        InboundToteManifest adaptedManifest = manifest(adapted, "adapted-tote", 1);
        InboundToteManifest associated1 = manifest(associated, "associated-1", 2);
        InboundToteManifest associated2 = manifest(associated, "associated-2", 3);
        PlannedBag bag104 = bag("rx104", "104", List.of(associated.order().orderSheetKey(),
                empty.order().orderSheetKey(), associated.order().orderSheetKey()), "pack-a", "pack-b", "pack-c");
        PlannedBag bag108 = bag("rx108", "108", List.of(full.order().orderSheetKey()), "pack-d");
        return new Fixture(ledger(List.of(adapted, full, associated, empty, futureEmpty),
                List.of(fullManifest, adaptedManifest, associated1, associated2), List.of(bag104, bag108)),
                fullManifest, adaptedManifest, associated1, associated2, empty, futureEmpty, bag104, bag108);
    }

    private static DspOperationalWorkLedger ledger(List<DspSchedulerOrderState> orders,
            List<InboundToteManifest> manifests, List<PlannedBag> bags) {
        return new DspOperationalWorkLedger(orders, new InboundToteManifestCatalog(manifests), bags, LINES);
    }

    private static DspSchedulerOrderState order(String id, int sheet, String centre, OrderType type) {
        DspOrderItem item = new DspOrderItem("line-" + id + "-" + sheet, "product", 1,
                "pharmacy-" + centre, "patient-" + id, "rx-" + id,
                type == OrderType.ADAPTED ? DspOrderLineType.ADAPTED : DspOrderLineType.FULL_PACK,
                type == OrderType.ADAPTED ? "target" : id, sheet, 1);
        return new DspSchedulerOrderState(new NotionalToteOrder(id, "notional-" + id + "-" + sheet,
                centre, sheet, type, List.of(item), 999, 0),
                new RouteRequirements(false, type == OrderType.ADAPTED, false, type != OrderType.ADAPTED,
                        false, type == OrderType.EMPTY ? StartLocation.AV02 : StartLocation.OSR),
                DspOrderStatus.WAITING);
    }

    private static InboundToteManifest manifest(DspSchedulerOrderState order, String id, long sequence) {
        return new InboundToteManifest(new PhysicalToteId(id), order.order().orderSheetKey(),
                order.order().orderType(), order.order().serviceCentreId(), order.order().items(), sequence);
    }

    private static PlannedBag bag(String id, String centre, List<OrderSheetKey> owners, String... packs) {
        return new PlannedBag(new BagKey(id, 1), centre, "pharmacy-" + centre, "patient-" + id,
                id, List.of(packs), owners);
    }

    private static Av02AllocatedTote av02(DspSchedulerOrderState order, String id) {
        PhysicalToteId toteId = new PhysicalToteId(id);
        return new Av02AllocatedTote(new OperationalPhysicalToteIdentity(OperationalPhysicalToteSource.AV02,
                toteId, order.order().orderSheetKey(), OrderType.EMPTY, order.order().serviceCentreId(),
                PhysicalToteRole.PRE_P2P, order.order().sequenceNumber()), PhysicalToteRecord.preP2p(toteId),
                "pharmacy-" + order.order().serviceCentreId());
    }

    private record Fixture(DspOperationalWorkLedger ledger, InboundToteManifest full, InboundToteManifest adapted,
            InboundToteManifest associated1, InboundToteManifest associated2, DspSchedulerOrderState empty,
            DspSchedulerOrderState futureEmpty, PlannedBag bag104, PlannedBag bag108) { }
}
