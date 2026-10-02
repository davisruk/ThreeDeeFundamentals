package online.davisfamily.warehouse.sim.dsp.analysis.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.path.LinearSegment3;
import online.davisfamily.threedee.sim.framework.SimulationController;
import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.threedee.sim.framework.objects.SimObject;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.warehouse.sim.dsp.analysis.DspUncalibratedFullDayProfile.P2pPlaceholderDurations;
import online.davisfamily.warehouse.sim.dsp.bagging.BagPlanningResult;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleLedger;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.outbound.DeterministicOutboundToteIdSource;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteAllocator;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteConfig;
import online.davisfamily.warehouse.sim.dsp.outbound.OutputSheetAllocator;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.p2p.arrival.AllowAllP2pArrivalAdmissionPolicy;
import online.davisfamily.warehouse.sim.dsp.p2p.arrival.P2pArrivalRouteBinding;
import online.davisfamily.warehouse.sim.dsp.p2p.arrival.P2pTipperPayloadFactory;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineDefinition;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingCoordinator;
import online.davisfamily.warehouse.sim.dsp.transport.routing.StationRoutedToteArrivalQueue;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperTotePayload;
import online.davisfamily.warehouse.sim.totebag.control.PdcPackDispositionPolicy;
import online.davisfamily.warehouse.sim.totebag.pack.Pack;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteToBagWorkPlanProvider;

class DspHeadlessP2pLineRuntimeFactoryTest {

    @Test
    void shouldWirePolicyAndKeepLineNonQuiescentUntilBypassedPackLeavesPdc() {
        DspHeadlessP2pLineConfig original = fixture("line-1").config();
        assertSame(PdcPackDispositionPolicy.noOp(), original.packDispositionPolicy());
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

    private static DspHeadlessP2pLineConfig withPolicy(DspHeadlessP2pLineConfig c,
            ToteToBagWorkPlanProvider work, PdcPackDispositionPolicy policy) {
        return new DspHeadlessP2pLineConfig(c.lineDefinition(), c.stationArrivalQueue(),
                c.tipperInputQueueCapacity(), c.admissionPolicy(), c.routeBinding(), c.tipperSegment(),
                c.payloadFactory(), c.stationProcessingCoordinator(), work, c.bagPlanningResult(),
                c.outboundToteAllocator(), c.toteCompletedListener(), c.durations(), policy);
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
