package online.davisfamily.warehouse.sim.dsp.analysis.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.analysis.*;
import online.davisfamily.warehouse.sim.dsp.analysis.input.DspFullDayInputPreflight;
import online.davisfamily.warehouse.sim.dsp.analysis.input.DspInputRejectionCatalog;
import online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayReportTestSupport;
import online.davisfamily.warehouse.sim.dsp.bagging.*;
import online.davisfamily.warehouse.sim.dsp.io.*;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleState;
import online.davisfamily.warehouse.sim.dsp.model.*;
import online.davisfamily.warehouse.sim.dsp.osr.OsrInventoryConfig;
import online.davisfamily.warehouse.sim.dsp.outbound.*;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineAllocationBlockReason;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pPhysicalToteAssignment;
import online.davisfamily.warehouse.sim.dsp.scheduler.operational.OperationalReleaseBlockType;
import online.davisfamily.warehouse.sim.dsp.scheduler.policy.DspSchedulerPolicy;
import online.davisfamily.warehouse.sim.dsp.scheduler.policy.WholeServiceCentreReleaseSnapshot;
import online.davisfamily.warehouse.sim.dsp.supply.FixedIntervalInboundToteArrivalPolicy;
import online.davisfamily.warehouse.sim.dsp.supply.ServiceCentreSupplyConfig;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyAreaConfig;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;

/** Bounded real-machine journeys; no direct queue, lease or completion mutations. */
class DspFullDayP2pOutstandingToteWatermarkScenarioTest {
    private static final int MAXIMUM_UPDATES = 2_000;

    @Test
    void shouldReopenCapOneAtActualTippingWhileDownstreamBagsRemainUnfinished() {
        var profile = profile(List.of("104"), 1, 90d);
        var input = input(profile, oneCentreOrders());
        try (var runtime = new DspFullDayAnalysisRuntimeFactory().create(input, profile)) {
            assertOutstandingMatchesLifecycle(runtime);
            boolean seeded = false;
            boolean reopenedWithBagsPending = false;
            boolean allTippedWithBagsPending = false;
            for (int step = 0; step < MAXIMUM_UPDATES && runtime.state() == DspFullDayRuntimeState.RUNNING; step++) {
                runtime.update(1d);
                assertOutstandingMatchesLifecycle(runtime);
                var releases = releases(runtime);
                long committed = releases.version(); // Every fixture release has a P2P assignment.
                long completed = releases.outstandingVersion() - committed;
                if (completed == 0) {
                    assertTrue(committed <= 5, "A sixth commit must wait for actual tipper completion");
                }
                if (committed == 5 && completed == 0) {
                    seeded = true;
                    assertTrue(releases.outstandingP2pToteCounts().values().stream().allMatch(count -> count == 1));
                    assertEquals(5, assignments(runtime).stream().map(P2pPhysicalToteAssignment::lineId).distinct().count());
                }
                boolean bagsPending = runtime.outboundToteAllocator().snapshot().allocatedBags().size() < 12;
                reopenedWithBagsPending |= committed > 5 && completed > 0 && bagsPending;
                allTippedWithBagsPending |= committed == 12 && completed == 12 && bagsPending
                        && releases.outstandingP2pToteCounts().values().stream().allMatch(count -> count == 0);
            }
            assertComplete(runtime);
            assertTrue(seeded, "All five lines must fill their initial slot before any tipper completion");
            assertTrue(reopenedWithBagsPending, "Actual tipping must reopen capacity independently of bags");
            assertTrue(allTippedWithBagsPending, "Zero inbound counts must not imply downstream completion");
            assertEquals(12, releases(runtime).version());
            assertEquals(24, releases(runtime).outstandingVersion());
            assertExactOutput(input, runtime);
        }
    }

    @Test
    void shouldCapEarlyNextCentreLineUntilOtherOldOwnersFullyDrain() {
        var profile = profile(List.of("104", "108"), 2, .1d);
        List<NotionalToteOrder> orders = new ArrayList<>();
        for (int index = 1; index <= 5; index++) {
            orders.add(order("old-" + index, "104", index, index == 5 ? 1 : 12));
        }
        for (int index = 1; index <= 12; index++) {
            orders.add(order("next-" + index, "108", index + 5, 1));
        }
        var input = input(profile, orders);
        var line5 = profile.p2pLineDefinitions().get(4).lineId();
        Set<OrderSheetKey> nextSheets = orders.stream().filter(order -> order.serviceCentreId().equals("108"))
                .map(NotionalToteOrder::orderSheetKey).collect(Collectors.toSet());
        try (var runtime = new DspFullDayAnalysisRuntimeFactory().create(input, profile)) {
            assertOutstandingMatchesLifecycle(runtime);
            boolean staggered = false;
            boolean cappedBeforeFirstNextTip = false;
            boolean typedWait = false;
            boolean otherLinesUsed = false;
            Set<P2pLineId> handedOver = new LinkedHashSet<>();
            Map<PhysicalToteId, Integer> closedAt = new LinkedHashMap<>();
            Map<String, P2pLineId> bagPins = new LinkedHashMap<>();
            for (int step = 0; step < MAXIMUM_UPDATES && runtime.state() == DspFullDayRuntimeState.RUNNING; step++) {
                final int observationStep = step;
                runtime.update(1d);
                assertOutstandingMatchesLifecycle(runtime);
                var snapshot = runtime.snapshot();
                var leases = snapshot.elastic().leases().lines();
                var lifecycle = runtime.lifecycleSnapshotSupplier().get();
                var outbound = runtime.outboundToteAllocator().snapshot();
                outbound.closedTotes().forEach(tote -> closedAt.putIfAbsent(tote.physicalToteId(), observationStep));
                boolean line5Alone = leases.get(4).serviceCentreId().equals(Optional.of("108"))
                        && leases.subList(0, 4).stream().allMatch(line -> line.serviceCentreId().equals(Optional.of("104")));
                staggered |= line5Alone;
                boolean nextTipCompleted = assignments(runtime).stream().filter(assignment -> assignment.serviceCentreId().equals("108"))
                        .anyMatch(assignment -> lifecycle.totes().get(assignment.physicalToteId()).state()
                                == PhysicalToteLifecycleState.CONSUMED_AT_P2P);
                if (line5Alone && !nextTipCompleted && releases(runtime).outstandingToteCount(line5) == 2) {
                    cappedBeforeFirstNextTip = true;
                    typedWait |= snapshot.operationalRelease().lastEvaluation().stream()
                            .flatMap(evaluation -> evaluation.blockedCandidates().stream())
                            .filter(candidate -> nextSheets.contains(candidate.orderSheetKey()))
                            .flatMap(candidate -> candidate.blocks().stream())
                            .anyMatch(block -> block.type() == OperationalReleaseBlockType.P2P_LINE_ALLOCATION
                                    && block.reason().equals(P2pLineAllocationBlockReason.OUTSTANDING_TOTE_WATERMARK.name()));
                }
                for (var line : leases) {
                    var id = line.definition().lineId();
                    if (line.serviceCentreId().equals(Optional.of("108")) && handedOver.add(id)) {
                        var oldAssignments = line.physicalAssignments().stream()
                                .filter(assignment -> assignment.serviceCentreId().equals("104")).toList();
                        assertFalse(oldAssignments.isEmpty());
                        assertTrue(oldAssignments.stream().allMatch(assignment -> lifecycle.totes()
                                .get(assignment.physicalToteId()).state() == PhysicalToteLifecycleState.CONSUMED_AT_P2P));
                        var oldClosed = outbound.closedTotes().stream().filter(tote -> tote.p2pLineId().equals(id)
                                && tote.serviceCentreId().equals(Optional.of("104"))).toList();
                        assertFalse(oldClosed.isEmpty(), "Old output must close before handover");
                        // Capacity closure is performed by bag allocation before lease retention
                        // and release in the same update; explicit final closure takes its own update.
                        assertTrue(oldClosed.stream().allMatch(tote -> closedAt.get(tote.physicalToteId()) < observationStep
                                || (tote.closureReason().equals(Optional.of(OutboundToteClosureReason.CAPACITY_REACHED))
                                        && tote.bagCount() == tote.maximumBagCount())));
                        assertTrue(input.bagPlan().plannedBags().stream()
                                .filter(bag -> bag.serviceCentreId().equals("104"))
                                .filter(bag -> runtime.elasticRuntime().correlationAssignmentSnapshot()
                                        .lineFor(bag.bagKey().correlationId()).equals(Optional.of(id)))
                                .allMatch(bag -> outbound.findAllocatedBag(bag.bagKey()).isPresent()));
                    }
                    for (var assignment : line.physicalAssignments()) {
                        if (assignment.serviceCentreId().equals("108")) {
                            assertTrue(handedOver.contains(id), "Next-centre work must never use an old foreign owner");
                            otherLinesUsed |= !id.equals(line5);
                        }
                    }
                    line.activity().openOutboundTote().ifPresent(tote -> assertEquals(line.serviceCentreId(), tote.serviceCentreId()));
                }
                for (var pin : runtime.elasticRuntime().correlationAssignmentSnapshot().assignments()) {
                    var previous = bagPins.putIfAbsent(pin.correlationId(), pin.lineId());
                    if (previous != null) { assertEquals(previous, pin.lineId()); }
                }
            }
            assertComplete(runtime);
            assertTrue(staggered, "Line 5 must reach 108 while lines 1-4 still own 104");
            assertTrue(cappedBeforeFirstNextTip, "Early line 5 must fill two slots before its first next-centre tip finishes");
            assertTrue(typedWait, "The capped otherwise-eligible line must produce the typed watermark wait");
            assertTrue(otherLinesUsed, "Later 108 assignments must use other fully drained lines");
            assertEquals(17, releases(runtime).version());
            assertEquals(34, releases(runtime).outstandingVersion());
            assertExactOutput(input, runtime);
        }
    }

    @Test
    void shouldLeaveDeadlineAwareReleaseAssignmentAndPhysicalOutputIdenticalAtOneAndEight() {
        var one = DspFullDayWholeServiceCentreScenarioTest.withPolicy(profile(List.of("104"), 1, 90d),
                DspSchedulerPolicy.DEADLINE_AWARE_ELASTIC_STICKY_LEASES);
        var eight = DspFullDayWholeServiceCentreScenarioTest.withPolicy(profile(List.of("104"), 8, 90d),
                DspSchedulerPolicy.DEADLINE_AWARE_ELASTIC_STICKY_LEASES);
        assertEquals(1, one.p2pOutstandingToteWatermark());
        assertEquals(8, eight.p2pOutstandingToteWatermark());
        var input = input(one, oneCentreOrders());
        try (var first = new DspFullDayAnalysisRuntimeFactory().create(input, one);
                var second = new DspFullDayAnalysisRuntimeFactory().create(input, eight)) {
            for (int step = 0; step < MAXIMUM_UPDATES && first.state() == DspFullDayRuntimeState.RUNNING; step++) {
                first.update(1d);
                second.update(1d);
                assertTrue(first.snapshot().elastic().allocation().wholeServiceCentrePolicy().isEmpty());
                assertTrue(second.snapshot().elastic().allocation().wholeServiceCentrePolicy().isEmpty());
                assertEquals(first.snapshot().elastic(), second.snapshot().elastic());
                assertEquals(first.snapshot().osr().departedTotes(), second.snapshot().osr().departedTotes());
                assertEquals(assignments(first), assignments(second));
                assertEquals(first.state(), second.state());
            }
            assertComplete(first);
            assertComplete(second);
            assertEquals(12, first.snapshot().osr().departedTotes().size());
            assertEquals(first.outboundToteAllocator().snapshot(), second.outboundToteAllocator().snapshot());
            assertExactOutput(input, first);
            assertExactOutput(input, second);
        }
    }

    @Test
    void shouldReconstructWithZeroAccountingAndNewCompletionCallbacks() {
        var profile = profile(List.of("104"), 1, 90d);
        var input = input(profile, oneCentreOrders());
        WholeServiceCentreReleaseSnapshot previous;
        List<Object> previousCallbacks;
        try (var runtime = new DspFullDayAnalysisRuntimeFactory().create(input, profile)) {
            for (int step = 0; step < 5; step++) {
                runtime.update(1d);
                assertOutstandingMatchesLifecycle(runtime);
            }
            previous = releases(runtime);
            assertEquals(5, previous.version());
            assertEquals(5, previous.outstandingVersion());
            previousCallbacks = runtime.lineRuntimes().stream()
                    .map(line -> (Object) line.config().toteCompletedListener()).toList();
        }
        try (var fresh = new DspFullDayAnalysisRuntimeFactory().create(input, profile)) {
            assertEquals(0, releases(fresh).version());
            assertEquals(0, releases(fresh).outstandingVersion());
            assertTrue(releases(fresh).outstandingP2pToteCounts().values().stream().allMatch(count -> count == 0));
            assertTrue(assignments(fresh).isEmpty());
            for (int index = 0; index < 5; index++) {
                assertNotSame(previousCallbacks.get(index), fresh.lineRuntimes().get(index).config().toteCompletedListener());
            }
            assertOutstandingMatchesLifecycle(fresh);
            for (int step = 0; step < MAXIMUM_UPDATES && fresh.state() == DspFullDayRuntimeState.RUNNING; step++) {
                fresh.update(1d);
                assertOutstandingMatchesLifecycle(fresh);
            }
            assertComplete(fresh);
            assertEquals(24, releases(fresh).outstandingVersion());
            assertEquals(5, previous.outstandingVersion());
            assertTrue(previous.outstandingP2pToteCounts().values().stream().allMatch(count -> count == 1));
            assertExactOutput(input, fresh);
        }
    }

    /** Small independent test-only history scan; never a production accounting path. */
    static void assertOutstandingMatchesLifecycle(DspFullDayAnalysisRuntime runtime) {
        var snapshot = runtime.snapshot();
        var releases = snapshot.elastic().allocation().wholeServiceCentrePolicy().orElseThrow().releases();
        var lifecycle = runtime.lifecycleSnapshotSupplier().get();
        assertEquals(snapshot.elastic().leases().lines().stream().map(line -> line.definition().lineId()).toList(),
                new ArrayList<>(releases.outstandingP2pToteCounts().keySet()));
        Set<PhysicalToteId> assignedIds = new LinkedHashSet<>();
        for (var line : snapshot.elastic().leases().lines()) {
            int expected = 0;
            for (var assignment : line.physicalAssignments()) {
                assertTrue(assignedIds.add(assignment.physicalToteId()), "Duplicate committed physical assignment");
                var record = lifecycle.totes().get(assignment.physicalToteId());
                assertNotNull(record, () -> "Missing lifecycle record for " + assignment.physicalToteId());
                if (record.state() != PhysicalToteLifecycleState.CONSUMED_AT_P2P) {
                    expected++;
                    assertEquals(Optional.of(assignment.serviceCentreId()), line.serviceCentreId(),
                            "An unfinished tote must retain its exact line owner");
                }
            }
            int actual = releases.outstandingToteCount(line.definition().lineId());
            assertEquals(expected, actual, () -> "Lifecycle/count mismatch on " + line.definition().lineId());
            assertTrue(actual >= 0 && actual <= releases.p2pOutstandingToteWatermark());
        }
    }

    private static WholeServiceCentreReleaseSnapshot releases(DspFullDayAnalysisRuntime runtime) {
        return runtime.snapshot().elastic().allocation().wholeServiceCentrePolicy().orElseThrow().releases();
    }

    private static List<P2pPhysicalToteAssignment> assignments(DspFullDayAnalysisRuntime runtime) {
        return runtime.snapshot().elastic().leases().lines().stream()
                .flatMap(line -> line.physicalAssignments().stream()).toList();
    }

    private static void assertComplete(DspFullDayAnalysisRuntime runtime) {
        assertEquals(DspFullDayRuntimeState.ALL_SUPPORTED_WORK_COMPLETE, runtime.state(), () -> {
            var snapshot = runtime.snapshot();
            return "Bounded journey incomplete: elapsed=" + snapshot.metrics().clock().elapsedSimulationTime()
                    + ", whole=" + snapshot.elastic().allocation().wholeServiceCentrePolicy()
                    + ", assigned=" + assignments(runtime).size()
                    + ", allocatedBags=" + runtime.outboundToteAllocator().snapshot().allocatedBags().size()
                    + ", blockedCandidates=" + snapshot.operationalRelease().lastEvaluation()
                            .map(evaluation -> evaluation.blockedCandidates().size()).orElse(0);
        });
    }

    private static void assertExactOutput(DspFullDayLoadedInput input, DspFullDayAnalysisRuntime runtime) {
        var assignments = assignments(runtime);
        assertEquals(input.data().inboundToteManifests().size(), assignments.size());
        assertEquals(input.data().inboundToteManifests().stream().map(InboundToteManifest::physicalToteId).collect(Collectors.toSet()),
                assignments.stream().map(P2pPhysicalToteAssignment::physicalToteId).collect(Collectors.toSet()));
        var outbound = runtime.outboundToteAllocator().snapshot();
        assertTrue(outbound.openTotesByLine().isEmpty());
        assertEquals(input.bagPlan().plannedBags().size(), outbound.allocatedBags().size());
        assertEquals(input.bagPlan().plannedBags().stream().map(PlannedBag::bagKey).collect(Collectors.toSet()),
                outbound.allocatedBags().stream().map(AllocatedOutboundBag::bagKey).collect(Collectors.toSet()));
        for (var bag : input.bagPlan().plannedBags()) {
            var allocated = outbound.findAllocatedBag(bag.bagKey()).orElseThrow();
            assertEquals(bag, allocated.plannedBag());
            assertEquals(bag.physicalPackIds(), allocated.actualPhysicalPackIds());
            assertTrue(allocated.missingPhysicalPackIds().isEmpty());
            assertEquals(outbound.findTote(allocated.outboundPhysicalToteId()).orElseThrow().p2pLineId(),
                    runtime.elasticRuntime().correlationAssignmentSnapshot()
                    .lineFor(bag.bagKey().correlationId()).orElseThrow());
        }
        if (runtime.snapshot().elastic().allocation().wholeServiceCentrePolicy().isPresent()) {
            assertOutstandingMatchesLifecycle(runtime);
            assertTrue(releases(runtime).outstandingP2pToteCounts().values().stream().allMatch(count -> count == 0));
        }
    }

    private static List<NotionalToteOrder> oneCentreOrders() {
        return java.util.stream.IntStream.rangeClosed(1, 12)
                .mapToObj(index -> order("single-" + index, "104", index, 1)).toList();
    }

    private static NotionalToteOrder order(String id, String centre, long sequence, int packs) {
        var lines = java.util.stream.IntStream.rangeClosed(1, packs).mapToObj(index -> {
            String identity = id + "-pack-" + index;
            return new DspOrderItem(identity, "small", 1, centre + "-pharmacy", identity, identity,
                    DspOrderLineType.FULL_PACK, id, 1, 1);
        }).toList();
        return new NotionalToteOrder(id, id + "-sheet-1", centre, 1, OrderType.FULL_PACK, lines,
                centre.equals("104") ? 999 : 998, sequence);
    }

    private static DspFullDayLoadedInput input(DspUncalibratedFullDayProfile profile, List<NotionalToteOrder> orders) {
        var report = DspDatasetLoadReport.empty();
        var manifests = orders.stream().map(order -> new InboundToteManifest(
                new PhysicalToteId("tote-" + order.orderId() + "-1"), order.orderSheetKey(), order.orderType(),
                order.serviceCentreId(), order.items(), order.sequenceNumber())).toList();
        var data = new LoadedDspData(List.of(new ProductMasterRecord("small", "Small", Optional.empty(),
                Optional.of(new PackDimensions(.07f, .034f, .027f)))), orders, List.of(), Set.of(), Set.of(), manifests, report);
        var projection = new DspFullDayInputPreflight().project(data, DspInputRejectionCatalog.empty());
        assertTrue(projection.rejectionCatalog().rejectedLines().isEmpty());
        var plan = new DeterministicBagPlanner(new MaximumPackCountBagCapacityPolicy(profile.maximumPacksPerBag()))
                .plan(new DspFullDayBagPlanningRequestFactory().create(projection.executableData()));
        return new DspFullDayLoadedInput(projection.executableData(), projection.reportableOrders(),
                projection.rejectionCatalog(), plan, report, profile.timetable());
    }

    private static DspUncalibratedFullDayProfile profile(List<String> preload, int watermark, double sealingSeconds) {
        var base = DspFullDayReportTestSupport.profile();
        return new DspUncalibratedFullDayProfile(base.operatingDate(), new OsrInventoryConfig(32, preload),
                new ServiceCentreSupplyConfig(4), new FixedIntervalInboundToteArrivalPolicy("watermark-journey", Duration.ofSeconds(1)),
                base.av02AllocationConfig(), base.p2pElasticAllocationConfig(), base.outboundToteConfig(),
                base.maximumPacksPerBag(), base.fixedStep(), base.maximumStepsPerAdvance(), base.metricSampleInterval(),
                100d, base.queueCapacities(), new ThirdPartyAreaConfig(4, 1, 80d), base.adaptingStorageConfig(),
                List.of(new DspUncalibratedFullDayProfile.AdaptingBenchDefinition("bench", 30d, 10d, 3)),
                new DspUncalibratedFullDayProfile.P2pPlaceholderDurations(40d, 3d, 1d, 1d, .1d, .1d, .1d, .1d, .1d, sealingSeconds, .1d),
                base.p2pLineDefinitions(), 31, base.timetable(), DspSchedulerPolicy.WHOLE_SERVICE_CENTRE_DRAINED_HANDOVER, watermark);
    }
}
