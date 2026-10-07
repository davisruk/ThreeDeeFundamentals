package online.davisfamily.warehouse.sim.dsp.analysis.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.path.LinearSegment3;
import online.davisfamily.threedee.sim.framework.SimulationController;
import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.threedee.sim.framework.objects.SimObject;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.warehouse.sim.dsp.analysis.DspUncalibratedFullDayProfile.P2pPlaceholderDurations;
import online.davisfamily.warehouse.sim.dsp.bagging.BagKey;
import online.davisfamily.warehouse.sim.dsp.bagging.BagPlanningResult;
import online.davisfamily.warehouse.sim.dsp.bagging.BagPlanningResultTestFixtures;
import online.davisfamily.warehouse.sim.dsp.bagging.PackSourceProvenance;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedBag;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedPackTrace;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleLedger;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.outbound.DeterministicOutboundToteIdSource;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteAllocator;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteConfig;
import online.davisfamily.warehouse.sim.dsp.outbound.OutputSheetAllocator;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.p2p.arrival.AllowAllP2pArrivalAdmissionPolicy;
import online.davisfamily.warehouse.sim.dsp.p2p.arrival.P2pArrivalRouteBinding;
import online.davisfamily.warehouse.sim.dsp.p2p.arrival.P2pTipperPayloadFactory;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineDefinition;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingCoordinator;
import online.davisfamily.warehouse.sim.dsp.transport.routing.StationRoutedToteArrivalQueue;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperTotePayload;
import online.davisfamily.warehouse.sim.totebag.conveyor.LinearLaneEntrySnapshot;
import online.davisfamily.warehouse.sim.totebag.conveyor.PrlConveyor;
import online.davisfamily.warehouse.sim.totebag.control.PdcPackDispositionPolicy;
import online.davisfamily.warehouse.sim.totebag.pack.Pack;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteToBagWorkPlanProvider;

class DspHeadlessP2pLineRuntimeFactoryTest {

    @Test
    void shouldWirePolicyAndKeepLineNonQuiescentUntilBypassedPackLeavesPdc() {
        DspHeadlessP2pLineConfig original = fixture("line-1").config();
        assertSame(PdcPackDispositionPolicy.noOp(), original.packDispositionPolicy());
        assertTrue(original.missingPackIdsProvider().apply("legacy-correlation").isEmpty());
        int[] collections = {0};
        PdcPackDispositionPolicy policy = new PdcPackDispositionPolicy() {
            @Override public boolean bypassPrl(String id) { return "wrong".equals(id); }
            @Override public int effectivePackCount(String id, int planned) { return 0; }
            @Override public boolean allowEmptyTote(String id) { return "authorized-empty".equals(id); }
            @Override public void collectedAtPdcOutfeed(String id) {
                assertEquals("wrong", id);
                collections[0]++;
            }
            @Override public boolean deferInitialPrlAssignments() { return true; }
            @Override public long classificationEpoch() { return 1; }
        };
        ToteToBagWorkPlanProvider work = new ToteToBagWorkPlanProvider() {
            @Override public OptionalInt expectedPackCount(String id) { return OptionalInt.of(1); }
            @Override public Set<String> expectedCorrelationIds() { return Set.of("missing-bag"); }
        };
        DspHeadlessP2pLineConfig config = withPolicy(original, work, policy);
        assertTrue(config.missingPackIdsProvider().apply("legacy-correlation").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> withPolicy(original, work, null));
        DspHeadlessP2pLineRuntime runtime = new DspHeadlessP2pLineRuntimeFactory().create(config);
        assertSame(policy, runtime.config().packDispositionPolicy());
        assertTrue(runtime.toteToBagFlowController().canAdmit(new ToteLoadPlan("authorized-empty", List.of())));
        assertFalse(runtime.toteToBagFlowController().canAdmit(new ToteLoadPlan("other-empty", List.of())));
        runtime.sortingMachine().receive(new Pack("wrong", "missing-bag",
                new PackDimensions(0.2f, 0.1f, 0.08f)));
        for (int i = 0; i < 100 && runtime.toteToBagFlowController().getPdcLaneEntries().isEmpty(); i++) {
            runtime.simulationWorld().update(0.05d);
        }
        assertEquals(1, runtime.toteToBagFlowController().getPdcLaneEntries().size());
        assertEquals(0, runtime.nonIdlePrlCount());
        assertEquals(0, runtime.toteToBagFlowController().getOutstandingExpectedBagGroupCount());
        assertFalse(runtime.activityProbe().snapshot().quiescent());
        for (int i = 0; i < 200 && collections[0] == 0; i++) { runtime.simulationWorld().update(0.05d); }
        assertEquals(1, collections[0]);
        assertTrue(runtime.activityProbe().snapshot().quiescent());
        assertTrue(runtime.bagReceiver().getReceivedBags().isEmpty());
        assertTrue(runtime.outboundToteAllocator().snapshot().openTotesByLine().isEmpty());
        assertTrue(runtime.outboundToteAllocator().snapshot().closedTotes().isEmpty());
        runtime.simulationWorld().update(0.05d);
        assertEquals(1, collections[0]);
    }

    @Test
    void shouldPassConfiguredPartialPackBagThroughTheCompleteHeadlessLine() {
        DspHeadlessP2pLineConfig base = fixture("line-1").config();
        OrderSheetKey owningSheet = new OrderSheetKey("order-1", 1);
        BagKey bagKey = new BagKey("rx-partial", 1);
        String correlationId = bagKey.correlationId();
        PhysicalToteId inputToteId = new PhysicalToteId("input-partial");
        PackDimensions dimensions = new PackDimensions(0.02f, 0.01f, 0.008f);
        List<PackPlan> packPlans = List.of(
                new PackPlan("p1", correlationId, dimensions),
                new PackPlan("p2", correlationId, dimensions),
                new PackPlan("p3", correlationId, dimensions));
        ToteLoadPlan toteLoadPlan = new ToteLoadPlan(inputToteId, packPlans);
        PlannedBag plannedBag = new PlannedBag(
                bagKey,
                "SC-1",
                "pharmacy-1",
                "patient-1",
                "rx-partial",
                List.of("p1", "p2", "p3"),
                List.of(owningSheet));
        List<PlannedPackTrace> traces = packPlans.stream()
                .map(packPlan -> new PlannedPackTrace(
                        packPlan.packId(),
                        new PackSourceProvenance(
                                owningSheet,
                                "line-" + packPlan.packId(),
                                "product-" + packPlan.packId(),
                                "SC-1",
                                "pharmacy-1",
                                "patient-1",
                                "rx-partial"),
                        inputToteId,
                        owningSheet,
                        bagKey))
                .toList();
        BagPlanningResult bagPlan = BagPlanningResultTestFixtures.complete(
                List.of(plannedBag), List.of(toteLoadPlan), traces);
        ToteToBagWorkPlanProvider workPlan = new ToteToBagWorkPlanProvider() {
            @Override
            public OptionalInt expectedPackCount(String id) {
                return correlationId.equals(id) ? OptionalInt.of(3) : OptionalInt.empty();
            }

            @Override
            public Set<String> expectedCorrelationIds() {
                return Set.of(correlationId);
            }
        };
        PdcPackDispositionPolicy dispositionPolicy = new PdcPackDispositionPolicy() {
            @Override public boolean bypassPrl(String id) { return false; }
            @Override public int effectivePackCount(String id, int planned) {
                assertEquals(correlationId, id);
                assertEquals(3, planned);
                return 2;
            }
            @Override public boolean allowEmptyTote(String id) { return false; }
            @Override public void collectedAtPdcOutfeed(String id) {
                fail("Claimable partial-bag packs must not be collected at the PDC outfeed");
            }
            @Override public boolean deferInitialPrlAssignments() { return true; }
            @Override public long classificationEpoch() { return 1; }
        };
        int[] missingProviderCalls = {0};
        Function<String, Set<String>> missingPackIdsProvider = id -> {
            assertEquals(correlationId, id);
            missingProviderCalls[0]++;
            return Set.of("p2");
        };
        OutboundToteAllocator allocator = new OutboundToteAllocator(
                new PhysicalToteLifecycleLedger(),
                new DeterministicOutboundToteIdSource(),
                new OutputSheetAllocator(List.of(owningSheet)),
                new OutboundToteConfig(4));
        DspHeadlessP2pLineConfig config = withPolicyAndProvider(
                base, workPlan, bagPlan, allocator, dispositionPolicy, missingPackIdsProvider);
        assertSame(missingPackIdsProvider, config.missingPackIdsProvider());
        assertThrows(IllegalArgumentException.class, () -> withPolicyAndProvider(
                base, workPlan, bagPlan, allocator, dispositionPolicy, null));

        RecordingWorld world = new RecordingWorld();
        DspHeadlessP2pLineRuntime runtime = new DspHeadlessP2pLineRuntimeFactory().create(world, config);
        assertEquals(4, world.controllers.size());
        assertEquals(List.of(
                        runtime.tipperFlowController(),
                        runtime.toteToBagFlowController(),
                        runtime.tipperInputQueueController(),
                        runtime.outboundToteAllocationController()),
                world.controllers);
        runtime.sortingMachine().receive(new Pack("p1", correlationId, dimensions));
        runtime.sortingMachine().receive(new Pack("p3", correlationId, dimensions));

        double simulatedSeconds = 0d;
        for (int index = 0; index < 2_000
                && allocator.snapshot().allocatedBags().isEmpty(); index++) {
            runtime.simulationWorld().update(0.05d);
            simulatedSeconds += 0.05d;
        }

        var outbound = allocator.snapshot();
        assertEquals(1, outbound.allocatedBags().size());
        var allocatedBag = outbound.allocatedBags().getFirst();
        assertSame(plannedBag, allocatedBag.plannedBag());
        assertEquals(List.of("p1", "p3"), allocatedBag.actualPhysicalPackIds());
        assertEquals(List.of("p2"), allocatedBag.missingPhysicalPackIds());
        assertTrue(outbound.openToteFor(config.lineDefinition().lineId())
                .orElseThrow().requiresExceptionProcessing());
        assertTrue(runtime.bagReceiver().getReceivedBags().isEmpty());
        assertEquals(1, missingProviderCalls[0]);

        Duration currentSimulationTime = Duration.ofNanos(
                Math.round(simulatedSeconds * 1_000_000_000d));
        var closed = runtime.closeOutboundToteForApplicableWorkCompletion(currentSimulationTime)
                .orElseThrow();
        assertTrue(closed.requiresExceptionProcessing());
    }

    @Test
    void shouldAllocateMixedLengthBagThroughProductionHeadlessGeometry() {
        DspHeadlessP2pLineConfig base = fixture("line-1").config();
        OrderSheetKey owningSheet = new OrderSheetKey("mixed-order", 1);
        BagKey bagKey = new BagKey("mixed-rx", 1);
        String correlationId = bagKey.correlationId();
        PhysicalToteId inputToteId = new PhysicalToteId("mixed-input");
        PackDimensions shortDimensions = new PackDimensions(0.030f, 0.042f, 0.086f);
        PackDimensions longDimensions = new PackDimensions(0.174f, 0.075f, 0.030f);
        List<PackPlan> packPlans = List.of(
                new PackPlan("short-pack", correlationId, shortDimensions),
                new PackPlan("long-pack", correlationId, longDimensions));
        ToteLoadPlan toteLoadPlan = new ToteLoadPlan(inputToteId, packPlans);
        PlannedBag plannedBag = new PlannedBag(
                bagKey,
                "SC-1",
                "pharmacy-1",
                "patient-1",
                "mixed-rx",
                List.of("short-pack", "long-pack"),
                List.of(owningSheet));
        List<PlannedPackTrace> traces = packPlans.stream()
                .map(packPlan -> new PlannedPackTrace(
                        packPlan.packId(),
                        new PackSourceProvenance(
                                owningSheet,
                                "line-" + packPlan.packId(),
                                "product-" + packPlan.packId(),
                                "SC-1",
                                "pharmacy-1",
                                "patient-1",
                                "mixed-rx"),
                        inputToteId,
                        owningSheet,
                        bagKey))
                .toList();
        BagPlanningResult bagPlan = BagPlanningResultTestFixtures.complete(
                List.of(plannedBag), List.of(toteLoadPlan), traces);
        ToteToBagWorkPlanProvider workPlan = new ToteToBagWorkPlanProvider() {
            @Override
            public OptionalInt expectedPackCount(String id) {
                return correlationId.equals(id) ? OptionalInt.of(2) : OptionalInt.empty();
            }

            @Override
            public Set<String> expectedCorrelationIds() {
                return Set.of(correlationId);
            }
        };
        PdcPackDispositionPolicy dispositionPolicy = new PdcPackDispositionPolicy() {
            @Override public boolean bypassPrl(String id) { return false; }
            @Override public int effectivePackCount(String id, int planned) {
                assertEquals(correlationId, id);
                assertEquals(2, planned);
                return planned;
            }
            @Override public boolean allowEmptyTote(String id) { return false; }
            @Override public void collectedAtPdcOutfeed(String id) {
                fail("Complete mixed-length bag packs must not be collected at the PDC outfeed");
            }
            @Override public boolean deferInitialPrlAssignments() { return true; }
            @Override public long classificationEpoch() { return 1; }
        };
        Function<String, Set<String>> missingPackIdsProvider = id -> {
            assertEquals(correlationId, id);
            return Set.of();
        };
        OutboundToteAllocator allocator = new OutboundToteAllocator(
                new PhysicalToteLifecycleLedger(),
                new DeterministicOutboundToteIdSource(),
                new OutputSheetAllocator(List.of(owningSheet)),
                new OutboundToteConfig(4));
        DspHeadlessP2pLineConfig config = withPolicyAndProvider(
                base, workPlan, bagPlan, allocator, dispositionPolicy, missingPackIdsProvider);

        RecordingWorld world = new RecordingWorld();
        DspHeadlessP2pLineRuntime runtime = new DspHeadlessP2pLineRuntimeFactory().create(world, config);
        assertEquals(4, world.controllers.size());
        assertEquals(List.of(
                        runtime.tipperFlowController(),
                        runtime.toteToBagFlowController(),
                        runtime.tipperInputQueueController(),
                        runtime.outboundToteAllocationController()),
                world.controllers);

        Pack shortPack = new Pack("short-pack", correlationId, shortDimensions);
        Pack longPack = new Pack("long-pack", correlationId, longDimensions);
        runtime.sortingMachine().receive(shortPack);
        runtime.sortingMachine().receive(longPack);

        double simulatedSeconds = 0d;
        for (int index = 0; index < 2_000
                && allocator.snapshot().allocatedBags().isEmpty(); index++) {
            runtime.simulationWorld().update(0.05d);
            simulatedSeconds += 0.05d;
            assertProductionLaneGeometry(runtime);
        }

        var outbound = allocator.snapshot();
        assertEquals(1, outbound.allocatedBags().size());
        var allocatedBag = outbound.allocatedBags().getFirst();
        assertSame(plannedBag, allocatedBag.plannedBag());
        assertEquals(List.of("short-pack", "long-pack"), allocatedBag.actualPhysicalPackIds());
        assertTrue(allocatedBag.missingPhysicalPackIds().isEmpty());
        assertFalse(outbound.openToteFor(config.lineDefinition().lineId())
                .orElseThrow().requiresExceptionProcessing());

        for (int index = 0; index < 20; index++) {
            runtime.simulationWorld().update(0.05d);
            simulatedSeconds += 0.05d;
            assertProductionLaneGeometry(runtime);
        }
        assertEquals(1, allocator.snapshot().allocatedBags().size());
        assertTrue(runtime.toteToBagFlowController().getPdcLaneEntries().isEmpty());
        assertTrue(runtime.toteToBagFlowController().getActivePdcTransfers().isEmpty());
        assertTrue(runtime.toteToBagFlowController().getActivePrlToPcrTransfers().isEmpty());
        for (PrlConveyor prl : runtime.prlConveyors()) {
            assertTrue(prl.getLaneEntries().isEmpty());
        }
        assertTrue(runtime.pcrConveyor().getLaneEntries().isEmpty());
        assertTrue(runtime.pcrConveyor().isEmpty());
        assertEquals(0, runtime.toteToBagFlowController().getOutstandingExpectedBagGroupCount());

        Duration currentSimulationTime = Duration.ofNanos(
                Math.round(simulatedSeconds * 1_000_000_000d));
        var closed = runtime.closeOutboundToteForApplicableWorkCompletion(currentSimulationTime)
                .orElseThrow();
        assertFalse(closed.requiresExceptionProcessing());
    }

    private static void assertProductionLaneGeometry(DspHeadlessP2pLineRuntime runtime) {
        assertLaneGeometry(runtime.toteToBagFlowController().getPdcLaneEntries(), 6.2f, 0.015f);
        for (PrlConveyor prl : runtime.prlConveyors()) {
            assertLaneGeometry(prl.getLaneEntries(), 1.8f, 0.015f);
        }
        assertLaneGeometry(runtime.pcrConveyor().getLaneEntries(),
                runtime.pcrConveyor().getUsableLength(), runtime.pcrConveyor().getMinimumGap());
    }

    private static void assertLaneGeometry(
            List<LinearLaneEntrySnapshot> entries,
            float usableLength,
            float minimumGap) {
        final float tolerance = 0.000001f;
        for (int index = 0; index < entries.size(); index++) {
            LinearLaneEntrySnapshot entry = entries.get(index);
            float packLength = entry.pack().getDimensions().length();
            assertTrue(entry.frontDistance() >= packLength - tolerance);
            assertTrue(entry.frontDistance() <= usableLength + tolerance);
            assertTrue(entry.rearDistance() >= -tolerance);
            assertTrue(entry.rearDistance() <= usableLength - packLength + tolerance);
            if (index > 0) {
                LinearLaneEntrySnapshot ahead = entries.get(index - 1);
                assertTrue(ahead.frontDistance() >= entry.frontDistance() - tolerance);
                assertTrue(ahead.rearDistance() - entry.frontDistance() >= minimumGap - tolerance);
            }
        }
    }

    private static DspHeadlessP2pLineConfig withPolicy(DspHeadlessP2pLineConfig c,
            ToteToBagWorkPlanProvider work, PdcPackDispositionPolicy policy) {
        return new DspHeadlessP2pLineConfig(c.lineDefinition(), c.stationArrivalQueue(),
                c.tipperInputQueueCapacity(), c.admissionPolicy(), c.routeBinding(), c.tipperSegment(),
                c.payloadFactory(), c.stationProcessingCoordinator(), work, c.bagPlanningResult(),
                c.outboundToteAllocator(), c.toteCompletedListener(), c.durations(), policy);
    }

    private static DspHeadlessP2pLineConfig withPolicyAndProvider(
            DspHeadlessP2pLineConfig c,
            ToteToBagWorkPlanProvider work,
            BagPlanningResult bagPlan,
            OutboundToteAllocator allocator,
            PdcPackDispositionPolicy policy,
            Function<String, Set<String>> missingPackIdsProvider) {
        return new DspHeadlessP2pLineConfig(
                c.lineDefinition(),
                c.stationArrivalQueue(),
                c.tipperInputQueueCapacity(),
                c.admissionPolicy(),
                c.routeBinding(),
                c.tipperSegment(),
                c.payloadFactory(),
                c.stationProcessingCoordinator(),
                work,
                bagPlan,
                allocator,
                c.toteCompletedListener(),
                c.durations(),
                policy,
                missingPackIdsProvider);
    }

    @Test
    void shouldRegisterOnlyTheHeadlessLineOwnersAfterConstruction() {
        RecordingWorld world = new RecordingWorld();
        Fixture fixture = fixture("line-1");

        DspHeadlessP2pLineRuntime runtime = new DspHeadlessP2pLineRuntimeFactory()
                .create(world, fixture.config());

        assertEquals(4, world.simObjects.size());
        assertEquals(4, world.controllers.size());
        assertEquals(
                List.of(
                        runtime.tipperFlowController(),
                        runtime.toteToBagFlowController(),
                        runtime.tipperInputQueueController(),
                        runtime.outboundToteAllocationController()),
                world.controllers);
        assertEquals(DspHeadlessP2pLineRuntimeFactory.PRL_COUNT_PER_LINE,
                runtime.prlConveyors().size());
        assertEquals(DspHeadlessP2pLineRuntimeFactory.PRL_COUNT_PER_LINE,
                runtime.pdcDiversionDevices().size());
        assertSame(fixture.config().stationArrivalQueue(), runtime.stationArrivalQueue());
        assertSame(fixture.config().outboundToteAllocator(), runtime.outboundToteAllocator());
        assertSame(fixture.config().stationProcessingCoordinator(),
                runtime.stationProcessingCoordinator());
        assertSame(fixture.config().workPlanProvider(), runtime.config().workPlanProvider());
        assertSame(runtime.stationProcessingTarget(), runtime.stationProcessingBinding().target());
        assertSame(runtime.stationArrivalQueue(), runtime.stationProcessingBinding().sourceQueue());
    }

    @Test
    void shouldConstructFiveIndependentLineIds() {
        for (int index = 1; index <= 5; index++) {
            String lineId = "dsp-p2p-line-" + index;
            DspHeadlessP2pLineRuntime runtime = new DspHeadlessP2pLineRuntimeFactory()
                    .create(fixture(lineId).config());

            assertEquals(lineId, runtime.lineDefinition().lineId().value());
            assertEquals(lineId, runtime.lineDefinition().destination().targetId());
            assertEquals(31, runtime.prlConveyors().size());
        }
    }

    private static Fixture fixture(String lineId) {
        OperationalRouteDestination destination = new OperationalRouteDestination(
                StationType.P2P, lineId);
        RouteSegment terminal = segment(lineId + "-terminal", 0f, 1f);
        RouteSegment tipper = segment(lineId + "-tipper", 1f, 2.25f);
        terminal.connectTo(tipper);
        StationRoutedToteArrivalQueue stationQueue =
                new StationRoutedToteArrivalQueue(destination, 4);
        StationProcessingCoordinator coordinator = new StationProcessingCoordinator();
        ToteToBagWorkPlanProvider workPlanProvider = new ToteToBagWorkPlanProvider() {
            @Override
            public OptionalInt expectedPackCount(String correlationId) {
                return OptionalInt.empty();
            }

            @Override
            public Set<String> expectedCorrelationIds() {
                return Set.of();
            }
        };
        OutboundToteAllocator outboundToteAllocator = new OutboundToteAllocator(
                new PhysicalToteLifecycleLedger(),
                new DeterministicOutboundToteIdSource(),
                new OutputSheetAllocator(List.of()),
                new OutboundToteConfig(4));
        P2pTipperPayloadFactory payloadFactory = routedTote -> new TipperTotePayload(
                routedTote.tote(), routedTote.renderable(), 0f, java.util.Map.of());
        DspHeadlessP2pLineConfig config = new DspHeadlessP2pLineConfig(
                new P2pLineDefinition(new P2pLineId(lineId), destination),
                stationQueue,
                2,
                new AllowAllP2pArrivalAdmissionPolicy(),
                new P2pArrivalRouteBinding(terminal, tipper),
                tipper,
                payloadFactory,
                coordinator,
                workPlanProvider,
                new BagPlanningResult(List.of(), List.of(), List.of(), List.of(), List.of()),
                outboundToteAllocator,
                online.davisfamily.warehouse.sim.totebag.control.TipperToteCompletedListener.NO_OP,
                P2pPlaceholderDurations.defaults());
        return new Fixture(config);
    }

    private static RouteSegment segment(String label, float startX, float endX) {
        return new RouteSegment(
                label,
                new LinearSegment3(
                        new Vec3(startX, 0f, 0f),
                        new Vec3(endX, 0f, 0f),
                        false));
    }

    private record Fixture(DspHeadlessP2pLineConfig config) {
    }

    private static final class RecordingWorld extends SimulationWorld {
        private final List<SimObject> simObjects = new ArrayList<>();
        private final List<SimulationController> controllers = new ArrayList<>();

        @Override
        public void addSimObject(SimObject object) {
            simObjects.add(object);
            super.addSimObject(object);
        }

        @Override
        public void addController(SimulationController controller) {
            controllers.add(controller);
            super.addController(controller);
        }
    }
}
