package online.davisfamily.warehouse.sim.dsp.scheduler.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.av02.ReleasePhysicalToteFromAv02Command;
import online.davisfamily.warehouse.sim.dsp.io.DspDatasetLoadReport;
import online.davisfamily.warehouse.sim.dsp.io.LoadedDspData;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.OperationalPhysicalToteReleaseCommand;
import online.davisfamily.warehouse.sim.dsp.osr.release.ReleasePhysicalToteFromOsrCommand;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteSource;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineDefinition;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pPhysicalToteAssignment;
import online.davisfamily.warehouse.sim.dsp.schedule.DspServiceCentreTimetable;
import online.davisfamily.warehouse.sim.dsp.schedule.ServiceCentreSchedule;
import online.davisfamily.warehouse.sim.dsp.time.OperationalDayTime;

class WholeServiceCentreReleaseLedgerTest {
    private static final List<P2pLineDefinition> LINES = java.util.stream.IntStream.rangeClosed(1, 5)
            .mapToObj(index -> new P2pLineDefinition(new P2pLineId("line-" + index),
                    destination("p2p-" + index))).toList();
    private static final DspServiceCentreTimetable TIMETABLE = new DspServiceCentreTimetable(List.of(
            schedule("B", 998), schedule("Z", 999), schedule("zero-work", 1000),
            schedule("Y", 997), schedule("A", 998)));

    @Test
    void shouldCountCommittedP2pAssignmentsUntilOneExactTippingCompletion() {
        var first = order("first", 1, "Z", OrderType.FULL_PACK);
        var second = order("second", 1, "Z", OrderType.FULL_PACK);
        var third = order("third", 1, "Z", OrderType.FULL_PACK);
        var firstManifest = manifest("physical-first", first);
        var secondManifest = manifest("physical-second", second);
        var thirdManifest = manifest("physical-third", third);
        var line = LINES.get(0);
        var ledger = ledger(List.of(first, second, third),
                List.of(firstManifest, secondManifest, thirdManifest), 2);

        var initial = ledger.snapshot();
        assertEquals(0, initial.outstandingVersion());
        assertEquals(2, initial.p2pOutstandingToteWatermark());
        assertEquals(List.of("line-1", "line-2", "line-3", "line-4", "line-5"),
                initial.outstandingP2pToteCounts().keySet().stream().map(P2pLineId::value).toList());
        for (P2pLineDefinition configuredLine : LINES) {
            assertEquals(0, initial.outstandingToteCount(configuredLine.lineId()));
        }
        assertSame(initial, ledger.snapshot());

        var firstCommand = osr(firstManifest, line);
        ledger.validateUnreleased(firstCommand);
        assertSame(initial, ledger.snapshot());
        recordSuccess(ledger, firstCommand);
        recordSuccess(ledger, osr(secondManifest, line));
        var atWatermark = ledger.snapshot();
        assertEquals(2, atWatermark.version());
        assertEquals(2, atWatermark.outstandingVersion());
        assertEquals(2, atWatermark.outstandingToteCount(line.lineId()));
        assertEquals(2, atWatermark.committedToteCount("Z", line.lineId()));

        var thirdCommand = osr(thirdManifest, line);
        ledger.validateUnreleased(thirdCommand); // A reached watermark is a deferral condition, not invalid input.
        assertSame(atWatermark, ledger.snapshot());
        assertThrows(IllegalStateException.class, () -> ledger.recordApplied(thirdCommand));
        assertSame(atWatermark, ledger.snapshot());

        assertThrows(IllegalArgumentException.class,
                () -> ledger.validateTippingCompletion(new PhysicalToteId("not-committed"), line.lineId()));
        assertThrows(IllegalArgumentException.class,
                () -> ledger.validateTippingCompletion(firstManifest.physicalToteId(), LINES.get(1).lineId()));
        assertThrows(IllegalStateException.class,
                () -> ledger.recordTippingCompleted(firstManifest.physicalToteId(), LINES.get(1).lineId()));
        assertThrows(IllegalArgumentException.class,
                () -> ledger.validateTippingCompletion(firstManifest.physicalToteId(), new P2pLineId("unknown")));
        assertThrows(IllegalArgumentException.class,
                () -> ledger.validateTippingCompletion(firstManifest.physicalToteId(), null));
        assertThrows(IllegalArgumentException.class,
                () -> ledger.validateTippingCompletion(null, line.lineId()));
        assertThrows(IllegalStateException.class,
                () -> ledger.recordTippingCompleted(new PhysicalToteId("not-committed"), line.lineId()));
        assertSame(atWatermark, ledger.snapshot());

        ledger.validateTippingCompletion(firstManifest.physicalToteId(), line.lineId());
        ledger.recordTippingCompleted(firstManifest.physicalToteId(), line.lineId());
        var afterTip = ledger.snapshot();
        assertNotSame(atWatermark, afterTip);
        assertEquals(2, afterTip.version());
        assertEquals(3, afterTip.outstandingVersion());
        assertEquals(1, afterTip.outstandingToteCount(line.lineId()));
        assertEquals(atWatermark.unreleasedOsrToteCounts(), afterTip.unreleasedOsrToteCounts());
        assertEquals(atWatermark.unreleasedEmptySheetCounts(), afterTip.unreleasedEmptySheetCounts());
        assertEquals(atWatermark.releaseServiceCentreId(), afterTip.releaseServiceCentreId());
        assertEquals(atWatermark.committedP2pToteCounts(), afterTip.committedP2pToteCounts());
        assertEquals(2, atWatermark.outstandingToteCount(line.lineId()));
        assertSame(afterTip, ledger.snapshot());
        assertThrows(IllegalArgumentException.class,
                () -> ledger.validateTippingCompletion(firstManifest.physicalToteId(), line.lineId()));
        assertThrows(IllegalStateException.class,
                () -> ledger.recordTippingCompleted(firstManifest.physicalToteId(), line.lineId()));
        assertSame(afterTip, ledger.snapshot());

        recordSuccess(ledger, thirdCommand);
        assertEquals(2, ledger.snapshot().outstandingToteCount(line.lineId()));
        assertEquals(3, ledger.snapshot().version());
        assertEquals(4, ledger.snapshot().outstandingVersion());
        ledger.recordTippingCompleted(secondManifest.physicalToteId(), line.lineId());
        ledger.recordTippingCompleted(thirdManifest.physicalToteId(), line.lineId());
        assertEquals(0, ledger.snapshot().outstandingToteCount(line.lineId()));
        assertEquals(3, ledger.snapshot().version());
        assertEquals(6, ledger.snapshot().outstandingVersion());
    }

    @Test
    void shouldCountAppliedAv02AssignmentsButNotStoreOrPredepartureValidation() {
        var full = order("full", 1, "Z", OrderType.FULL_PACK);
        var adapted = order("adapted", 1, "Z", OrderType.ADAPTED);
        var empty = order("empty", 1, "Z", OrderType.EMPTY);
        var fullManifest = manifest("physical-full", full);
        var adaptedManifest = manifest("physical-adapted", adapted);
        var ledger = ledger(List.of(full, adapted, empty), List.of(fullManifest, adaptedManifest), 2);
        var line = LINES.get(1);

        recordSuccess(ledger, new ReleasePhysicalToteFromOsrCommand(adaptedManifest.physicalToteId(),
                adaptedManifest.orderSheetKey(), "Z", "adapting-1"));
        assertEquals(1, ledger.snapshot().version());
        assertEquals(0, ledger.snapshot().outstandingVersion());
        assertEquals(0, ledger.snapshot().outstandingToteCount(line.lineId()));

        var osrCommand = osr(fullManifest, line);
        var beforeAssignment = ledger.snapshot();
        ledger.validateUnreleased(osrCommand);
        assertSame(beforeAssignment, ledger.snapshot());
        recordSuccess(ledger, osrCommand);
        assertEquals(1, ledger.snapshot().outstandingToteCount(line.lineId()));
        assertEquals(1, ledger.snapshot().outstandingVersion());

        var av02Command = av02(empty, "av02-generated-1", line);
        var beforeDeparture = ledger.snapshot();
        ledger.validateUnreleased(av02Command); // Logical allocation/proposal does not commit a departure.
        assertSame(beforeDeparture, ledger.snapshot());
        assertEquals(1, beforeDeparture.unreleasedEmptySheetCounts().get("Z"));
        recordSuccess(ledger, av02Command);
        assertEquals(2, ledger.snapshot().outstandingToteCount(line.lineId()));
        assertEquals(2, ledger.snapshot().outstandingVersion());
        assertEquals(3, ledger.snapshot().version());

        var fresh = ledger(List.of(full, adapted, empty), List.of(fullManifest, adaptedManifest), 2);
        assertEquals(0, fresh.snapshot().outstandingVersion());
        assertEquals(0, fresh.snapshot().outstandingToteCount(line.lineId()));
        assertEquals(2, ledger.snapshot().outstandingToteCount(line.lineId()));

        ledger.recordTippingCompleted(new PhysicalToteId("av02-generated-1"), line.lineId());
        ledger.recordTippingCompleted(fullManifest.physicalToteId(), line.lineId());
        assertEquals(0, ledger.snapshot().outstandingToteCount(line.lineId()));
        assertEquals(4, ledger.snapshot().outstandingVersion());
        assertEquals(3, ledger.snapshot().version());
        assertEquals(0, fresh.snapshot().outstandingToteCount(line.lineId()));
    }

    @Test
    void shouldCountTheWholeDayAndAdvanceOnlyAfterEverySourceObligationCommits() {
        var full = order("full", 1, "Z", OrderType.FULL_PACK);
        var adapted = order("adapted", 1, "Z", OrderType.ADAPTED);
        var empty = order("empty", 2, "Z", OrderType.EMPTY);
        var a = order("later-empty", 3, "A", OrderType.EMPTY);
        var b = order("b", 1, "B", OrderType.FULL_PACK);
        var y = order("y", 1, "Y", OrderType.ASSOCIATED);
        var first = manifest("physical-full-1", full);
        var second = manifest("physical-full-2", full);
        var preparation = manifest("physical-adapted", adapted);
        var bManifest = manifest("physical-b", b);
        var yManifest = manifest("physical-y", y);
        // No supply, preload or AV02 allocation is consulted: every obligation already exists.
        var ledger = ledger(List.of(b, a, full, empty, adapted, y),
                List.of(bManifest, second, preparation, yManifest, first));
        var initial = ledger.snapshot();
        assertEquals(List.of("Z", "A", "B", "Y"), initial.orderedServiceCentreIds());
        assertEquals(Integer.MAX_VALUE, initial.p2pOutstandingToteWatermark());
        assertEquals(0, initial.outstandingVersion());
        assertEquals(List.of("line-1", "line-2", "line-3", "line-4", "line-5"),
                initial.outstandingP2pToteCounts().keySet().stream().map(P2pLineId::value).toList());
        assertEquals(Map.of("Z", 3, "A", 0, "B", 1, "Y", 1), initial.unreleasedOsrToteCounts());
        assertEquals(Map.of("Z", 1, "A", 1, "B", 0, "Y", 0), initial.unreleasedEmptySheetCounts());
        assertState(initial, 0, "Z", 3, 1);
        for (String centre : initial.orderedServiceCentreIds()) {
            assertFalse(initial.allReleased(centre));
            for (P2pLineDefinition line : LINES) {
                assertEquals(0, initial.committedToteCount(centre, line.lineId()));
            }
        }
        assertThrows(IllegalArgumentException.class, () -> initial.allReleased("zero-work"));
        var firstCommand = osr(first, LINES.get(0));
        ledger.validateUnreleased(firstCommand);
        ledger.validateUnreleased(osr(second, LINES.get(1)));
        assertSame(initial, ledger.snapshot());
        rejectWithoutMutation(ledger, osr(bManifest, LINES.get(0)));

        // Non-P2P ADAPTED STORE counts globally but never adds a P2P committed tote.
        recordSuccess(ledger, new ReleasePhysicalToteFromOsrCommand(preparation.physicalToteId(),
                preparation.orderSheetKey(), "Z", "adapting-1"));
        assertState(ledger.snapshot(), 1, "Z", 2, 1);
        assertEquals(0, ledger.snapshot().committedToteCount("Z", LINES.get(0).lineId()));
        recordSuccess(ledger, firstCommand);
        assertState(ledger.snapshot(), 2, "Z", 1, 1);
        recordSuccess(ledger, osr(second, LINES.get(1)));
        var waitingForEmpty = ledger.snapshot();
        assertState(waitingForEmpty, 3, "Z", 0, 1);
        assertFalse(waitingForEmpty.allReleased("Z"));
        assertEquals(1, waitingForEmpty.committedToteCount("Z", LINES.get(0).lineId()));
        assertEquals(1, waitingForEmpty.committedToteCount("Z", LINES.get(1).lineId()));
        rejectWithoutMutation(ledger, av02(a, "av02-000002", LINES.get(2)));
        rejectWithoutMutation(ledger, firstCommand);
        ledger.validateUnreleased(av02(empty, "av02-000001", LINES.get(0)));
        assertSame(waitingForEmpty, ledger.snapshot());
        recordSuccess(ledger, av02(empty, "av02-000001", LINES.get(0)));
        assertState(ledger.snapshot(), 4, "A", 0, 0);
        assertTrue(ledger.snapshot().allReleased("Z"));
        assertEquals(2, ledger.snapshot().committedToteCount("Z", LINES.get(0).lineId()));
        // Release-complete is independent of the still-pinned processing and output work.
        rejectWithoutMutation(ledger, av02(empty, "av02-retry", LINES.get(0)));
        recordSuccess(ledger, av02(a, "av02-000002", LINES.get(2)));
        assertState(ledger.snapshot(), 5, "B", 0, 0);
        recordSuccess(ledger, osr(bManifest, LINES.get(3)));
        assertState(ledger.snapshot(), 6, "Y", 0, 0);
        recordSuccess(ledger, osr(yManifest, LINES.get(4)));
        var finished = ledger.snapshot();
        assertEquals(7, finished.version());
        assertTrue(finished.releaseServiceCentreId().isEmpty());
        assertEquals(List.of("Z", "A", "B", "Y"), List.copyOf(finished.unreleasedOsrToteCounts().keySet()));
        assertEquals(1, finished.committedToteCount("A", LINES.get(2).lineId()));
        assertEquals(1, finished.committedToteCount("B", LINES.get(3).lineId()));
        assertEquals(1, finished.committedToteCount("Y", LINES.get(4).lineId()));
        for (String centre : finished.orderedServiceCentreIds()) {
            assertTrue(finished.allReleased(centre));
        }
        rejectWithoutMutation(ledger, osr(yManifest, LINES.get(4)));
        assertEquals(0, initial.version());
        assertEquals(3, initial.unreleasedOsrToteCounts().get("Z"));
        assertEquals(1, initial.unreleasedEmptySheetCounts().get("Z"));
        assertEquals(0, initial.committedToteCount("Z", LINES.get(0).lineId()));
        assertFalse(initial.allReleased("Z"));
        assertNotSame(initial, finished);
    }

    @Test
    void shouldHoldTheCentreForTheLastUpstreamPhysicalManifest() {
        var order = order("same-sheet", 1, "Z", OrderType.FULL_PACK);
        var preloaded = manifest("preloaded", order);
        var upstream = manifest("not-supplied-yet", order);
        var ledger = ledger(List.of(order), List.of(preloaded, upstream));
        recordSuccess(ledger, osr(preloaded, LINES.get(0)));
        var blocked = ledger.snapshot();
        assertState(blocked, 1, "Z", 1, 0);
        assertFalse(blocked.allReleased("Z"));
        for (int index = 0; index < 100; index++) {
            assertSame(blocked, ledger.snapshot());
        }
        recordSuccess(ledger, osr(upstream, LINES.get(0)));
        assertEquals(2, ledger.snapshot().version());
        assertTrue(ledger.snapshot().allReleased("Z"));
        assertTrue(ledger.snapshot().releaseServiceCentreId().isEmpty());
    }

    @Test
    void shouldRejectWrongSourceSheetCentreAndLineWithoutChangingAnyAccounting() {
        var full = order("full", 1, "Z", OrderType.FULL_PACK);
        var empty = order("empty", 1, "Z", OrderType.EMPTY);
        var physical = manifest("physical-full", full);
        var ledger = ledger(List.of(full, empty), List.of(physical));
        var valid = osr(physical, LINES.get(0));
        var wrongLine = new P2pLineDefinition(new P2pLineId("not-configured"), destination("p2p-other"));
        var wrongDestination = new P2pLineDefinition(LINES.get(0).lineId(), destination("p2p-other"));
        for (OperationalPhysicalToteReleaseCommand invalid : Arrays.asList(
                null,
                new ReleasePhysicalToteFromOsrCommand(new PhysicalToteId("unknown"),
                        full.orderSheetKey(), "Z", "entry"),
                new ReleasePhysicalToteFromOsrCommand(physical.physicalToteId(),
                        new OrderSheetKey("wrong-sheet", 1), "Z", "entry"),
                new ReleasePhysicalToteFromOsrCommand(physical.physicalToteId(),
                        full.orderSheetKey(), "B", "entry"),
                new ReleasePhysicalToteFromAv02Command(new PhysicalToteId("av02-000001"),
                        full.orderSheetKey(), "Z", "entry"),
                new ReleasePhysicalToteFromOsrCommand(new PhysicalToteId("av02-000001"),
                        empty.orderSheetKey(), "Z", "entry"),
                av02(empty, physical.physicalToteId().value(), LINES.get(0)),
                new ReleasePhysicalToteFromAv02Command(new PhysicalToteId("av02-000001"),
                        new OrderSheetKey("empty", 2), "Z", "entry"),
                new ReleasePhysicalToteFromAv02Command(new PhysicalToteId("av02-000001"),
                        empty.orderSheetKey(), "B", "entry"),
                osr(physical, wrongLine), osr(physical, wrongDestination),
                av02(empty, "av02-000001", wrongLine), av02(empty, "av02-000001", wrongDestination),
                new UncheckedCommand(null, physical.physicalToteId(), full.orderSheetKey(), "Z", Optional.empty()),
                new UncheckedCommand(OperationalPhysicalToteSource.OSR, null, full.orderSheetKey(),
                        "Z", Optional.empty()),
                new UncheckedCommand(OperationalPhysicalToteSource.OSR, physical.physicalToteId(), null,
                        "Z", Optional.empty()),
                new UncheckedCommand(OperationalPhysicalToteSource.OSR, physical.physicalToteId(),
                        full.orderSheetKey(), null, Optional.empty()),
                new UncheckedCommand(OperationalPhysicalToteSource.OSR, physical.physicalToteId(),
                        full.orderSheetKey(), "Z", null),
                new UncheckedCommand(OperationalPhysicalToteSource.OSR, physical.physicalToteId(),
                        full.orderSheetKey(), "Z", Optional.of(assignment(
                                new PhysicalToteId("different-physical"), "Z", LINES.get(0)))),
                new UncheckedCommand(OperationalPhysicalToteSource.OSR, physical.physicalToteId(),
                        full.orderSheetKey(), "Z", Optional.of(assignment(
                                physical.physicalToteId(), "B", LINES.get(0)))))) {
            rejectWithoutMutation(ledger, invalid);
        }
        ledger.validateUnreleased(valid);
        assertState(ledger.snapshot(), 0, "Z", 1, 1);
        recordSuccess(ledger, valid);
        rejectWithoutMutation(ledger, valid);
        assertState(ledger.snapshot(), 1, "Z", 0, 1);
    }

    @Test
    void shouldKeepEmptySheetsDistinctAndRejectReusingAnAppliedAv02PhysicalIdentity() {
        var first = order("logical-not-a-barcode", 1, "Z", OrderType.EMPTY);
        var second = order("logical-not-a-barcode", 2, "Z", OrderType.EMPTY);
        var ledger = ledger(List.of(first, second), List.of());
        recordSuccess(ledger, av02(first, "av02-000001", LINES.get(0)));
        assertState(ledger.snapshot(), 1, "Z", 0, 1);
        rejectWithoutMutation(ledger, av02(second, "av02-000001", LINES.get(1)));
        rejectWithoutMutation(ledger, av02(first, "av02-000002", LINES.get(1)));
        recordSuccess(ledger, av02(second, "av02-000002", LINES.get(1)));
        assertTrue(ledger.snapshot().releaseServiceCentreId().isEmpty());
        assertEquals(1, ledger.snapshot().committedToteCount("Z", LINES.get(0).lineId()));
        assertEquals(1, ledger.snapshot().committedToteCount("Z", LINES.get(1).lineId()));
    }

    @Test
    void shouldReuseSnapshotsForALargeStaticDayAndPublishIndependentVersions() {
        List<NotionalToteOrder> orders = new ArrayList<>();
        List<InboundToteManifest> manifests = new ArrayList<>();
        for (int index = 0; index < 10_000; index++) {
            var order = order("large-" + index, 1, "Z", OrderType.FULL_PACK);
            orders.add(order);
            manifests.add(manifest("physical-" + index, order));
        }
        var ledger = ledger(orders, manifests);
        var initial = ledger.snapshot();
        var command = osr(manifests.getFirst(), LINES.get(0));
        for (int index = 0; index < 1_000; index++) {
            ledger.validateUnreleased(command);
            assertSame(initial, ledger.snapshot());
        }
        recordSuccess(ledger, command);
        var replacement = ledger.snapshot();
        assertNotSame(initial, replacement);
        assertEquals(10_000, initial.unreleasedOsrToteCounts().get("Z"));
        assertEquals(9_999, replacement.unreleasedOsrToteCounts().get("Z"));
        assertEquals(1, replacement.version());
        assertEquals(0, initial.committedToteCount("Z", LINES.get(0).lineId()));
        assertEquals(1, replacement.committedToteCount("Z", LINES.get(0).lineId()));
        for (int index = 0; index < 1_000; index++) {
            assertSame(replacement, ledger.snapshot());
        }
    }

    @Test
    void shouldRejectMissingOrConflictingExecutableAndConfiguredIdentities() {
        var full = order("full", 1, "Z", OrderType.FULL_PACK);
        var physical = manifest("physical-full", full);
        var data = data(List.of(full), List.of(physical));
        assertThrows(IllegalArgumentException.class, () -> new WholeServiceCentreReleaseLedger(null, TIMETABLE, LINES));
        assertThrows(IllegalArgumentException.class, () -> new WholeServiceCentreReleaseLedger(data, null, LINES));
        for (List<P2pLineDefinition> invalid : Arrays.asList(null, List.<P2pLineDefinition>of(),
                Arrays.asList((P2pLineDefinition) null), List.of(LINES.get(0), LINES.get(0)),
                List.of(LINES.get(0), new P2pLineDefinition(LINES.get(1).lineId(), LINES.get(0).destination())))) {
            assertThrows(IllegalArgumentException.class, () -> new WholeServiceCentreReleaseLedger(data, TIMETABLE, invalid));
        }
        assertThrows(IllegalArgumentException.class,
                () -> new WholeServiceCentreReleaseLedger(data, TIMETABLE, LINES, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new WholeServiceCentreReleaseLedger(data, TIMETABLE, LINES, -1));
        var empty = order("empty", 1, "Z", OrderType.EMPTY);
        var unknown = order("unknown", 1, "unknown", OrderType.FULL_PACK);
        var wrongPriority = new NotionalToteOrder(full.orderId(), full.notionalToteId(), "Z", 1,
                full.orderType(), full.items(), 998, 0);
        var wrongCentre = new NotionalToteOrder(full.orderId(), full.notionalToteId(), "B", 1,
                full.orderType(), full.items(), 998, 0);
        for (LoadedDspData invalid : List.of(
                data(List.of(full), List.of()),
                data(List.of(), List.of(physical)),
                data(List.of(full, full), List.of(physical)),
                data(List.of(full, wrongCentre), List.of(physical)),
                data(List.of(empty, empty), List.of()),
                data(List.of(full), List.of(physical, physical)),
                data(List.of(full, empty), List.of(new InboundToteManifest(new PhysicalToteId("wrong-source"),
                        empty.orderSheetKey(), OrderType.FULL_PACK, "Z", full.items(), 0))),
                data(List.of(full), List.of(new InboundToteManifest(physical.physicalToteId(),
                        full.orderSheetKey(), OrderType.ASSOCIATED, "Z", full.items(), 0))),
                data(List.of(full), List.of(new InboundToteManifest(physical.physicalToteId(),
                        full.orderSheetKey(), full.orderType(), "B", full.items(), 0))),
                data(List.of(unknown), List.of(manifest("unknown-physical", unknown))),
                data(List.of(wrongPriority), List.of(physical)))) {
            assertThrows(IllegalArgumentException.class,
                    () -> new WholeServiceCentreReleaseLedger(invalid, TIMETABLE, LINES));
        }
        var noWork = ledger(List.of(), List.of()).snapshot();
        assertTrue(noWork.orderedServiceCentreIds().isEmpty());
        assertTrue(noWork.releaseServiceCentreId().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> noWork.allReleased("Z"));
    }

    @Test
    void shouldDefensivelyPublishOnlySmallCountMapsAndRejectUnknownQueries() {
        List<String> centres = new ArrayList<>(List.of("Z"));
        Map<String, Integer> osr = new LinkedHashMap<>(Map.of("Z", 1));
        Map<String, Integer> empty = new LinkedHashMap<>(Map.of("Z", 0));
        Map<P2pLineId, Integer> perLine = new LinkedHashMap<>();
        LINES.forEach(line -> perLine.put(line.lineId(), 0));
        Map<String, Map<P2pLineId, Integer>> committed = new LinkedHashMap<>();
        committed.put("Z", perLine);
        var snapshot = new WholeServiceCentreReleaseSnapshot(0, centres, Optional.of("Z"), osr, empty, committed);
        centres.clear();
        osr.put("Z", 0);
        empty.put("Z", 7);
        perLine.put(LINES.get(0).lineId(), 8);
        committed.clear();
        assertEquals(List.of("Z"), snapshot.orderedServiceCentreIds());
        assertEquals(1, snapshot.unreleasedOsrToteCounts().get("Z"));
        assertEquals(0, snapshot.unreleasedEmptySheetCounts().get("Z"));
        assertEquals(0, snapshot.committedToteCount("Z", LINES.get(0).lineId()));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.orderedServiceCentreIds().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.unreleasedOsrToteCounts().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.unreleasedEmptySheetCounts().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.committedP2pToteCounts().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.committedP2pToteCounts().get("Z").clear());
        for (String unknown : Arrays.asList(null, "", "unknown", "zero-work")) {
            assertThrows(IllegalArgumentException.class, () -> snapshot.allReleased(unknown));
            assertThrows(IllegalArgumentException.class, () -> snapshot.committedToteCount(unknown, LINES.get(0).lineId()));
        }
        assertThrows(IllegalArgumentException.class, () -> snapshot.committedToteCount("Z", null));
        assertThrows(IllegalArgumentException.class, () -> snapshot.committedToteCount("Z", new P2pLineId("unknown")));
        assertEquals(List.of("version", "orderedServiceCentreIds", "releaseServiceCentreId",
                "unreleasedOsrToteCounts", "unreleasedEmptySheetCounts", "committedP2pToteCounts",
                "outstandingVersion", "p2pOutstandingToteWatermark", "outstandingP2pToteCounts"),
                Arrays.stream(WholeServiceCentreReleaseSnapshot.class.getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName).toList());
    }

    @Test
    void shouldRejectMalformedSnapshotCountsAndCursorFacts() {
        var line = LINES.get(0).lineId();
        var validCounts = Map.of("Z", Map.of(line, 0));
        assertThrows(IllegalArgumentException.class, () -> new WholeServiceCentreReleaseSnapshot(
                -1, List.of("Z"), Optional.of("Z"), Map.of("Z", 1), Map.of("Z", 0), validCounts));
        assertThrows(IllegalArgumentException.class, () -> new WholeServiceCentreReleaseSnapshot(
                0, null, Optional.of("Z"), Map.of("Z", 1), Map.of("Z", 0), validCounts));
        assertThrows(IllegalArgumentException.class, () -> new WholeServiceCentreReleaseSnapshot(
                0, List.of("Z"), null, Map.of("Z", 1), Map.of("Z", 0), validCounts));
        assertThrows(IllegalArgumentException.class, () -> new WholeServiceCentreReleaseSnapshot(
                0, List.of("Z"), Optional.of("Z"), null, Map.of("Z", 0), validCounts));
        assertThrows(IllegalArgumentException.class, () -> new WholeServiceCentreReleaseSnapshot(
                0, List.of("Z"), Optional.of("Z"), Map.of("Z", 1), null, validCounts));
        assertThrows(IllegalArgumentException.class, () -> new WholeServiceCentreReleaseSnapshot(
                0, List.of("Z"), Optional.of("Z"), Map.of("Z", 1), Map.of("Z", 0), null));
        for (List<String> invalid : List.of(List.of("Z", "Z"), List.of(""), Arrays.asList((String) null))) {
            assertThrows(IllegalArgumentException.class, () -> new WholeServiceCentreReleaseSnapshot(
                    0, invalid, Optional.of("Z"), Map.of("Z", 1), Map.of("Z", 0), validCounts));
        }
        for (Map<String, Integer> invalid : List.of(Map.of("Z", -1), Map.of("unknown", 1),
                Map.<String, Integer>of(), java.util.Collections.<String, Integer>singletonMap("Z", null))) {
            assertThrows(IllegalArgumentException.class, () -> new WholeServiceCentreReleaseSnapshot(
                    0, List.of("Z"), Optional.of("Z"), invalid, Map.of("Z", 0), validCounts));
            assertThrows(IllegalArgumentException.class, () -> new WholeServiceCentreReleaseSnapshot(
                    0, List.of("Z"), Optional.of("Z"), Map.of("Z", 1), invalid, validCounts));
        }
        for (Map<String, Map<P2pLineId, Integer>> invalid : List.of(Map.<String, Map<P2pLineId, Integer>>of(),
                Map.of("unknown", Map.of(line, 0)), Map.of("Z", Map.<P2pLineId, Integer>of()),
                java.util.Collections.<String, Map<P2pLineId, Integer>>singletonMap("Z", null),
                Map.of("Z", Map.of(line, -1)),
                Map.of("Z", java.util.Collections.<P2pLineId, Integer>singletonMap(line, null)),
                Map.of("Z", java.util.Collections.<P2pLineId, Integer>singletonMap(null, 0)))) {
            assertThrows(IllegalArgumentException.class, () -> new WholeServiceCentreReleaseSnapshot(
                    0, List.of("Z"), Optional.of("Z"), Map.of("Z", 1), Map.of("Z", 0), invalid));
        }
        assertThrows(IllegalArgumentException.class, () -> new WholeServiceCentreReleaseSnapshot(
                0, List.of("Z", "A"), Optional.of("Z"), Map.of("Z", 1, "A", 1), Map.of("Z", 0, "A", 0),
                Map.of("Z", Map.of(line, 0), "A", Map.of(LINES.get(1).lineId(), 0))));
        for (Optional<String> invalid : List.of(Optional.<String>empty(), Optional.of("unknown"), Optional.of("A"))) {
            assertThrows(IllegalArgumentException.class, () -> new WholeServiceCentreReleaseSnapshot(
                    0, List.of("Z", "A"), invalid, Map.of("Z", 1, "A", 1), Map.of("Z", 0, "A", 0),
                    Map.of("Z", Map.of(line, 0), "A", Map.of(line, 0))));
        }
        assertThrows(IllegalArgumentException.class, () -> new WholeServiceCentreReleaseSnapshot(
                0, List.of("Z"), Optional.of("Z"), Map.of("Z", 0), Map.of("Z", 0), validCounts));
    }

    private static void assertState(WholeServiceCentreReleaseSnapshot snapshot, long version,
            String releaseCentre, int osr, int empty) {
        assertEquals(version, snapshot.version());
        assertEquals(Optional.of(releaseCentre), snapshot.releaseServiceCentreId());
        assertEquals(osr, snapshot.unreleasedOsrToteCounts().get("Z"));
        assertEquals(empty, snapshot.unreleasedEmptySheetCounts().get("Z"));
    }

    private static void rejectWithoutMutation(WholeServiceCentreReleaseLedger ledger,
            OperationalPhysicalToteReleaseCommand command) {
        var before = ledger.snapshot();
        assertThrows(IllegalArgumentException.class, () -> ledger.validateUnreleased(command));
        assertSame(before, ledger.snapshot());
        assertThrows(IllegalStateException.class, () -> ledger.recordApplied(command));
        assertSame(before, ledger.snapshot());
    }

    private static void recordSuccess(WholeServiceCentreReleaseLedger ledger,
            OperationalPhysicalToteReleaseCommand command) {
        ledger.validateUnreleased(command);
        // Isolated accounting boundary: the caller records only an applied delegate result.
        ledger.recordApplied(command);
    }

    private static WholeServiceCentreReleaseLedger ledger(List<NotionalToteOrder> orders,
            List<InboundToteManifest> manifests) {
        return new WholeServiceCentreReleaseLedger(data(orders, manifests), TIMETABLE, LINES);
    }

    private static WholeServiceCentreReleaseLedger ledger(List<NotionalToteOrder> orders,
            List<InboundToteManifest> manifests, int watermark) {
        return new WholeServiceCentreReleaseLedger(data(orders, manifests), TIMETABLE, LINES, watermark);
    }

    private static LoadedDspData data(List<NotionalToteOrder> orders, List<InboundToteManifest> manifests) {
        return new LoadedDspData(List.of(), orders, List.of(), Set.of(), Set.of(), manifests,
                DspDatasetLoadReport.empty());
    }

    private static NotionalToteOrder order(String id, int sheet, String centre, OrderType type) {
        int priority = switch (centre) {
            case "Z" -> 999;
            case "A", "B" -> 998;
            default -> 997;
        };
        var item = new DspOrderItem(id + "-line-" + sheet, "product", 1, "pharmacy", "patient", "rx-" + id,
                type == OrderType.ADAPTED ? DspOrderLineType.ADAPTED : DspOrderLineType.FULL_PACK,
                id, sheet, 1);
        return new NotionalToteOrder(id, "notional-" + id, centre, sheet, type, List.of(item), priority, 0);
    }

    private static InboundToteManifest manifest(String physicalId, NotionalToteOrder order) {
        return new InboundToteManifest(new PhysicalToteId(physicalId), order.orderSheetKey(), order.orderType(),
                order.serviceCentreId(), order.items(), order.sequenceNumber());
    }

    private static ReleasePhysicalToteFromOsrCommand osr(InboundToteManifest manifest, P2pLineDefinition line) {
        return new ReleasePhysicalToteFromOsrCommand(manifest.physicalToteId(), manifest.orderSheetKey(),
                manifest.serviceCentreId(), "entry", Optional.of(assignment(
                        manifest.physicalToteId(), manifest.serviceCentreId(), line)));
    }

    private static ReleasePhysicalToteFromAv02Command av02(NotionalToteOrder empty,
            String physicalId, P2pLineDefinition line) {
        var physical = new PhysicalToteId(physicalId);
        return new ReleasePhysicalToteFromAv02Command(physical, empty.orderSheetKey(), empty.serviceCentreId(),
                "entry", Optional.of(assignment(physical, empty.serviceCentreId(), line)));
    }

    private static P2pPhysicalToteAssignment assignment(PhysicalToteId physical, String centre, P2pLineDefinition line) {
        return new P2pPhysicalToteAssignment(physical, centre, line.lineId(), line.destination());
    }

    private static OperationalRouteDestination destination(String id) {
        return new OperationalRouteDestination(StationType.P2P, id);
    }

    private static ServiceCentreSchedule schedule(String id, int priority) {
        return new ServiceCentreSchedule(id, id, priority, OperationalDayTime.day0(java.time.LocalTime.of(17, 0)));
    }

    private record UncheckedCommand(OperationalPhysicalToteSource source, PhysicalToteId physicalToteId,
            OrderSheetKey orderSheetKey, String serviceCentreId,
            Optional<P2pPhysicalToteAssignment> proposedP2pAssignment)
            implements OperationalPhysicalToteReleaseCommand {
        @Override
        public String releaseTargetId() {
            return "entry";
        }
    }
}
