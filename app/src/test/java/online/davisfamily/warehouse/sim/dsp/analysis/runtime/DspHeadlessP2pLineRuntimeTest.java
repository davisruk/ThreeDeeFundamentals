package online.davisfamily.warehouse.sim.dsp.analysis.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.behaviour.routing.RouteFollower;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.matrices.Vec4;
import online.davisfamily.threedee.model.Mesh;
import online.davisfamily.threedee.path.LinearSegment3;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.sim.dsp.analysis.DspUncalibratedFullDayProfile.P2pPlaceholderDurations;
import online.davisfamily.warehouse.sim.dsp.bagging.BagKey;
import online.davisfamily.warehouse.sim.dsp.bagging.BagPlanningResult;
import online.davisfamily.warehouse.sim.dsp.bagging.PackSourceProvenance;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedBag;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedPackTrace;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleLedger;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRole;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.osr.release.OperationalPhysicalToteReleaseRequest;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteIdentity;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteSource;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteLaunchRequest;
import online.davisfamily.warehouse.sim.dsp.outbound.DeterministicOutboundToteIdSource;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteAllocator;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteConfig;
import online.davisfamily.warehouse.sim.dsp.outbound.OutputSheetAllocator;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.p2p.arrival.AllowAllP2pArrivalAdmissionPolicy;
import online.davisfamily.warehouse.sim.dsp.p2p.arrival.ContainedPackP2pTipperPayloadFactory;
import online.davisfamily.warehouse.sim.dsp.p2p.arrival.P2pArrivalRouteBinding;
import online.davisfamily.warehouse.sim.dsp.p2p.arrival.StationProcessingP2pToteCompletedListener;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineActivitySnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineDefinition;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationArrivalClaimController;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingCoordinator;
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;
import online.davisfamily.warehouse.sim.dsp.transport.routing.StationRoutedToteArrivalQueue;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteToBagWorkPlanProvider;

class DspHeadlessP2pLineRuntimeTest {
    private static final double UPDATE_SECONDS = 0.1d;
    private static final PackDimensions PACK_DIMENSIONS =
            new PackDimensions(0.20f, 0.10f, 0.08f);

    @Test
    void shouldDriveExactTotesThroughTipperToOneBagAndOutputClosure() {
        Fixture fixture = fixture();
        DspHeadlessP2pLineRuntime runtime = fixture.runtime();
        DspHeadlessP2pLineRuntimeSnapshot beforeUpdate = runtime.snapshot();

        RoutedPhysicalTote first = fixture.enqueue(
                "input-tote-1",
                fixture.firstPlan());
        RoutedPhysicalTote second = fixture.enqueue(
                "input-tote-2",
                fixture.secondPlan());

        advanceUntil(runtime, () -> runtime.snapshot().outboundAllocation().allocatedBags().size() == 1);

        DspHeadlessP2pLineRuntimeSnapshot completed = runtime.snapshot();
        assertNotSame(beforeUpdate, completed);
        assertEquals(List.of(first.tote().getId(), second.tote().getId()), fixture.completedToteIds());
        assertEquals(1, completed.completedBagCorrelationIds().size());
        assertEquals(1, completed.outboundAllocation().allocatedBags().size());
        assertEquals(1, completed.outboundAllocation().openTotesByLine().size());
        assertTrue(completed.activity().processingDrained());
        assertFalse(completed.activity().quiescent());
        assertTrue(completed.prlStatesById().values().stream()
                .allMatch(state -> state == online.davisfamily.warehouse.sim.totebag.assignment.PrlState.IDLE));
        assertTrue(completed.outboundAllocation().allocatedBags().stream()
                .allMatch(allocated -> !allocated.outboundPhysicalToteId().value().startsWith("input-tote-")));
        assertThrows(UnsupportedOperationException.class,
                () -> completed.prlStatesById().put("unexpected", null));

        String outboundId = completed.outboundAllocation().allocatedBags().getFirst()
                .outboundPhysicalToteId().value();
        runtime.closeOutboundToteForApplicableWorkCompletion(Duration.ofSeconds(100));
        DspHeadlessP2pLineRuntimeSnapshot closedOutput = runtime.snapshot();
        assertTrue(closedOutput.activity().quiescent());
        assertTrue(closedOutput.outboundAllocation().openTotesByLine().isEmpty());
        assertEquals(outboundId,
                closedOutput.outboundAllocation().closedTotes().getFirst().physicalToteId().value());

        fixture.provider().put(fixture.laterCorrelation(), 1);
        runtime.update(UPDATE_SECONDS);
        DspHeadlessP2pLineRuntimeSnapshot laterWork = runtime.snapshot();
        assertFalse(laterWork.activity().quiescent());
        assertFalse(laterWork.activity().processingDrained());
        assertEquals(2, fixture.provider().expected.size());

        assertFalse(runtime.isClosed());
        runtime.close();
        runtime.close();
        assertTrue(runtime.isClosed());
    }

    @Test
    void shouldResetByReconstructionAndKeepOldSnapshotsDetached() {
        Fixture firstFixture = fixture();
        DspHeadlessP2pLineRuntime firstRuntime = firstFixture.runtime();
        DspHeadlessP2pLineRuntimeSnapshot initial = firstRuntime.snapshot();
        firstFixture.enqueue("input-tote-1", firstFixture.firstPlan());
        firstFixture.enqueue("input-tote-2", firstFixture.secondPlan());
        advanceUntil(firstRuntime,
                () -> firstRuntime.snapshot().outboundAllocation().allocatedBags().size() == 1);

        assertEquals(0, initial.outboundAllocation().allocatedBags().size());
        assertEquals(0, initial.completedBagCorrelationIds().size());
        assertTrue(initial.prlStatesById().values().stream()
                .allMatch(state -> state == online.davisfamily.warehouse.sim.totebag.assignment.PrlState.IDLE));

        Fixture reconstructedFixture = fixture();
        DspHeadlessP2pLineRuntime reconstructed = reconstructedFixture.runtime();
        DspHeadlessP2pLineRuntimeSnapshot reconstructedSnapshot = reconstructed.snapshot();
        assertEquals(0, reconstructedSnapshot.outboundAllocation().allocatedBags().size());
        assertEquals(0, reconstructedSnapshot.completedBagCorrelationIds().size());
        assertEquals(P2pLineActivitySnapshot.idle().input(),
                reconstructedSnapshot.activity().input());
        assertSame(reconstructedFixture.coordinator(), reconstructed.stationProcessingCoordinator());
    }

    private static Fixture fixture() {
        String lineId = "headless-line-1";
        OperationalRouteDestination destination = new OperationalRouteDestination(
                online.davisfamily.warehouse.sim.dsp.model.StationType.P2P,
                lineId);
        RouteSegment terminal = segment("terminal", 0f, 1f);
        RouteSegment approach = segment("approach", 1f, 2f);
        RouteSegment tipper = segment("tipper", 2f, 3.25f);
        RouteSegment exit = segment("exit", 3.25f, 5f);
        terminal.connectTo(approach);
        approach.connectTo(tipper);
        tipper.connectTo(exit);

        PhysicalToteId firstId = new PhysicalToteId("input-tote-1");
        PhysicalToteId secondId = new PhysicalToteId("input-tote-2");
        OrderSheetKey orderSheetKey = new OrderSheetKey("order-1", 1);
        BagKey bagKey = new BagKey("rx-1", 1);
        String correlationId = bagKey.correlationId();
        ToteLoadPlan firstPlan = new ToteLoadPlan(
                firstId,
                List.of(new PackPlan("pack-1", correlationId, PACK_DIMENSIONS)));
        ToteLoadPlan secondPlan = new ToteLoadPlan(
                secondId,
                List.of(new PackPlan("pack-2", correlationId, PACK_DIMENSIONS)));
        PlannedBag plannedBag = new PlannedBag(
                bagKey,
                "SC-1",
                "pharmacy-1",
                "patient-1",
                "rx-1",
                List.of("pack-1", "pack-2"),
                List.of(orderSheetKey));
        BagPlanningResult bagPlanningResult = new BagPlanningResult(
                List.of(plannedBag),
                List.of(firstPlan, secondPlan),
                List.of(
                        trace("pack-1", firstId, bagKey, orderSheetKey),
                        trace("pack-2", secondId, bagKey, orderSheetKey)));

        MutableWorkPlanProvider provider = new MutableWorkPlanProvider();
        provider.put(correlationId, 2);
        StationProcessingCoordinator coordinator = new StationProcessingCoordinator();
        List<String> completedToteIds = new ArrayList<>();
        StationProcessingP2pToteCompletedListener completionListener =
                new StationProcessingP2pToteCompletedListener(
                        (tote, ignored) -> completedToteIds.add(tote.getId()),
                        coordinator);
        OutboundToteAllocator outboundToteAllocator = new OutboundToteAllocator(
                new PhysicalToteLifecycleLedger(),
                new DeterministicOutboundToteIdSource(),
                new OutputSheetAllocator(List.of(orderSheetKey)),
                new OutboundToteConfig(4));
        StationRoutedToteArrivalQueue stationQueue =
                new StationRoutedToteArrivalQueue(destination, 4);
        DspHeadlessP2pLineConfig config = new DspHeadlessP2pLineConfig(
                new P2pLineDefinition(new P2pLineId(lineId), destination),
                stationQueue,
                4,
                new AllowAllP2pArrivalAdmissionPolicy(),
                new P2pArrivalRouteBinding(terminal, tipper),
                tipper,
                new ContainedPackP2pTipperPayloadFactory(1f, 1f, 0f, 0f, 0f, 0f),
                coordinator,
                provider,
                bagPlanningResult,
                outboundToteAllocator,
                completionListener,
                new P2pPlaceholderDurations(
                        0d,
                        0d,
                        0d,
                        0.01d,
                        0d,
                        0.01d,
                        0.01d,
                        0d,
                        0d,
                        0d,
                        0.01d));
        SimulationWorld world = new SimulationWorld();
        DspHeadlessP2pLineRuntime runtime = new DspHeadlessP2pLineRuntimeFactory()
                .create(world, config);
        world.addController(new StationArrivalClaimController(runtime.stationProcessingBinding()));
        return new Fixture(
                runtime,
                provider,
                coordinator,
                destination,
                terminal,
                firstPlan,
                secondPlan,
                correlationId,
                completedToteIds);
    }

    private static PlannedPackTrace trace(
            String packId,
            PhysicalToteId inputToteId,
            BagKey bagKey,
            OrderSheetKey orderSheetKey) {
        return new PlannedPackTrace(
                packId,
                new PackSourceProvenance(
                        orderSheetKey,
                        "line-1",
                        "product-1",
                        "SC-1",
                        "pharmacy-1",
                        "patient-1",
                        bagKey.prescriptionId()),
                inputToteId,
                orderSheetKey,
                bagKey);
    }

    private static RouteSegment segment(String label, float startX, float endX) {
        return new RouteSegment(
                label,
                new LinearSegment3(
                        new Vec3(startX, 0f, 0f),
                        new Vec3(endX, 0f, 0f),
                        false));
    }

    private static void advanceUntil(
            DspHeadlessP2pLineRuntime runtime,
            BooleanSupplier condition) {
        for (int step = 0; step < 2_000; step++) {
            if (condition.getAsBoolean()) {
                return;
            }
            runtime.update(UPDATE_SECONDS);
        }
        throw new AssertionError("headless P2P line did not reach expected state");
    }

    private record Fixture(
            DspHeadlessP2pLineRuntime runtime,
            MutableWorkPlanProvider provider,
            StationProcessingCoordinator coordinator,
            OperationalRouteDestination destination,
            RouteSegment terminal,
            ToteLoadPlan firstPlan,
            ToteLoadPlan secondPlan,
            String correlationId,
            List<String> completedToteIds) {

        private RoutedPhysicalTote enqueue(String toteId, ToteLoadPlan plan) {
            RoutedPhysicalTote routed = routedTote(toteId, destination, terminal, plan);
            runtime.stationArrivalQueue().enqueue(routed);
            runtime.simulationWorld().addTrackableObject(routed.tote());
            return routed;
        }

        private String laterCorrelation() {
            return "rx-later/bag-1";
        }

        private static RoutedPhysicalTote routedTote(
                String toteId,
                OperationalRouteDestination destination,
                RouteSegment terminal,
                ToteLoadPlan plan) {
            PhysicalToteId physicalToteId = new PhysicalToteId(toteId);
            OperationalPhysicalToteIdentity identity = new OperationalPhysicalToteIdentity(
                    OperationalPhysicalToteSource.OSR,
                    physicalToteId,
                    new OrderSheetKey("order-" + toteId, 1),
                    OrderType.FULL_PACK,
                    "SC-1",
                    PhysicalToteRole.INBOUND_PACK,
                    1);
            OperationalPhysicalToteReleaseRequest releaseRequest =
                    new OperationalPhysicalToteReleaseRequest(
                            identity,
                            List.of("pharmacy-1"),
                            Duration.ZERO,
                            Optional.empty());
            OperationalRouteLaunchRequest launchRequest = new OperationalRouteLaunchRequest(
                    releaseRequest,
                    destination);
            RenderableObject renderable = RenderableObject.create(
                    toteId,
                    null,
                    anchorMesh(),
                    new Mat4.ObjectTransformation(
                            0f, 0f, 0f, 0f, 0f, 0f, new Mat4()),
                    triangleIndex -> 0,
                    false);
            Tote tote = new Tote(
                    toteId,
                    new RouteFollower(toteId, terminal, terminal.length(), 1.5d),
                    renderable,
                    new Vec3(),
                    0f);
            tote.setInteractionMode(Tote.ToteMotionState.HELD);
            tote.snapToRouteDistance(terminal.length());
            return new RoutedPhysicalTote(launchRequest, plan, tote, renderable);
        }

        private static Mesh anchorMesh() {
            return new Mesh(
                    new Vec4[] {
                            new Vec4(0f, 0f, 0f, 1f),
                            new Vec4(0f, 0f, 0f, 1f),
                            new Vec4(0f, 0f, 0f, 1f)
                    },
                    new int[][] {{0, 1, 2}},
                    "anchor");
        }
    }

    private static final class MutableWorkPlanProvider implements ToteToBagWorkPlanProvider {
        private final java.util.LinkedHashMap<String, Integer> expected = new java.util.LinkedHashMap<>();

        private void put(String correlationId, int expectedPackCount) {
            expected.put(correlationId, expectedPackCount);
        }

        @Override
        public OptionalInt expectedPackCount(String correlationId) {
            Integer count = expected.get(correlationId);
            return count == null ? OptionalInt.empty() : OptionalInt.of(count);
        }

        @Override
        public Set<String> expectedCorrelationIds() {
            return Set.copyOf(expected.keySet());
        }
    }
}
