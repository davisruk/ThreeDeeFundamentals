package online.davisfamily.warehouse.sim.dsp.scheduler.operational;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteLifecycleController;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifestCatalog;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteAssignment;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteAssignmentStage;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleLedger;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StartLocation;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.OsrInventoryConfig;
import online.davisfamily.warehouse.sim.dsp.osr.OsrPhysicalInventory;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseCommandHandler;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseRequest;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseSnapshotFactory;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseTarget;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseTargetRegistry;
import online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements;
import online.davisfamily.warehouse.sim.dsp.runtime.DspSchedulerRuntimeState;
import online.davisfamily.warehouse.sim.dsp.runtime.SchedulerCommandApplicationResult;
import online.davisfamily.warehouse.sim.dsp.runtime.operational.DspOperationalReleaseController;
import online.davisfamily.warehouse.sim.dsp.runtime.operational.DspOperationalReleaseControllerSnapshot;
import online.davisfamily.warehouse.sim.dsp.runtime.operational.OperationalReleaseEvaluationResult;
import online.davisfamily.warehouse.sim.dsp.runtime.operational.OperationalReleaseEvaluationSource;
import online.davisfamily.warehouse.sim.dsp.runtime.operational.SynchronousOperationalReleaseEvaluationSource;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspOrderStatus;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationCapacity;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClock;
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClockConfig;
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClockSnapshot;

class DspDependencyReadyOperationalReleaseScenarioTest {

    @Test
    void shouldReleaseAdaptedAndFullPackWithoutOrderTypePriority() {
        DspOrderItem fullItem = fullPackLine("full-line", "pharmacy-1");
        DspOrderItem adaptedItem = adaptedLine(
                "adapted-line", "pharmacy-2", "associated-order");
        InboundToteManifest fullManifest = manifest(
                "full-tote", "full-order", OrderType.FULL_PACK, "sc-1", 1,
                List.of(fullItem));
        InboundToteManifest adaptedManifest = manifest(
                "adapted-tote", "adapted-order", OrderType.ADAPTED, "sc-1", 2,
                List.of(adaptedItem));
        try (Scenario scenario = scenario(
                List.of(fullManifest, adaptedManifest),
                List.of(fullManifest, adaptedManifest),
                List.of(
                        orderState(fullManifest, 999, List.of(fullItem)),
                        orderState(adaptedManifest, 999, List.of(adaptedItem))),
                Set.of())) {

            scenario.update();
            scenario.update();

            assertEquals(
                    List.of(new PhysicalToteId("full-tote"), new PhysicalToteId("adapted-tote")),
                    scenario.inventory.snapshot().departedTotes().stream()
                            .map(InboundToteManifest::physicalToteId)
                            .toList());
            assertEquals(List.of("full-tote"), scenario.p2pTarget.acceptedToteIds());
            assertEquals(List.of("adapted-tote"), scenario.adaptingTarget.acceptedToteIds());
        }
    }

    @Test
    void shouldReleaseAssociatedOnlyAfterOwnDependencyBecomesReady() {
        DspOrderItem adaptedDependency = adaptedLine(
                "adapted-line", "pharmacy-1", "associated-order");
        InboundToteManifest associatedManifest = manifest(
                "associated-tote",
                "associated-order",
                OrderType.ASSOCIATED,
                "sc-1",
                1,
                List.of(adaptedDependency));
        DspOrderItem readyLine = fullPackLine("ready-line", "pharmacy-1");
        InboundToteManifest readyManifest = manifest(
                "ready-tote",
                "ready-order",
                OrderType.FULL_PACK,
                "sc-1",
                2,
                List.of(readyLine));
        DspSchedulerOrderState associatedState = orderState(
                associatedManifest, 999, List.of(adaptedDependency));
        try (Scenario scenario = scenario(
                List.of(associatedManifest, readyManifest),
                List.of(associatedManifest, readyManifest),
                List.of(
                        associatedState,
                        orderState(readyManifest, 999, List.of(readyLine))),
                Set.of())) {

            scenario.update();
            assertTrue(scenario.inventory.snapshot().contains(new PhysicalToteId("associated-tote")));
            assertEquals(List.of("ready-tote"), scenario.p2pTarget.acceptedToteIds());
            assertEquals(OperationalReleaseBlockType.ADAPTED_DEPENDENCY,
                    scenario.controller.snapshot().lastEvaluation().orElseThrow()
                            .blockedCandidates().get(0).blocks().get(0).type());

            scenario.runtimeState.addPreparedLineKey(
                    new PreparedLineKey("unrelated-order", "adapted-line"));
            scenario.update();
            assertTrue(scenario.inventory.snapshot().contains(new PhysicalToteId("associated-tote")));
            assertEquals(List.of("ready-tote"), scenario.p2pTarget.acceptedToteIds());

            scenario.runtimeState.addPreparedLineKey(
                    PreparedLineKey.forDispatchLine(
                            associatedState.order(), adaptedDependency));
            scenario.update();

            assertFalse(scenario.inventory.snapshot().contains(
                    new PhysicalToteId("associated-tote")));
            assertEquals(
                    List.of("ready-tote", "associated-tote"),
                    scenario.p2pTarget.acceptedToteIds());
        }
    }

    @Test
    void shouldSequenceRepeatedPhysicalManifestsWithoutLogicalReleaseMutation() {
        DspOrderItem firstLine = fullPackLine("line-1", "pharmacy-1");
        DspOrderItem secondLine = fullPackLine("line-2", "pharmacy-1");
        InboundToteManifest firstManifest = manifest(
                "tote-1", "shared-order", OrderType.FULL_PACK, "sc-1", 1,
                List.of(firstLine));
        InboundToteManifest secondManifest = manifest(
                "tote-2", "shared-order", OrderType.FULL_PACK, "sc-1", 2,
                List.of(secondLine));
        DspSchedulerOrderState sharedState = orderState(
                firstManifest, 999, List.of(firstLine, secondLine));
        try (Scenario scenario = scenario(
                List.of(firstManifest, secondManifest),
                List.of(firstManifest, secondManifest),
                List.of(sharedState),
                Set.of())) {

            scenario.update();
            scenario.update();

            assertEquals(List.of("tote-1"), scenario.p2pTarget.acceptedToteIds());
            assertTrue(scenario.inventory.snapshot().contains(new PhysicalToteId("tote-2")));
            assertEquals(OperationalReleaseBlockType.ACTIVE_SHEET_ASSIGNMENT,
                    scenario.controller.snapshot().lastEvaluation().orElseThrow()
                            .blockedCandidates().get(0).blocks().get(0).type());

            scenario.clockAt(Duration.ofSeconds(1));
            scenario.lifecycleController.advanceToPreP2p(
                    new PhysicalToteId("tote-1"), Duration.ofSeconds(1));
            scenario.lifecycleController.consumeAtP2p(
                    new PhysicalToteId("tote-1"), Duration.ofSeconds(2));
            scenario.clockAt(Duration.ofSeconds(3));
            scenario.update();

            assertEquals(List.of("tote-1", "tote-2"), scenario.p2pTarget.acceptedToteIds());
            List<PhysicalToteAssignment> assignmentHistory =
                    scenario.lifecycleController.snapshot()
                            .assignmentHistoryFor(new OrderSheetKey("shared-order", 1));
            assertEquals(
                    List.of(
                            PhysicalToteAssignmentStage.INBOUND_PACK,
                            PhysicalToteAssignmentStage.PRE_P2P,
                            PhysicalToteAssignmentStage.INBOUND_PACK),
                    assignmentHistory.stream().map(PhysicalToteAssignment::stage).toList());
            assertEquals(
                    List.of(
                            new PhysicalToteId("tote-1"),
                            new PhysicalToteId("tote-1"),
                            new PhysicalToteId("tote-2")),
                    assignmentHistory.stream()
                            .map(PhysicalToteAssignment::physicalToteId)
                            .toList());
            assertEquals(1, assignmentHistory.stream()
                    .filter(PhysicalToteAssignment::active)
                    .count());
            assertEquals(
                    DspOrderStatus.WAITING,
                    scenario.runtimeState.snapshot().orderStates().get(0).status());
            assertTrue(scenario.runtimeState.snapshot().activeServiceCentreId().isEmpty());
        }
    }

    @Test
    void shouldPreserveStablePharmacyGroupingAcrossPhysicalReleases() {
        InboundToteManifest catalogAnchor = manifest(
                "anchor-tote", "anchor-order", OrderType.FULL_PACK, "sc-1", 1,
                List.of(fullPackLine("anchor-line", "pharmacy-a")));
        InboundToteManifest pharmacyBEarlierSource = manifest(
                "pharmacy-b-tote", "order-b", OrderType.FULL_PACK, "sc-1", 2,
                List.of(fullPackLine("line-b", "pharmacy-b")));
        InboundToteManifest pharmacyALaterSource = manifest(
                "pharmacy-a-tote", "order-a", OrderType.FULL_PACK, "sc-1", 3,
                List.of(fullPackLine("line-a", "pharmacy-a")));
        try (Scenario scenario = scenario(
                List.of(catalogAnchor, pharmacyBEarlierSource, pharmacyALaterSource),
                List.of(pharmacyBEarlierSource, pharmacyALaterSource),
                List.of(
                        orderState(pharmacyBEarlierSource, 999, pharmacyBEarlierSource.items()),
                        orderState(pharmacyALaterSource, 999, pharmacyALaterSource.items())),
                Set.of())) {

            assertEquals(List.of(
                    new ServiceCentrePharmacyGroup("sc-1", "pharmacy-a", 0, 1),
                    new ServiceCentrePharmacyGroup("sc-1", "pharmacy-b", 1, 2)),
                    scenario.operationalSnapshot().pharmacyGroups());

            scenario.update();
            scenario.update();

            assertEquals(
                    List.of("pharmacy-a-tote", "pharmacy-b-tote"),
                    scenario.p2pTarget.acceptedToteIds());
        }
    }

    @Test
    void shouldSelectHighestPriorityServiceCentreWithEligibleWork() {
        InboundToteManifest blockedHigh = manifest(
                "high-blocked", "high-associated", OrderType.ASSOCIATED, "sc-high", 1,
                List.of(adaptedLine("high-adapted", "pharmacy-1", "high-associated")));
        InboundToteManifest readyLow = manifest(
                "low-ready", "low-full", OrderType.FULL_PACK, "sc-low", 2,
                List.of(fullPackLine("low-line", "pharmacy-1")));
        try (Scenario scenario = scenario(
                List.of(blockedHigh, readyLow),
                List.of(blockedHigh, readyLow),
                List.of(
                        orderState(blockedHigh, 999, blockedHigh.items()),
                        orderState(readyLow, 990, readyLow.items())),
                Set.of())) {

            scenario.update();

            assertEquals(List.of("low-ready"), scenario.p2pTarget.acceptedToteIds());
            assertTrue(scenario.inventory.snapshot().contains(new PhysicalToteId("high-blocked")));
        }

        InboundToteManifest readyHigh = manifest(
                "high-ready", "high-full", OrderType.FULL_PACK, "sc-high", 20,
                List.of(fullPackLine("high-line", "pharmacy-1")));
        InboundToteManifest earlierLow = manifest(
                "low-earlier", "low-full", OrderType.FULL_PACK, "sc-low", 1,
                List.of(fullPackLine("low-line", "pharmacy-1")));
        try (Scenario scenario = scenario(
                List.of(readyHigh, earlierLow),
                List.of(readyHigh, earlierLow),
                List.of(
                        orderState(readyHigh, 999, readyHigh.items()),
                        orderState(earlierLow, 990, earlierLow.items())),
                Set.of())) {

            scenario.update();

            assertEquals(List.of("high-ready"), scenario.p2pTarget.acceptedToteIds());
            assertTrue(scenario.inventory.snapshot().contains(new PhysicalToteId("low-earlier")));
        }
    }

    @Test
    void shouldRetryAfterRouteEntryTargetDefers() {
        InboundToteManifest manifest = manifest(
                "tote-1", "order-1", OrderType.FULL_PACK, "sc-1", 1,
                List.of(fullPackLine("line-1", "pharmacy-1")));
        try (Scenario scenario = scenario(
                List.of(manifest),
                List.of(manifest),
                List.of(orderState(manifest, 999, manifest.items())),
                Set.of())) {
            scenario.p2pTarget.result =
                    SchedulerCommandApplicationResult.deferredResult("target full");

            scenario.update();

            assertTrue(scenario.inventory.snapshot().contains(new PhysicalToteId("tote-1")));
            assertTrue(scenario.controller.snapshot().lastCommandApplicationResult()
                    .orElseThrow().deferred());

            scenario.p2pTarget.result = SchedulerCommandApplicationResult.appliedResult();
            scenario.clockAt(Duration.ofSeconds(1));
            scenario.update();

            assertFalse(scenario.inventory.snapshot().contains(new PhysicalToteId("tote-1")));
            assertEquals(List.of("tote-1", "tote-1"), scenario.p2pTarget.acceptedToteIds());
        }
    }

    @Test
    void shouldRejectStaleWorkerDecisionWithoutDuplicateTargetMutation() {
        InboundToteManifest manifest = manifest(
                "tote-1", "order-1", OrderType.FULL_PACK, "sc-1", 1,
                List.of(fullPackLine("line-1", "pharmacy-1")));
        try (Scenario scenario = scenario(
                List.of(manifest),
                List.of(manifest),
                List.of(orderState(manifest, 999, manifest.items())),
                Set.of())) {
            DspOperationalReleaseSnapshot staleSnapshot = scenario.operationalSnapshot();
            DspOperationalReleaseEvaluation staleEvaluation =
                    new DspOperationalReleaseScheduler().evaluate(staleSnapshot);
            assertTrue(scenario.commandHandler.apply(
                    staleEvaluation.releaseDecision().orElseThrow().command()).applied());
            assertEquals(1, scenario.p2pTarget.requests.size());

            PrecomputedEvaluationSource staleSource = new PrecomputedEvaluationSource(
                    new OperationalReleaseEvaluationResult(
                            0, staleSnapshot, staleEvaluation));
            try (DspOperationalReleaseController staleController =
                    new DspOperationalReleaseController(
                            staleSource,
                            scenario::operationalSnapshot,
                            scenario.commandHandler)) {
                staleController.update(new SimulationContext(), 0.1d);

                assertFalse(staleController.snapshot().lastCommandApplicationResult()
                        .orElseThrow().applied());
                assertTrue(staleController.snapshot().lastCommandApplicationResult()
                        .orElseThrow().reason().contains("already departed"));
                assertEquals(1, scenario.p2pTarget.requests.size());
            }
        }
    }

    @Test
    void shouldReconstructExactOperationalReleaseStartupState() {
        InboundToteManifest first = manifest(
                "tote-1", "order-1", OrderType.FULL_PACK, "sc-1", 1,
                List.of(fullPackLine("line-1", "pharmacy-1")));
        InboundToteManifest second = manifest(
                "tote-2", "order-2", OrderType.FULL_PACK, "sc-1", 2,
                List.of(fullPackLine("line-2", "pharmacy-2")));
        List<InboundToteManifest> manifests = List.of(first, second);
        List<DspSchedulerOrderState> states = List.of(
                orderState(first, 999, first.items()),
                orderState(second, 999, second.items()));
        Scenario used = scenario(manifests, manifests, states, Set.of());
        used.update();
        assertFalse(used.inventory.snapshot().departedTotes().isEmpty());
        assertFalse(used.lifecycleController.snapshot().assignments().isEmpty());
        assertTrue(used.controller.snapshot().lastEvaluation().isPresent());
        assertTrue(used.controller.snapshot().lastCommandApplicationResult().isPresent());
        used.close();

        try (Scenario rebuilt = scenario(manifests, manifests, states, Set.of())) {
            assertEquals(
                    List.of(new PhysicalToteId("tote-1"), new PhysicalToteId("tote-2")),
                    rebuilt.operationalSnapshot().candidates().stream()
                            .map(candidate -> candidate.physicalCandidate().physicalToteId())
                            .toList());
            assertEquals(manifests, rebuilt.inventory.snapshot().storedTotes());
            assertTrue(rebuilt.inventory.snapshot().departedTotes().isEmpty());
            assertTrue(rebuilt.lifecycleController.snapshot().assignments().isEmpty());
            assertTrue(rebuilt.controller.snapshot().lastCompletedEvaluationSequence().isEmpty());
            assertTrue(rebuilt.controller.snapshot().lastEvaluation().isEmpty());
            assertTrue(rebuilt.controller.snapshot().lastCommandApplicationResult().isEmpty());
            assertTrue(rebuilt.controller.snapshot().lastPhysicalToteId().isEmpty());
            assertTrue(rebuilt.p2pTarget.requests.isEmpty());
        }
    }

    private static Scenario scenario(
            List<InboundToteManifest> catalogManifests,
            List<InboundToteManifest> storedManifests,
            List<DspSchedulerOrderState> orderStates,
            Set<PreparedLineKey> preparedLineKeys) {
        return new Scenario(
                catalogManifests,
                storedManifests,
                orderStates,
                preparedLineKeys);
    }

    private static InboundToteManifest manifest(
            String physicalToteId,
            String orderId,
            OrderType orderType,
            String serviceCentreId,
            long sourceSequenceNumber,
            List<DspOrderItem> items) {
        return new InboundToteManifest(
                new PhysicalToteId(physicalToteId),
                new OrderSheetKey(orderId, 1),
                orderType,
                serviceCentreId,
                items,
                sourceSequenceNumber);
    }

    private static DspSchedulerOrderState orderState(
            InboundToteManifest manifest,
            int priority,
            List<DspOrderItem> logicalItems) {
        NotionalToteOrder order = new NotionalToteOrder(
                manifest.orderSheetKey().orderId(),
                "notional-" + manifest.orderSheetKey().orderId(),
                manifest.serviceCentreId(),
                manifest.orderSheetKey().sheetNumber(),
                manifest.orderType(),
                logicalItems,
                priority,
                manifest.sourceSequenceNumber());
        RouteRequirements route = switch (manifest.orderType()) {
            case ADAPTED -> new RouteRequirements(
                    false, true, false, false, false, StartLocation.OSR);
            case ASSOCIATED, FULL_PACK -> new RouteRequirements(
                    false, false, false, true, false, StartLocation.OSR);
            case EMPTY -> throw new IllegalArgumentException(
                    "EMPTY cannot have an inbound manifest");
        };
        return new DspSchedulerOrderState(order, route, DspOrderStatus.WAITING);
    }

    private static DspOrderItem fullPackLine(String lineReference, String pharmacyId) {
        return line(
                lineReference,
                pharmacyId,
                DspOrderLineType.FULL_PACK,
                "reference-" + lineReference);
    }

    private static DspOrderItem adaptedLine(
            String lineReference,
            String pharmacyId,
            String referenceOrderId) {
        return line(
                lineReference,
                pharmacyId,
                DspOrderLineType.ADAPTED,
                referenceOrderId);
    }

    private static DspOrderItem line(
            String lineReference,
            String pharmacyId,
            DspOrderLineType lineType,
            String referenceOrderId) {
        return new DspOrderItem(
                lineReference,
                "product-" + lineReference,
                1,
                pharmacyId,
                "patient-" + lineReference,
                "prescription-" + lineReference,
                lineType,
                referenceOrderId,
                1,
                1);
    }

    private static final class Scenario implements AutoCloseable {
        private static final LocalDate OPERATING_DATE = LocalDate.of(2026, 8, 21);

        private final InboundToteManifestCatalog manifestCatalog;
        private final OsrPhysicalInventory inventory;
        private final InboundToteLifecycleController lifecycleController;
        private final DspSchedulerRuntimeState runtimeState;
        private final DspOperationalReleaseSnapshotFactory operationalSnapshotFactory =
                new DspOperationalReleaseSnapshotFactory();
        private final OsrProcessingReleaseSnapshotFactory physicalSnapshotFactory =
                new OsrProcessingReleaseSnapshotFactory();
        private final DspOperationalClock clock = new DspOperationalClock(
                DspOperationalClockConfig.productionBaseline(OPERATING_DATE));
        private final AtomicReference<DspOperationalClockSnapshot> clockSnapshot =
                new AtomicReference<>(clock.initialSnapshot());
        private final RecordingTarget p2pTarget = new RecordingTarget("p2p-1");
        private final RecordingTarget adaptingTarget = new RecordingTarget("adapting-1");
        private final RecordingTarget thirdPartyTarget = new RecordingTarget("third-party-1");
        private final OsrProcessingReleaseCommandHandler commandHandler;
        private final DspOperationalReleaseController controller;

        private Scenario(
                List<InboundToteManifest> catalogManifests,
                List<InboundToteManifest> storedManifests,
                List<DspSchedulerOrderState> orderStates,
                Set<PreparedLineKey> preparedLineKeys) {
            manifestCatalog = new InboundToteManifestCatalog(catalogManifests);
            inventory = new OsrPhysicalInventory(new OsrInventoryConfig(
                    Math.max(1, catalogManifests.size()), List.of()));
            inventory.storeAll(storedManifests);
            lifecycleController = new InboundToteLifecycleController(
                    new PhysicalToteLifecycleLedger(), manifestCatalog);
            runtimeState = new DspSchedulerRuntimeState(new WarehouseSchedulerSnapshot(
                    orderStates,
                    stationAdmissions(),
                    preparedLineKeys,
                    Optional.empty()));
            commandHandler = new OsrProcessingReleaseCommandHandler(
                    inventory,
                    lifecycleController,
                    clockSnapshot::get,
                    new OsrProcessingReleaseTargetRegistry(List.of(
                            thirdPartyTarget,
                            adaptingTarget,
                            p2pTarget)));
            controller = new DspOperationalReleaseController(
                    new SynchronousOperationalReleaseEvaluationSource(
                            new DspOperationalReleaseScheduler()),
                    this::operationalSnapshot,
                    commandHandler);
        }

        private DspOperationalReleaseSnapshot operationalSnapshot() {
            return operationalSnapshotFactory.create(
                    physicalSnapshotFactory.create(
                            inventory.snapshot(), lifecycleController.snapshot()),
                    manifestCatalog,
                    runtimeState.snapshot());
        }

        private void update() {
            controller.update(new SimulationContext(), 0.1d);
        }

        private void clockAt(Duration elapsedTime) {
            clockSnapshot.set(clock.snapshotAt(elapsedTime));
        }

        @Override
        public void close() {
            controller.close();
        }

        private static Map<StationType, StationAdmissionSnapshot> stationAdmissions() {
            return Map.of(
                    StationType.THIRD_PARTY,
                    admission(StationType.THIRD_PARTY, "third-party-1"),
                    StationType.ADAPTING,
                    admission(StationType.ADAPTING, "adapting-1"),
                    StationType.P2P,
                    admission(StationType.P2P, "p2p-1"));
        }

        private static StationAdmissionSnapshot admission(
                StationType stationType,
                String targetId) {
            return new StationAdmissionSnapshot(
                    stationType,
                    new StationCapacity(10, 10),
                    new StationSnapshot(stationType, 0, 0),
                    true,
                    "",
                    Optional.of(targetId));
        }
    }

    private static final class RecordingTarget implements OsrProcessingReleaseTarget {
        private final String targetId;
        private final List<OsrProcessingReleaseRequest> requests = new ArrayList<>();
        private SchedulerCommandApplicationResult result =
                SchedulerCommandApplicationResult.appliedResult();

        private RecordingTarget(String targetId) {
            this.targetId = targetId;
        }

        @Override
        public String targetId() {
            return targetId;
        }

        @Override
        public SchedulerCommandApplicationResult accept(OsrProcessingReleaseRequest request) {
            requests.add(request);
            return result;
        }

        private List<String> acceptedToteIds() {
            return requests.stream()
                    .map(request -> request.manifest().physicalToteId().value())
                    .toList();
        }
    }

    private static final class PrecomputedEvaluationSource
            implements OperationalReleaseEvaluationSource {
        private OperationalReleaseEvaluationResult pendingResult;

        private PrecomputedEvaluationSource(OperationalReleaseEvaluationResult pendingResult) {
            this.pendingResult = pendingResult;
        }

        @Override
        public boolean canSubmit() {
            return false;
        }

        @Override
        public void submit(DspOperationalReleaseSnapshot snapshot) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<OperationalReleaseEvaluationResult> pollResult() {
            OperationalReleaseEvaluationResult result = pendingResult;
            pendingResult = null;
            return Optional.ofNullable(result);
        }

        @Override
        public void close() {
        }
    }
}
