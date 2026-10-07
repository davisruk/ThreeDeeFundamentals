package online.davisfamily.warehouse.sim.dsp.analysis.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import online.davisfamily.threedee.sim.framework.SimulationController;
import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayInputLoader;
import online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayInputPaths;
import online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayLoadedInput;
import online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayBagPlanningRequestFactory;
import online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayRuntimeState;
import online.davisfamily.warehouse.sim.dsp.analysis.DspP2pOutputClosureState;
import online.davisfamily.warehouse.sim.dsp.analysis.DspUncalibratedFullDayProfile;
import online.davisfamily.warehouse.sim.dsp.analysis.input.DspFullDayInputPreflight;
import online.davisfamily.warehouse.sim.dsp.analysis.input.DspInputRejectionCatalog;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingBinId;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingBinSnapshot;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingStorageConfig;
import online.davisfamily.warehouse.sim.dsp.bagging.BagPlanningResult;
import online.davisfamily.warehouse.sim.dsp.bagging.DeterministicBagPlanner;
import online.davisfamily.warehouse.sim.dsp.bagging.MaximumPackCountBagCapacityPolicy;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedPackSlotKey;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedBag;
import online.davisfamily.warehouse.sim.dsp.av02.Av02AllocatedTote;
import online.davisfamily.warehouse.sim.dsp.av02.Av02InventorySnapshot;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifestCatalog;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.io.DspDatasetLoadReport;
import online.davisfamily.warehouse.sim.dsp.io.LoadedDspData;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRecord;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRole;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.model.ProductMasterRecord;
import online.davisfamily.warehouse.sim.dsp.osr.OsrInventoryConfig;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteSource;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteIdentity;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundAllocationSnapshot;
import online.davisfamily.warehouse.sim.dsp.outbound.AllocatedOutboundBag;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteSnapshot;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pPhysicalToteAssignment;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pReleaseAssignmentRequest;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;
import online.davisfamily.warehouse.sim.dsp.scheduler.operational.OperationalReleaseBlockType;
import online.davisfamily.warehouse.sim.totebag.handoff.BagReservation;
import online.davisfamily.warehouse.sim.totebag.bag.Bag;
import online.davisfamily.warehouse.sim.totebag.plan.BagSpec;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;

class DspFullDayAnalysisRuntimeFactoryTest {

    @Test
    void shouldComposeSixPhysicalBenchesWithEighteenPositionsAndSharedWaiting() {
        var configured = online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayReportTestSupport
                .configuredStations(sheetOwnedProfile());
        var input = exceptionFixtureInput(configured, true);
        try (var runtime = new DspFullDayAnalysisRuntimeFactory().create(input, configured)) {
            var capacity = runtime.schedulerRuntimeState().snapshot().stationAdmissions()
                    .get(StationType.ADAPTING).capacity();
            assertEquals(18, capacity.maxInProgress());
            assertEquals(18, capacity.queueLimit());
            assertEquals(6, runtime.stationProcessingRuntime().destinations().stream()
                    .filter(destination -> destination.stationType() == StationType.ADAPTING).count());
            assertEquals(6, runtime.stationProcessingRuntime().claimantSnapshots().stream()
                    .filter(claim -> claim.destination().stationType() == StationType.ADAPTING).count());
            var benches = runtime.adaptingBenchAdmissionSnapshots();
            assertEquals(6, benches.size());
            assertTrue(benches.stream().allMatch(bench -> bench.processingCapacity() == 3
                    && bench.occupiedProcessingPositions() == 0 && bench.queueSnapshot().capacity() == 3));
        }
    }
    private static final LocalDate OPERATING_DATE = LocalDate.of(2026, 9, 2);

    @Test
    void shouldInspectConfiguredBenchCapacityWithoutChangingRuntime(@TempDir Path directory)
            throws IOException {
        DspUncalibratedFullDayProfile profile = profile();
        DspFullDayLoadedInput input = loadSingleFullPack(directory, profile);
        try (DspFullDayAnalysisRuntime runtime = new DspFullDayAnalysisRuntimeFactory()
                .create(input, profile)) {
            var before = runtime.snapshot();
            var benches = runtime.adaptingBenchAdmissionSnapshots();
            assertEquals(profile.adaptingBenchDefinitions().stream()
                    .map(DspUncalibratedFullDayProfile.AdaptingBenchDefinition::id).sorted().toList(),
                    benches.stream().map(bench -> bench.benchId().value()).toList());
            assertTrue(benches.stream().allMatch(bench -> bench.admissionOpen()
                    && bench.queueSnapshot().toteIds().isEmpty()
                    && bench.benchSnapshot().activeToteId().isEmpty()));
            assertThrows(UnsupportedOperationException.class, benches::clear);
            assertEquals(benches, runtime.adaptingBenchAdmissionSnapshots());
            assertEquals(before, runtime.snapshot());
        }
    }

    @Test
    void shouldRetainThirdPartyOrdersAndDeterministicOrderAcrossReleaseEvaluations(
            @TempDir Path directory) throws IOException {
        DspUncalibratedFullDayProfile profile = profile();
        DspFullDayLoadedInput input = loadThirdPartyFullPacks(directory, profile);
        List<OrderSheetKey> inputOrder = input.data().orders().stream()
                .map(order -> order.orderSheetKey()).toList();

        try (DspFullDayAnalysisRuntime runtime =
                new DspFullDayAnalysisRuntimeFactory().create(input, profile)) {
            runtime.update(1d);
            DspFullDayAnalysisRuntimeSnapshot first = runtime.snapshot();
            runtime.update(1d);
            DspFullDayAnalysisRuntimeSnapshot second = runtime.snapshot();

            assertEquals(DspFullDayRuntimeState.RUNNING, second.state());
            assertTrue(first.operationalRelease().lastEvaluation().isPresent());
            assertTrue(second.operationalRelease().lastEvaluation().isPresent());
            assertTrue(second.operationalRelease().lastCompletedEvaluationSequence().orElseThrow()
                    > first.operationalRelease().lastCompletedEvaluationSequence().orElseThrow());
            assertEquals(inputOrder, first.scheduler().orderStates().stream()
                    .map(state -> state.order().orderSheetKey()).toList());
            assertEquals(inputOrder, second.scheduler().orderStates().stream()
                    .map(state -> state.order().orderSheetKey()).toList());
        }
    }

    @Test
    void shouldComposeFiveLinesWithSharedAuthoritativeOwners(@TempDir Path directory)
            throws IOException {
        DspUncalibratedFullDayProfile profile = profile();
        DspFullDayLoadedInput input = loadSingleFullPack(directory, profile);

        try (DspFullDayAnalysisRuntime runtime =
                new DspFullDayAnalysisRuntimeFactory().create(input, profile)) {
            assertEquals(5, runtime.lineRuntimes().size());
            assertEquals(31, runtime.lineRuntimes().getFirst().prlConveyors().size());
            assertTrue(runtime.lineRuntimes().stream()
                    .allMatch(line -> line.config().workPlanProvider()
                            instanceof AssignedLineWorkPlanProvider));
            assertSame(runtime.outboundToteAllocator(),
                    runtime.lineRuntimes().getFirst().outboundToteAllocator());
            assertEquals(DspFullDayRuntimeState.RUNNING, runtime.state());
            assertEquals(5, runtime.snapshot().lineSnapshots().size());

            runtime.update(1d);
            runtime.update(120d);
            assertEquals(DspFullDayRuntimeState.RUNNING, runtime.state());
            assertTrue(runtime.snapshot().completions().stream()
                    .anyMatch(snapshot -> snapshot.serviceCentreId().equals("104")));
        }
    }

    @Test
    void shouldKeepCompletionCountsIndexedByServiceCentreAcrossFixedSteps(
            @TempDir Path directory) throws IOException {
        DspUncalibratedFullDayProfile profile = profile();
        DspFullDayLoadedInput input = loadSingleFullPack(directory, profile);

        try (DspFullDayAnalysisRuntime runtime =
                new DspFullDayAnalysisRuntimeFactory().create(input, profile)) {
            var initial = runtime.snapshot().completionSnapshots();
            assertEquals(List.of("104", "108"), initial.stream()
                    .map(snapshot -> snapshot.serviceCentreId())
                    .toList());
            assertEquals(1, initial.get(0).osrWaitingCount());
            assertEquals(1, initial.get(0).remainingPhysicalPackCount());
            assertEquals(1, initial.get(0).remainingPlannedBagCount());
            assertEquals(1, initial.get(1).osrWaitingCount());
            assertEquals(1, initial.get(1).remainingPhysicalPackCount());
            assertEquals(1, initial.get(1).remainingPlannedBagCount());

            runtime.update(1d);

            var afterOneFixedStep = runtime.snapshot().completionSnapshots();
            assertEquals(List.of("104", "108"), afterOneFixedStep.stream()
                    .map(snapshot -> snapshot.serviceCentreId())
                    .toList());
            assertTrue(afterOneFixedStep.stream()
                    .allMatch(snapshot -> snapshot.remainingPlannedBagCount() >= 0
                            && snapshot.remainingPhysicalPackCount() >= 0));
        }
    }

    @Test
    void shouldSharePublishedCompletionCaptureWithMetricsAndKeepPublicReadsFresh(
            @TempDir Path directory) throws IOException {
        DspUncalibratedFullDayProfile profile = profile();
        DspFullDayLoadedInput input = loadSingleFullPack(directory, profile);

        try (DspFullDayAnalysisRuntime runtime =
                new DspFullDayAnalysisRuntimeFactory().create(input, profile)) {
            List<?> firstPublicRead = runtime.completionSnapshots();
            List<?> secondPublicRead = runtime.completionSnapshots();
            assertNotSame(firstPublicRead, secondPublicRead);
            assertTrue(runtime.cutoffController().latestCompletionSnapshots().isEmpty());

            runtime.update(1d);
            assertEquals(DspFullDayRuntimeState.RUNNING, runtime.state());
            List<?> firstPublication = runtime.cutoffController()
                    .latestCompletionSnapshots().orElseThrow();

            runtime.metricsSnapshot();
            runtime.metricsSnapshot();
            assertSame(firstPublication,
                    runtime.cutoffController().latestCompletionSnapshots().orElseThrow());

            List<?> publicReadAfterUpdate = runtime.completionSnapshots();
            assertNotSame(firstPublication, publicReadAfterUpdate);

            runtime.update(1d);
            assertEquals(DspFullDayRuntimeState.RUNNING, runtime.state());
            List<?> secondPublication = runtime.cutoffController()
                    .latestCompletionSnapshots().orElseThrow();
            assertNotSame(firstPublication, secondPublication);
            assertSame(secondPublication,
                    runtime.cutoffController().latestCompletionSnapshots().orElseThrow());
        }
    }

    @Test
    void shouldResolvePhysicalToteOwnersByLayeredPrecedence(
            @TempDir Path directory) throws IOException, ReflectiveOperationException {
        DspUncalibratedFullDayProfile profile = profile();
        DspFullDayLoadedInput input = loadSingleFullPack(directory, profile);

        try (DspFullDayAnalysisRuntime runtime =
                new DspFullDayAnalysisRuntimeFactory().create(input, profile)) {
            InboundToteManifestCatalog manifests = runtime.manifestCatalog();
            PhysicalToteId conflictingId = input.data().inboundToteManifests().getFirst()
                    .physicalToteId();
            Av02AllocatedTote waiting = av02Tote(conflictingId, "av02-waiting", 1);
            Av02AllocatedTote departed = av02Tote(conflictingId, "av02-departed", 2);
            OutboundAllocationSnapshot outbound = outboundTote(conflictingId, "outbound");

            assertEquals("104", resolveOwner(
                    Map.of(), emptyOutbound(), emptyAv02(), manifests, conflictingId));
            assertEquals("av02-waiting", resolveOwner(
                    Map.of(), emptyOutbound(), new Av02InventorySnapshot(1, List.of(waiting), List.of()),
                    manifests, conflictingId));
            assertEquals("av02-departed", resolveOwner(
                    Map.of(), emptyOutbound(), new Av02InventorySnapshot(1, List.of(), List.of(departed)),
                    manifests, conflictingId));
            assertEquals("outbound", resolveOwner(
                    Map.of(), outbound, new Av02InventorySnapshot(1, List.of(waiting), List.of()),
                    manifests, conflictingId));
            assertEquals("active", resolveOwner(
                    Map.of(conflictingId, "active"), outbound,
                    new Av02InventorySnapshot(1, List.of(waiting), List.of()),
                    manifests, conflictingId));
            assertNull(resolveOwner(
                    Map.of(), emptyOutbound(), emptyAv02(), manifests,
                    new PhysicalToteId("missing-owner")));
        }
    }

    @Test
    void shouldRefreshComposedP2pAdmissionAcrossOperationalEvaluations(
            @TempDir Path directory) throws IOException {
        DspUncalibratedFullDayProfile profile = profile();
        DspFullDayLoadedInput input = loadSingleFullPack(directory, profile);

        try (DspFullDayAnalysisRuntime runtime =
                new DspFullDayAnalysisRuntimeFactory().create(input, profile)) {
            runtime.update(1d);
            DspFullDayAnalysisRuntimeSnapshot first = runtime.snapshot();
            long firstSequence = first.operationalRelease()
                    .lastCompletedEvaluationSequence()
                    .orElseThrow();

            runtime.update(1d);
            DspFullDayAnalysisRuntimeSnapshot second = runtime.snapshot();
            long secondSequence = second.operationalRelease()
                    .lastCompletedEvaluationSequence()
                    .orElseThrow();

            assertTrue(secondSequence > firstSequence);
            assertTrue(first.operationalRelease().lastEvaluation().isPresent());
            assertTrue(second.operationalRelease().lastEvaluation().isPresent());
            assertEquals(second.p2pLines().size(), runtime.lineRuntimes().size());
            for (int index = 0; index < runtime.lineRuntimes().size(); index++) {
                DspHeadlessP2pLineRuntime lineRuntime = runtime.lineRuntimes().get(index);
                DspHeadlessP2pLineRuntimeSnapshot lineSnapshot = second.p2pLines().get(index);
                assertEquals(
                        lineSnapshot.activity().packPath().nonIdlePrlCount(),
                        lineRuntime.nonIdlePrlCount());
                assertEquals(
                        lineSnapshot.activity().input().stationArrivalCount(),
                        lineRuntime.stationArrivalCount());
            }
        }
    }

    @Test
    void shouldRefreshCompletionWhenDynamicStationStateChangesForLargeInputs(
            @TempDir Path directory) throws IOException {
        DspUncalibratedFullDayProfile profile = profile();
        DspFullDayLoadedInput input = loadManyFullPacks(directory, profile, 24);

        assertTrue(input.data().inboundToteManifests().size() >= 24);
        assertTrue(input.bagPlan().plannedBags().size() >= 24);

        try (DspFullDayAnalysisRuntime runtime =
                new DspFullDayAnalysisRuntimeFactory().create(input, profile)) {
            var previousCompletion = runtime.completionSnapshots();
            var previousStation = runtime.stationProcessingRuntime().coordinatorSnapshot();
            var previousLifecycle = runtime.lifecycleSnapshotSupplier().get();
            int previousDynamicStationCount = previousCompletion.stream()
                    .mapToInt(snapshot -> snapshot.activeStationClaimCount()
                            + snapshot.pendingStationDispositionCount())
                    .sum();

            var changedCompletion = previousCompletion;
            var changedStation = previousStation;
            boolean foundUnchangedLifecycleWithStationChange = false;
            for (int step = 0; step < 60 && !foundUnchangedLifecycleWithStationChange; step++) {
                runtime.update(1d);
                var currentCompletion = runtime.completionSnapshots();
                var currentStation = runtime.stationProcessingRuntime().coordinatorSnapshot();
                var currentLifecycle = runtime.lifecycleSnapshotSupplier().get();
                int currentDynamicStationCount = currentCompletion.stream()
                        .mapToInt(snapshot -> snapshot.activeStationClaimCount()
                                + snapshot.pendingStationDispositionCount())
                        .sum();

                if (currentLifecycle == previousLifecycle
                        && currentDynamicStationCount != previousDynamicStationCount) {
                    changedCompletion = currentCompletion;
                    changedStation = currentStation;
                    foundUnchangedLifecycleWithStationChange = true;
                } else {
                    previousCompletion = currentCompletion;
                    previousStation = currentStation;
                    previousLifecycle = currentLifecycle;
                    previousDynamicStationCount = currentDynamicStationCount;
                }
            }

            assertTrue(foundUnchangedLifecycleWithStationChange);
            assertNotEquals(previousStation, changedStation);
            assertNotEquals(previousCompletion, changedCompletion);
        }
    }

    @Test
    void shouldCloseOnlyTheAllocatedCentreAndRecheckLiveProcessingState(
            @TempDir Path directory) throws IOException {
        DspUncalibratedFullDayProfile profile = profile();
        DspFullDayLoadedInput input = loadSingleFullPack(directory, profile);

        try (DspFullDayAnalysisRuntime runtime =
                new DspFullDayAnalysisRuntimeFactory().create(input, profile)) {
            PlannedBag centre104Bag = input.bagPlan().plannedBags().stream()
                    .filter(bag -> bag.serviceCentreId().equals("104"))
                    .findFirst()
                    .orElseThrow();
            assertTrue(input.bagPlan().plannedBags().stream()
                    .anyMatch(bag -> bag.serviceCentreId().equals("108")));

            var line = runtime.lineRuntimes().getFirst();
            OrderSheetKey sourceSheet = centre104Bag.owningOrderSheetKeys().getFirst();
            var manifest = input.data().inboundToteManifests().stream()
                    .filter(candidate -> candidate.orderSheetKey().equals(sourceSheet))
                    .findFirst()
                    .orElseThrow();
            P2pPhysicalToteAssignment assignment = new P2pPhysicalToteAssignment(
                    manifest.physicalToteId(),
                    centre104Bag.serviceCentreId(),
                    line.lineDefinition().lineId(),
                    line.lineDefinition().destination());
            runtime.elasticRuntime().operationalReleaseAssignmentCommitter()
                    .prepare(new P2pReleaseAssignmentRequest(
                            manifest.physicalToteId(),
                            sourceSheet,
                            centre104Bag.serviceCentreId(),
                            line.lineDefinition().destination().targetId(),
                            OperationalPhysicalToteSource.OSR,
                            java.util.Optional.of(assignment)))
                    .commit();
            runtime.outboundToteAllocator().allocate(
                    line.lineDefinition().lineId(), centre104Bag, Duration.ZERO);
            Bag probeBag = probeBag();
            BagReservation reservation = line.bagReceiver().reserveIncomingBag(probeBag);
            line.bagReceiver().beginReceiving(reservation);

            OutboundAllocationSnapshot beforeProcessingCheck =
                    runtime.outboundToteAllocator().snapshot();
            runtime.cutoffController().update(new SimulationContext(), 0d);

            assertSame(beforeProcessingCheck, runtime.outboundToteAllocator().snapshot());
            assertEquals(1, runtime.outboundToteAllocator().snapshot().openTotesByLine().size());

            line.bagReceiver().completeReceiving(reservation);
            assertTrue(line.bagReceiver().removeReceivedBag(probeBag));
            runtime.cutoffController().update(new SimulationContext(), 0d);

            OutboundAllocationSnapshot afterClosure = runtime.outboundToteAllocator().snapshot();
            assertNotSame(beforeProcessingCheck, afterClosure);
            assertEquals(1, afterClosure.closedTotes().size());
            assertEquals("104", afterClosure.closedTotes().getFirst().serviceCentreId().orElseThrow());
            assertTrue(afterClosure.openTotesByLine().isEmpty());
            assertSame(afterClosure, runtime.outboundToteAllocator().snapshot());

            runtime.cutoffController().update(new SimulationContext(), 0d);
            assertSame(afterClosure, runtime.outboundToteAllocator().snapshot());
        }
    }

    @Test
    void shouldValidateBeforeRegisteringAnyController(@TempDir Path directory)
            throws IOException {
        DspUncalibratedFullDayProfile valid = profile();
        DspFullDayLoadedInput input = loadSingleFullPack(directory, valid);
        DspUncalibratedFullDayProfile invalid = new DspUncalibratedFullDayProfile(
                valid.operatingDate(),
                valid.osrInventoryConfig(),
                valid.serviceCentreSupplyConfig(),
                valid.inboundToteArrivalPolicy(),
                valid.av02AllocationConfig(),
                valid.p2pElasticAllocationConfig(),
                valid.outboundToteConfig(),
                valid.maximumPacksPerBag(),
                valid.fixedStep(),
                valid.maximumStepsPerAdvance(),
                valid.metricSampleInterval(),
                valid.routeSpeedUnitsPerSecond(),
                valid.queueCapacities(),
                valid.thirdPartyAreaConfig(),
                valid.adaptingStorageConfig(),
                List.of(new DspUncalibratedFullDayProfile.AdaptingBenchDefinition(
                        valid.p2pLineDefinitions().getFirst().destination().targetId(), 0d)),
                valid.p2pPlaceholderDurations(),
                valid.p2pLineDefinitions(),
                valid.prlCountPerLine(),
                valid.timetable());
        RecordingWorld world = new RecordingWorld();

        assertThrows(IllegalArgumentException.class,
                () -> new DspFullDayAnalysisRuntimeFactory().create(world, input, invalid));
        assertEquals(0, world.controllerCount);
    }

    @Test
    void shouldStopAtHardCutoffAndMakeTerminalActionsIdempotent(@TempDir Path directory)
            throws IOException {
        DspUncalibratedFullDayProfile profile = profile();
        DspFullDayLoadedInput input = loadSingleFullPack(directory, profile);

        DspFullDayAnalysisRuntime runtime = new DspFullDayAnalysisRuntimeFactory()
                .create(input, profile);
        try {
            runtime.update(24 * 60 * 60d);
            assertEquals(DspFullDayRuntimeState.HARD_CUTOFF_REACHED, runtime.state());
            DspFullDayAnalysisRuntimeSnapshot cutoff = runtime.snapshot();

            runtime.update(1d);
            assertEquals(DspFullDayRuntimeState.HARD_CUTOFF_REACHED, runtime.state());
            assertEquals(cutoff.cutoffSnapshot(), runtime.snapshot().cutoffSnapshot());
        } finally {
            runtime.close();
            runtime.close();
        }
        assertTrue(runtime.isClosed());
    }

    @Test
    void shouldReachEarlyCompletionAfterPhysicalWorkAndOutputClosure(@TempDir Path directory)
            throws IOException {
        DspUncalibratedFullDayProfile profile = profile();
        DspFullDayLoadedInput input = loadSingleFullPack(directory, profile);

        try (DspFullDayAnalysisRuntime runtime =
                new DspFullDayAnalysisRuntimeFactory().create(input, profile)) {
            for (int step = 0; step < 300
                    && runtime.state() == DspFullDayRuntimeState.RUNNING; step++) {
                runtime.update(1d);
            }

            assertEquals(
                    DspFullDayRuntimeState.ALL_SUPPORTED_WORK_COMPLETE,
                    runtime.state(),
                    () -> "completion=" + runtime.snapshot().completionSnapshots()
                            + ", operational=" + runtime.snapshot().operationalRelease()
                            + ", elastic=" + runtime.snapshot().elastic()
                            + ", scheduler=" + runtime.snapshot().scheduler());
            assertTrue(runtime.snapshot().completions().stream()
                    .allMatch(snapshot -> snapshot.complete()));
        }
    }

    @Test
    void shouldTipOrderOwnedBinsIntoFirstAssociatedSheet() {
        DspUncalibratedFullDayProfile profile = sheetOwnedProfile();
        assertEquals("ORDER_WIDE_PREPARATION_READY_OVERLAP", profile.orderEligibilityPolicyId());
        assertEquals("ADAPTED_FIRST_PHARMACY_GROUPED_THEN_SOURCE_SEQUENCE",
                profile.candidateRankingPolicyId());
        DspFullDayLoadedInput input = adaptedInput(profile, OrderType.ASSOCIATED);
        OrderSheetKey source = new OrderSheetKey("adapted-source", 1);
        OrderSheetKey first = new OrderSheetKey("associated-target", 1);
        OrderSheetKey second = new OrderSheetKey("associated-target", 2);
        Set<PreparedLineKey> expectedKeys = Set.of(
                new PreparedLineKey("associated-target", "A1"),
                new PreparedLineKey("associated-target", "A2"),
                new PreparedLineKey("associated-target", "B"));

        try (DspFullDayAnalysisRuntime runtime =
                new DspFullDayAnalysisRuntimeFactory().create(input, profile)) {
            runtime.update(1d);
            var evaluation = runtime.snapshot().operationalRelease().lastEvaluation().orElseThrow();
            for (OrderSheetKey sheet : List.of(first, second)) {
                assertTrue(evaluation.blockedCandidates().stream().anyMatch(candidate ->
                        candidate.orderSheetKey().equals(sheet)
                                && candidate.blocks().stream().anyMatch(block ->
                                        block.type() == OperationalReleaseBlockType.ADAPTED_DEPENDENCY)),
                        () -> "Expected dependency block for " + sheet + ": " + evaluation);
            }
            assertTrue(runtime.adaptingBinSnapshots().isEmpty());

            for (int step = 0; step < 500
                    && !runtime.schedulerRuntimeState().snapshot().preparedLineKeys().containsAll(expectedKeys);
                    step++) {
                runtime.update(1d);
            }
            assertTrue(runtime.schedulerRuntimeState().snapshot().preparedLineKeys().containsAll(expectedKeys),
                    () -> "Prepared lines not published: " + runtime.snapshot());
            List<AdaptingBinSnapshot> stored = runtime.adaptingBinSnapshots();
            assertSame(stored, runtime.adaptingBinSnapshots());
            assertEquals(List.of("associated-target", "associated-target", "associated-target"),
                    stored.stream().map(bin -> bin.id().referenceOrderId()).toList());
            assertEquals(List.of(1, 2, 3), stored.stream().map(bin -> bin.id().ordinal()).toList());
            assertEquals(List.of(
                    new AdaptingBinId("pharmacy-1", "associated-target", 1),
                    new AdaptingBinId("pharmacy-1", "associated-target", 2),
                    new AdaptingBinId("pharmacy-1", "associated-target", 3)),
                    stored.stream().map(AdaptingBinSnapshot::id).toList());
            assertSame(stored.get(1).id(), stored.getFirst().nextBinId().orElseThrow());
            assertSame(stored.get(2).id(), stored.get(1).nextBinId().orElseThrow());
            assertTrue(stored.get(2).nextBinId().isEmpty());
            assertTrue(stored.stream().flatMap(bin -> bin.stagedRecords().stream())
                    .allMatch(record -> record.location().isEmpty()));
            assertEquals(List.of("A1", "A2", "B"), stored.stream()
                    .flatMap(bin -> bin.stagedRecords().stream())
                    .map(record -> record.line().lineReference()).toList());
            assertTrue(stored.stream().flatMap(bin -> bin.stagedRecords().stream())
                    .allMatch(record -> record.sourceOrderSheetKey().equals(source)
                            && record.sourceServiceCentreId().equals("104")));

            for (int step = 0; step < 500 && !runtime.adaptingBinSnapshots().isEmpty(); step++) {
                runtime.update(1d);
            }
            assertTrue(runtime.adaptingBinSnapshots().isEmpty());
            assertCorrelations(input.bagPlan(), runtime, "tote-associated-1", source,
                    List.of("A1", "A2", "B"));
            assertTrue(runtime.loadPlanRegistry().getLoadPlanFor("tote-associated-2")
                    .getPackPlans().isEmpty());
        }
    }

    @Test
    void shouldBagMixedOrdinaryAndAdaptedPacksRegardlessOfTheirPhysicalOrder() {
        DspUncalibratedFullDayProfile profile = sheetOwnedProfile();
        String prescriptionId = "mixed-prescription";
        String patientId = "mixed-patient";
        // STORE order differs from fulfilment line order, and COLLECT appends to a
        // tote that already holds its ordinary pack. Neither order determines membership.
        NotionalToteOrder source = adaptedOrder("adapted-source", 1, OrderType.ADAPTED,
                List.of(adaptedLine("A2", "associated-target", prescriptionId, patientId),
                        adaptedLine("A1", "associated-target", prescriptionId, patientId)), 0);
        NotionalToteOrder target = adaptedOrder("associated-target", 1, OrderType.ASSOCIATED,
                List.of(adaptedLine("A1", "adapted-source", prescriptionId, patientId),
                        adaptedLine("A2", "adapted-source", prescriptionId, patientId),
                        new DspOrderItem("D", "product-a", 1, "pharmacy-1", patientId,
                                prescriptionId, DspOrderLineType.FULL_PACK,
                                "associated-target", 1, 1)), 1);
        PhysicalToteId targetToteId = new PhysicalToteId("tote-associated-1");
        DspDatasetLoadReport report = DspDatasetLoadReport.empty();
        LoadedDspData assembled = new LoadedDspData(
                List.of(new ProductMasterRecord("product-a", "Product A", Optional.empty(),
                        Optional.of(new PackDimensions(0.07f, 0.034f, 0.027f)))),
                List.of(source, target), source.items(),
                source.items().stream().map(PreparedLineKey::forPreparedLine)
                        .collect(java.util.stream.Collectors.toSet()),
                Set.of(), List.of(
                        new InboundToteManifest(new PhysicalToteId("tote-adapted"),
                                source.orderSheetKey(), source.orderType(), "104", source.items(), 0),
                        new InboundToteManifest(targetToteId, target.orderSheetKey(),
                                target.orderType(), "104", target.items(), 1)), report);
        var projection = new DspFullDayInputPreflight().project(
                assembled, DspInputRejectionCatalog.empty());
        assertTrue(projection.rejectionCatalog().rejectedLines().isEmpty());
        BagPlanningResult plan = new DeterministicBagPlanner(
                new MaximumPackCountBagCapacityPolicy(profile.maximumPacksPerBag()))
                        .plan(new DspFullDayBagPlanningRequestFactory()
                                .create(projection.executableData()));
        DspFullDayLoadedInput input = new DspFullDayLoadedInput(
                projection.executableData(), projection.reportableOrders(),
                projection.rejectionCatalog(), plan, report, profile.timetable());
        String a1 = plan.requirePlannedPackSlot(new PlannedPackSlotKey(
                source.orderSheetKey(), "A1", 1)).reservedPhysicalPackId();
        String a2 = plan.requirePlannedPackSlot(new PlannedPackSlotKey(
                source.orderSheetKey(), "A2", 1)).reservedPhysicalPackId();
        String direct = plan.requirePlannedPackSlot(new PlannedPackSlotKey(
                target.orderSheetKey(), "D", 1)).reservedPhysicalPackId();
        PlannedBag plannedBag = plan.plannedBags().getFirst();
        assertEquals(1, plan.plannedBags().size());
        assertEquals(List.of(a1, a2, direct), plannedBag.physicalPackIds());

        try (DspFullDayAnalysisRuntime runtime =
                new DspFullDayAnalysisRuntimeFactory().create(input, profile)) {
            for (int step = 0; step < 3_000
                    && runtime.state() == DspFullDayRuntimeState.RUNNING; step++) {
                runtime.update(1d);
            }

            assertEquals(DspFullDayRuntimeState.ALL_SUPPORTED_WORK_COMPLETE, runtime.state());
            assertEquals(List.of(direct, a2, a1), runtime.loadPlanRegistry()
                    .getLoadPlanFor(targetToteId.value()).getPackPlans().stream()
                    .map(PackPlan::packId).toList());
            var releasedGroups = runtime.lineRuntimes().stream()
                    .flatMap(line -> line.toteToBagFlowController().getReleasedGroups().stream()).toList();
            assertEquals(1, releasedGroups.size());
            assertEquals(plannedBag.bagKey().correlationId(), releasedGroups.getFirst().correlationId());
            assertEquals(List.of(direct, a2, a1), releasedGroups.getFirst().packs().stream()
                    .map(pack -> pack.getId()).toList());
            OutboundAllocationSnapshot outbound = runtime.outboundToteAllocator().snapshot();
            assertEquals(1, outbound.allocatedBags().size());
            AllocatedOutboundBag allocation = outbound.findAllocatedBag(plannedBag.bagKey()).orElseThrow();
            assertSame(plannedBag, allocation.plannedBag());
            assertEquals(List.of(a1, a2, direct), allocation.actualPhysicalPackIds());
            assertTrue(allocation.missingPhysicalPackIds().isEmpty());
            assertEquals(1, outbound.closedTotes().size());
            assertFalse(outbound.closedTotes().getFirst().requiresExceptionProcessing());
            assertTrue(outbound.openTotesByLine().isEmpty());
            var completion = runtime.completionSnapshots().stream()
                    .filter(value -> value.serviceCentreId().equals("104")).findFirst().orElseThrow();
            assertTrue(completion.complete());
            assertEquals(DspP2pOutputClosureState.P2P_OUTPUT_CLOSED, completion.p2pOutputClosureState());
            assertEquals(0, completion.missingPackCount());
            assertEquals(0, completion.pdcCollectedPackCount());
        }
    }

    @Test
    void shouldCompletePartialAndZeroPackExceptionsWithValidIncomingSheetOwnership() {
        DspUncalibratedFullDayProfile profile = sheetOwnedProfile();
        DspFullDayLoadedInput input = exceptionFixtureInput(profile);
        assertExceptionFixtureCompletes(input, profile);
    }

    @Test
    void shouldCollectDifferentNonDefaultProductDimensionsAndCompleteExceptionFlow() {
        DspUncalibratedFullDayProfile profile = sheetOwnedProfile();
        DspFullDayLoadedInput input = exceptionFixtureInput(profile, true);
        OrderSheetKey source = new OrderSheetKey("adapted-source", 1);
        var first = input.bagPlan().requirePlannedPackSlot(new PlannedPackSlotKey(source, "A1", 1));
        var second = input.bagPlan().requirePlannedPackSlot(new PlannedPackSlotKey(source, "A2", 1));
        assertEquals("product-a", first.sourceProvenance().productId());
        assertEquals("product-b", second.sourceProvenance().productId());
        assertEquals(new PackDimensions(0.07f, 0.034f, 0.027f), first.dimensions());
        assertEquals(new PackDimensions(0.174f, 0.075f, 0.03f), second.dimensions());
        assertExceptionFixtureCompletes(input, profile);
    }

    private static void assertExceptionFixtureCompletes(
            DspFullDayLoadedInput input,
            DspUncalibratedFullDayProfile profile) {
        PlannedBag firstBag = input.bagPlan().plannedBags().stream()
                .filter(bag -> bag.prescriptionId().equals("first-prescription"))
                .findFirst()
                .orElseThrow();
        PlannedBag partialBag = input.bagPlan().plannedBags().stream()
                .filter(bag -> bag.prescriptionId().equals("partial-prescription"))
                .findFirst()
                .orElseThrow();
        PlannedBag zeroPackBag = input.bagPlan().plannedBags().stream()
                .filter(bag -> bag.prescriptionId().equals("zero-prescription"))
                .findFirst()
                .orElseThrow();

        OrderSheetKey sourceSheet = new OrderSheetKey("adapted-source", 1);
        OrderSheetKey firstSheet = new OrderSheetKey("associated-target", 1);
        OrderSheetKey laterSheet = new OrderSheetKey("associated-target", 2);
        assertEquals(4, input.bagPlan().plannedPackSlots().size());
        assertEquals(3, input.bagPlan().plannedBags().size());
        String firstPackId = input.bagPlan().requirePlannedPackSlot(
                new PlannedPackSlotKey(sourceSheet, "A1", 1)).reservedPhysicalPackId();
        String partialAdaptedPackId = input.bagPlan().requirePlannedPackSlot(
                new PlannedPackSlotKey(sourceSheet, "A2", 1)).reservedPhysicalPackId();
        String zeroPackId = input.bagPlan().requirePlannedPackSlot(
                new PlannedPackSlotKey(sourceSheet, "B", 1)).reservedPhysicalPackId();
        String directPackId = input.bagPlan().requirePlannedPackSlot(
                new PlannedPackSlotKey(laterSheet, "D", 1)).reservedPhysicalPackId();
        assertEquals(List.of(firstPackId), firstBag.physicalPackIds());
        assertEquals(List.of(partialAdaptedPackId, directPackId), partialBag.physicalPackIds());
        assertEquals(List.of(zeroPackId), zeroPackBag.physicalPackIds());
        assertEquals(List.of(firstSheet), firstBag.owningOrderSheetKeys());
        assertEquals(List.of(laterSheet), partialBag.owningOrderSheetKeys());
        assertEquals(List.of(laterSheet), zeroPackBag.owningOrderSheetKeys());

        PhysicalToteId firstSheetTote = new PhysicalToteId("tote-associated-1");
        PhysicalToteId secondSheetTote = new PhysicalToteId("tote-associated-2");

        try (DspFullDayAnalysisRuntime runtime =
                new DspFullDayAnalysisRuntimeFactory().create(input, profile)) {
            for (int step = 0; step < 3_000
                    && runtime.state() == DspFullDayRuntimeState.RUNNING; step++) {
                runtime.update(1d);
            }

            assertEquals(DspFullDayRuntimeState.ALL_SUPPORTED_WORK_COMPLETE, runtime.state(),
                    () -> "completion=" + runtime.completionSnapshots()
                            + ", operational=" + runtime.snapshot().operationalRelease()
                            + ", elastic=" + runtime.snapshot().elastic());

            var completion = runtime.completionSnapshots().stream()
                    .filter(value -> value.serviceCentreId().equals("104"))
                    .findFirst()
                    .orElseThrow();
            assertTrue(completion.complete());
            assertEquals(DspP2pOutputClosureState.P2P_OUTPUT_CLOSED_WITH_EXCEPTION,
                    completion.p2pOutputClosureState());
            assertEquals(2, completion.missingPackCount());
            assertEquals(2, completion.pdcCollectedPackCount());
            assertEquals(1, completion.affectedAllocatedBagCount());
            assertEquals(1, completion.markedOutboundToteCount());
            assertEquals(1, completion.pendingEmptyBagCount());

            var firstCollectedPackIds = runtime.loadPlanRegistry()
                    .getLoadPlanFor(firstSheetTote.value()).getPackPlans().stream()
                    .map(PackPlan::packId).toList();
            assertEquals(List.of(firstPackId, partialAdaptedPackId, zeroPackId),
                    firstCollectedPackIds);
            var collectedPlans = runtime.loadPlanRegistry()
                    .getLoadPlanFor(firstSheetTote.value()).getPackPlans();
            List<String> sourceLineReferences = List.of("A1", "A2", "B");
            for (int index = 0; index < sourceLineReferences.size(); index++) {
                var slot = input.bagPlan().requirePlannedPackSlot(new PlannedPackSlotKey(
                        sourceSheet, sourceLineReferences.get(index), 1));
                assertSame(slot.dimensions(), collectedPlans.get(index).dimensions());
                assertEquals(slot.bagKey().correlationId(), collectedPlans.get(index).correlationId());
            }
            assertEquals(List.of(directPackId), runtime.loadPlanRegistry()
                    .getLoadPlanFor(secondSheetTote.value()).getPackPlans().stream()
                    .map(PackPlan::packId).toList());

            OutboundAllocationSnapshot outbound = runtime.outboundToteAllocator().snapshot();
            AllocatedOutboundBag allocatedFirst = outbound.findAllocatedBag(firstBag.bagKey())
                    .orElseThrow();
            assertSame(firstBag, allocatedFirst.plannedBag());
            assertEquals(List.of(firstPackId), allocatedFirst.actualPhysicalPackIds());
            assertEquals(List.of(), allocatedFirst.missingPhysicalPackIds());

            AllocatedOutboundBag partial = outbound.findAllocatedBag(partialBag.bagKey())
                    .orElseThrow();
            assertSame(partialBag, partial.plannedBag());
            assertEquals(List.of(directPackId), partial.actualPhysicalPackIds());
            assertEquals(List.of(partialAdaptedPackId), partial.missingPhysicalPackIds());
            assertEquals(List.of(laterSheet), partial.plannedBag().owningOrderSheetKeys());
            assertTrue(outbound.findAllocatedBag(zeroPackBag.bagKey()).isEmpty());
            assertEquals(2, outbound.allocatedBags().size());
            List<OutboundToteSnapshot> markedTotes = outbound.closedTotes().stream()
                    .filter(OutboundToteSnapshot::requiresExceptionProcessing)
                    .toList();
            assertEquals(1, markedTotes.size());
            OutboundToteSnapshot markedTote = markedTotes.getFirst();
            assertEquals(partial.outboundPhysicalToteId(), markedTote.physicalToteId());
            assertTrue(outbound.openTotesByLine().isEmpty());
        }
    }

    private static DspFullDayLoadedInput exceptionFixtureInput(
            DspUncalibratedFullDayProfile profile) {
        return exceptionFixtureInput(profile, false);
    }

    private static DspFullDayLoadedInput exceptionFixtureInput(
            DspUncalibratedFullDayProfile profile,
            boolean mixedDimensions) {
        String laterProduct = mixedDimensions ? "product-b" : "product-a";
        List<DspOrderItem> preparedLines = List.of(
                adaptedLine("A1", "associated-target", "first-prescription", "patient-first"),
                adaptedLine("A2", "associated-target", "partial-prescription", "patient-partial", 1, laterProduct),
                adaptedLine("B", "associated-target", "zero-prescription", "patient-zero", 1, laterProduct));
        NotionalToteOrder source = adaptedOrder(
                "adapted-source", 1, OrderType.ADAPTED, preparedLines, 0);
        NotionalToteOrder first = adaptedOrder(
                "associated-target", 1, OrderType.ASSOCIATED,
                List.of(adaptedLine("A1", "adapted-source", "first-prescription", "patient-first")), 1);
        NotionalToteOrder second = adaptedOrder(
                "associated-target", 2, OrderType.ASSOCIATED,
                List.of(
                        adaptedLine("A2", "adapted-source", "partial-prescription", "patient-partial", 1, laterProduct),
                        adaptedLine("B", "adapted-source", "zero-prescription", "patient-zero", 1, laterProduct),
                        new DspOrderItem("D", "product-a", 1, "pharmacy-1",
                                "patient-partial", "partial-prescription",
                                DspOrderLineType.FULL_PACK, "associated-target", 2, 1)), 2);
        List<InboundToteManifest> manifests = List.of(
                new InboundToteManifest(new PhysicalToteId("tote-adapted"),
                        source.orderSheetKey(), source.orderType(), "104", source.items(), 0),
                new InboundToteManifest(new PhysicalToteId("tote-associated-1"),
                        first.orderSheetKey(), first.orderType(), "104", first.items(), 1),
                new InboundToteManifest(new PhysicalToteId("tote-associated-2"),
                        second.orderSheetKey(), second.orderType(), "104", second.items(), 2));
        DspDatasetLoadReport report = DspDatasetLoadReport.empty();
        LoadedDspData assembled = new LoadedDspData(
                List.of(new ProductMasterRecord("product-a", "Product A", Optional.empty(),
                        Optional.of(mixedDimensions
                                ? new PackDimensions(0.07f, 0.034f, 0.027f)
                                : new PackDimensions(0.20f, 0.10f, 0.08f))),
                        new ProductMasterRecord("product-b", "Product B", Optional.empty(),
                                Optional.of(new PackDimensions(0.174f, 0.075f, 0.03f)))),
                List.of(source, first, second),
                preparedLines,
                preparedLines.stream().map(PreparedLineKey::forPreparedLine)
                        .collect(java.util.stream.Collectors.toSet()),
                Set.of(),
                manifests,
                report);
        var projection = new DspFullDayInputPreflight().project(
                assembled, DspInputRejectionCatalog.empty());
        assertTrue(projection.rejectionCatalog().rejectedLines().isEmpty());
        BagPlanningResult plan = new DeterministicBagPlanner(
                new MaximumPackCountBagCapacityPolicy(profile.maximumPacksPerBag()))
                        .plan(new DspFullDayBagPlanningRequestFactory()
                                .create(projection.executableData()));
        return new DspFullDayLoadedInput(
                projection.executableData(),
                projection.reportableOrders(),
                projection.rejectionCatalog(),
                plan,
                report,
                profile.timetable());
    }

    private static DspOrderItem adaptedLine(
            String lineReference,
            String referenceOrderId,
            String prescriptionId,
            String patientId) {
        return adaptedLine(lineReference, referenceOrderId, prescriptionId, patientId, 1);
    }

    private static DspOrderItem adaptedLine(
            String lineReference,
            String referenceOrderId,
            String prescriptionId,
            String patientId,
            int referenceSheetNumber) {
        return adaptedLine(lineReference, referenceOrderId, prescriptionId, patientId,
                referenceSheetNumber, "product-a");
    }

    private static DspOrderItem adaptedLine(
            String lineReference,
            String referenceOrderId,
            String prescriptionId,
            String patientId,
            int referenceSheetNumber,
            String productId) {
        return new DspOrderItem(
                lineReference, productId, 1, "pharmacy-1", patientId, prescriptionId,
                DspOrderLineType.ADAPTED, referenceOrderId, referenceSheetNumber, 0);
    }

    @Test
    void shouldRejectConflictingPreparedStoreBeforeConstructingRuntime() {
        DspUncalibratedFullDayProfile profile = sheetOwnedProfile();
        DspFullDayLoadedInput valid = adaptedInput(profile, OrderType.ASSOCIATED);
        LoadedDspData data = valid.data();
        NotionalToteOrder firstTarget = data.orders().get(1);
        DspOrderItem original = firstTarget.items().getFirst();
        DspOrderItem mismatched = new DspOrderItem(
                original.lineReference(), original.productId(), original.quantity(),
                "different-store", original.patientId(), original.prescriptionId(),
                original.lineType(), original.referenceOrderId(), original.referenceSheetNumber(),
                original.numberOfPacksPicked());
        NotionalToteOrder changedTarget = new NotionalToteOrder(
                firstTarget.orderId(), firstTarget.notionalToteId(), firstTarget.serviceCentreId(),
                firstTarget.sheetNumber(), firstTarget.orderType(),
                List.of(mismatched, firstTarget.items().get(1)), firstTarget.orderPriority(),
                firstTarget.sequenceNumber());
        LoadedDspData conflicting = new LoadedDspData(
                data.products(), List.of(data.orders().getFirst(), changedTarget, data.orders().get(2)),
                data.preparedLines(), data.loadedPreparedLineKeys(), data.startupReadyPreparedLineKeys(),
                data.inboundToteManifests(), data.report(), data.retainedInputLines());
        DspFullDayLoadedInput input = new DspFullDayLoadedInput(
                conflicting, valid.reportableOrders(), valid.rejectionCatalog(), valid.bagPlan(),
                valid.report(), valid.timetable());
        RecordingWorld world = new RecordingWorld();

        assertThrows(IllegalStateException.class,
                () -> new DspFullDayAnalysisRuntimeFactory().create(world, input, profile));
        assertEquals(0, world.controllerCount);
    }

    @Test
    void shouldUseSheet002AsFirstCollectorWhenSheet001IsNotExecutable() {
        DspUncalibratedFullDayProfile profile = sheetOwnedProfile();
        DspFullDayLoadedInput input = adaptedInput(profile, OrderType.EMPTY);
        OrderSheetKey emptySheet = new OrderSheetKey("associated-target", 2);
        OrderSheetKey source = new OrderSheetKey("adapted-source", 1);

        try (DspFullDayAnalysisRuntime runtime =
                new DspFullDayAnalysisRuntimeFactory().create(input, profile)) {
            for (int step = 0; step < 500
                    && !runtime.schedulerRuntimeState().snapshot().preparedLineKeys()
                            .contains(new PreparedLineKey("associated-target", "B")); step++) {
                runtime.update(1d);
            }
            assertEquals(List.of("associated-target"), runtime.adaptingBinSnapshots().stream()
                    .map(bin -> bin.id().referenceOrderId()).toList());
            List<AdaptingBinSnapshot> emptyBins = runtime.adaptingBinSnapshots();
            assertEquals(List.of(new AdaptingBinId("pharmacy-1", "associated-target", 1)),
                    emptyBins.stream().map(AdaptingBinSnapshot::id).toList());
            assertTrue(emptyBins.getFirst().nextBinId().isEmpty());
            assertEquals(List.of("B"), emptyBins.stream()
                    .flatMap(bin -> bin.stagedRecords().stream())
                    .map(record -> record.line().lineReference()).toList());
            assertTrue(emptyBins.stream().flatMap(bin -> bin.stagedRecords().stream())
                    .allMatch(record -> record.location().isEmpty()));

            for (int step = 0; step < 500
                    && !runtime.adaptingBinSnapshots().isEmpty(); step++) {
                runtime.update(1d);
            }
            assertTrue(runtime.adaptingBinSnapshots().isEmpty(),
                    () -> "EMPTY collection did not complete: state=" + runtime.state()
                            + ", av02=" + runtime.av02AllocationRuntimeController().snapshot()
                            + ", operational=" + runtime.operationalReleaseRuntime().controller().snapshot()
                            + ", station=" + runtime.stationProcessingRuntime().coordinatorSnapshot());
            PhysicalToteId allocatedId = runtime.av02Inventory().snapshot()
                    .findTote(emptySheet).orElseThrow().physicalToteId();
            assertCorrelations(input.bagPlan(), runtime, allocatedId.value(), source, List.of("B"));
        }
    }

    private static void assertCorrelations(
            BagPlanningResult plan,
            DspFullDayAnalysisRuntime runtime,
            String toteId,
            OrderSheetKey source,
            List<String> lineReferences) {
        var loadPlan = runtime.loadPlanRegistry().getLoadPlanFor(toteId);
        assertEquals(lineReferences.stream().map(line -> plan.requirePlannedPackSlot(
                new PlannedPackSlotKey(source, line, 1)).bagKey().correlationId()).toList(),
                loadPlan.getPackPlans().stream().map(PackPlan::correlationId).toList());
    }

    private static DspUncalibratedFullDayProfile sheetOwnedProfile() {
        DspUncalibratedFullDayProfile base = profile();
        return new DspUncalibratedFullDayProfile(
                base.operatingDate(), new OsrInventoryConfig(
                        base.osrInventoryConfig().capacity(), List.of("104")),
                base.serviceCentreSupplyConfig(),
                base.inboundToteArrivalPolicy(), base.av02AllocationConfig(),
                base.p2pElasticAllocationConfig(), base.outboundToteConfig(), base.maximumPacksPerBag(),
                base.fixedStep(), base.maximumStepsPerAdvance(), base.metricSampleInterval(),
                100d, base.queueCapacities(), base.thirdPartyAreaConfig(),
                new AdaptingStorageConfig(1, 2, 2),
                List.of(new DspUncalibratedFullDayProfile.AdaptingBenchDefinition(
                        "adapting-bench-1", 0d)),
                base.p2pPlaceholderDurations(), base.p2pLineDefinitions(),
                base.prlCountPerLine(), base.timetable());
    }

    private static DspFullDayLoadedInput adaptedInput(
            DspUncalibratedFullDayProfile profile, OrderType secondTargetType) {
        boolean emptyOnly = secondTargetType == OrderType.EMPTY;
        List<DspOrderItem> preparedLines = emptyOnly
                ? List.of(adaptedLine("B", "associated-target"))
                : List.of(adaptedLine("A1", "associated-target"),
                        adaptedLine("A2", "associated-target"),
                        adaptedLine("B", "associated-target"));
        NotionalToteOrder source = adaptedOrder("adapted-source", 1, OrderType.ADAPTED,
                preparedLines, 0);
        NotionalToteOrder first = adaptedOrder("associated-target", 1, OrderType.ASSOCIATED,
                List.of(adaptedLine("A1", "adapted-source"),
                        adaptedLine("A2", "adapted-source")), 1);
        NotionalToteOrder second = adaptedOrder("associated-target", 2, secondTargetType,
                List.of(adaptedLine("B", "adapted-source")), 2);
        List<NotionalToteOrder> orders = emptyOnly
                ? List.of(source, second)
                : List.of(source, first, second);
        List<InboundToteManifest> manifests = new ArrayList<>();
        manifests.add(new InboundToteManifest(new PhysicalToteId("tote-adapted"),
                source.orderSheetKey(), source.orderType(), "104", source.items(), 0));
        if (!emptyOnly) {
            manifests.add(new InboundToteManifest(new PhysicalToteId("tote-associated-1"),
                    first.orderSheetKey(), first.orderType(), "104", first.items(), 1));
        }
        if (secondTargetType != OrderType.EMPTY) {
            manifests.add(new InboundToteManifest(new PhysicalToteId("tote-associated-2"),
                    second.orderSheetKey(), second.orderType(), "104", second.items(), 2));
        }
        DspDatasetLoadReport report = DspDatasetLoadReport.empty();
        LoadedDspData assembled = new LoadedDspData(
                List.of(new ProductMasterRecord("product-a", "Product A", Optional.empty(),
                        Optional.of(new PackDimensions(0.20f, 0.10f, 0.08f)))),
                orders, source.items(), preparedLines.stream()
                        .map(PreparedLineKey::forPreparedLine).collect(java.util.stream.Collectors.toSet()),
                Set.of(), manifests, report);
        var projection = new DspFullDayInputPreflight().project(
                assembled, DspInputRejectionCatalog.empty());
        assertTrue(projection.rejectionCatalog().rejectedLines().isEmpty());
        BagPlanningResult plan = new DeterministicBagPlanner(
                new MaximumPackCountBagCapacityPolicy(profile.maximumPacksPerBag()))
                        .plan(new DspFullDayBagPlanningRequestFactory().create(projection.executableData()));
        return new DspFullDayLoadedInput(projection.executableData(), projection.reportableOrders(),
                projection.rejectionCatalog(), plan, report, profile.timetable());
    }

    private static NotionalToteOrder adaptedOrder(String orderId, int sheetNumber,
            OrderType type, List<DspOrderItem> lines, long sequence) {
        return new NotionalToteOrder(orderId, orderId, "104", sheetNumber,
                type, lines, 999, sequence);
    }

    private static DspOrderItem adaptedLine(String lineReference, String referenceOrderId) {
        return new DspOrderItem(lineReference, "product-a", 1, "pharmacy-1",
                "patient-1", "prescription-" + lineReference, DspOrderLineType.ADAPTED,
                referenceOrderId, 1, 0);
    }

    private static DspUncalibratedFullDayProfile profile() {
        return DspUncalibratedFullDayProfile.productionBaseline(
                OPERATING_DATE, 10, Duration.ofSeconds(1), 2, 4, 4);
    }

    private static DspFullDayLoadedInput loadSingleFullPack(
            Path directory,
            DspUncalibratedFullDayProfile profile) throws IOException {
        Path productMaster = Files.writeString(directory.resolve("products.csv"), """
                dispensingProductPackColumbusCode,name,thirdPartyLocation,length,width,height
                product-a,Product A,,200,100,80
                """);
        Path firstOrder = Files.writeString(directory.resolve("order-104.json"),
                message("order-104", "tote-104", "104", "999"));
        Path secondOrder = Files.writeString(directory.resolve("order-108.json"),
                message("order-108", "tote-108", "108", "998"));
        return new DspFullDayInputLoader().load(
                new DspFullDayInputPaths(productMaster, List.of(firstOrder, secondOrder)), profile);
    }

    private static DspFullDayLoadedInput loadThirdPartyFullPacks(
            Path directory,
            DspUncalibratedFullDayProfile profile) throws IOException {
        Path productMaster = Files.writeString(directory.resolve("products.csv"), """
                dispensingProductPackColumbusCode,name,thirdPartyLocation,length,width,height
                product-a,Product A,Y74,200,100,80
                """);
        Path firstOrder = Files.writeString(directory.resolve("order-104.json"),
                message("order-104", "tote-104", "104", "999"));
        Path secondOrder = Files.writeString(directory.resolve("order-108.json"),
                message("order-108", "tote-108", "108", "998")
                        .replace("\"orderLineNumber\":\"line-1\"", "\"orderLineNumber\":\"line-2\""));
        return new DspFullDayInputLoader().load(
                new DspFullDayInputPaths(productMaster, List.of(firstOrder, secondOrder)), profile);
    }

    private static DspFullDayLoadedInput loadManyFullPacks(
            Path directory,
            DspUncalibratedFullDayProfile profile,
            int orderCount) throws IOException {
        Path productMaster = Files.writeString(directory.resolve("products.csv"), """
                dispensingProductPackColumbusCode,name,thirdPartyLocation,length,width,height
                product-a,Product A,,200,100,80
                """);
        List<Path> orderPaths = new ArrayList<>();
        for (int index = 0; index < orderCount; index++) {
            String orderId = "order-large-" + index;
            String serviceCentreId = index % 2 == 0 ? "104" : "108";
            String priority = index % 2 == 0 ? "999" : "998";
            orderPaths.add(Files.writeString(
                    directory.resolve(orderId + ".json"),
                    message(orderId, "tote-large-" + index, serviceCentreId, priority)));
        }
        return new DspFullDayInputLoader().load(
                new DspFullDayInputPaths(productMaster, orderPaths), profile);
    }

    private static String message(
            String orderId,
            String physicalToteId,
            String serviceCentreId,
            String priority) {
        return """
                {
                  "header": {"orderId":"%s","sheetNumber":"001"},
                  "toteIdentifier": {"payload":"05"},
                  "transportContainer": {"payload":"%s"},
                  "orderPriority": {"payload":"%s"},
                  "serviceCentre": {"payload":"%s"},
                  "orderDetail": {
                    "numberOfOrderLines": 1,
                    "orderLines": [
                      {
                        "orderLineNumber":"line-1",
                        "orderLineType":"05",
                        "pharmacyId":"pharmacy-1",
                        "patientId":"patient-1",
                        "prescriptionId":"%s-prescription",
                        "productId":"product-a",
                        "numberOfPacks":"1",
                        "referenceSheetNumber":"001",
                        "numberOfPacksPicked":"1",
                        "referenceOrderId":"%s"
                      }
                    ]
                  }
                }
                """.formatted(orderId, physicalToteId, priority, serviceCentreId, orderId, orderId);
    }

    private static Bag probeBag() {
        String correlationId = "probe-correlation";
        return new Bag(
                "probe-bag",
                correlationId,
                List.of(new PackPlan(
                        "probe-pack",
                        correlationId,
                        new PackDimensions(0.20f, 0.10f, 0.08f))),
                new BagSpec(0.34f, 0.28f, 0.22f));
    }

    private static Av02AllocatedTote av02Tote(
            PhysicalToteId physicalToteId,
            String serviceCentreId,
            long sourceSequenceNumber) {
        return new Av02AllocatedTote(
                new OperationalPhysicalToteIdentity(
                        OperationalPhysicalToteSource.AV02,
                        physicalToteId,
                        new OrderSheetKey("av02-" + sourceSequenceNumber, 1),
                        OrderType.EMPTY,
                        serviceCentreId,
                        PhysicalToteRole.PRE_P2P,
                        sourceSequenceNumber),
                PhysicalToteRecord.preP2p(physicalToteId),
                "pharmacy-" + serviceCentreId);
    }

    private static OutboundAllocationSnapshot outboundTote(
            PhysicalToteId physicalToteId,
            String serviceCentreId) {
        OutboundToteSnapshot tote = new OutboundToteSnapshot(
                physicalToteId,
                new P2pLineId("owner-test-line"),
                Optional.of(serviceCentreId),
                Optional.of("pharmacy-" + serviceCentreId),
                1,
                List.of(),
                Optional.empty());
        return new OutboundAllocationSnapshot(
                Map.of(tote.p2pLineId(), tote), List.of(), List.of());
    }

    private static OutboundAllocationSnapshot emptyOutbound() {
        return new OutboundAllocationSnapshot(Map.of(), List.of(), List.of());
    }

    private static Av02InventorySnapshot emptyAv02() {
        return new Av02InventorySnapshot(1, List.of(), List.of());
    }

    private static String resolveOwner(
            Map<PhysicalToteId, String> activeRouteOwners,
            OutboundAllocationSnapshot outbound,
            Av02InventorySnapshot av02,
            InboundToteManifestCatalog manifests,
            PhysicalToteId physicalToteId) throws ReflectiveOperationException {
        Class<?> lookupClass = Class.forName(
                DspFullDayAnalysisRuntimeFactory.class.getName()
                        + "$CompletionSnapshotSource$LayeredPhysicalToteOwnerLookup");
        Constructor<?> constructor = lookupClass.getDeclaredConstructor(
                Map.class,
                OutboundAllocationSnapshot.class,
                Av02InventorySnapshot.class,
                InboundToteManifestCatalog.class);
        constructor.setAccessible(true);
        Object lookup = constructor.newInstance(activeRouteOwners, outbound, av02, manifests);
        Method ownerFor = lookupClass.getDeclaredMethod("ownerFor", PhysicalToteId.class);
        ownerFor.setAccessible(true);
        return (String) ownerFor.invoke(lookup, physicalToteId);
    }

    private static final class RecordingWorld extends SimulationWorld {
        private int controllerCount;

        @Override
        public void addController(SimulationController controller) {
            controllerCount++;
            super.addController(controller);
        }
    }
}
