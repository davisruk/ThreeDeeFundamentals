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
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.analysis.*;
import online.davisfamily.warehouse.sim.dsp.analysis.input.DspFullDayInputPreflight;
import online.davisfamily.warehouse.sim.dsp.analysis.input.DspInputRejectionCatalog;
import online.davisfamily.warehouse.sim.dsp.analysis.report.*;
import online.davisfamily.warehouse.sim.dsp.bagging.*;
import online.davisfamily.warehouse.sim.dsp.io.*;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.av02.Av02AllocationBlockReason;
import online.davisfamily.warehouse.sim.dsp.model.*;
import online.davisfamily.warehouse.sim.dsp.osr.OsrInventoryConfig;
import online.davisfamily.warehouse.sim.dsp.outbound.*;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;
import online.davisfamily.warehouse.sim.dsp.scheduler.policy.DspSchedulerPolicy;
import online.davisfamily.warehouse.sim.dsp.supply.FixedIntervalInboundToteArrivalPolicy;
import online.davisfamily.warehouse.sim.dsp.supply.ServiceCentreSupplyConfig;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyAreaConfig;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;

/** Production composition only: no lease mutations or direct machine/controller calls. */
public class DspFullDayWholeServiceCentreScenarioTest {
    @Test
    void shouldSeedFiveLinesThenCloseAndHandOverOneWhileAnotherOldLineIsStillActive() {
        var profile = journeyProfile(List.of("104", "108"), Duration.ofSeconds(1));
        List<NotionalToteOrder> orders = new ArrayList<>();
        for (int index = 1; index <= 5; index++) {
            String id = "current-" + index;
            orders.add(order(id, 1, "104", OrderType.FULL_PACK, index,
                    java.util.stream.IntStream.rangeClosed(1, index == 5 ? 12 : 1)
                            .mapToObj(pack -> full(id + "-pack-" + pack, 1, "old-pharmacy")).toList()));
        }
        orders.add(order("next", 1, "108", OrderType.FULL_PACK, 6,
                List.of(full("next", 1, "new-pharmacy"))));
        var input = input(profile, orders, nsReport());
        try (var runtime = new DspFullDayAnalysisRuntimeFactory().create(input, profile)) {
            assertTrue(runtime.osrInventory().snapshot().storedTotes().stream()
                    .anyMatch(tote -> tote.serviceCentreId().equals("108")));
            Map<P2pLineId, List<Optional<String>>> ownerHistory = new LinkedHashMap<>();
            Map<P2pLineId, online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineLeaseTransitionSnapshot> transitions = new LinkedHashMap<>();
            Map<PhysicalToteId, Integer> closedAt = new LinkedHashMap<>();
            Map<String, P2pLineId> pinned = new LinkedHashMap<>();
            Map<P2pLineId, Set<String>> completedBags = new LinkedHashMap<>();
            Set<P2pLineId> handedOver = new LinkedHashSet<>();
            boolean seeded = false;
            boolean allBusyHold = false;
            boolean handedOverWhileBusy = false;
            boolean lazyNewOutput = false;
            for (int step = 0; step < 1_000 && runtime.state() == DspFullDayRuntimeState.RUNNING; step++) {
                final int observationStep = step;
                runtime.update(1d);
                var snapshot = runtime.snapshot();
                var policy = snapshot.elastic().allocation().wholeServiceCentrePolicy().orElseThrow();
                var releases = policy.releases();
                var leases = snapshot.elastic().leases().lines();
                var outbound = runtime.outboundToteAllocator().snapshot();
                for (var tote : outbound.closedTotes()) { closedAt.putIfAbsent(tote.physicalToteId(), step); }
                var currentAssignments = leases.stream().flatMap(line -> line.physicalAssignments().stream())
                        .filter(assignment -> assignment.serviceCentreId().equals("104")).toList();
                if (currentAssignments.size() == 5 && !seeded) {
                    seeded = true;
                    assertEquals(5, currentAssignments.stream().map(assignment -> assignment.lineId()).distinct().count());
                    for (var line : leases) {
                        assertEquals(1, releases.committedToteCount("104", line.definition().lineId()));
                    }
                }
                if (!releases.allReleased("104")) {
                    assertTrue(snapshot.osr().departedTotes().stream().noneMatch(tote -> tote.serviceCentreId().equals("108")));
                }
                if (releases.allReleased("104") && leases.stream().allMatch(line -> line.leased()
                        && !line.activity().processingDrained())) {
                    allBusyHold = true;
                    assertTrue(policy.eligibleServiceCentreId().isEmpty());
                    assertTrue(policy.availableUnleasedLineIds().isEmpty());
                    assertTrue(snapshot.osr().departedTotes().stream().noneMatch(tote -> tote.serviceCentreId().equals("108")));
                }
                for (var line : leases) {
                    P2pLineId id = line.definition().lineId();
                    var transition = runtime.elasticRuntime().leaseRuntimeSnapshot().findLine(id).orElseThrow().lastTransition();
                    var history = ownerHistory.computeIfAbsent(id, ignored -> new ArrayList<>(List.of(Optional.empty())));
                    if (!history.getLast().equals(line.serviceCentreId())) {
                        if (line.serviceCentreId().equals(Optional.of("108"))) {
                            if (history.getLast().equals(Optional.of("104"))) {
                                // Release, acquire and exact assignment can occur in one update, after earlier closure.
                                var previous = transitions.get(id);
                                assertTrue(previous.details().startsWith("OUTBOUND_TOTE_CLOSED"));
                                assertEquals(previous.sequence() + 3, transition.orElseThrow().sequence());
                                assertTrue(transition.orElseThrow().details().startsWith("ASSIGNMENT_COMMITTED"));
                            } else {
                                assertEquals(Optional.empty(), history.getLast());
                            }
                            assertTrue(history.contains(Optional.of("104")));
                            handedOver.add(id);
                            var oldClosed = outbound.closedTotes().stream().filter(tote -> tote.p2pLineId().equals(id)
                                    && tote.serviceCentreId().equals(Optional.of("104"))).toList();
                            assertFalse(oldClosed.isEmpty());
                            assertTrue(oldClosed.stream().allMatch(tote -> closedAt.get(tote.physicalToteId()) < observationStep));
                            assertTrue(oldClosed.stream().anyMatch(tote -> tote.bagCount() < tote.maximumBagCount()));
                            handedOverWhileBusy |= leases.stream().anyMatch(other -> other.serviceCentreId().equals(Optional.of("104"))
                                    && !other.activity().processingDrained());
                            lazyNewOutput |= outbound.allocatedBags().stream().noneMatch(bag -> bag.plannedBag().serviceCentreId().equals("108"))
                                    && line.activity().openOutboundTote().isEmpty();
                        }
                        if (line.serviceCentreId().isEmpty() && history.getLast().equals(Optional.of("104"))) {
                            assertTrue(line.activity().quiescent());
                        }
                        history.add(line.serviceCentreId());
                    }
                    transition.ifPresent(value -> transitions.put(id, value));
                    line.activity().openOutboundTote().ifPresent(tote -> {
                        assertEquals(line.serviceCentreId(), tote.serviceCentreId());
                        assertTrue(tote.allocatedBags().stream().allMatch(bag -> bag.plannedBag().serviceCentreId()
                                .equals(line.serviceCentreId().orElseThrow())));
                    });
                    for (var assignment : line.physicalAssignments()) {
                        if (assignment.serviceCentreId().equals("108")) { assertTrue(handedOver.contains(id)); }
                    }
                    var metric = snapshot.metrics().p2pLines().stream().filter(value -> value.lineId().equals(id)).findFirst().orElseThrow();
                    assertEquals(line.serviceCentreId(), metric.owner());
                    assertEquals(line.leased() && line.serviceCentreId().equals(releases.releaseServiceCentreId()), metric.feeding());
                    assertEquals(line.leased() && releases.allReleased(line.serviceCentreId().orElseThrow()), metric.draining());
                    var physical = snapshot.p2pLines().stream().filter(value -> value.lineDefinition().lineId().equals(id)).findFirst().orElseThrow();
                    var seenBags = completedBags.computeIfAbsent(id, ignored -> new LinkedHashSet<>());
                    for (String correlation : physical.completedBagCorrelationIds()) {
                        if (seenBags.add(correlation)) {
                            var ownership = runtime.elasticRuntime().correlationAssignmentSnapshot().find(correlation).orElseThrow();
                            assertEquals(id, ownership.lineId());
                            if (line.leased()) {
                                assertEquals(line.serviceCentreId().orElseThrow(), ownership.serviceCentreId());
                            } else {
                                assertTrue(line.activity().quiescent());
                                assertTrue(history.contains(Optional.of(ownership.serviceCentreId())));
                            }
                        }
                    }
                    for (var claim : physical.stationProcessing().activeClaims()) {
                        if (!claim.destination().equals(line.definition().destination())) { continue; }
                        var assignment = line.physicalAssignments().stream()
                                .filter(value -> value.physicalToteId().equals(claim.physicalToteId())).findFirst().orElseThrow();
                        assertEquals(line.serviceCentreId().orElseThrow(), assignment.serviceCentreId());
                    }
                }
                for (var centre : snapshot.metrics().serviceCentres()) {
                    assertEquals(leases.stream().filter(line -> line.serviceCentreId().equals(Optional.of(centre.serviceCentreId()))).count(),
                            centre.ownedLineCount());
                    if (releases.allReleased(centre.serviceCentreId())) { assertEquals(0, centre.desiredLineCount()); }
                }
                for (var correlation : runtime.elasticRuntime().correlationAssignmentSnapshot().assignments()) {
                    var previous = pinned.putIfAbsent(correlation.correlationId(), correlation.lineId());
                    if (previous != null) { assertEquals(previous, correlation.lineId()); }
                }
            }
            assertEquals(DspFullDayRuntimeState.ALL_SUPPORTED_WORK_COMPLETE, runtime.state(),
                    () -> "Mixed journey did not complete: policy=" + runtime.snapshot().elastic().allocation().wholeServiceCentrePolicy()
                            + ", operational=" + runtime.operationalReleaseRuntime().controller().snapshot()
                            + ", station=" + runtime.stationProcessingRuntime().coordinatorSnapshot()
                            + ", supply=" + runtime.supplyController().snapshot()
                            + ", bins=" + runtime.adaptingBinSnapshots());
            assertTrue(seeded);
            assertTrue(allBusyHold, "The next centre must pause with all five old lines busy");
            assertTrue(handedOverWhileBusy, "Handover must not wait for every old line to drain");
            assertTrue(lazyNewOutput, "New output is introduced by the first bag, not lease acquisition");
            assertEquals(1, handedOver.size());
            var finalPolicy = runtime.snapshot().elastic().allocation().wholeServiceCentrePolicy().orElseThrow();
            assertEquals(6, finalPolicy.releases().version());
            assertTrue(finalPolicy.releases().releaseServiceCentreId().isEmpty());
            assertExactCommitsAndOutput(input, runtime);
            var outbound = runtime.outboundToteAllocator().snapshot();
            var oldSheets = outbound.allocatedBags().stream().filter(bag -> bag.plannedBag().serviceCentreId().equals("104"))
                    .flatMap(bag -> bag.outputSheetAllocations().stream()).map(OutputSheetAllocation::outputSheetKey).collect(java.util.stream.Collectors.toSet());
            assertTrue(outbound.allocatedBags().stream().filter(bag -> bag.plannedBag().serviceCentreId().equals("108"))
                    .flatMap(bag -> bag.outputSheetAllocations().stream()).noneMatch(sheet -> oldSheets.contains(sheet.outputSheetKey())));
            var progress = DspFullDayProgressSnapshot.from(runtime.snapshot(), input, profile);
            assertTrue(progress.completedWithNsCandidates());
            assertTrue(new DspFullDayProgressFormatter().describe(progress).stream()
                    .anyMatch(line -> line.startsWith("NsCandidatesPendingByServiceCentre: {109=1}")));
            assertEquals(List.of("104", "108"), finalPolicy.releases().orderedServiceCentreIds());
        }
    }

    @Test
    void shouldHoldFutureEmptyAllocationThroughUpstreamStoreFirstCollectAndLastCurrentEmptyDeparture() {
        var profile = journeyProfile(List.of("108"), Duration.ofSeconds(20));
        var sourceA = order("source-a", 1, "104", OrderType.ADAPTED, 0,
                List.of(adapted("a", "target", "small", 1)));
        var sourceB = order("source-b", 1, "104", OrderType.ADAPTED, 1,
                List.of(adapted("b", "target", "third-party", 1), adapted("empty", "target", "third-party", 1)));
        var first = order("target", 1, "104", OrderType.ASSOCIATED, 2,
                List.of(adapted("a", "source-a", "small", 1), adapted("b", "source-b", "third-party", 1)));
        var lastEmpty = order("target", 2, "104", OrderType.EMPTY, 3,
                List.of(adapted("empty", "source-b", "third-party", 1)));
        // Independently ready and supply-authorized, but not yet in the release centre.
        var futureEmpty = order("future-empty", 1, "108", OrderType.EMPTY, 4,
                List.of(new DspOrderItem("future-empty", "third-party", 1, "new-pharmacy", "future-empty", "future-empty",
                        DspOrderLineType.FULL_PACK, "future-empty", 1, 1)));
        var futureFull = order("future-full", 1, "108", OrderType.FULL_PACK, 5,
                List.of(full("future-full", 1, "new-pharmacy")));
        var input = input(profile, List.of(sourceA, sourceB, first, lastEmpty, futureEmpty, futureFull), nsReport());
        try (var runtime = new DspFullDayAnalysisRuntimeFactory().create(input, profile)) {
            Set<String> activeStores = new LinkedHashSet<>();
            Set<String> completedStores = new LinkedHashSet<>();
            boolean futureSequenceBlocked = false;
            boolean currentEmptyAllocated = false;
            boolean currentEmptyDeparted = false;
            boolean futureEmptyAllocated = false;
            boolean futureEmptyDeparted = false;
            boolean futureAllocatedWhileOldLineBusy = false;
            boolean upstreamHeld = false;
            boolean emptyHeldCursor = false;
            boolean firstCollect = false;
            for (int step = 0; step < 2_000 && runtime.state() == DspFullDayRuntimeState.RUNNING; step++) {
                boolean currentWasReleased = runtime.snapshot().elastic().allocation()
                        .wholeServiceCentrePolicy().orElseThrow().releases().allReleased("104");
                runtime.update(1d);
                var snapshot = runtime.snapshot();
                var releases = snapshot.elastic().allocation().wholeServiceCentrePolicy().orElseThrow().releases();
                var claims = snapshot.stationProcessing().activeClaims();
                for (var source : List.of(sourceA, sourceB)) {
                    String id = physicalId(source).value();
                    boolean active = claims.stream().anyMatch(claim -> claim.destination().stationType() == StationType.ADAPTING
                            && claim.physicalToteId().value().equals(id));
                    if (active) { activeStores.add(id); }
                    else if (activeStores.contains(id)) { completedStores.add(id); }
                }
                boolean currentReleased = releases.allReleased("104");
                currentEmptyAllocated |= snapshot.av02().findTote(lastEmpty.orderSheetKey()).isPresent();
                currentEmptyDeparted |= snapshot.av02().departedTotes().stream()
                        .anyMatch(tote -> tote.orderSheetKey().equals(lastEmpty.orderSheetKey()));
                if (!currentReleased) {
                    assertTrue(snapshot.osr().departedTotes().stream().noneMatch(tote -> tote.serviceCentreId().equals("108")));
                    assertTrue(snapshot.av02().departedTotes().stream().noneMatch(tote -> tote.serviceCentreId().equals("108")));
                    assertTrue(snapshot.av02().waitingTotes().stream().noneMatch(tote -> tote.serviceCentreId().equals("108")));
                    assertTrue(runtime.lifecycleSnapshotSupplier().get().assignments().stream()
                            .noneMatch(assignment -> assignment.orderSheetKey().equals(futureEmpty.orderSheetKey())));
                    var candidates = runtime.av02AllocationRuntimeController().snapshot().snapshot().candidates();
                    var currentCandidate = candidates.stream().filter(candidate -> candidate.orderSheetKey().equals(lastEmpty.orderSheetKey())).findFirst();
                    if (currentCandidate.isPresent() && currentCandidate.orElseThrow().blockReasons()
                            .contains(Av02AllocationBlockReason.DEPENDENCY_NOT_READY)) {
                        assertTrue(runtime.supplyController().snapshot().authorizedEmptyOrderSheetKeys().contains(futureEmpty.orderSheetKey()));
                        var futureCandidate = candidates.stream().filter(candidate -> candidate.orderSheetKey().equals(futureEmpty.orderSheetKey()))
                                .findFirst().orElseThrow();
                        assertEquals(List.of(Av02AllocationBlockReason.SERVICE_CENTRE_SEQUENCE), futureCandidate.blockReasons());
                        futureSequenceBlocked = true;
                    }
                    upstreamHeld |= runtime.supplyController().snapshot().serviceCentres().stream()
                            .anyMatch(centre -> centre.serviceCentreId().equals("104") && centre.upstreamWaitingCount() > 0);
                    emptyHeldCursor |= releases.unreleasedOsrToteCounts().get("104") == 0
                            && releases.unreleasedEmptySheetCounts().get("104") == 1;
                }
                if (currentReleased) {
                    assertTrue(currentEmptyAllocated);
                    assertTrue(currentEmptyDeparted, "Only the exact EMPTY departure advances the release cursor");
                    if (!currentWasReleased) {
                        assertEquals(4, releases.version());
                        assertEquals(Optional.of("108"), releases.releaseServiceCentreId());
                    }
                }
                if (snapshot.av02().findTote(futureEmpty.orderSheetKey()).isPresent()) {
                    assertTrue(currentWasReleased, "Future allocation must follow, not precede, cursor advancement");
                    futureEmptyAllocated = true;
                    futureAllocatedWhileOldLineBusy |= snapshot.elastic().leases().lines().stream()
                            .anyMatch(line -> line.serviceCentreId().equals(Optional.of("104")) && !line.activity().processingDrained());
                }
                futureEmptyDeparted |= snapshot.av02().departedTotes().stream()
                        .anyMatch(tote -> tote.orderSheetKey().equals(futureEmpty.orderSheetKey()));
                for (var claim : claims) {
                    if (claim.destination().stationType() != StationType.ADAPTING) { continue; }
                    if (claim.physicalToteId().equals(physicalId(first))) {
                        firstCollect = true;
                        assertTrue(completedStores.contains(physicalId(sourceA).value()));
                        assertTrue(completedStores.contains(physicalId(sourceB).value()), "All source STOREs of the order must be complete");
                    }
                    var allocatedEmpty = snapshot.av02().findTote(lastEmpty.orderSheetKey());
                    if (allocatedEmpty.isPresent() && claim.physicalToteId().equals(allocatedEmpty.orElseThrow().physicalToteId())) {
                        assertTrue(firstCollect);
                        assertTrue(claims.stream().noneMatch(other -> other.destination().stationType() == StationType.ADAPTING
                                        && other.physicalToteId().equals(physicalId(first))),
                                "Later EMPTY COLLECT waits for committed first collection");
                        assertEquals(3, runtime.loadPlanRegistry().getLoadPlanFor(physicalId(first)).getPackPlans().size(),
                                "First COLLECT must already have committed all order-owned prepared packs");
                    }
                }
            }
            assertEquals(DspFullDayRuntimeState.ALL_SUPPORTED_WORK_COMPLETE, runtime.state(),
                    () -> "Mixed journey did not complete: policy=" + runtime.snapshot().elastic().allocation().wholeServiceCentrePolicy()
                            + ", operational=" + runtime.operationalReleaseRuntime().controller().snapshot()
                            + ", station=" + runtime.stationProcessingRuntime().coordinatorSnapshot()
                            + ", supply=" + runtime.supplyController().snapshot()
                            + ", bins=" + runtime.adaptingBinSnapshots());
            assertTrue(futureSequenceBlocked, "Ready authorized future EMPTY must be sequence-blocked while current EMPTY is unready");
            assertTrue(currentEmptyAllocated);
            assertTrue(currentEmptyDeparted);
            assertTrue(futureEmptyAllocated, "Allocation resumes after the release cursor advances");
            assertTrue(futureEmptyDeparted);
            assertTrue(futureAllocatedWhileOldLineBusy, "Allocation must not wait for all predecessor lines to drain");
            assertTrue(upstreamHeld, "The barrier includes unsupplied current physical manifests");
            assertTrue(emptyHeldCursor, "The last EMPTY obligation holds the current centre");
            assertTrue(firstCollect);
            var releases = runtime.snapshot().elastic().allocation().wholeServiceCentrePolicy().orElseThrow().releases();
            assertEquals(6, releases.version());
            assertEquals(4, releases.committedP2pToteCounts().values().stream().flatMap(counts -> counts.values().stream()).mapToInt(Integer::intValue).sum(),
                    "Two non-P2P STORE releases count globally, not as P2P commitments");
            var pendingEmptyBag = input.bagPlan().plannedBags().stream()
                    .filter(bag -> bag.prescriptionId().equals("rx-empty")).findFirst().orElseThrow();
            assertEquals(List.of(lastEmpty.orderSheetKey()), pendingEmptyBag.owningOrderSheetKeys());
            assertEquals(1, pendingEmptyBag.physicalPackIds().size());
            assertTrue(runtime.loadPlanRegistry().getLoadPlanFor(physicalId(first)).getPackPlans().stream()
                    .anyMatch(pack -> pack.packId().equals(pendingEmptyBag.physicalPackIds().getFirst())),
                    "The later sheet's pack was physically collected by the designated first sheet");
            var emptyTote = runtime.snapshot().av02().findTote(lastEmpty.orderSheetKey()).orElseThrow();
            assertTrue(runtime.loadPlanRegistry().getLoadPlanFor(emptyTote.physicalToteId()).getPackPlans().isEmpty());
            var completion = runtime.completionSnapshots().stream().filter(centre -> centre.serviceCentreId().equals("104"))
                    .findFirst().orElseThrow();
            assertEquals(DspP2pOutputClosureState.P2P_OUTPUT_CLOSED_WITH_EXCEPTION, completion.p2pOutputClosureState());
            assertEquals(1, completion.missingPackCount());
            assertEquals(1, completion.pdcCollectedPackCount());
            assertEquals(1, completion.pendingEmptyBagCount());
            assertEquals(0, completion.affectedAllocatedBagCount());
            assertEquals(0, completion.markedOutboundToteCount());
            assertEquals(0, completion.nsCandidateInputLineCount(), "Physical exceptions are not NS input candidates");
            assertExactCommitsAndOutput(input, runtime, Set.of(pendingEmptyBag.bagKey()));
            assertTrue(DspFullDayProgressSnapshot.from(runtime.snapshot(), input, profile).completedWithNsCandidates());
        }
    }

    @Test
    void shouldNotSkipCurrentCentreWhileItsExecutableStoreDependencyIsStillUnresolved() {
        var profile = journeyProfile(List.of("104", "108"), Duration.ofSeconds(1));
        var source = order("current-source", 1, "104", OrderType.ADAPTED, 0,
                List.of(adapted("blocked", "current-target", "third-party", 1)));
        var target = order("current-target", 1, "104", OrderType.ASSOCIATED, 1,
                List.of(adapted("blocked", "current-source", "third-party", 1)));
        var future = order("future", 1, "108", OrderType.FULL_PACK, 2,
                List.of(full("future", 1, "new-pharmacy")));
        var input = input(profile, List.of(source, target, future), nsReport());
        try (var runtime = new DspFullDayAnalysisRuntimeFactory().create(input, profile)) {
            advanceUntil(runtime, 100, () -> runtime.clockController().snapshot().elapsedSimulationTime().compareTo(Duration.ofSeconds(100)) >= 0);
            var snapshot = runtime.snapshot();
            assertEquals(DspFullDayRuntimeState.RUNNING, runtime.state());
            assertEquals(List.of(physicalId(source)), snapshot.osr().departedTotes().stream().map(InboundToteManifest::physicalToteId).toList());
            assertTrue(snapshot.av02().departedTotes().isEmpty());
            var releases = snapshot.elastic().allocation().wholeServiceCentrePolicy().orElseThrow().releases();
            assertEquals(1, releases.version());
            assertEquals(Optional.of("104"), releases.releaseServiceCentreId());
            assertEquals(1, releases.unreleasedOsrToteCounts().get("104"));
            assertEquals(List.of("104", "108"), releases.orderedServiceCentreIds());
            assertFalse(runtime.schedulerRuntimeState().snapshot().preparedLineKeys().contains(new PreparedLineKey("current-target", "blocked")));
        }
    }

    @Test
    void shouldPreserveOldPolicyAllocationOfReadyLaterEmptyWhileHigherCentreIsBlocked() {
        var profile = withPolicy(journeyProfile(List.of("104", "108"), Duration.ofSeconds(1)),
                DspSchedulerPolicy.DEADLINE_AWARE_ELASTIC_STICKY_LEASES);
        var source = order("source", 1, "104", OrderType.ADAPTED, 0,
                List.of(adapted("blocked", "current-empty", "third-party", 1)));
        var current = order("current-empty", 1, "104", OrderType.EMPTY, 1,
                List.of(adapted("blocked", "source", "third-party", 1)));
        var future = order("future-empty", 1, "108", OrderType.EMPTY, 2,
                List.of(new DspOrderItem("ready", "third-party", 1, "new-pharmacy", "ready", "ready",
                        DspOrderLineType.FULL_PACK, "future-empty", 1, 1)));
        var input = input(profile, List.of(source, current, future), nsReport());
        try (var runtime = new DspFullDayAnalysisRuntimeFactory().create(input, profile)) {
            assertTrue(runtime.supplyController().snapshot().authorizedEmptyOrderSheetKeys().contains(future.orderSheetKey()));
            runtime.update(1d);
            var allocation = runtime.av02AllocationRuntimeController().snapshot();
            assertEquals(future.orderSheetKey(), allocation.selectedCommand().orElseThrow().orderSheetKey());
            assertEquals(List.of(Av02AllocationBlockReason.DEPENDENCY_NOT_READY), allocation.snapshot().candidates().stream()
                    .filter(candidate -> candidate.orderSheetKey().equals(current.orderSheetKey())).findFirst().orElseThrow().blockReasons());
            assertTrue(runtime.av02Inventory().snapshot().findTote(future.orderSheetKey()).isPresent());
            assertTrue(runtime.av02Inventory().snapshot().findTote(current.orderSheetKey()).isEmpty());
            assertTrue(runtime.snapshot().elastic().allocation().wholeServiceCentrePolicy().isEmpty());
        }
    }

    private static void assertExactCommitsAndOutput(DspFullDayLoadedInput input, DspFullDayAnalysisRuntime runtime) {
        assertExactCommitsAndOutput(input, runtime, Set.of());
    }

    private static void assertExactCommitsAndOutput(DspFullDayLoadedInput input, DspFullDayAnalysisRuntime runtime,
            Set<BagKey> expectedPendingEmptyBags) {
        var snapshot = runtime.snapshot();
        var releases = snapshot.elastic().allocation().wholeServiceCentrePolicy().orElseThrow().releases();
        for (var line : snapshot.elastic().leases().lines()) {
            for (String centre : releases.orderedServiceCentreIds()) {
                assertEquals(line.physicalAssignments().stream().filter(assignment -> assignment.serviceCentreId().equals(centre)).count(),
                        releases.committedToteCount(centre, line.definition().lineId()));
            }
        }
        var outbound = runtime.outboundToteAllocator().snapshot();
        assertTrue(outbound.openTotesByLine().isEmpty());
        assertEquals(input.bagPlan().plannedBags().size() - expectedPendingEmptyBags.size(), outbound.allocatedBags().size());
        assertEquals(input.bagPlan().plannedBags().stream().map(PlannedBag::bagKey)
                        .filter(key -> !expectedPendingEmptyBags.contains(key)).collect(java.util.stream.Collectors.toSet()),
                outbound.allocatedBags().stream().map(AllocatedOutboundBag::bagKey).collect(java.util.stream.Collectors.toSet()));
        for (var key : expectedPendingEmptyBags) { assertTrue(outbound.findAllocatedBag(key).isEmpty()); }
        for (var tote : outbound.closedTotes()) {
            for (var bag : tote.allocatedBags()) {
                assertEquals(bag.plannedBag().physicalPackIds(), bag.actualPhysicalPackIds());
                assertTrue(bag.missingPhysicalPackIds().isEmpty());
                assertEquals(tote.p2pLineId(), runtime.elasticRuntime().correlationAssignmentSnapshot()
                        .lineFor(bag.bagKey().correlationId()).orElseThrow());
                assertEquals(tote.serviceCentreId().orElseThrow(), bag.plannedBag().serviceCentreId());
                assertEquals(tote.pharmacyId().orElseThrow(), bag.plannedBag().pharmacyId());
            }
        }
    }

    public static DspUncalibratedFullDayProfile withPolicy(DspUncalibratedFullDayProfile base, DspSchedulerPolicy policy) {
        return new DspUncalibratedFullDayProfile(base.operatingDate(), base.osrInventoryConfig(), base.serviceCentreSupplyConfig(),
                base.inboundToteArrivalPolicy(), base.av02AllocationConfig(), base.p2pElasticAllocationConfig(), base.outboundToteConfig(),
                base.maximumPacksPerBag(), base.fixedStep(), base.maximumStepsPerAdvance(), base.metricSampleInterval(),
                base.routeSpeedUnitsPerSecond(), base.queueCapacities(), base.thirdPartyAreaConfig(), base.adaptingStorageConfig(),
                base.adaptingBenchDefinitions(), base.p2pPlaceholderDurations(), base.p2pLineDefinitions(), base.prlCountPerLine(), base.timetable(), policy);
    }

    private static DspUncalibratedFullDayProfile journeyProfile(List<String> preload, Duration arrivalInterval) {
        var base = DspFullDayReportTestSupport.profile();
        return new DspUncalibratedFullDayProfile(base.operatingDate(), new OsrInventoryConfig(20, preload), new ServiceCentreSupplyConfig(4),
                new FixedIntervalInboundToteArrivalPolicy("journey-fixture", arrivalInterval), base.av02AllocationConfig(),
                base.p2pElasticAllocationConfig(), base.outboundToteConfig(), base.maximumPacksPerBag(), base.fixedStep(),
                base.maximumStepsPerAdvance(), base.metricSampleInterval(), 100d, base.queueCapacities(),
                new ThirdPartyAreaConfig(4, 1, 80d), base.adaptingStorageConfig(),
                List.of(new DspUncalibratedFullDayProfile.AdaptingBenchDefinition("bench", 30d, 10d, 3)),
                new DspUncalibratedFullDayProfile.P2pPlaceholderDurations(40d, 3d, 1d, 1d, 0.1d, 0.1d, 0.1d, 0.1d, 0.1d, 0.1d, 0.1d),
                base.p2pLineDefinitions(), base.prlCountPerLine(), base.timetable(), DspSchedulerPolicy.WHOLE_SERVICE_CENTRE_DRAINED_HANDOVER);
    }

    private static DspFullDayLoadedInput input(DspUncalibratedFullDayProfile profile, List<NotionalToteOrder> orders, DspDatasetLoadReport report) {
        var prepared = orders.stream().filter(order -> order.orderType() == OrderType.ADAPTED).flatMap(order -> order.items().stream()).toList();
        var manifests = orders.stream().filter(order -> order.orderType() != OrderType.EMPTY).map(order -> new InboundToteManifest(
                physicalId(order), order.orderSheetKey(), order.orderType(), order.serviceCentreId(), order.items(), order.sequenceNumber())).toList();
        var data = new LoadedDspData(List.of(new ProductMasterRecord("small", "Small", Optional.empty(),
                Optional.of(new PackDimensions(0.07f, 0.034f, 0.027f))), new ProductMasterRecord("third-party", "Third party",
                Optional.of("Y74"), Optional.of(new PackDimensions(0.174f, 0.075f, 0.03f)))), orders, prepared,
                prepared.stream().map(PreparedLineKey::forPreparedLine).collect(java.util.stream.Collectors.toSet()), Set.of(), manifests, report);
        var projection = new DspFullDayInputPreflight().project(data, DspInputRejectionCatalog.empty());
        assertTrue(projection.rejectionCatalog().rejectedLines().isEmpty());
        var plan = new DeterministicBagPlanner(new MaximumPackCountBagCapacityPolicy(profile.maximumPacksPerBag()))
                .plan(new DspFullDayBagPlanningRequestFactory().create(projection.executableData()));
        return new DspFullDayLoadedInput(projection.executableData(), projection.reportableOrders(), projection.rejectionCatalog(), plan, report, profile.timetable());
    }

    private static DspDatasetLoadReport nsReport() {
        return new DspDatasetLoadReport(0, 0, 0, List.of(new UnresolvedProductLine("ns-only", "line", "NS", "109")), List.of());
    }

    private static NotionalToteOrder order(String id, int sheet, String centre, OrderType type, long sequence, List<DspOrderItem> lines) {
        return new NotionalToteOrder(id, id + "-sheet-" + sheet, centre, sheet, type, lines, centre.equals("104") ? 999 : 998, sequence);
    }

    private static PhysicalToteId physicalId(NotionalToteOrder order) { return new PhysicalToteId("tote-" + order.orderId() + "-" + order.sheetNumber()); }

    private static DspOrderItem full(String id, int packs, String pharmacy) {
        return new DspOrderItem(id, "small", packs, pharmacy, id, id, DspOrderLineType.FULL_PACK, id, 1, packs);
    }

    private static DspOrderItem adapted(String line, String reference, String product, int referenceSheet) {
        return new DspOrderItem(line, product, 1, "adapted-pharmacy", "patient-" + line, "rx-" + line,
                DspOrderLineType.ADAPTED, reference, referenceSheet, 1);
    }

    static void advanceUntil(DspFullDayAnalysisRuntime runtime, int maximumSteps, BooleanSupplier outcome) {
        for (int step = 0; step < maximumSteps && !outcome.getAsBoolean()
                && runtime.state() == DspFullDayRuntimeState.RUNNING; step++) { runtime.update(1d); }
        assertTrue(outcome.getAsBoolean(), () -> "Bounded outcome not reached: " + runtime.operationalReleaseRuntime().controller().snapshot());
    }
}
