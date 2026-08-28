package online.davisfamily.warehouse.sim.dsp.station.processing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.behaviour.routing.RouteFollower;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.matrices.Vec4;
import online.davisfamily.threedee.model.Mesh;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptedLineStore;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingArea;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingAreaController;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingBench;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingBenchId;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingBenchState;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingStationProcessingController;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingStationProcessingTarget;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingStorageMap;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingVisit;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingVisitFactory;
import online.davisfamily.warehouse.sim.dsp.adapting.DefaultCollectedPackPlanFactory;
import online.davisfamily.warehouse.sim.dsp.adapting.MapBackedToteLoadPlanRegistry;
import online.davisfamily.warehouse.sim.dsp.av02.Av02AllocatedTote;
import online.davisfamily.warehouse.sim.dsp.av02.Av02AllocationConfig;
import online.davisfamily.warehouse.sim.dsp.av02.Av02InventorySnapshot;
import online.davisfamily.warehouse.sim.dsp.av02.Av02PhysicalToteInventory;
import online.davisfamily.warehouse.sim.dsp.bagging.DspPackPlanFactory;
import online.davisfamily.warehouse.sim.dsp.bagging.PackProvenanceRegistry;
import online.davisfamily.warehouse.sim.dsp.bagging.PackProvenanceSnapshot;
import online.davisfamily.warehouse.sim.dsp.lifecycle.Av02ToteLifecycleController;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteLifecycleController;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifestCatalog;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteAssignmentEndReason;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleLedger;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleSnapshot;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleState;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRole;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRecord;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.ProductMasterRecord;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.OperationalPhysicalToteReleaseRequest;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteIdentity;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteSource;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteLaunchRequest;
import online.davisfamily.warehouse.sim.dsp.outbound.DeterministicOutboundToteIdSource;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundAllocationSnapshot;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteAllocator;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteConfig;
import online.davisfamily.warehouse.sim.dsp.outbound.OutputSheetAllocator;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.p2p.arrival.ContainedPackP2pTipperPayloadFactory;
import online.davisfamily.warehouse.sim.dsp.p2p.arrival.P2pArrivalRouteBinding;
import online.davisfamily.warehouse.sim.dsp.p2p.arrival.P2pStationProcessingTarget;
import online.davisfamily.warehouse.sim.dsp.p2p.arrival.P2pTipperArrivalTarget;
import online.davisfamily.warehouse.sim.dsp.p2p.arrival.StationProcessingP2pToteCompletedListener;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.InboundLifecycleP2pToteCompletedListener;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.OperationalLifecycleP2pToteCompletedListener;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineActivitySnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineDefinition;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineLeaseRegistry;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pPhysicalToteAssignment;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.StickyP2pArrivalAdmissionPolicy;
import online.davisfamily.warehouse.sim.dsp.runtime.DspSchedulerRuntimeState;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyArea;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyAreaController;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyAreaSnapshot;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyStationProcessingController;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyStationProcessingTarget;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyVisitFactory;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyAreaConfig;
import online.davisfamily.warehouse.sim.machine.queue.MachineWaitQueueSnapshot;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperInputQueue;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperInputQueueController;
import online.davisfamily.warehouse.sim.totebag.control.TipperDownstreamFlow;
import online.davisfamily.warehouse.sim.totebag.control.ToteTrackTipperFlowController;
import online.davisfamily.warehouse.sim.totebag.machine.TippingMachine;
import online.davisfamily.warehouse.sim.totebag.pack.Pack;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.bag.Bag;
import online.davisfamily.warehouse.sim.totebag.handoff.StoredBagReceiver;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;
import online.davisfamily.warehouse.sim.dsp.transport.routing.StationRoutedToteArrivalQueue;
import online.davisfamily.warehouse.sim.dsp.transport.routing.StationRoutedToteArrivalQueueSnapshot;

/**
 * Cross-boundary production scenarios for the generic station-processing runtime.
 *
 * <p>The scenarios intentionally stop at the station boundary.  No route continuation or
 * transport republisher is installed in this class.</p>
 */
class DspStationProcessingBoundaryScenarioTest {


    @Test
    void shouldClaimOnlyAfterExactStationAcceptance() {
        NotionalToteOrder order = order("capacity-head", OrderType.ADAPTED,
                DspOrderLineType.ADAPTED);
        ScenarioFixture fixture = ScenarioFixture.create(List.of(order), List.of());
        fixture.configureAdapting(10d, 0, "bench-1", "bench-2");
        OperationalRouteDestination destination = adaptingDestination("bench-1");
        RoutedPhysicalTote head = fixture.addAdapting(order, "capacity-head-physical", destination,
                OperationalPhysicalToteSource.OSR);
        AdaptingVisit blocker = new AdaptingVisitFactory().create(
                new PhysicalToteId("blocker-physical"), order);
        fixture.adaptingArea.submitVisitTo(new AdaptingBenchId("bench-1"), blocker);
        fixture.adaptingArea.bench(new AdaptingBenchId("bench-1")).startProcessing();
        fixture.enqueue(head);
        fixture.compose();

        BoundaryState beforeBlocked = fixture.boundaryState();
        fixture.world.update(0d);
        assertSame(head, fixture.queue(destination).peek().orElseThrow());
        assertEquals(List.of(head.physicalToteId()),
                fixture.queue(destination).snapshot().entries().stream()
                        .map(entry -> entry.physicalToteId()).toList());
        assertTrue(fixture.coordinator.snapshot().activeClaims().isEmpty());
        assertTrue(fixture.coordinator.pendingDispositions().isEmpty());

        // The generic binding itself rejects a destination mismatch before a queue can be
        // composed. This is the one scenario-level wrong-destination case; field-by-field target
        // identity failures remain covered by the focused station tests.
        StationRoutedToteArrivalQueue wrongDestinationQueue =
                new StationRoutedToteArrivalQueue(adaptingDestination("bench-2"), 1);
        AdaptingStationProcessingTarget wrongDestinationTarget =
                new AdaptingStationProcessingTarget(
                        destination, fixture.orderCatalog, fixture.registry,
                        new AdaptingVisitFactory(), fixture.adaptingArea, fixture.coordinator);
        assertThrows(IllegalArgumentException.class,
                () -> new StationProcessingBinding(wrongDestinationQueue, wrongDestinationTarget));
        assertEquals(beforeBlocked, fixture.boundaryState());
        assertEquals(AdaptingBenchState.PROCESSING_STORE,
                fixture.adaptingArea.bench(new AdaptingBenchId("bench-1")).state());
        assertEquals(AdaptingBenchState.IDLE,
                fixture.adaptingArea.bench(new AdaptingBenchId("bench-2")).state());

        fixture.adaptingArea.bench(new AdaptingBenchId("bench-1")).tick(10d);
        fixture.adaptingAreaController.applyBenchCompletion(new AdaptingBenchId("bench-1"));
        fixture.world.update(0d);

        assertTrue(fixture.queue(destination).snapshot().entries().isEmpty());
        StationProcessingClaim claim = fixture.coordinator.requireActiveClaim(head.physicalToteId());
        assertSame(head, claim.routedTote());
        assertEquals(destination, claim.destination());
        assertTrue(fixture.coordinator.pendingDispositions().isEmpty());

        // A separate P2P line proves candidate-specific pinned-assignment admission.
        NotionalToteOrder p2pOrder = order("assignment-head", OrderType.FULL_PACK,
                DspOrderLineType.FULL_PACK);
        ScenarioFixture p2p = ScenarioFixture.create(List.of(p2pOrder), List.of());
        OperationalRouteDestination p2pDestination = p2pDestination("p2p-claim");
        P2pLineDefinition definition = new P2pLineDefinition(
                new P2pLineId("line-claim"), p2pDestination);
        P2pLineLeaseRegistry leaseRegistry = new P2pLineLeaseRegistry(List.of(definition));
        P2pPhysicalToteAssignment assignment = assignment(
                "assignment-head-physical", "SC-1", definition);
        leaseRegistry.acquireLease(definition.lineId(), "SC-1", P2pLineActivitySnapshot.idle());
        leaseRegistry.commitAssignment(assignment);
        leaseRegistry.releaseLease(definition.lineId(), "SC-1", P2pLineActivitySnapshot.idle());
        leaseRegistry.acquireLease(definition.lineId(), "SC-OTHER", P2pLineActivitySnapshot.idle());
        P2pRoute route = p2p.configureP2p(p2pOrder, "assignment-head-physical",
                p2pDestination, assignment, leaseRegistry, false);
        p2p.enqueue(route.routedTote());
        p2p.compose();
        BoundaryState p2pBefore = p2p.boundaryState();
        p2p.world.update(0d);
        assertSame(route.routedTote(), p2p.queue(p2pDestination).peek().orElseThrow());
        assertTrue(p2p.coordinator.snapshot().activeClaims().isEmpty());
        assertTrue(p2p.coordinator.pendingDispositions().isEmpty());
        assertEquals(p2pBefore, p2p.boundaryState());

        leaseRegistry.releaseLease(definition.lineId(), "SC-OTHER", P2pLineActivitySnapshot.idle());
        leaseRegistry.acquireLease(definition.lineId(), "SC-1", P2pLineActivitySnapshot.idle());
        p2p.world.update(0d);
        assertTrue(p2p.queue(p2pDestination).snapshot().entries().isEmpty());
        assertSame(route.routedTote(),
                p2p.coordinator.requireActiveClaim(route.routedTote().physicalToteId()).routedTote());
        assertTrue(p2p.coordinator.pendingDispositions().isEmpty());
    }

    @Test
    void shouldPreserveReplacementPlanAtThirdPartyAndAdaptingCollectDispositions() {
        NotionalToteOrder thirdOrder = order("mixed-third", OrderType.FULL_PACK,
                DspOrderLineType.FULL_PACK);
        NotionalToteOrder collectOrder = order("mixed-collect", OrderType.EMPTY,
                DspOrderLineType.ADAPTED);
        ScenarioFixture fixture = ScenarioFixture.create(List.of(thirdOrder, collectOrder), List.of());
        fixture.configureThirdParty(2d);
        fixture.configureAdapting(1d, 0, "bench-collect");
        RoutedPhysicalTote third = fixture.addThirdParty(thirdOrder, "mixed-third-physical",
                thirdDestination("third-party-mixed"), OperationalPhysicalToteSource.OSR);
        RoutedPhysicalTote collect = fixture.addAdapting(collectOrder, "mixed-collect-physical",
                adaptingDestination("bench-collect"), OperationalPhysicalToteSource.AV02);
        fixture.adaptedStore.stage(collectOrder.items().getFirst(),
                new OrderSheetKey("source-mixed-collect", 1), "SC-1");
        fixture.enqueue(third);
        fixture.enqueue(collect);
        fixture.compose();
        ToteLoadPlan thirdOriginal = third.loadPlan();
        ToteLoadPlan collectOriginal = collect.loadPlan();

        fixture.world.update(0d);
        assertEquals(List.of(collect.physicalToteId(), third.physicalToteId()),
                fixture.coordinator.snapshot().activeClaims().stream()
                        .map(StationProcessingSnapshot.ActiveClaim::physicalToteId).toList());
        fixture.world.update(1d);

        StationProcessingDisposition collectDisposition = fixture.coordinator.pendingDispositions()
                .stream().filter(item -> item.physicalToteId().equals(collect.physicalToteId()))
                .findFirst().orElseThrow();
        ToteLoadPlan collectReplacement = fixture.registry.getLoadPlanFor(collect.physicalToteId());
        assertEquals(StationProcessingDispositionType.CONTINUE, collectDisposition.type());
        assertSame(collectReplacement, collectDisposition.currentLoadPlan());
        assertNotSame(collectOriginal, collectReplacement);
        assertOriginalIdentity(collect, collectDisposition);
        assertEquals(Tote.ToteMotionState.HELD, collect.tote().getInteractionMode());
        assertTrue(collect.renderable().isVisible());

        fixture.world.update(1d);
        StationProcessingDisposition thirdDisposition = fixture.coordinator.pendingDispositions()
                .stream().filter(item -> item.physicalToteId().equals(third.physicalToteId()))
                .findFirst().orElseThrow();
        ToteLoadPlan thirdReplacement = fixture.registry.getLoadPlanFor(third.physicalToteId());
        assertEquals(StationProcessingDispositionType.CONTINUE, thirdDisposition.type());
        assertSame(thirdReplacement, thirdDisposition.currentLoadPlan());
        assertNotSame(thirdOriginal, thirdReplacement);
        assertOriginalIdentity(third, thirdDisposition);
        assertEquals(Tote.ToteMotionState.HELD, third.tote().getInteractionMode());
        assertTrue(third.renderable().isVisible());
        assertEquals(List.of(collect.physicalToteId(), third.physicalToteId()),
                fixture.coordinator.pendingDispositions().stream()
                        .map(StationProcessingDisposition::physicalToteId).toList());
        assertTrue(third.renderable().isVisible());
        assertTrue(collect.renderable().isVisible());
        assertSame(third.tote(), thirdDisposition.claim().routedTote().tote());
        assertSame(collect.tote(), collectDisposition.claim().routedTote().tote());
        assertTrue(fixture.allStationQueuesEmpty());
        assertEquals(0, fixture.outboundAllocator.snapshot().allocatedBags().size());

        BoundaryState afterCompletions = fixture.boundaryState();
        fixture.world.update(1d);
        assertSame(collectDisposition, fixture.coordinator.pendingDispositions().getFirst());
        assertSame(thirdDisposition, fixture.coordinator.pendingDispositions().get(1));
        assertSame(collectReplacement, fixture.registry.getLoadPlanFor(collect.physicalToteId()));
        assertSame(thirdReplacement, fixture.registry.getLoadPlanFor(third.physicalToteId()));
        assertEquals(afterCompletions, fixture.boundaryState());
    }

    @Test
    void shouldConsumeAdaptedStoreWithoutContinuingInboundTote() {
        NotionalToteOrder order = order("store-boundary", OrderType.ADAPTED,
                DspOrderLineType.ADAPTED);
        PhysicalToteId physicalId = new PhysicalToteId("store-boundary-physical");
        InboundToteManifest manifest = manifest(order, physicalId);
        ScenarioFixture fixture = ScenarioFixture.create(List.of(order), List.of(manifest));
        fixture.configureAdapting(1d, 0, "bench-store");
        RoutedPhysicalTote routed = fixture.addAdapting(order, physicalId.value(),
                adaptingDestination("bench-store"), OperationalPhysicalToteSource.OSR);
        fixture.inboundLifecycle.activate(physicalId, Duration.ZERO);
        fixture.inboundLifecycle.advanceToPreP2p(physicalId, Duration.ZERO);
        fixture.enqueue(routed);
        fixture.compose();

        fixture.world.update(0d);
        assertSame(routed, fixture.coordinator.requireActiveClaim(physicalId).routedTote());
        assertTrue(fixture.coordinator.pendingDispositions().isEmpty());
        BoundaryState beforeCompletion = fixture.boundaryState();

        fixture.world.update(1d);
        StationProcessingDisposition disposition = fixture.coordinator.peekDisposition().orElseThrow();
        assertEquals(StationProcessingDispositionType.CONSUME, disposition.type());
        assertSame(routed.loadPlan(), disposition.currentLoadPlan());
        assertSame(routed, disposition.claim().routedTote());
        assertEquals(PhysicalToteLifecycleState.CONSUMED_AT_ADAPTING,
                fixture.lifecycleLedger.tote(physicalId).orElseThrow().state());
        assertEquals(PhysicalToteAssignmentEndReason.CONSUMED_AT_ADAPTING,
                fixture.lifecycleLedger.assignmentHistoryFor(order.orderSheetKey()).getLast()
                        .endReason().orElseThrow());
        assertTrue(fixture.schedulerState.snapshot().preparedLineKeys().contains(
                PreparedLineKey.forPreparedLine(order.items().getFirst())));
        assertEquals(AdaptingBenchState.IDLE,
                fixture.adaptingArea.bench(new AdaptingBenchId("bench-store")).state());
        assertFalse(routed.tote().areLidsOpen());
        assertEquals(Tote.ToteMotionState.HELD, routed.tote().getInteractionMode());
        assertFalse(routed.renderable().isVisible());
        assertTrue(fixture.allStationQueuesEmpty());
        assertTrue(fixture.p2pInputQueues.isEmpty());
        assertEquals(0, fixture.outboundAllocator.snapshot().allocatedBags().size());
        assertEquals(0, fixture.bagReceiver.getReceivedBags().size());
        assertEquals(1, fixture.coordinator.pendingDispositions().size());
        assertTrue(beforeCompletion.coordinator().activeClaims().stream()
                .anyMatch(item -> item.physicalToteId().equals(physicalId)));

        BoundaryState terminal = fixture.boundaryState();
        fixture.world.update(1d);
        fixture.world.update(1d);
        assertEquals(terminal, fixture.boundaryState());
    }

    @Test
    void shouldCompleteP2pOnlyAtTipperCompletion() {
        NotionalToteOrder order = order("p2p-empty-boundary", OrderType.EMPTY,
                DspOrderLineType.ADAPTED);
        PhysicalToteId physicalId = new PhysicalToteId("p2p-empty-boundary-physical");
        ScenarioFixture fixture = ScenarioFixture.create(List.of(order), List.of());
        P2pLineDefinition definition = new P2pLineDefinition(
                new P2pLineId("line-p2p-boundary"), p2pDestination("p2p-boundary"));
        P2pLineLeaseRegistry leases = new P2pLineLeaseRegistry(List.of(definition));
        leases.acquireLease(definition.lineId(), "SC-1", P2pLineActivitySnapshot.idle());
        P2pPhysicalToteAssignment assignment = assignment(physicalId.value(), "SC-1", definition);
        leases.commitAssignment(assignment);
        Av02AllocatedTote allocated = fixture.allocateAv02(order, physicalId);
        fixture.av02Inventory.store(allocated);
        fixture.av02Inventory.recordDeparture(physicalId);
        P2pRoute p2p = fixture.configureP2p(order, physicalId.value(), definition.destination(),
                assignment, leases, true);
        fixture.enqueue(p2p.routedTote());
        fixture.compose();

        fixture.world.update(0d);
        assertSame(p2p.routedTote(), fixture.coordinator.requireActiveClaim(physicalId).routedTote());
        assertTrue(fixture.coordinator.pendingDispositions().isEmpty());
        assertEquals(PhysicalToteLifecycleState.ACTIVE_PRE_P2P,
                fixture.lifecycleLedger.tote(physicalId).orElseThrow().state());
        assertTrue(fixture.p2pInputQueues.get(definition.destination()).snapshot().toteIds()
                .contains(physicalId.value()));

        BoundaryState beforeWrongCallback = fixture.boundaryState();
        Tote wrongInstance = routedTote(order, physicalId, definition.destination(),
                OperationalPhysicalToteSource.AV02, assignment, segment("wrong-instance", 0f, 1f),
                new ToteLoadPlan(physicalId, List.of())).tote();
        assertThrows(IllegalStateException.class, () -> fixture.p2pCompletionListener
                .onToteCompleted(wrongInstance, context(1d)));
        assertEquals(beforeWrongCallback, fixture.boundaryState());

        // The real tipper flow invokes the production completion listener for the exact tote.
        fixture.driveUntilDisposition(physicalId);
        StationProcessingDisposition disposition = fixture.coordinator.peekDisposition().orElseThrow();
        assertEquals(StationProcessingDispositionType.CONSUME, disposition.type());
        assertSame(p2p.routedTote(), disposition.claim().routedTote());
        assertEquals(OperationalPhysicalToteSource.AV02,
                p2p.routedTote().launchRequest().source());
        assertSame(assignment, p2p.routedTote().p2pAssignment().orElseThrow());
        assertSame(p2p.routedTote().tote(), disposition.claim().routedTote().tote());
        assertSame(p2p.routedTote().renderable(), disposition.claim().routedTote().renderable());
        assertTrue(fixture.allStationQueuesEmpty());
        assertTrue(fixture.p2pInputQueues.get(definition.destination()).snapshot().toteIds().isEmpty());
        assertEquals(PhysicalToteLifecycleState.CONSUMED_AT_P2P,
                fixture.lifecycleLedger.tote(physicalId).orElseThrow().state());
        assertEquals(PhysicalToteAssignmentEndReason.CONSUMED_AT_P2P,
                fixture.lifecycleLedger.assignmentHistoryFor(order.orderSheetKey()).getFirst()
                        .endReason().orElseThrow());
        assertFalse(p2p.routedTote().tote().areLidsOpen());
        assertEquals(Tote.ToteMotionState.HELD, p2p.routedTote().tote().getInteractionMode());
        assertFalse(p2p.routedTote().renderable().isVisible());
        assertEquals(0, fixture.outboundAllocator.snapshot().allocatedBags().size());
        assertEquals(0, fixture.bagReceiver.getReceivedBags().size());
        assertEquals(1, fixture.av02Inventory.snapshot().departedTotes().size());

        BoundaryState terminal = fixture.boundaryState();
        assertThrows(IllegalStateException.class, () -> fixture.p2pCompletionListener
                .onToteCompleted(p2p.routedTote().tote(), context(3d)));
        fixture.world.update(1d);
        assertEquals(terminal, fixture.boundaryState());
    }

    @Test
    void shouldKeepDispositionFifoAcrossStationTypes() {
        NotionalToteOrder thirdOrder = order("fifo-third", OrderType.FULL_PACK,
                DspOrderLineType.FULL_PACK);
        NotionalToteOrder storeOrder = order("fifo-store", OrderType.ADAPTED,
                DspOrderLineType.ADAPTED);
        NotionalToteOrder p2pOrder = order("fifo-p2p", OrderType.EMPTY,
                DspOrderLineType.ADAPTED);
        PhysicalToteId storeId = new PhysicalToteId("fifo-store-physical");
        InboundToteManifest storeManifest = manifest(storeOrder, storeId);
        ScenarioFixture fixture = ScenarioFixture.create(
                List.of(thirdOrder, storeOrder, p2pOrder), List.of(storeManifest));
        fixture.configureThirdParty(1d);
        fixture.configureAdapting(10d, 0, "fifo-store-bench");
        RoutedPhysicalTote third = fixture.addThirdParty(thirdOrder, "fifo-third-physical",
                thirdDestination("fifo-third"), OperationalPhysicalToteSource.OSR);
        RoutedPhysicalTote store = fixture.addAdapting(storeOrder, storeId.value(),
                adaptingDestination("fifo-store-bench"), OperationalPhysicalToteSource.OSR);
        fixture.inboundLifecycle.activate(storeId, Duration.ZERO);
        fixture.inboundLifecycle.advanceToPreP2p(storeId, Duration.ZERO);

        P2pLineDefinition definition = new P2pLineDefinition(
                new P2pLineId("fifo-p2p-line"), p2pDestination("fifo-p2p"));
        P2pLineLeaseRegistry leases = new P2pLineLeaseRegistry(List.of(definition));
        leases.acquireLease(definition.lineId(), "SC-1", P2pLineActivitySnapshot.idle());
        PhysicalToteId p2pId = new PhysicalToteId("fifo-p2p-physical");
        P2pPhysicalToteAssignment assignment = assignment(p2pId.value(), "SC-1", definition);
        leases.commitAssignment(assignment);
        Av02AllocatedTote allocated = fixture.allocateAv02(p2pOrder, p2pId);
        fixture.av02Inventory.store(allocated);
        fixture.av02Inventory.recordDeparture(p2pId);
        P2pRoute p2p = fixture.configureP2p(p2pOrder, p2pId.value(), definition.destination(),
                assignment, leases, true);

        fixture.enqueue(store);
        fixture.enqueue(p2p.routedTote());
        fixture.enqueue(third);
        fixture.compose();

        fixture.world.update(0d);
        assertEquals(List.of(storeId, p2pId, third.physicalToteId()),
                fixture.coordinator.snapshot().activeClaims().stream()
                        .map(StationProcessingSnapshot.ActiveClaim::physicalToteId).toList());

        fixture.world.update(1d); // Third Party completes first: CONTINUE.
        StationProcessingDisposition thirdDisposition = fixture.coordinator.peekDisposition().orElseThrow();
        assertEquals(StationProcessingDispositionType.CONTINUE, thirdDisposition.type());
        fixture.driveUntilDisposition(p2pId); // The real tipper callback publishes P2P CONSUME.
        fixture.driveUntilDisposition(storeId); // STORE completes last: CONSUME.

        List<StationProcessingDisposition> pending = fixture.coordinator.pendingDispositions();
        assertEquals(List.of(third.physicalToteId(), p2pId, storeId),
                pending.stream().map(StationProcessingDisposition::physicalToteId).toList());
        assertEquals(List.of(StationProcessingDispositionType.CONTINUE,
                StationProcessingDispositionType.CONSUME,
                StationProcessingDispositionType.CONSUME),
                pending.stream().map(StationProcessingDisposition::type).toList());
        assertSame(third, pending.get(0).claim().routedTote());
        assertSame(p2p.routedTote(), pending.get(1).claim().routedTote());
        assertSame(store, pending.get(2).claim().routedTote());
        assertFalse(p2p.routedTote().renderable().isVisible());
        assertFalse(store.renderable().isVisible());

        BoundaryState beforeRepeat = fixture.boundaryState();
        fixture.world.update(1d);
        assertEquals(beforeRepeat, fixture.boundaryState());
        for (StationProcessingDisposition expected : pending) {
            StationProcessingDisposition dequeued = fixture.coordinator.dequeueDisposition().orElseThrow();
            assertSame(expected, dequeued);
            assertSame(expected.currentLoadPlan(),
                    fixture.registry.getLoadPlanFor(expected.physicalToteId()));
        }
        assertTrue(fixture.coordinator.pendingDispositions().isEmpty());
        assertEquals(3, fixture.coordinator.snapshot().completedCount());

        assertEquals(storeId, fixture.coordinator.snapshot().lastCompletedPhysicalToteId().orElseThrow());
        assertEquals(StationProcessingDispositionType.CONSUME,
                fixture.coordinator.snapshot().lastCompletedType().orElseThrow());
        BoundaryState beforeReclaim = fixture.boundaryState();
        for (StationProcessingDisposition completed : pending) {
            if (completed.type() == StationProcessingDispositionType.CONTINUE) {
                assertDoesNotThrow(() -> fixture.coordinator.validateCanClaim(
                        completed.claim().routedTote(), Duration.ofSeconds(11)));
            } else {
                assertThrows(IllegalStateException.class,
                        () -> fixture.coordinator.claim(
                                completed.claim().routedTote(), Duration.ofSeconds(11)));
            }
            assertEquals(beforeReclaim, fixture.boundaryState());
            if (completed.type() == StationProcessingDispositionType.CONSUME) {
                assertThrows(IllegalStateException.class,
                        () -> fixture.coordinator.complete(
                                completed.physicalToteId(), completed.type(),
                                completed.currentLoadPlan(), Duration.ofSeconds(11)));
            }
            assertEquals(beforeReclaim, fixture.boundaryState());
        }
    }

    private static NotionalToteOrder order(
            String orderId,
            OrderType type,
            DspOrderLineType lineType) {
        return new NotionalToteOrder(
                orderId,
                "notional-" + orderId,
                "SC-1",
                1,
                type,
                List.of(new DspOrderItem(
                        "line-" + orderId,
                        "product-1",
                        1,
                        "pharmacy-1",
                        "patient-" + orderId,
                        "prescription-" + orderId,
                        lineType,
                        orderId,
                        1,
                        0)),
                0,
                1);
    }

    private static InboundToteManifest manifest(NotionalToteOrder order, PhysicalToteId physicalId) {
        return new InboundToteManifest(
                physicalId,
                order.orderSheetKey(),
                order.orderType(),
                order.serviceCentreId(),
                order.items(),
                0);
    }

    private static OperationalRouteDestination adaptingDestination(String id) {
        return new OperationalRouteDestination(StationType.ADAPTING, id);
    }

    private static OperationalRouteDestination thirdDestination(String id) {
        return new OperationalRouteDestination(StationType.THIRD_PARTY, id);
    }

    private static OperationalRouteDestination p2pDestination(String id) {
        return new OperationalRouteDestination(StationType.P2P, id);
    }

    private static P2pPhysicalToteAssignment assignment(
            String physicalId,
            String serviceCentre,
            P2pLineDefinition definition) {
        return new P2pPhysicalToteAssignment(
                new PhysicalToteId(physicalId), serviceCentre,
                definition.lineId(), definition.destination());
    }

    private static void assertOriginalIdentity(
            RoutedPhysicalTote expected,
            StationProcessingDisposition disposition) {
        RoutedPhysicalTote actual = disposition.claim().routedTote();
        assertSame(expected, actual);
        assertSame(expected.tote(), actual.tote());
        assertSame(expected.renderable(), actual.renderable());
        assertSame(expected.tote().getRouteFollower(), actual.tote().getRouteFollower());
        assertSame(expected.launchRequest(), actual.launchRequest());
        assertSame(expected.launchRequest().identity(), actual.launchRequest().identity());
        assertEquals(expected.physicalToteId(), actual.physicalToteId());
        assertEquals(expected.destination(), actual.destination());
        assertEquals(expected.p2pAssignment(), actual.p2pAssignment());
    }

    private static RouteSegment segment(String id, float start, float end) {
        return new RouteSegment(id, new online.davisfamily.threedee.path.LinearSegment3(
                new Vec3(start, 0f, 0f), new Vec3(end, 0f, 0f), false));
    }

    private static SimulationContext context(double seconds) {
        SimulationContext context = new SimulationContext();
        context.setSimulationTimeSeconds(seconds);
        return context;
    }

    private static RenderableObject renderable(String id) {
        return RenderableObject.create(
                id,
                null,
                new Mesh(new Vec4[] {
                        new Vec4(0f, 0f, 0f, 1f),
                        new Vec4(0f, 0f, 0f, 1f),
                        new Vec4(0f, 0f, 0f, 1f)},
                        new int[][] {{0, 1, 2}}, "anchor"),
                new Mat4.ObjectTransformation(0f, 0f, 0f, 0f, 0f, 0f, new Mat4()),
                ignored -> 0,
                false);
    }

    private static RoutedPhysicalTote routedTote(
            NotionalToteOrder order,
            PhysicalToteId physicalId,
            OperationalRouteDestination destination,
            OperationalPhysicalToteSource source,
            P2pPhysicalToteAssignment assignment,
            RouteSegment routeSegment,
            ToteLoadPlan plan) {
        PhysicalToteRole role = source == OperationalPhysicalToteSource.AV02
                ? PhysicalToteRole.PRE_P2P : PhysicalToteRole.INBOUND_PACK;
        OperationalPhysicalToteIdentity identity = new OperationalPhysicalToteIdentity(
                source, physicalId, order.orderSheetKey(), order.orderType(),
                order.serviceCentreId(), role, 0);
        OperationalPhysicalToteReleaseRequest release = new OperationalPhysicalToteReleaseRequest(
                identity, List.of("pharmacy-1"), Duration.ZERO,
                Optional.ofNullable(assignment));
        OperationalRouteLaunchRequest launch = new OperationalRouteLaunchRequest(release, destination);
        RenderableObject visual = renderable(physicalId.value());
        Tote tote = new Tote(physicalId.value(),
                new RouteFollower(physicalId.value(), routeSegment, 0f, 1d),
                visual, new Vec3(), 0f);
        tote.setInteractionMode(Tote.ToteMotionState.HELD);
        return new RoutedPhysicalTote(launch, plan, tote, visual);
    }

    private static TipperDownstreamFlow acceptingDownstreamFlow() {
        return new TipperDownstreamFlow() {
            @Override
            public boolean canAcceptDischargedPack(Pack pack) { return true; }
            @Override
            public void acceptDischargedPack(Pack pack) { }
            @Override
            public void update(double dtSeconds) { }
            @Override
            public boolean keepsTipperOccupied() { return false; }
        };
    }

    private record P2pRoute(
            RoutedPhysicalTote routedTote,
            RoutedPhysicalTote bootstrap,
            RouteSegment terminal,
            RouteSegment tipper,
            P2pTipperArrivalTarget target,
            boolean tipperFlowEnabled) { }

    private static final class ScenarioFixture {
        private final SimulationWorld world = new SimulationWorld();
        private final StationProcessingCoordinator coordinator = new StationProcessingCoordinator();
        private final StationProcessingOrderCatalog orderCatalog;
        private final MapBackedToteLoadPlanRegistry registry = new MapBackedToteLoadPlanRegistry();
        private final PhysicalToteLifecycleLedger lifecycleLedger = new PhysicalToteLifecycleLedger();
        private final InboundToteManifestCatalog manifestCatalog;
        private final InboundToteLifecycleController inboundLifecycle;
        private final Av02PhysicalToteInventory av02Inventory =
                new Av02PhysicalToteInventory(new Av02AllocationConfig(4));
        private final Av02ToteLifecycleController av02Lifecycle;
        private final DspSchedulerRuntimeState schedulerState = new DspSchedulerRuntimeState(
                new WarehouseSchedulerSnapshot(List.of(), Map.of(), Set.of(), Optional.empty()));
        private final AdaptedLineStore adaptedStore = new AdaptedLineStore();
        private final PackProvenanceRegistry provenanceRegistry = new PackProvenanceRegistry();
        private final OutboundToteAllocator outboundAllocator;
        private final OutputSheetAllocator outputSheetAllocator;
        private final StoredBagReceiver bagReceiver = new StoredBagReceiver("boundary-bags");
        private final List<StationProcessingBinding> bindings = new ArrayList<>();
        private final List<NotionalToteOrder> orders;
        private final Map<OperationalRouteDestination, StationRoutedToteArrivalQueue> queues =
                new LinkedHashMap<>();
        private final Map<PhysicalToteId, RoutedPhysicalTote> routedTotes = new LinkedHashMap<>();
        private final Map<OperationalRouteDestination, P2pTipperArrivalTarget> p2pTargets =
                new LinkedHashMap<>();
        private final Map<OperationalRouteDestination, TipperInputQueue> p2pInputQueues =
                new LinkedHashMap<>();
        private final Map<OperationalRouteDestination, P2pRoute> p2pRoutes =
                new LinkedHashMap<>();
        private final List<AdaptingStationProcessingTarget> adaptingTargets = new ArrayList<>();
        private final List<ThirdPartyStationProcessingTarget> thirdPartyTargets = new ArrayList<>();
        private final Set<OperationalRouteDestination> adaptingDestinations = new LinkedHashSet<>();
        private final Set<OperationalRouteDestination> thirdPartyDestinations = new LinkedHashSet<>();
        private final List<StationProcessingCompletionController> completionControllers = new ArrayList<>();
        private final List<StationProcessingP2pToteCompletedListener> p2pListeners = new ArrayList<>();
        private AdaptingArea adaptingArea;
        private AdaptingAreaController adaptingAreaController;
        private ThirdPartyArea thirdPartyArea;
        private ThirdPartyAreaController thirdPartyAreaController;
        private DspStationProcessingRuntime runtime;
        private StationProcessingP2pToteCompletedListener p2pCompletionListener;
        private long av02Ordinal;

        private ScenarioFixture(List<NotionalToteOrder> orders, List<InboundToteManifest> manifests) {
            this.orders = List.copyOf(orders);
            this.orderCatalog = new StationProcessingOrderCatalog(this.orders);
            this.manifestCatalog = new InboundToteManifestCatalog(manifests);
            this.inboundLifecycle = new InboundToteLifecycleController(
                    lifecycleLedger, manifestCatalog);
            this.av02Lifecycle = new Av02ToteLifecycleController(
                    lifecycleLedger, () -> new PhysicalToteId("av02-auto-" + (++av02Ordinal)));
            this.outputSheetAllocator = new OutputSheetAllocator(
                    this.orders.stream().map(NotionalToteOrder::orderSheetKey).toList());
            this.outboundAllocator = new OutboundToteAllocator(
                    lifecycleLedger, new DeterministicOutboundToteIdSource(),
                    outputSheetAllocator, new OutboundToteConfig(4));
        }

        private static ScenarioFixture create(
                List<NotionalToteOrder> orders,
                List<InboundToteManifest> manifests) {
            return new ScenarioFixture(orders, manifests);
        }

        private void configureAdapting(double duration, int queueCapacity, String... benchIds) {
            AdaptingStorageMap storageMap = new AdaptingStorageMap();
            List<AdaptingBenchId> ids = Arrays.stream(benchIds).map(AdaptingBenchId::new).toList();
            storageMap.configureAvailableBenches(ids);
            storageMap.assignPharmacyToBench("pharmacy-1", ids.getFirst());
            List<AdaptingBench> benches = Arrays.stream(benchIds)
                    .map(id -> new AdaptingBench(id, adaptedStore, duration)).toList();
            adaptingArea = new AdaptingArea(benches, queueCapacity, storageMap);
            adaptingAreaController = new AdaptingAreaController(
                    adaptingArea, schedulerState, registry,
                    new DefaultCollectedPackPlanFactory(
                            new PackDimensions(0.2f, 0.1f, 0.08f),
                            new DspPackPlanFactory(provenanceRegistry)));
        }

        private void configureThirdParty(double duration) {
            thirdPartyArea = new ThirdPartyArea(new ThirdPartyAreaConfig(0, 1, duration));
            thirdPartyAreaController = new ThirdPartyAreaController(
                    thirdPartyArea, registry,
                    (visit, lineWork, ordinal) -> new PackPlan(
                            "tp-pack-" + lineWork.lineReference() + "-" + ordinal,
                            visit.orderSheetKey().orderId(),
                            new PackDimensions(0.2f, 0.1f, 0.08f)));
        }

        private RoutedPhysicalTote addAdapting(
                NotionalToteOrder order,
                String physicalId,
                OperationalRouteDestination destination,
                OperationalPhysicalToteSource source) {
            PhysicalToteId id = new PhysicalToteId(physicalId);
            ToteLoadPlan plan = new ToteLoadPlan(id, List.of());
            registry.putLoadPlan(plan);
            RoutedPhysicalTote routed = routedTote(order, id, destination, source, null,
                    segment("segment-" + physicalId, 0f, 1f), plan);
            AdaptingStationProcessingTarget target = new AdaptingStationProcessingTarget(
                    destination, orderCatalog, registry, new AdaptingVisitFactory(),
                    adaptingArea, coordinator);
            StationRoutedToteArrivalQueue queue = new StationRoutedToteArrivalQueue(destination, 4);
            bindings.add(new StationProcessingBinding(queue, target));
            adaptingTargets.add(target);
            adaptingDestinations.add(destination);
            queues.put(destination, queue);
            routedTotes.put(id, routed);
            return routed;
        }

        private RoutedPhysicalTote addThirdParty(
                NotionalToteOrder order,
                String physicalId,
                OperationalRouteDestination destination,
                OperationalPhysicalToteSource source) {
            PhysicalToteId id = new PhysicalToteId(physicalId);
            ToteLoadPlan plan = new ToteLoadPlan(id, List.of());
            registry.putLoadPlan(plan);
            RoutedPhysicalTote routed = routedTote(order, id, destination, source, null,
                    segment("segment-" + physicalId, 0f, 1f), plan);
            ThirdPartyVisitFactory visitFactory = new ThirdPartyVisitFactory(
                    new online.davisfamily.warehouse.sim.dsp.routing.InMemoryProductMasterRepository(
                            List.of(new ProductMasterRecord(
                                    "product-1", "Boundary Product", Optional.of("Y74"),
                                    Optional.of(new PackDimensions(0.2f, 0.1f, 0.08f))))));
            ThirdPartyStationProcessingTarget target = new ThirdPartyStationProcessingTarget(
                    destination, orderCatalog, registry, visitFactory,
                    thirdPartyArea, coordinator);
            StationRoutedToteArrivalQueue queue = new StationRoutedToteArrivalQueue(destination, 4);
            bindings.add(new StationProcessingBinding(queue, target));
            thirdPartyTargets.add(target);
            thirdPartyDestinations.add(destination);
            queues.put(destination, queue);
            routedTotes.put(id, routed);
            return routed;
        }

        private P2pRoute configureP2p(
                NotionalToteOrder order,
                String physicalId,
                OperationalRouteDestination destination,
                P2pPhysicalToteAssignment assignment,
                P2pLineLeaseRegistry leaseRegistry,
                boolean tipperFlowEnabled) {
            PhysicalToteId id = new PhysicalToteId(physicalId);
            ToteLoadPlan plan = registry.getLoadPlanFor(id);
            if (plan == null) {
                plan = new ToteLoadPlan(id, List.of());
                registry.putLoadPlan(plan);
            }
            RouteSegment terminal = segment("terminal-" + physicalId, 0f, 1f);
            RouteSegment tipper = segment("tipper-" + physicalId, 1f, 2f);
            terminal.connectTo(tipper);
            RoutedPhysicalTote routed = routedTote(order, id, destination,
                    assignment == null ? OperationalPhysicalToteSource.OSR
                            : (order.orderType() == OrderType.EMPTY
                                    ? OperationalPhysicalToteSource.AV02
                                    : OperationalPhysicalToteSource.OSR),
                    assignment, terminal, plan);
            TipperInputQueue input = new TipperInputQueue(destination.targetId() + "-input", 1);
            P2pTipperArrivalTarget target = new P2pTipperArrivalTarget(destination, input);
            P2pStationProcessingTarget processingTarget = new P2pStationProcessingTarget(
                    new StickyP2pArrivalAdmissionPolicy(
                            leaseRegistry.definitions().getFirst(),
                            () -> leaseRegistry.snapshot(Map.of(
                                    leaseRegistry.definitions().getFirst().lineId(),
                                    P2pLineActivitySnapshot.idle()))),
                    new P2pArrivalRouteBinding(terminal, tipper),
                    new ContainedPackP2pTipperPayloadFactory(1f, 1f, 0f, 0f, 0f, 0f),
                    target, coordinator);
            StationRoutedToteArrivalQueue queue = new StationRoutedToteArrivalQueue(destination, 4);
            bindings.add(new StationProcessingBinding(queue, processingTarget));
            queues.put(destination, queue);
            p2pTargets.put(destination, target);
            p2pInputQueues.put(destination, input);
            routedTotes.put(id, routed);

            PhysicalToteId bootstrapId = new PhysicalToteId("bootstrap-" + physicalId);
            ToteLoadPlan bootstrapPlan = new ToteLoadPlan(bootstrapId, List.of());
            RoutedPhysicalTote bootstrap = routedTote(
                    order, bootstrapId, destination,
                    order.orderType() == OrderType.EMPTY
                            ? OperationalPhysicalToteSource.AV02
                            : OperationalPhysicalToteSource.OSR,
                    null, tipper, bootstrapPlan);
            bootstrap.tote().setInteractionMode(Tote.ToteMotionState.HELD);
            bootstrap.tote().getRouteFollower().setDistanceAlongSegment(0.625f);
            routedTotes.put(bootstrapId, bootstrap);

            // A real tipper listener is retained for the scenarios that complete P2P work.
            InboundLifecycleP2pToteCompletedListener inbound =
                    new InboundLifecycleP2pToteCompletedListener(inboundLifecycle);
            OperationalLifecycleP2pToteCompletedListener operational =
                    new OperationalLifecycleP2pToteCompletedListener(
                            manifestCatalog, inbound, av02Inventory, av02Lifecycle);
            p2pCompletionListener = new StationProcessingP2pToteCompletedListener(
                    operational, coordinator);
            p2pListeners.add(p2pCompletionListener);
            P2pRoute route = new P2pRoute(
                    routed, bootstrap, terminal, tipper, target, tipperFlowEnabled);
            p2pRoutes.put(destination, route);
            return route;
        }

        private Av02AllocatedTote allocateAv02(NotionalToteOrder order, PhysicalToteId id) {
            PhysicalToteRecord record = av02Lifecycle.allocateFor(order, Duration.ZERO, id);
            OperationalPhysicalToteIdentity identity = new OperationalPhysicalToteIdentity(
                    OperationalPhysicalToteSource.AV02, id, order.orderSheetKey(),
                    OrderType.EMPTY, order.serviceCentreId(), PhysicalToteRole.PRE_P2P, 0);
            return new Av02AllocatedTote(identity, record, "pharmacy-1");
        }

        private void enqueue(RoutedPhysicalTote routed) {
            StationRoutedToteArrivalQueue queue = queues.get(routed.destination());
            if (queue == null) {
                throw new IllegalStateException("No station queue for " + routed.destination());
            }
            queue.enqueue(routed);
            world.addTrackableObject(routed.tote());
        }

        private void compose() {
            if (!adaptingDestinations.isEmpty()) {
                completionControllers.add(new AdaptingStationProcessingController(
                        "adapting-boundary", adaptingDestinations, registry, adaptingArea,
                        adaptingAreaController, inboundLifecycle, coordinator));
            }
            if (!thirdPartyDestinations.isEmpty()) {
                completionControllers.add(new ThirdPartyStationProcessingController(
                        "third-party-boundary", thirdPartyDestinations, registry,
                        thirdPartyAreaController, coordinator));
            }

            // Install the real tipper input boundary where the scenario exercises physical
            // tipper completion. Other scenarios still use the same production input queue and
            // exact completion listener, but do not need an active machine to prove FIFO order.
            for (Map.Entry<OperationalRouteDestination, TipperInputQueue> entry
                    : p2pInputQueues.entrySet()) {
                OperationalRouteDestination destination = entry.getKey();
                P2pRoute route = p2pRoutes.get(destination);
                if (!route.tipperFlowEnabled()) {
                    continue;
                }
                P2pTipperArrivalTarget target = p2pTargets.get(destination);
                RoutedPhysicalTote bootstrap = route.bootstrap();
                RouteSegment tipper = route.tipper();
                TippingMachine machine = new TippingMachine(
                        destination.targetId() + "-tipper", 0d, 0d, 0d);
                world.addTrackableObject(bootstrap.tote());
                ToteTrackTipperFlowController flow = new ToteTrackTipperFlowController(
                        bootstrap.tote(),
                        toteId -> toteId.equals(bootstrap.physicalToteId().value())
                                ? bootstrap.loadPlan() : target.getLoadPlanFor(toteId),
                        tipper, 0.625f, -1.02f, machine, acceptingDownstreamFlow(), 0.01d,
                        (tote, context) -> {
                            if (tote == route.routedTote().tote()) {
                                p2pCompletionListener.onToteCompleted(tote, context);
                            }
                        });
                world.addSimObject(machine);
                world.addController(flow);
                world.addController(new TipperInputQueueController(entry.getValue(), flow));
            }
            runtime = new DspStationProcessingRuntimeFactory().create(
                    world, coordinator, bindings, completionControllers);
        }

        private void driveUntilDisposition(PhysicalToteId physicalId) {
            int safety = 0;
            while (coordinator.pendingDispositions().stream()
                    .noneMatch(disposition -> disposition.physicalToteId().equals(physicalId))) {
                world.update(0.25d);
                if (++safety > 80) {
                    throw new AssertionError("P2P tipper did not publish a disposition: " + physicalId);
                }
            }
        }

        private StationRoutedToteArrivalQueue queue(OperationalRouteDestination destination) {
            return queues.get(destination);
        }

        private boolean allStationQueuesEmpty() {
            return queues.values().stream().allMatch(queue -> queue.snapshot().entries().isEmpty());
        }

        private BoundaryState boundaryState() {
            Map<PhysicalToteId, ToteState> toteStates = new LinkedHashMap<>();
            Map<PhysicalToteId, IdentityRef<ToteLoadPlan>> planRefs = new LinkedHashMap<>();
            Map<PhysicalToteId, IdentityRef<RoutedPhysicalTote>> routedRefs = new LinkedHashMap<>();
            Map<PhysicalToteId, IdentityRef<OperationalRouteLaunchRequest>> launchRefs =
                    new LinkedHashMap<>();
            Map<PhysicalToteId, IdentityRef<OperationalPhysicalToteIdentity>> identityRefs =
                    new LinkedHashMap<>();
            Map<PhysicalToteId, Optional<P2pPhysicalToteAssignment>> assignments = new LinkedHashMap<>();
            Map<PhysicalToteId, IdentityRef<P2pPhysicalToteAssignment>> assignmentRefs =
                    new LinkedHashMap<>();
            for (Map.Entry<PhysicalToteId, RoutedPhysicalTote> entry : routedTotes.entrySet()) {
                RoutedPhysicalTote routed = entry.getValue();
                toteStates.put(entry.getKey(), new ToteState(
                        routed.tote().getInteractionMode(), routed.tote().areLidsOpen(),
                        routed.renderable().isVisible(),
                        routed.tote().getRouteFollower().getCurrentSegment(),
                        (float) routed.tote().getRouteFollower().getDistanceAlongSegment()));
                planRefs.put(entry.getKey(), IdentityRef.of(registry.getLoadPlanFor(entry.getKey())));
                routedRefs.put(entry.getKey(), IdentityRef.of(routed));
                launchRefs.put(entry.getKey(), IdentityRef.of(routed.launchRequest()));
                identityRefs.put(entry.getKey(), IdentityRef.of(routed.launchRequest().identity()));
                assignments.put(entry.getKey(), routed.p2pAssignment());
                routed.p2pAssignment().ifPresent(value ->
                        assignmentRefs.put(entry.getKey(), IdentityRef.of(value)));
            }
            Map<AdaptingBenchId, online.davisfamily.warehouse.sim.dsp.adapting.AdaptingBenchSnapshot>
                    adaptingSnapshots = new LinkedHashMap<>();
            if (adaptingArea != null) {
                for (AdaptingBenchId id : adaptingAreaIds()) {
                    adaptingSnapshots.put(id, adaptingArea.bench(id).snapshot());
                }
            }
            Map<OperationalRouteDestination, MachineWaitQueueSnapshot> tipperSnapshots =
                    new LinkedHashMap<>();
            for (Map.Entry<OperationalRouteDestination, TipperInputQueue> entry
                    : p2pInputQueues.entrySet()) {
                tipperSnapshots.put(entry.getKey(), entry.getValue().snapshot());
            }
            return new BoundaryState(
                    queues.values().stream().map(StationRoutedToteArrivalQueue::snapshot).toList(),
                    coordinator.snapshot(), planRefs, routedRefs, launchRefs, identityRefs,
                    assignments, assignmentRefs,
                    lifecycleLedger.snapshot(),
                    thirdPartyArea == null ? Optional.empty() : Optional.of(thirdPartyArea.snapshot()),
                    thirdPartyAreaController == null
                            ? Set.of()
                            : thirdPartyAreaController.completedLineReferences(),
                    thirdPartyCompletionRefs(),
                    adaptingSnapshots,
                    adaptedStore.snapshot(), provenanceRegistry.snapshot(), schedulerState.snapshot(),
                    tipperSnapshots,
                    toteStates, av02Inventory.snapshot(), outboundAllocator.snapshot(),
                    List.copyOf(bagReceiver.getReceivedBags()));
        }

        private Map<PhysicalToteId, IdentityRef<Object>> thirdPartyCompletionRefs() {
            Map<PhysicalToteId, IdentityRef<Object>> refs = new LinkedHashMap<>();
            if (thirdPartyAreaController == null) {
                return refs;
            }
            for (PhysicalToteId physicalId : routedTotes.keySet()) {
                thirdPartyAreaController.completionForTote(physicalId)
                        .ifPresent(completion -> refs.put(physicalId, IdentityRef.of(completion)));
            }
            return refs;
        }

        private List<AdaptingBenchId> adaptingAreaIds() {
            return adaptingArea == null ? List.of() : adaptingDestinations.stream()
                    .map(destination -> new AdaptingBenchId(destination.targetId())).distinct().sorted().toList();
        }
    }

    private record ToteState(
            Tote.ToteMotionState motion,
            boolean lidsOpen,
            boolean visible,
            RouteSegment routeSegment,
            float distanceAlongSegment) { }

    private record BoundaryState(
            List<StationRoutedToteArrivalQueueSnapshot> stationFifos,
            StationProcessingSnapshot coordinator,
            Map<PhysicalToteId, IdentityRef<ToteLoadPlan>> planRefs,
            Map<PhysicalToteId, IdentityRef<RoutedPhysicalTote>> routedRefs,
            Map<PhysicalToteId, IdentityRef<OperationalRouteLaunchRequest>> launchRefs,
            Map<PhysicalToteId, IdentityRef<OperationalPhysicalToteIdentity>> identityRefs,
            Map<PhysicalToteId, Optional<P2pPhysicalToteAssignment>> assignments,
            Map<PhysicalToteId, IdentityRef<P2pPhysicalToteAssignment>> assignmentRefs,
            PhysicalToteLifecycleSnapshot lifecycle,
            Optional<ThirdPartyAreaSnapshot> thirdParty,
            Set<String> thirdPartyCompletedLines,
            Map<PhysicalToteId, IdentityRef<Object>> thirdPartyCompletions,
            Map<AdaptingBenchId, online.davisfamily.warehouse.sim.dsp.adapting.AdaptingBenchSnapshot>
                    adaptingBenches,
            online.davisfamily.warehouse.sim.dsp.adapting.AdaptedLineStoreSnapshot adaptedStore,
            PackProvenanceSnapshot provenance,
            WarehouseSchedulerSnapshot preparedReadiness,
            Map<OperationalRouteDestination, MachineWaitQueueSnapshot> tipperInputs,
            Map<PhysicalToteId, ToteState> totes,
            Av02InventorySnapshot av02Inventory,
            OutboundAllocationSnapshot outbound,
            List<Bag> bags) { }

    private static final class IdentityRef<T> {
        private final T reference;
        private IdentityRef(T reference) { this.reference = reference; }
        private static <T> IdentityRef<T> of(T reference) { return new IdentityRef<>(reference); }
        @Override
        public boolean equals(Object other) {
            return other instanceof IdentityRef<?> value && value.reference == reference;
        }
        @Override
        public int hashCode() { return System.identityHashCode(reference); }
    }
}
