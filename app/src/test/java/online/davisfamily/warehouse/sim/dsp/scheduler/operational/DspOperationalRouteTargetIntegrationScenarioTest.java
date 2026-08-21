package online.davisfamily.warehouse.sim.dsp.scheduler.operational;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptedLineStore;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingArea;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingBench;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingStationAdmissionResolver;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteLifecycleController;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifestCatalog;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleLedger;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleState;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.ProductMasterRecord;
import online.davisfamily.warehouse.sim.dsp.model.StartLocation;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.OsrInventoryConfig;
import online.davisfamily.warehouse.sim.dsp.osr.OsrPhysicalInventory;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseRequest;
import online.davisfamily.warehouse.sim.dsp.osr.release.route.OperationalRouteEntryQueue;
import online.davisfamily.warehouse.sim.dsp.osr.release.route.OperationalRouteTargetDefinition;
import online.davisfamily.warehouse.sim.dsp.osr.release.route.OperationalRouteTargetRegistry;
import online.davisfamily.warehouse.sim.dsp.p2p.P2pAdmissionResult;
import online.davisfamily.warehouse.sim.dsp.p2p.P2pAdmissionSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.P2pStationAdmissionResolver;
import online.davisfamily.warehouse.sim.dsp.p2p.StaticP2pAdmission;
import online.davisfamily.warehouse.sim.dsp.routing.InMemoryProductMasterRepository;
import online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements;
import online.davisfamily.warehouse.sim.dsp.runtime.DspSchedulerRuntimeState;
import online.davisfamily.warehouse.sim.dsp.runtime.operational.DspOperationalReleaseRuntime;
import online.davisfamily.warehouse.sim.dsp.runtime.operational.DspOperationalReleaseRuntimeFactory;
import online.davisfamily.warehouse.sim.dsp.runtime.operational.OperationalReleaseEvaluationResult;
import online.davisfamily.warehouse.sim.dsp.runtime.operational.OperationalReleaseEvaluationSource;
import online.davisfamily.warehouse.sim.dsp.runtime.operational.SynchronousOperationalReleaseEvaluationSource;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspOrderStatus;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;
import online.davisfamily.warehouse.sim.dsp.scheduler.SnapshotStationAdmissionResolver;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionResolver;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationCapacity;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyArea;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyAreaConfig;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyStationAdmissionResolver;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyVisitFactory;
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClock;
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClockConfig;

class DspOperationalRouteTargetIntegrationScenarioTest {

    @Test
    void shouldRoutePhysicalTotesIntoExactThirdPartyAdaptingAndP2pQueues() {
        DspSchedulerOrderState thirdParty = state(
                order("third-party", OrderType.FULL_PACK, "pharmacy-1",
                        DspOrderLineType.FULL_PACK, "third-party-product", 1),
                route(true, false, true));
        DspSchedulerOrderState adaptedOne = state(
                order("adapted-1", OrderType.ADAPTED, "pharmacy-2",
                        DspOrderLineType.ADAPTED, "regular-product", 2),
                route(false, true, false));
        DspSchedulerOrderState adaptedTwo = state(
                order("adapted-2", OrderType.ADAPTED, "pharmacy-3",
                        DspOrderLineType.ADAPTED, "regular-product", 3),
                route(false, true, false));
        DspSchedulerOrderState p2p = state(
                order("p2p", OrderType.FULL_PACK, "pharmacy-4",
                        DspOrderLineType.FULL_PACK, "regular-product", 4),
                route(false, false, true));

        try (Scenario scenario = scenario(List.of(thirdParty, adaptedOne, adaptedTwo, p2p))) {
            scenario.update(4);

            assertEquals(List.of("tote-third-party"), scenario.ids("third-party-ingress"));
            assertEquals(List.of("tote-adapted-1"), scenario.ids("bench-1"));
            assertEquals(List.of("tote-adapted-2"), scenario.ids("bench-2"));
            assertEquals(List.of("tote-p2p"), scenario.ids("p2p-ingress"));
            assertEquals(0, scenario.inventory.snapshot().occupancy());
            assertEquals(
                    Duration.ZERO,
                    scenario.queue("third-party-ingress").peek().orElseThrow().releaseTime());
            assertEquals(
                    PhysicalToteLifecycleState.INBOUND_PACK_TOTE,
                    scenario.lifecycle.snapshot().totes()
                            .get(new PhysicalToteId("tote-p2p")).state());
            assertTrue(scenario.lifecycle.snapshot().activeAssignmentFor(
                    p2p.order().orderSheetKey()).isPresent());
            assertEquals(
                    Duration.ZERO,
                    scenario.lifecycle.snapshot().activeAssignmentFor(
                            p2p.order().orderSheetKey()).orElseThrow().activatedAt());
        }
    }

    @Test
    void shouldBlockOnFullQueueThenReleaseAfterCapacityReturns() {
        DspSchedulerOrderState p2p = state(
                order("p2p", OrderType.FULL_PACK, "pharmacy-1",
                        DspOrderLineType.FULL_PACK, "regular-product", 1),
                route(false, false, true));

        try (Scenario scenario = scenario(List.of(p2p))) {
            InboundToteManifest blocker = manifest(order(
                    "blocker", OrderType.FULL_PACK, "pharmacy-x",
                    DspOrderLineType.FULL_PACK, "regular-product", 99));
            scenario.queue("p2p-ingress").enqueue(
                    new OsrProcessingReleaseRequest(blocker, Duration.ZERO));

            scenario.update(1);

            assertTrue(scenario.inventory.snapshot().findStored(
                    new PhysicalToteId("tote-p2p")).isPresent());
            assertEquals(
                    OperationalReleaseBlockType.STATION_ADMISSION,
                    scenario.runtime.controller().snapshot().lastEvaluation().orElseThrow()
                            .blockedCandidates().get(0).blocks().get(0).type());
            assertTrue(scenario.lifecycle.snapshot().activeAssignmentFor(
                    p2p.order().orderSheetKey()).isEmpty());

            scenario.queue("p2p-ingress").dequeue();
            scenario.update(1);

            assertEquals(List.of("tote-p2p"), scenario.ids("p2p-ingress"));
            assertTrue(scenario.inventory.snapshot().findStored(
                    new PhysicalToteId("tote-p2p")).isEmpty());
        }
    }

    @Test
    void shouldExposeUnknownTargetConfigurationWithoutEmittingACommand() {
        DspSchedulerOrderState p2p = state(
                order("p2p", OrderType.FULL_PACK, "pharmacy-1",
                        DspOrderLineType.FULL_PACK, "regular-product", 1),
                route(false, false, true));
        OperationalRouteTargetRegistry registry = new OperationalRouteTargetRegistry(List.of(
                queue(StationType.P2P, "different-p2p", 1)));
        try (Scenario misconfigured = new Scenario(
                List.of(p2p), registry, resolver("unknown-p2p", adaptingArea()))) {
            misconfigured.update(1);

            assertTrue(misconfigured.inventory.snapshot().findStored(
                    new PhysicalToteId("tote-p2p")).isPresent());
            assertFalse(misconfigured.runtime.controller().snapshot()
                    .lastCommandApplicationResult().isPresent());
            assertTrue(misconfigured.runtime.controller().snapshot().lastEvaluation()
                    .orElseThrow().blockedCandidates().get(0).blocks().get(0).reason()
                    .contains("Unknown operational route target"));
        }
    }

    @Test
    void shouldExposeWrongStationTargetConfigurationWithoutEmittingACommand() {
        DspSchedulerOrderState p2p = state(
                order("p2p", OrderType.FULL_PACK, "pharmacy-1",
                        DspOrderLineType.FULL_PACK, "regular-product", 1),
                route(false, false, true));
        OperationalRouteTargetRegistry registry = new OperationalRouteTargetRegistry(List.of(
                queue(StationType.THIRD_PARTY, "wrong-station-target", 1)));

        try (Scenario scenario = new Scenario(
                List.of(p2p),
                registry,
                resolver("wrong-station-target", adaptingArea()))) {
            scenario.update(1);

            assertTrue(scenario.inventory.snapshot().findStored(
                    new PhysicalToteId("tote-p2p")).isPresent());
            assertFalse(scenario.runtime.controller().snapshot()
                    .lastCommandApplicationResult().isPresent());
            assertTrue(scenario.runtime.controller().snapshot().lastEvaluation()
                    .orElseThrow().blockedCandidates().get(0).blocks().get(0).reason()
                    .contains("belongs to station THIRD_PARTY instead of P2P"));
        }
    }

    @Test
    void shouldDeferWhenQueueFillsAfterEvaluationAndBlockOnFreshEvaluation() {
        DspSchedulerOrderState p2p = state(
                order("p2p", OrderType.FULL_PACK, "pharmacy-1",
                        DspOrderLineType.FULL_PACK, "regular-product", 1),
                route(false, false, true));
        OperationalRouteTargetRegistry registry = new OperationalRouteTargetRegistry(List.of(
                queue(StationType.P2P, "p2p-ingress", 1)));
        PausedEvaluationSource source = new PausedEvaluationSource();

        try (Scenario scenario = new Scenario(
                List.of(p2p),
                registry,
                resolver("p2p-ingress", adaptingArea()),
                source)) {
            scenario.update(1);
            InboundToteManifest blocker = manifest(order(
                    "blocker", OrderType.FULL_PACK, "pharmacy-x",
                    DspOrderLineType.FULL_PACK, "regular-product", 99));
            scenario.queue("p2p-ingress").enqueue(
                    new OsrProcessingReleaseRequest(blocker, Duration.ZERO));
            source.releasePending();

            scenario.update(1);

            assertTrue(scenario.runtime.controller().snapshot()
                    .lastCommandApplicationResult().orElseThrow().deferred());
            assertTrue(scenario.inventory.snapshot().findStored(
                    new PhysicalToteId("tote-p2p")).isPresent());
            assertTrue(scenario.lifecycle.snapshot().activeAssignmentFor(
                    p2p.order().orderSheetKey()).isEmpty());

            scenario.update(1);

            assertTrue(scenario.runtime.controller().snapshot().lastEvaluation()
                    .orElseThrow().releaseDecision().isEmpty());
            assertEquals(1, scenario.runtime.controller().snapshot().lastEvaluation()
                    .orElseThrow().blockedCandidates().size());
        }
    }

    @Test
    void shouldKeepRepeatedPhysicalManifestsForOneLogicalSheetSequenced() {
        NotionalToteOrder sharedOrder = new NotionalToteOrder(
                "shared-order",
                "notional-shared-order",
                "sc-1",
                1,
                OrderType.FULL_PACK,
                List.of(
                        item("line-1", "pharmacy-1"),
                        item("line-2", "pharmacy-1")),
                999,
                1);
        DspSchedulerOrderState sharedState = state(
                sharedOrder, route(false, false, true));
        List<InboundToteManifest> manifests = List.of(
                new InboundToteManifest(
                        new PhysicalToteId("shared-tote-1"),
                        sharedOrder.orderSheetKey(),
                        sharedOrder.orderType(),
                        sharedOrder.serviceCentreId(),
                        List.of(sharedOrder.items().get(0)),
                        1),
                new InboundToteManifest(
                        new PhysicalToteId("shared-tote-2"),
                        sharedOrder.orderSheetKey(),
                        sharedOrder.orderType(),
                        sharedOrder.serviceCentreId(),
                        List.of(sharedOrder.items().get(1)),
                        2));
        OperationalRouteTargetRegistry registry = new OperationalRouteTargetRegistry(List.of(
                queue(StationType.P2P, "p2p-ingress", 2)));

        try (Scenario scenario = new Scenario(
                List.of(sharedState),
                manifests,
                registry,
                resolver("p2p-ingress", adaptingArea()),
                new SynchronousOperationalReleaseEvaluationSource(
                        new DspOperationalReleaseScheduler()))) {
            scenario.update(2);

            assertEquals(List.of("shared-tote-1"), scenario.ids("p2p-ingress"));
            assertTrue(scenario.inventory.snapshot().findStored(
                    new PhysicalToteId("shared-tote-2")).isPresent());
            assertEquals(DspOrderStatus.WAITING, sharedState.status());
            assertEquals(
                    OperationalReleaseBlockType.ACTIVE_SHEET_ASSIGNMENT,
                    scenario.runtime.controller().snapshot().lastEvaluation().orElseThrow()
                            .blockedCandidates().get(0).blocks().get(0).type());
        }
    }

    private static Scenario scenario(List<DspSchedulerOrderState> states) {
        AdaptingArea adaptingArea = adaptingArea();
        OperationalRouteTargetRegistry registry = new OperationalRouteTargetRegistry(List.of(
                queue(StationType.THIRD_PARTY, "third-party-ingress", 4),
                queue(StationType.ADAPTING, "bench-1", 1),
                queue(StationType.ADAPTING, "bench-2", 1),
                queue(StationType.P2P, "p2p-ingress", 1)));
        return new Scenario(states, registry, resolver("p2p-ingress", adaptingArea));
    }

    private static StationAdmissionResolver resolver(
            String p2pTargetId,
            AdaptingArea adaptingArea) {
        InMemoryProductMasterRepository products = new InMemoryProductMasterRepository(List.of(
                new ProductMasterRecord(
                        "third-party-product", "Third party", Optional.of("Y74"), Optional.empty()),
                new ProductMasterRecord(
                        "regular-product", "Regular", Optional.empty(), Optional.empty())));
        ThirdPartyArea thirdPartyArea = new ThirdPartyArea(
                new ThirdPartyAreaConfig(4, 1, 1d));
        StationAdmissionResolver thirdParty = new ThirdPartyStationAdmissionResolver(
                new SnapshotStationAdmissionResolver(),
                new ThirdPartyVisitFactory(products),
                thirdPartyArea::snapshot,
                "third-party-ingress");
        StationAdmissionResolver adapting = new AdaptingStationAdmissionResolver(
                thirdParty, adaptingArea, new StationCapacity(4, 4));
        return new P2pStationAdmissionResolver(
                adapting,
                new StaticP2pAdmission(P2pAdmissionResult.acceptedResult()),
                () -> new P2pAdmissionSnapshot(
                        "p2p-1", 1, Set.of(), Set.of(), true),
                new StationCapacity(1, 1),
                () -> new StationSnapshot(StationType.P2P, 0, 0),
                p2pTargetId);
    }

    private static AdaptingArea adaptingArea() {
        AdaptedLineStore store = new AdaptedLineStore();
        return new AdaptingArea(List.of(
                new AdaptingBench("bench-1", store, 1d),
                new AdaptingBench("bench-2", store, 1d)), 1);
    }

    private static OperationalRouteEntryQueue queue(
            StationType stationType,
            String targetId,
            int capacity) {
        return new OperationalRouteEntryQueue(
                new OperationalRouteTargetDefinition(stationType, targetId, capacity));
    }

    private static DspSchedulerOrderState state(
            NotionalToteOrder order,
            RouteRequirements route) {
        return new DspSchedulerOrderState(order, route, DspOrderStatus.WAITING);
    }

    private static RouteRequirements route(
            boolean thirdParty,
            boolean adapting,
            boolean p2p) {
        return new RouteRequirements(
                thirdParty, adapting, false, p2p, false, StartLocation.OSR);
    }

    private static NotionalToteOrder order(
            String orderId,
            OrderType orderType,
            String pharmacyId,
            DspOrderLineType lineType,
            String productId,
            long sequence) {
        DspOrderItem item = new DspOrderItem(
                "line-" + orderId,
                productId,
                1,
                pharmacyId,
                "patient-" + orderId,
                "prescription-" + orderId,
                lineType,
                "associated-" + orderId,
                1,
                0);
        return new NotionalToteOrder(
                orderId,
                "notional-" + orderId,
                "sc-1",
                1,
                orderType,
                List.of(item),
                999,
                sequence);
    }

    private static InboundToteManifest manifest(NotionalToteOrder order) {
        return new InboundToteManifest(
                new PhysicalToteId("tote-" + order.orderId()),
                order.orderSheetKey(),
                order.orderType(),
                order.serviceCentreId(),
                order.items(),
                order.sequenceNumber());
    }

    private static DspOrderItem item(String lineReference, String pharmacyId) {
        return new DspOrderItem(
                lineReference,
                "regular-product",
                1,
                pharmacyId,
                "patient-" + lineReference,
                "prescription-" + lineReference,
                DspOrderLineType.FULL_PACK,
                "shared-order",
                1,
                0);
    }

    private static final class Scenario implements AutoCloseable {
        private final InboundToteManifestCatalog catalog;
        private final OsrPhysicalInventory inventory;
        private final InboundToteLifecycleController lifecycle;
        private final OperationalRouteTargetRegistry registry;
        private final DspOperationalReleaseRuntime runtime;

        private Scenario(
                List<DspSchedulerOrderState> states,
                OperationalRouteTargetRegistry registry,
                StationAdmissionResolver admissionResolver) {
            this(
                    states,
                    states.stream().map(state -> manifest(state.order())).toList(),
                    registry,
                    admissionResolver,
                    new SynchronousOperationalReleaseEvaluationSource(
                            new DspOperationalReleaseScheduler()));
        }

        private Scenario(
                List<DspSchedulerOrderState> states,
                OperationalRouteTargetRegistry registry,
                StationAdmissionResolver admissionResolver,
                OperationalReleaseEvaluationSource evaluationSource) {
            this(
                    states,
                    states.stream().map(state -> manifest(state.order())).toList(),
                    registry,
                    admissionResolver,
                    evaluationSource);
        }

        private Scenario(
                List<DspSchedulerOrderState> states,
                List<InboundToteManifest> manifests,
                OperationalRouteTargetRegistry registry,
                StationAdmissionResolver admissionResolver,
                OperationalReleaseEvaluationSource evaluationSource) {
            catalog = new InboundToteManifestCatalog(manifests);
            inventory = new OsrPhysicalInventory(new OsrInventoryConfig(
                    Math.max(1, manifests.size()), List.of()));
            inventory.storeAll(manifests);
            lifecycle = new InboundToteLifecycleController(
                    new PhysicalToteLifecycleLedger(), catalog);
            DspSchedulerRuntimeState logicalState = new DspSchedulerRuntimeState(
                    new WarehouseSchedulerSnapshot(
                            states, Map.of(), Set.of(), Optional.empty()));
            this.registry = registry;
            DspOperationalClock clock = new DspOperationalClock(
                    DspOperationalClockConfig.productionBaseline(
                            LocalDate.of(2026, 8, 21)));
            runtime = new DspOperationalReleaseRuntimeFactory().create(
                    evaluationSource,
                    inventory,
                    lifecycle,
                    catalog,
                    logicalState::snapshot,
                    clock::initialSnapshot,
                    admissionResolver,
                    registry);
        }

        private void update(int count) {
            for (int index = 0; index < count; index++) {
                runtime.controller().update(new SimulationContext(), 0.1d);
            }
        }

        private OperationalRouteEntryQueue queue(String targetId) {
            return registry.find(targetId).orElseThrow();
        }

        private List<String> ids(String targetId) {
            return queue(targetId).snapshot().physicalToteIds().stream()
                    .map(PhysicalToteId::value)
                    .toList();
        }

        @Override
        public void close() {
            runtime.close();
        }
    }

    private static final class PausedEvaluationSource
            implements OperationalReleaseEvaluationSource {
        private final DspOperationalReleaseScheduler scheduler =
                new DspOperationalReleaseScheduler();
        private OperationalReleaseEvaluationResult pending;
        private long nextSequence;
        private boolean releasePending;

        @Override
        public boolean canSubmit() {
            return pending == null;
        }

        @Override
        public void submit(DspOperationalReleaseSnapshot snapshot) {
            pending = new OperationalReleaseEvaluationResult(
                    nextSequence++, snapshot, scheduler.evaluate(snapshot));
        }

        @Override
        public Optional<OperationalReleaseEvaluationResult> pollResult() {
            if (!releasePending || pending == null) {
                return Optional.empty();
            }
            OperationalReleaseEvaluationResult result = pending;
            pending = null;
            return Optional.of(result);
        }

        private void releasePending() {
            releasePending = true;
        }

        @Override
        public void close() {
        }
    }
}
