package online.davisfamily.warehouse.sim.dsp.analysis.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayLoadedInput;
import online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayRuntimeState;
import online.davisfamily.warehouse.sim.dsp.analysis.DspP2pOutputClosureState;
import online.davisfamily.warehouse.sim.dsp.analysis.DspUncalibratedFullDayProfile;
import online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayBagPlanningRequestFactory;
import online.davisfamily.warehouse.sim.dsp.analysis.input.DspFullDayInputPreflight;
import online.davisfamily.warehouse.sim.dsp.analysis.input.DspInputRejectionCatalog;
import online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayReportTestSupport;
import online.davisfamily.warehouse.sim.dsp.bagging.DeterministicBagPlanner;
import online.davisfamily.warehouse.sim.dsp.bagging.MaximumPackCountBagCapacityPolicy;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedPackSlotKey;
import online.davisfamily.warehouse.sim.dsp.io.DspDatasetLoadReport;
import online.davisfamily.warehouse.sim.dsp.io.LoadedDspData;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.ProductMasterRecord;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundAllocationSnapshot;
import online.davisfamily.warehouse.sim.dsp.runtime.operational.DspOperationalReleaseController;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationCapacity;
import online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalReleaseSnapshot;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;

class DspFullDayConcurrentAdaptingScenarioTest {
    private static final PackDimensions SMALL = new PackDimensions(0.07f, 0.034f, 0.027f);
    private static final PackDimensions LARGE = new PackDimensions(0.174f, 0.075f, 0.03f);

    @Test
    void shouldRunConcurrentReadyOrdersWithExactExceptionsAndRepeatableDispositions() throws Exception {
        var base = DspFullDayReportTestSupport.profile();
        var profile = DspFullDayReportTestSupport.configuredStations(DspUncalibratedFullDayProfile.uncalibrated(
                base.operatingDate(), new online.davisfamily.warehouse.sim.dsp.osr.OsrInventoryConfig(
                        base.osrInventoryConfig().capacity(), List.of("104")),
                base.serviceCentreSupplyConfig(), base.inboundToteArrivalPolicy(), base.av02AllocationConfig(),
                base.p2pElasticAllocationConfig(), base.outboundToteConfig(), base.maximumPacksPerBag()));
        var input = input(profile);
        Outcome first = run(input, profile);
        Outcome second = run(input, profile);
        assertEquals(first, second);
    }

    private static Outcome run(DspFullDayLoadedInput input, DspUncalibratedFullDayProfile profile)
            throws Exception {
        try (var runtime = new DspFullDayAnalysisRuntimeFactory().create(input, profile)) {
            StationCapacity initialCapacity = runtime.schedulerRuntimeState().snapshot().stationAdmissions()
                    .get(StationType.ADAPTING).capacity();
            assertEquals(new StationCapacity(18, 18), initialCapacity);
            assertEquals(6, runtime.stationProcessingRuntime().destinations().stream()
                    .filter(destination -> destination.stationType() == StationType.ADAPTING).count());
            assertEquals(6, runtime.stationProcessingRuntime().claimantSnapshots().stream()
                    .filter(claim -> claim.destination().stationType() == StationType.ADAPTING).count());

            // Read the existing production evaluation boundary, without adding an inspection hook.
            var field = DspOperationalReleaseController.class.getDeclaredField("snapshotSupplier");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            var operationalInputs = (Supplier<DspOperationalReleaseSnapshot>) field.get(
                    runtime.operationalReleaseRuntime().controller());
            Map<PhysicalToteId, Duration> starts = new LinkedHashMap<>();
            Set<PhysicalToteId> completed = new LinkedHashSet<>();
            List<String> dispositions = new ArrayList<>();
            boolean threeConcurrent = false;
            boolean mixedReadyWork = false;
            boolean liveCapacityChecked = false;
            boolean thirdPartyObserved = false;
            Duration thirdPartyStart = null;
            for (int step = 0; step < 3_000 && runtime.state() == DspFullDayRuntimeState.RUNNING; step++) {
                runtime.update(1d);
                Duration now = runtime.clockController().snapshot().elapsedSimulationTime();
                var claims = runtime.stationProcessingRuntime().coordinatorSnapshot().activeClaims();
                var benches = runtime.adaptingBenchAdmissionSnapshots();
                for (var bench : benches) {
                    assertEquals(3, bench.processingCapacity());
                    assertEquals(3, bench.queueSnapshot().capacity());
                    long active = claims.stream().filter(claim -> claim.destination().stationType() == StationType.ADAPTING
                            && claim.destination().targetId().equals(bench.benchId().value())).count();
                    assertEquals(active, bench.occupiedProcessingPositions() + bench.queueSnapshot().toteIds().size());
                    threeConcurrent |= bench.occupiedProcessingPositions() == 3
                            && bench.queueSnapshot().toteIds().isEmpty();
                }
                for (var claim : claims) {
                    if (claim.destination().stationType() == StationType.THIRD_PARTY) {
                        thirdPartyObserved = true;
                        thirdPartyStart = claim.claimedAt();
                        assertEquals(1, claims.stream()
                                .filter(value -> value.destination().stationType() == StationType.THIRD_PARTY).count());
                    }
                    if (claim.destination().stationType() != StationType.ADAPTING) {
                        continue;
                    }
                    PhysicalToteId id = claim.physicalToteId();
                    boolean queued = benches.stream().anyMatch(bench -> bench.queueSnapshot().toteIds().contains(id.value()));
                    if (!queued) {
                        starts.putIfAbsent(id, claim.claimedAt());
                    }
                    if (id.value().equals("tote-source-c")) {
                        assertTrue(thirdPartyStart != null);
                        assertTrue(claim.claimedAt().minus(thirdPartyStart).compareTo(Duration.ofSeconds(19)) >= 0);
                    }
                    if (id.value().startsWith("tote-target-")) {
                        String order = id.value().contains("target-a") ? "target-a" : "target-b";
                        List<String> sources = order.equals("target-a")
                                ? List.of("tote-source-a") : List.of("tote-source-b", "tote-source-c");
                        assertTrue(sources.stream().allMatch(source -> completed.contains(new PhysicalToteId(source))),
                                "COLLECT must not claim an order before all its STORE completions");
                        mixedReadyWork |= claims.stream().anyMatch(other -> other.destination().stationType() == StationType.ADAPTING
                                && other.physicalToteId().value().startsWith("tote-source-"));
                        if (id.value().endsWith("-2")) {
                            assertTrue(completed.contains(new PhysicalToteId("tote-" + order + "-1")),
                                    "Later sheet must wait for committed designated-first collection");
                        }
                    }
                }
                for (var entry : starts.entrySet()) {
                    var id = entry.getKey();
                    boolean active = claims.stream().anyMatch(claim -> claim.physicalToteId().equals(id)
                            && claim.destination().stationType() == StationType.ADAPTING);
                    if (!active && completed.add(id)) {
                        long expected = id.value().startsWith("tote-source-") ? 60 : 10;
                        Duration elapsed = now.minus(entry.getValue());
                        assertTrue(elapsed.compareTo(Duration.ofSeconds(expected - 1)) >= 0
                                && elapsed.compareTo(Duration.ofSeconds(expected + 1)) <= 0,
                                () -> id + " processing interval=" + elapsed);
                        dispositions.add(id.value() + ":" + (expected == 60 ? "CONSUME" : "CONTINUE") + ":" + now);
                    }
                }
                if (!liveCapacityChecked && threeConcurrent) {
                    int occupied = benches.stream().mapToInt(bench -> bench.occupiedProcessingPositions()).sum();
                    for (var admission : operationalInputs.get().routeAdmissions()) {
                        if (admission.stationAdmission().stationType() == StationType.ADAPTING) {
                            assertSame(initialCapacity, admission.stationAdmission().capacity());
                            assertEquals(occupied, admission.stationAdmission().snapshot().inProgress());
                            liveCapacityChecked = true;
                        }
                    }
                }
            }
            assertEquals(DspFullDayRuntimeState.ALL_SUPPORTED_WORK_COMPLETE, runtime.state());
            assertTrue(threeConcurrent, "At least three positions must process together on one physical bench");
            assertTrue(mixedReadyWork, "A ready COLLECT must overlap another order's STORE");
            assertTrue(liveCapacityChecked, "The live resolver must publish occupied positions against 18/18 capacity");
            assertTrue(thirdPartyObserved);
            assertEquals(7, completed.size());
            assertTrue(runtime.adaptingBinSnapshots().isEmpty());
            assertTrue(runtime.adaptingBenchAdmissionSnapshots().stream().allMatch(bench ->
                    bench.occupiedProcessingPositions() == 0 && bench.queueSnapshot().toteIds().isEmpty()));
            var terminal = runtime.snapshot();
            assertTrue(terminal.stationProcessing().activeClaims().isEmpty());
            assertTrue(terminal.stationProcessing().pendingDispositions().isEmpty());
            assertTrue(terminal.stationArrivals().stream().allMatch(queue -> queue.entries().isEmpty()));
            assertEquals(0, terminal.transportInFlight().occupancy());
            assertTrue(terminal.transportArrival().pendingArrivals().isEmpty());
            assertEquals(0, terminal.transportIngress().transportOccupancy());

            var closure = runtime.completionSnapshots().stream()
                    .filter(value -> value.serviceCentreId().equals("104")).findFirst().orElseThrow();
            assertTrue(closure.complete());
            assertEquals(DspP2pOutputClosureState.P2P_OUTPUT_CLOSED_WITH_EXCEPTION, closure.p2pOutputClosureState());
            assertEquals(4, closure.missingPackCount());
            assertEquals(4, closure.pdcCollectedPackCount());
            assertEquals(2, closure.affectedAllocatedBagCount());
            assertEquals(2, closure.pendingEmptyBagCount());
            OutboundAllocationSnapshot outbound = runtime.outboundToteAllocator().snapshot();
            assertEquals(4, outbound.allocatedBags().size());
            assertTrue(outbound.openTotesByLine().isEmpty());
            for (String group : List.of("a", "b")) {
                var firstPlans = runtime.loadPlanRegistry().getLoadPlanFor("tote-target-" + group + "-1").getPackPlans();
                String source = group.equals("a") ? "source-a" : "source-b";
                String laterSource = group.equals("a") ? "source-a" : "source-c";
                List<PlannedPackSlotKey> keys = List.of(slot(source, "first"), slot(laterSource, "partial"), slot(laterSource, "zero"));
                assertEquals(keys.stream().map(key -> input.bagPlan().requirePlannedPackSlot(key).reservedPhysicalPackId()).toList(),
                        firstPlans.stream().map(PackPlan::packId).toList());
                for (int index = 0; index < keys.size(); index++) {
                    var planned = input.bagPlan().requirePlannedPackSlot(keys.get(index));
                    assertSame(planned.dimensions(), firstPlans.get(index).dimensions());
                    assertEquals(planned.bagKey().correlationId(), firstPlans.get(index).correlationId());
                    assertEquals(new OrderSheetKey(index == 0 ? source : laterSource, 1),
                            planned.sourceProvenance().sourceOrderSheetKey());
                }
                var direct = input.bagPlan().requirePlannedPackSlot(new PlannedPackSlotKey(
                        new OrderSheetKey("target-" + group, 2), group + "-direct", 1));
                assertEquals(List.of(direct.reservedPhysicalPackId()), runtime.loadPlanRegistry()
                        .getLoadPlanFor("tote-target-" + group + "-2").getPackPlans().stream().map(PackPlan::packId).toList());
                for (var bag : input.bagPlan().plannedBags()) {
                    if (!bag.prescriptionId().startsWith(group + "-")) {
                        continue;
                    }
                    var allocated = outbound.findAllocatedBag(bag.bagKey());
                    if (bag.prescriptionId().endsWith("zero")) {
                        assertTrue(allocated.isEmpty());
                    } else if (bag.prescriptionId().endsWith("partial")) {
                        assertEquals(List.of(direct.reservedPhysicalPackId()), allocated.orElseThrow().actualPhysicalPackIds());
                        assertEquals(List.of(input.bagPlan().requirePlannedPackSlot(slot(laterSource, "partial"))
                                .reservedPhysicalPackId()), allocated.orElseThrow().missingPhysicalPackIds());
                        assertTrue(outbound.closedTotes().stream().anyMatch(tote -> tote.requiresExceptionProcessing()
                                && tote.physicalToteId().equals(allocated.orElseThrow().outboundPhysicalToteId())));
                    } else {
                        assertEquals(bag.physicalPackIds(), allocated.orElseThrow().actualPhysicalPackIds());
                        assertTrue(allocated.orElseThrow().missingPhysicalPackIds().isEmpty());
                    }
                }
            }
            return new Outcome(List.copyOf(dispositions), outbound,
                    terminal.stationProcessing().completedCount(), terminal.metrics().observedSimulationDuration());
        }
    }

    private static PlannedPackSlotKey slot(String source, String line) {
        return new PlannedPackSlotKey(new OrderSheetKey(source, 1),
                (source.equals("source-a") ? "a-" : "b-") + line, 1);
    }

    private static DspFullDayLoadedInput input(DspUncalibratedFullDayProfile profile) {
        var sourceA = order("source-a", 1, OrderType.ADAPTED, 0, List.of(
                line("first", "target-a", "a-first", "small", DspOrderLineType.ADAPTED, 1),
                line("partial", "target-a", "a-partial", "small", DspOrderLineType.ADAPTED, 1),
                line("zero", "target-a", "a-zero", "small", DspOrderLineType.ADAPTED, 1)));
        var sourceB = order("source-b", 1, OrderType.ADAPTED, 1, List.of(
                line("first", "target-b", "b-first", "small", DspOrderLineType.ADAPTED, 1)));
        // Third Party preparation naturally staggers the third STORE, while preserving release/arrival policy.
        var sourceC = order("source-c", 1, OrderType.ADAPTED, 2, List.of(
                line("partial", "target-b", "b-partial", "large", DspOrderLineType.ADAPTED, 1),
                line("zero", "target-b", "b-zero", "large", DspOrderLineType.ADAPTED, 1)));
        List<NotionalToteOrder> orders = new ArrayList<>(List.of(sourceA, sourceB, sourceC));
        for (String group : List.of("a", "b")) {
            String source = group.equals("a") ? "source-a" : "source-b";
            String laterSource = group.equals("a") ? "source-a" : "source-c";
            String product = group.equals("a") ? "small" : "large";
            orders.add(order("target-" + group, 1, OrderType.ASSOCIATED, orders.size(), List.of(
                    line("first", source, group + "-first", "small", DspOrderLineType.ADAPTED, 1))));
            orders.add(order("target-" + group, 2, OrderType.ASSOCIATED, orders.size(), List.of(
                    line("partial", laterSource, group + "-partial", product, DspOrderLineType.ADAPTED, 1),
                    line("zero", laterSource, group + "-zero", product, DspOrderLineType.ADAPTED, 1),
                    line("direct", "target-" + group, group + "-partial", "small", DspOrderLineType.FULL_PACK, 2))));
        }
        List<DspOrderItem> prepared = orders.stream().filter(order -> order.orderType() == OrderType.ADAPTED)
                .flatMap(order -> order.items().stream()).toList();
        List<InboundToteManifest> manifests = orders.stream().map(order -> new InboundToteManifest(
                new PhysicalToteId("tote-" + order.orderId()
                        + (order.orderType() == OrderType.ASSOCIATED ? "-" + order.sheetNumber() : "")),
                order.orderSheetKey(), order.orderType(), "104", order.items(), order.sequenceNumber())).toList();
        var report = DspDatasetLoadReport.empty();
        var assembled = new LoadedDspData(List.of(
                new ProductMasterRecord("small", "Small", Optional.empty(), Optional.of(SMALL)),
                new ProductMasterRecord("large", "Large", Optional.of("Y74"), Optional.of(LARGE))),
                orders, prepared, prepared.stream().map(PreparedLineKey::forPreparedLine)
                        .collect(java.util.stream.Collectors.toSet()), Set.of(), manifests, report);
        var projection = new DspFullDayInputPreflight().project(assembled, DspInputRejectionCatalog.empty());
        assertTrue(projection.rejectionCatalog().rejectedLines().isEmpty());
        var bagPlan = new DeterministicBagPlanner(new MaximumPackCountBagCapacityPolicy(profile.maximumPacksPerBag()))
                .plan(new DspFullDayBagPlanningRequestFactory().create(projection.executableData()));
        assertEquals(8, bagPlan.plannedPackSlots().size());
        assertEquals(6, bagPlan.plannedBags().size());
        return new DspFullDayLoadedInput(projection.executableData(), projection.reportableOrders(),
                projection.rejectionCatalog(), bagPlan, report, profile.timetable());
    }

    private static NotionalToteOrder order(String id, int sheet, OrderType type, long sequence, List<DspOrderItem> items) {
        return new NotionalToteOrder(id, id, "104", sheet, type, items, 999, sequence);
    }

    private static DspOrderItem line(String id, String reference, String prescription, String product,
            DspOrderLineType type, int referenceSheet) {
        return new DspOrderItem(prescription.charAt(0) + "-" + id, product, 1, "pharmacy-1", "patient-" + prescription, prescription,
                type, reference, referenceSheet, type == DspOrderLineType.FULL_PACK ? 1 : 0);
    }

    private record Outcome(List<String> dispositions, OutboundAllocationSnapshot outbound,
            long completedDispositions, Duration elapsed) {
    }
}
