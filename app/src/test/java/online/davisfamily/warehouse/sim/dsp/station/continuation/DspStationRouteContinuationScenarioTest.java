package online.davisfamily.warehouse.sim.dsp.station.continuation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

import online.davisfamily.threedee.behaviour.routing.RouteFollower;
import online.davisfamily.threedee.behaviour.routing.RouteFollower.TravelDirection;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.matrices.Vec4;
import online.davisfamily.threedee.model.Mesh;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.threedee.sim.framework.events.DetectionEvent;
import online.davisfamily.threedee.sim.framework.events.DetectionEvent.DetectionType;
import online.davisfamily.threedee.sim.framework.objects.TrackableObject;
import online.davisfamily.warehouse.rendering.model.tracks.GuideSide;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptedLineStore;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingArea;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingAreaController;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingBench;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingBenchId;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingBenchSnapshot;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingStationProcessingController;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingStationProcessingTarget;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingStorageMap;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingVisitFactory;
import online.davisfamily.warehouse.sim.dsp.adapting.DefaultCollectedPackPlanFactory;
import online.davisfamily.warehouse.sim.dsp.adapting.MapBackedToteLoadPlanRegistry;
import online.davisfamily.warehouse.sim.dsp.av02.Av02AllocatedTote;
import online.davisfamily.warehouse.sim.dsp.av02.Av02AllocationConfig;
import online.davisfamily.warehouse.sim.dsp.av02.Av02InventorySnapshot;
import online.davisfamily.warehouse.sim.dsp.av02.Av02PhysicalToteInventory;
import online.davisfamily.warehouse.sim.dsp.bagging.DspPackPlanFactory;
import online.davisfamily.warehouse.sim.dsp.bagging.PackProvenanceRegistry;
import online.davisfamily.warehouse.sim.dsp.lifecycle.Av02ToteLifecycleController;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteLifecycleController;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifestCatalog;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteAssignmentEndReason;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleLedger;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleSnapshot;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleState;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRecord;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRole;
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
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OsrOutboundRouteLaunchController;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OsrOutboundRouteLaunchQueue;
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
import online.davisfamily.warehouse.sim.dsp.routing.DspRouteDeriver;
import online.davisfamily.warehouse.sim.dsp.routing.InMemoryProductMasterRepository;
import online.davisfamily.warehouse.sim.dsp.runtime.DspSchedulerRuntimeState;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;
import online.davisfamily.warehouse.sim.dsp.station.processing.DspStationProcessingRuntimeFactory;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingBinding;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingCoordinator;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingDisposition;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingDispositionType;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingOrderCatalog;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingSnapshot;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingTarget;
import online.davisfamily.warehouse.sim.dsp.station.processing.DspStationProcessingRuntime;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyArea;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyAreaConfig;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyAreaController;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyAreaSnapshot;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyStationProcessingController;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyStationProcessingTarget;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyVisitFactory;
import online.davisfamily.warehouse.sim.dsp.transport.OsrOutboundTransportQueueSnapshot;
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;
import online.davisfamily.warehouse.sim.dsp.transport.LoadPlanOsrOutboundToteHydrator;
import online.davisfamily.warehouse.sim.dsp.transport.OsrOutboundTransportQueue;
import online.davisfamily.warehouse.sim.dsp.transport.routing.RouteBoundDetachedOutboundToteFactory;
import online.davisfamily.warehouse.sim.dsp.transport.routing.SimulationWorldWarehouseTransportPublisher;
import online.davisfamily.warehouse.sim.dsp.transport.routing.StationRoutedToteArrivalQueue;
import online.davisfamily.warehouse.sim.dsp.transport.routing.StationRoutedToteArrivalQueueSnapshot;
import online.davisfamily.warehouse.sim.dsp.transport.routing.StationRoutedToteArrivalRegistry;
import online.davisfamily.warehouse.sim.dsp.transport.routing.WarehouseRouteCatalog;
import online.davisfamily.warehouse.sim.dsp.transport.routing.WarehouseRouteDefinition;
import online.davisfamily.warehouse.sim.dsp.transport.routing.WarehouseTransferRoutingTable;
import online.davisfamily.warehouse.sim.dsp.transport.routing.WarehouseTransportArrivalController;
import online.davisfamily.warehouse.sim.dsp.transport.routing.WarehouseTransportIngressController;
import online.davisfamily.warehouse.sim.dsp.transport.routing.WarehouseTransportInFlightRegistry;
import online.davisfamily.warehouse.sim.dsp.transport.routing.WarehouseTransportInFlightSnapshot;
import online.davisfamily.warehouse.sim.dsp.transport.routing.WarehouseTransportPublicationState;
import online.davisfamily.warehouse.sim.dsp.transport.routing.WarehouseTransportPublisher;
import online.davisfamily.warehouse.sim.machine.queue.MachineWaitQueueSnapshot;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperInputQueue;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperInputQueueController;
import online.davisfamily.warehouse.sim.totebag.control.TipperDownstreamFlow;
import online.davisfamily.warehouse.sim.totebag.control.ToteTrackTipperFlowController;
import online.davisfamily.warehouse.sim.totebag.handoff.StoredBagReceiver;
import online.davisfamily.warehouse.sim.totebag.machine.TippingMachine;
import online.davisfamily.warehouse.sim.totebag.pack.Pack;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;
import online.davisfamily.warehouse.sim.transfer.TransferMotionConfig;
import online.davisfamily.warehouse.sim.transfer.TransferOrientationPolicy;
import online.davisfamily.warehouse.sim.transfer.TransferRoutingDecision;
import online.davisfamily.warehouse.sim.transfer.TransferTarget;
import online.davisfamily.warehouse.sim.transfer.TransferZone;
import online.davisfamily.warehouse.sim.transfer.TransferZoneMachine;

/** Real-boundary scenarios for the generic station route continuation. */
class DspStationRouteContinuationScenarioTest {

    private static final PackDimensions PACK_DIMENSIONS = new PackDimensions(0.2f, 0.1f, 0.08f);

    @Test
    void shouldContinueAssociatedThroughThirdPartyAdaptingAndP2pArrival() {
        NotionalToteOrder order = associatedCollectOrder("associated-continuation");
        PhysicalToteId id = new PhysicalToteId("associated-continuation-physical");
        ScenarioFixture fixture = ScenarioFixture.create(
                List.of(order), List.of(manifest(order, id)));
        P2pPhysicalToteAssignment assignment = fixture.pin(id);
        fixture.activateInbound(id);
        fixture.stageCollect(order, new OrderSheetKey("source-associated", 1));
        fixture.compose();

        OperationalRouteLaunchRequest request = fixture.osrRequest(
                order, id, fixture.thirdPartyDestination(), assignment, 17);
        RoutedPhysicalTote thirdParty = fixture.launchAndClaim(request);
        assertSame(request, thirdParty.launchRequest());
        assertEquals(OperationalPhysicalToteSource.OSR, thirdParty.launchRequest().source());
        assertSame(assignment, thirdParty.p2pAssignment().orElseThrow());
        ToteLoadPlan firstPlan = thirdParty.loadPlan();

        fixture.advanceUntil(
                () -> fixture.continuationRuntime.snapshot().continuedCount() == 1,
                fixture.world, 0.25, 80);
        ToteLoadPlan secondPlan = fixture.currentPlan(id);
        assertNotSame(firstPlan, secondPlan);

        RoutedPhysicalTote adapting = fixture.reenterAndClaim(id, fixture.adaptingDestination());
        assertNewEnvelopeWithSamePhysicalObjects(thirdParty, adapting, assignment);
        assertSame(secondPlan, adapting.loadPlan());

        fixture.advanceUntil(
                () -> fixture.continuationRuntime.snapshot().continuedCount() == 2,
                fixture.world, 0.25, 120);
        ToteLoadPlan thirdPlan = fixture.currentPlan(id);
        assertNotSame(secondPlan, thirdPlan);

        RoutedPhysicalTote p2p = fixture.reenterAndClaim(id, fixture.p2pDestination());
        assertNewEnvelopeWithSamePhysicalObjects(adapting, p2p, assignment);
        assertSame(thirdPlan, p2p.loadPlan());
        fixture.advanceUntil(
                () -> fixture.continuationRuntime.snapshot().consumedAcknowledgementCount() == 1,
                fixture.world, 0.25, 120);

        assertEquals(List.of(
                fixture.thirdPartyDestination(), fixture.adaptingDestination(),
                fixture.p2pDestination()), fixture.stationHistory);
        assertEquals(1, fixture.ingressController.snapshot()
                .initialPublicationCount());
        assertEquals(2, fixture.ingressController.snapshot()
                .exactObjectReentryCount());
        assertEquals(3, fixture.coordinator.snapshot().completedCount());
        assertEquals(2, fixture.coordinator.snapshot().acknowledgedContinuationCount());
        assertEquals(1, fixture.coordinator.snapshot().acknowledgedConsumeCount());
        assertTrue(fixture.coordinator.pendingDispositions().isEmpty(),
                () -> "pending=" + fixture.coordinator.pendingDispositions()
                        + " continuation=" + fixture.continuationRuntime.snapshot()
                        + " transport=" + fixture.transportQueue.snapshot()
                        + " inflight=" + fixture.inFlightRegistry.snapshot()
                        + " arrivals=" + fixture.arrivalRegistry.snapshots());
        assertEquals(PhysicalToteLifecycleState.CONSUMED_AT_P2P,
                fixture.ledger.tote(id).orElseThrow().state());
        assertEquals(PhysicalToteAssignmentEndReason.CONSUMED_AT_P2P,
                fixture.ledger.assignmentHistoryFor(order.orderSheetKey()).getLast()
                        .endReason().orElseThrow());
        assertEquals(1, fixture.publishedToteCount(id));
        assertEquals(1, fixture.renderables.size());
        assertTrue(fixture.transportPublisher.contains(id));
        assertEquals(WarehouseTransportPublicationState.PUBLISHED_EXACT_OBJECTS,
                fixture.transportPublisher.publicationState(p2p));
        assertEquals(List.of("pharmacy-1"), p2p.launchRequest().pharmacyIds());
        assertEquals(Duration.ZERO, p2p.launchRequest().releaseTime());
        assertSame(request.releaseRequest(), p2p.launchRequest().releaseRequest());
        assertFalse(p2p.renderable().isVisible());
        assertEquals(Tote.ToteMotionState.HELD, p2p.tote().getInteractionMode());
        assertEquals(0, fixture.outboundAllocator.snapshot().allocatedBags().size());
        assertTrue(fixture.bagReceiver.getReceivedBags().isEmpty());
    }

    @Test
    void shouldContinueAv02EmptyFromAdaptingCollectToPinnedP2p() {
        NotionalToteOrder order = emptyCollectOrder("av02-continuation");
        PhysicalToteId id = new PhysicalToteId("av02-continuation-physical");
        ScenarioFixture fixture = ScenarioFixture.create(List.of(order), List.of());
        P2pPhysicalToteAssignment assignment = fixture.pin(id);
        var leaseBefore = fixture.leases.snapshot(Map.of(
                fixture.p2pLine.lineId(), P2pLineActivitySnapshot.idle()));
        Av02AllocatedTote allocated = fixture.allocateAv02(order, id);
        fixture.av02Inventory.store(allocated);
        fixture.av02Inventory.recordDeparture(id);
        fixture.stageCollect(order, new OrderSheetKey("source-av02", 1));
        WarehouseSchedulerSnapshot schedulerBefore = fixture.schedulerState.snapshot();
        fixture.compose();

        OperationalRouteLaunchRequest request = fixture.av02Request(
                order, id, fixture.adaptingDestination(), assignment, 23);
        RoutedPhysicalTote adapting = fixture.launchAndClaim(request);
        assertEquals(OperationalPhysicalToteSource.AV02, adapting.launchRequest().source());
        assertEquals(PhysicalToteRole.PRE_P2P,
                adapting.launchRequest().identity().physicalToteRole());
        assertSame(assignment, adapting.p2pAssignment().orElseThrow());
        ToteLoadPlan firstPlan = adapting.loadPlan();

        fixture.advanceUntil(
                () -> fixture.continuationRuntime.snapshot().continuedCount() == 1,
                fixture.world, 0.25, 120);
        ToteLoadPlan replacement = fixture.currentPlan(id);
        assertNotSame(firstPlan, replacement);
        RoutedPhysicalTote p2p = fixture.reenterAndClaim(id, fixture.p2pDestination());
        assertNewEnvelopeWithSamePhysicalObjects(adapting, p2p, assignment);
        assertSame(replacement, p2p.loadPlan());
        fixture.advanceUntil(
                () -> fixture.continuationRuntime.snapshot().consumedAcknowledgementCount() == 1,
                fixture.world, 0.25, 120);

        assertEquals(List.of(fixture.adaptingDestination(), fixture.p2pDestination()),
                fixture.stationHistory);
        assertTrue(fixture.manifestCatalog.manifests().isEmpty());
        assertTrue(fixture.manifestCatalog.findByPhysicalToteId(id).isEmpty());
        assertEquals(order.orderSheetKey(), p2p.launchRequest().orderSheetKey());
        assertEquals(List.of("pharmacy-1"), p2p.launchRequest().pharmacyIds());
        assertEquals(Duration.ZERO, p2p.launchRequest().releaseTime());
        assertSame(request.releaseRequest(), p2p.launchRequest().releaseRequest());
        assertEquals(PhysicalToteLifecycleState.CONSUMED_AT_P2P,
                fixture.ledger.tote(id).orElseThrow().state());
        assertEquals(1, fixture.av02Inventory.snapshot().departedTotes().size());
        assertTrue(fixture.av02Inventory.snapshot().waitingTotes().isEmpty());
        assertEquals(schedulerBefore, fixture.schedulerState.snapshot());
        assertEquals(leaseBefore, fixture.leases.snapshot(Map.of(
                fixture.p2pLine.lineId(), P2pLineActivitySnapshot.idle())));
        assertEquals(1, fixture.ingressController.snapshot()
                .initialPublicationCount());
        assertEquals(1, fixture.ingressController.snapshot()
                .exactObjectReentryCount());
        assertEquals(0, fixture.outboundAllocator.snapshot().allocatedBags().size());
        assertTrue(fixture.bagReceiver.getReceivedBags().isEmpty());
        assertFalse(p2p.renderable().isVisible());
        assertEquals(Tote.ToteMotionState.HELD, p2p.tote().getInteractionMode());
    }

    @Test
    void shouldConsumeAdaptedStoreAndP2pWithoutContinuation() {
        NotionalToteOrder storeOrder = adaptedStoreOrder("adapted-store");
        NotionalToteOrder p2pOrder = directP2pOrder("direct-p2p");
        PhysicalToteId storeId = new PhysicalToteId("adapted-store-physical");
        PhysicalToteId p2pId = new PhysicalToteId("direct-p2p-physical");
        ScenarioFixture fixture = ScenarioFixture.create(
                List.of(storeOrder, p2pOrder),
                List.of(manifest(storeOrder, storeId), manifest(p2pOrder, p2pId)));
        fixture.activateInbound(storeId);
        fixture.activateInbound(p2pId);
        P2pPhysicalToteAssignment p2pAssignment = fixture.pin(p2pId);
        fixture.compose();

        RoutedPhysicalTote store = fixture.launchAndClaim(fixture.osrRequest(
                storeOrder, storeId, fixture.thirdPartyDestination(), null, 31));
        fixture.advanceUntil(
                () -> fixture.continuationRuntime.snapshot().continuedCount() == 1,
                fixture.world, 0.25, 80);
        RoutedPhysicalTote adapting = fixture.reenterAndClaim(storeId, fixture.adaptingDestination());
        assertNewEnvelopeWithSamePhysicalObjects(store, adapting, null);
        fixture.advanceUntil(
                () -> fixture.continuationRuntime.snapshot().consumedAcknowledgementCount() == 1,
                fixture.world, 0.25, 120);

        RoutedPhysicalTote p2p = fixture.launchAndClaim(fixture.osrRequest(
                p2pOrder, p2pId, fixture.p2pDestination(), p2pAssignment, 37));
        fixture.advanceUntil(
                () -> fixture.continuationRuntime.snapshot().consumedAcknowledgementCount() == 2,
                fixture.world, 0.25, 120);

        assertEquals(List.of(
                fixture.thirdPartyDestination(), fixture.adaptingDestination(),
                fixture.p2pDestination()), fixture.stationHistory);
        assertEquals(1, fixture.continuationRuntime.snapshot().continuedCount());
        assertEquals(2, fixture.continuationRuntime.snapshot().consumedAcknowledgementCount());
        assertEquals(PhysicalToteLifecycleState.CONSUMED_AT_ADAPTING,
                fixture.ledger.tote(storeId).orElseThrow().state());
        assertEquals(PhysicalToteLifecycleState.CONSUMED_AT_P2P,
                fixture.ledger.tote(p2pId).orElseThrow().state());
        assertEquals(PhysicalToteAssignmentEndReason.CONSUMED_AT_ADAPTING,
                fixture.ledger.assignmentHistoryFor(storeOrder.orderSheetKey()).getLast()
                        .endReason().orElseThrow());
        assertEquals(PhysicalToteAssignmentEndReason.CONSUMED_AT_P2P,
                fixture.ledger.assignmentHistoryFor(p2pOrder.orderSheetKey()).getLast()
                        .endReason().orElseThrow());
        ContinuationState terminalState = fixture.continuationState();
        assertThrows(IllegalStateException.class,
                () -> fixture.coordinator.validateCanClaim(store, Duration.ofSeconds(20)));
        assertEquals(terminalState, fixture.continuationState());
        assertThrows(IllegalStateException.class,
                () -> fixture.coordinator.validateCanClaim(p2p, Duration.ofSeconds(20)));
        assertEquals(terminalState, fixture.continuationState());
        assertEquals(2, fixture.ingressController.snapshot()
                .initialPublicationCount());
        assertEquals(1, fixture.ingressController.snapshot()
                .exactObjectReentryCount());
        assertTrue(fixture.transportQueue.snapshot().entries().isEmpty());
        assertEquals(2, fixture.renderables.size());
        assertFalse(store.renderable().isVisible());
        assertFalse(p2p.renderable().isVisible());
        assertEquals(Tote.ToteMotionState.HELD, store.tote().getInteractionMode());
        assertEquals(Tote.ToteMotionState.HELD, p2p.tote().getInteractionMode());
    }

    @Test
    void shouldRetainDispositionFifoUnderContinuationBackpressure() {
        NotionalToteOrder thirdPartyOrder = fullPackThirdPartyOrder("fifo-third-party");
        NotionalToteOrder terminalOrder = directP2pOrder("fifo-terminal");
        NotionalToteOrder adaptingOrder = emptyCollectOrder("fifo-adapting");
        NotionalToteOrder blockerOrder = directP2pOrder("fifo-blocker");
        NotionalToteOrder fillerOrder = directP2pOrder("fifo-filler");
        PhysicalToteId thirdPartyId = new PhysicalToteId("fifo-third-party-physical");
        PhysicalToteId terminalId = new PhysicalToteId("fifo-terminal-physical");
        PhysicalToteId adaptingId = new PhysicalToteId("fifo-adapting-physical");
        PhysicalToteId blockerId = new PhysicalToteId("fifo-blocker-physical");
        PhysicalToteId fillerId = new PhysicalToteId("fifo-filler-physical");
        ScenarioFixture fixture = ScenarioFixture.create(
                List.of(thirdPartyOrder, terminalOrder, adaptingOrder, blockerOrder, fillerOrder),
                List.of(manifest(thirdPartyOrder, thirdPartyId),
                        manifest(terminalOrder, terminalId)));
        fixture.activateInbound(thirdPartyId);
        fixture.activateInbound(terminalId);
        P2pPhysicalToteAssignment thirdPartyAssignment = fixture.pin(thirdPartyId);
        P2pPhysicalToteAssignment terminalAssignment = fixture.pin(terminalId);
        P2pPhysicalToteAssignment adaptingAssignment = fixture.pin(adaptingId);
        fixture.allocateAndDepartAv02(adaptingOrder, adaptingId);
        fixture.stageCollect(adaptingOrder, new OrderSheetKey("source-fifo", 1));
        fixture.compose();

        RoutedPhysicalTote thirdParty = fixture.launchAndClaim(fixture.osrRequest(
                thirdPartyOrder, thirdPartyId, fixture.thirdPartyDestination(),
                thirdPartyAssignment, 41));
        RoutedPhysicalTote terminal = fixture.launchAndClaim(fixture.osrRequest(
                terminalOrder, terminalId, fixture.p2pDestination(), terminalAssignment, 43));
        RoutedPhysicalTote adapting = fixture.launchAndClaim(fixture.av02Request(
                adaptingOrder, adaptingId, fixture.adaptingDestination(), adaptingAssignment, 47));

        fixture.installTransportBackpressure(
                fixture.barrierRequest(blockerOrder, blockerId),
                fixture.barrierRequest(fillerOrder, fillerId));
        fixture.step(1d);
        assertEquals(1, fixture.coordinator.snapshot().completedCount());
        assertEquals(StationProcessingDispositionType.CONTINUE,
                fixture.coordinator.peekDisposition().orElseThrow().type());
        assertTrue(fixture.continuationRuntime.snapshot().blocked());
        ContinuationState beforeRetry = fixture.continuationState();
        fixture.step(0d);
        assertEquals(beforeRetry, fixture.continuationState());

        fixture.advanceUntil(() -> fixture.coordinator.snapshot().completedCount() >= 2,
                fixture.world, 0.25, 80);
        assertEquals(List.of(thirdPartyId, terminalId),
                fixture.coordinator.pendingDispositions().stream()
                        .map(StationProcessingDisposition::physicalToteId).toList());
        assertEquals(List.of(StationProcessingDispositionType.CONTINUE,
                StationProcessingDispositionType.CONSUME), fixture.coordinator.pendingDispositions()
                        .stream().map(StationProcessingDisposition::type).toList());

        fixture.advanceUntil(() -> fixture.coordinator.snapshot().completedCount() >= 3,
                fixture.world, 0.25, 120);
        assertEquals(List.of(thirdPartyId, terminalId, adaptingId),
                fixture.coordinator.pendingDispositions().stream()
                        .map(StationProcessingDisposition::physicalToteId).toList());
        assertEquals(List.of(StationProcessingDispositionType.CONTINUE,
                StationProcessingDispositionType.CONSUME,
                StationProcessingDispositionType.CONTINUE), fixture.coordinator.pendingDispositions()
                        .stream().map(StationProcessingDisposition::type).toList());
        assertEquals(thirdPartyId,
                fixture.continuationRuntime.snapshot().blockedPhysicalToteId().orElseThrow());

        fixture.arriveTransportOnly(blockerId, fixture.blockerDestination());
        fixture.step(0d); // publish the filler, then acknowledge only the FIFO head.
        assertEquals(1, fixture.continuationRuntime.snapshot().continuedCount());
        assertEquals(0, fixture.continuationRuntime.snapshot().consumedAcknowledgementCount());
        fixture.arriveTransportOnly(fillerId, fixture.blockerDestination());
        fixture.step(0d); // terminal consume follows the original order.
        assertEquals(1, fixture.continuationRuntime.snapshot().continuedCount());
        assertEquals(1, fixture.continuationRuntime.snapshot().consumedAcknowledgementCount());
        fixture.step(0d); // the following CONTINUE is considered on the next controller update.
        assertEquals(2, fixture.continuationRuntime.snapshot().continuedCount());

        assertTrue(fixture.coordinator.pendingDispositions().isEmpty(),
                () -> "pending=" + fixture.coordinator.pendingDispositions()
                        + " continuation=" + fixture.continuationRuntime.snapshot()
                        + " transport=" + fixture.transportQueue.snapshot()
                        + " inflight=" + fixture.inFlightRegistry.snapshot()
                        + " arrivals=" + fixture.arrivalRegistry.snapshots());
        assertEquals(2, fixture.continuationRuntime.snapshot().continuedCount());
        assertEquals(1, fixture.continuationRuntime.snapshot().consumedAcknowledgementCount());
        assertEquals(3, fixture.coordinator.snapshot().completedCount());
        assertEquals(3, fixture.coordinator.snapshot().acknowledgedContinuationCount()
                + fixture.coordinator.snapshot().acknowledgedConsumeCount());
        assertEquals(Tote.ToteMotionState.HELD, terminal.tote().getInteractionMode());
        assertFalse(terminal.renderable().isVisible());
        assertSame(thirdParty.tote(), fixture.latestTote(thirdPartyId));
        assertSame(adapting.tote(), fixture.latestTote(adaptingId));
    }

    private static void assertNewEnvelopeWithSamePhysicalObjects(
            RoutedPhysicalTote previous,
            RoutedPhysicalTote next,
            P2pPhysicalToteAssignment expectedAssignment) {
        assertNotSame(previous, next);
        assertSame(previous.tote(), next.tote());
        assertSame(previous.renderable(), next.renderable());
        assertSame(previous.tote().getRouteFollower(), next.tote().getRouteFollower());
        assertSame(previous.launchRequest().releaseRequest(), next.launchRequest().releaseRequest());
        assertSame(previous.launchRequest().identity(), next.launchRequest().identity());
        assertEquals(previous.physicalToteId(), next.physicalToteId());
        assertEquals(previous.p2pAssignment(), next.p2pAssignment());
        if (expectedAssignment != null) {
            assertSame(expectedAssignment, next.p2pAssignment().orElseThrow());
        }
    }

    private static InboundToteManifest manifest(NotionalToteOrder order, PhysicalToteId id) {
        return new InboundToteManifest(
                id, order.orderSheetKey(), order.orderType(), order.serviceCentreId(),
                order.items(), 0);
    }

    private static NotionalToteOrder associatedCollectOrder(String id) {
        return order(id, OrderType.ASSOCIATED, List.of(
                item("third-party-line-" + id, "third-party-product", DspOrderLineType.FULL_PACK, id),
                item("adapted-line-" + id, "adapted-product", DspOrderLineType.ADAPTED, id)));
    }

    private static NotionalToteOrder adaptedStoreOrder(String id) {
        return order(id, OrderType.ADAPTED,
                List.of(item("adapted-store-line-" + id, "third-party-product",
                        DspOrderLineType.ADAPTED, id)));
    }

    private static NotionalToteOrder emptyCollectOrder(String id) {
        return order(id, OrderType.EMPTY,
                List.of(item("empty-line-" + id, "adapted-product",
                        DspOrderLineType.ADAPTED, id)));
    }

    private static NotionalToteOrder directP2pOrder(String id) {
        return order(id, OrderType.FULL_PACK,
                List.of(item("p2p-line-" + id, "regular-product",
                        DspOrderLineType.FULL_PACK, id)));
    }

    private static NotionalToteOrder fullPackThirdPartyOrder(String id) {
        return order(id, OrderType.FULL_PACK,
                List.of(item("third-party-full-line-" + id, "third-party-product",
                        DspOrderLineType.FULL_PACK, id)));
    }

    private static DspOrderItem item(
            String lineReference,
            String productId,
            DspOrderLineType lineType,
            String orderId) {
        return new DspOrderItem(
                lineReference, productId, 1, "pharmacy-1", "patient-" + orderId,
                "prescription-" + orderId, lineType, orderId, 1, 0);
    }

    private static NotionalToteOrder order(
            String id,
            OrderType type,
            List<DspOrderItem> items) {
        return new NotionalToteOrder(
                id, "notional-" + id, "SC-1", 1, type, items, 0, 1);
    }

    private static RenderableObject renderable(String id) {
        return RenderableObject.create(
                id, null,
                new Mesh(new Vec4[] {
                        new Vec4(0f, 0f, 0f, 1f),
                        new Vec4(0f, 0f, 0f, 1f),
                        new Vec4(0f, 0f, 0f, 1f)},
                        new int[][] {{0, 1, 2}}, "anchor"),
                new Mat4.ObjectTransformation(0f, 0f, 0f, 0f, 0f, 0f, new Mat4()),
                ignored -> 0,
                false);
    }

    private static RouteSegment segment(String id, float length) {
        return new RouteSegment(id, new online.davisfamily.threedee.path.LinearSegment3(
                new Vec3(0f, 0f, 0f), new Vec3(length, 0f, 0f), false));
    }

    private static final class ScenarioFixture {
        private final RecordingSimulationWorld world = new RecordingSimulationWorld();
        private final SimulationContext eventContext = new SimulationContext();
        private final List<RenderableObject> renderables = new ArrayList<>();
        private final OsrOutboundRouteLaunchQueue launchQueue =
                new OsrOutboundRouteLaunchQueue("continuation-launch", 32);
        private final StationProcessingCoordinator coordinator = new StationProcessingCoordinator();
        private final MapBackedToteLoadPlanRegistry loadPlanRegistry =
                new MapBackedToteLoadPlanRegistry();
        private final PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        private final InboundToteManifestCatalog manifestCatalog;
        private final InboundToteLifecycleController inboundLifecycle;
        private final Av02PhysicalToteInventory av02Inventory =
                new Av02PhysicalToteInventory(new Av02AllocationConfig(8));
        private final Av02ToteLifecycleController av02Lifecycle;
        private final AdaptedLineStore adaptedStore = new AdaptedLineStore();
        private final PackProvenanceRegistry provenanceRegistry = new PackProvenanceRegistry();
        private final DspSchedulerRuntimeState schedulerState = new DspSchedulerRuntimeState(
                new WarehouseSchedulerSnapshot(List.of(), Map.of(), Set.of(), Optional.empty()));
        private final StoredBagReceiver bagReceiver = new StoredBagReceiver("continuation-bags");
        private final OutboundToteAllocator outboundAllocator;
        private final StationProcessingOrderCatalog orderCatalog;
        private final List<NotionalToteOrder> orders;
        private final Map<PhysicalToteId, RoutedPhysicalTote> latestById = new LinkedHashMap<>();
        private final List<OperationalRouteDestination> stationHistory = new ArrayList<>();
        private final Map<OperationalRouteDestination, StationRoutedToteArrivalQueue> stationQueues =
                new LinkedHashMap<>();
        private final List<StationProcessingBinding> bindings = new ArrayList<>();
        private final RouteTopology topology = RouteTopology.create();
        private final P2pLineDefinition p2pLine;
        private final P2pLineLeaseRegistry leases;
        private final TipperInputQueue p2pInputQueue;
        private final P2pTipperArrivalTarget p2pTarget;
        private final StationProcessingTarget adaptingTarget;
        private final StationProcessingTarget thirdPartyTarget;
        private final AdaptingArea adaptingArea;
        private final AdaptingAreaController adaptingAreaController;
        private final ThirdPartyArea thirdPartyArea;
        private final ThirdPartyAreaController thirdPartyAreaController;
        private final InboundLifecycleP2pToteCompletedListener inboundP2pListener;
        private final OperationalLifecycleP2pToteCompletedListener operationalP2pListener;
        private final OsrOutboundTransportQueue transportQueue =
                new OsrOutboundTransportQueue("continuation-transport", 1);
        private final WarehouseTransportInFlightRegistry inFlightRegistry =
                new WarehouseTransportInFlightRegistry(1);
        private final WarehouseTransportPublisher transportPublisher;
        private final StationRoutedToteArrivalRegistry arrivalRegistry;
        private OsrOutboundRouteLaunchController routeLaunchController;
        private WarehouseTransportIngressController ingressController;
        private WarehouseTransportArrivalController arrivalController;
        private StationProcessingP2pToteCompletedListener stationP2pListener;
        private DspStationProcessingRuntime stationRuntime;
        private DspStationRouteContinuationRuntime continuationRuntime;
        private ToteTrackTipperFlowController tipperFlow;
        private double simulationTime;
        private long av02Ordinal;
        private boolean composed;

        private ScenarioFixture(List<NotionalToteOrder> orders, List<InboundToteManifest> manifests) {
            this.orders = List.copyOf(orders);
            this.orderCatalog = new StationProcessingOrderCatalog(this.orders);
            this.manifestCatalog = new InboundToteManifestCatalog(manifests);
            this.inboundLifecycle = new InboundToteLifecycleController(ledger, manifestCatalog);
            this.av02Lifecycle = new Av02ToteLifecycleController(
                    ledger, () -> new PhysicalToteId("av02-auto-" + (++av02Ordinal)));
            OutputSheetAllocator outputSheets = new OutputSheetAllocator(
                    this.orders.stream().map(NotionalToteOrder::orderSheetKey).toList());
            this.outboundAllocator = new OutboundToteAllocator(
                    ledger, new DeterministicOutboundToteIdSource(), outputSheets,
                    new OutboundToteConfig(4));

            this.p2pLine = new P2pLineDefinition(
                    new P2pLineId("continuation-p2p-line"), topology.p2pDestination());
            this.leases = new P2pLineLeaseRegistry(List.of(p2pLine));
            this.p2pInputQueue = new TipperInputQueue("continuation-p2p-input", 1);
            this.p2pTarget = new P2pTipperArrivalTarget(topology.p2pDestination(), p2pInputQueue);

            AdaptingStorageMap storageMap = new AdaptingStorageMap();
            AdaptingBenchId benchId = new AdaptingBenchId(topology.adaptingDestination().targetId());
            storageMap.configureAvailableBenches(List.of(benchId));
            storageMap.assignPharmacyToBench("pharmacy-1", benchId);
            this.adaptingArea = new AdaptingArea(
                    List.of(new AdaptingBench(benchId.value(), adaptedStore, 8d)), 4, storageMap);
            this.adaptingAreaController = new AdaptingAreaController(
                    adaptingArea, schedulerState, loadPlanRegistry,
                    new DefaultCollectedPackPlanFactory(PACK_DIMENSIONS,
                            new DspPackPlanFactory(provenanceRegistry)));
            this.thirdPartyArea = new ThirdPartyArea(new ThirdPartyAreaConfig(0, 1, 1d));
            this.thirdPartyAreaController = new ThirdPartyAreaController(
                    thirdPartyArea, loadPlanRegistry,
                    (visit, lineWork, ordinal) -> new PackPlan(
                            "third-party-pack-" + lineWork.lineReference() + "-" + ordinal,
                            visit.orderSheetKey().orderId(), PACK_DIMENSIONS));

            InMemoryProductMasterRepository productMaster = new InMemoryProductMasterRepository(List.of(
                    new ProductMasterRecord("third-party-product", "Third Party", Optional.of("Y74"),
                            Optional.of(PACK_DIMENSIONS)),
                    new ProductMasterRecord("adapted-product", "Adapted", Optional.empty(),
                            Optional.of(PACK_DIMENSIONS)),
                    new ProductMasterRecord("regular-product", "Regular", Optional.empty(),
                            Optional.of(PACK_DIMENSIONS))));
            adaptingTarget = new AdaptingStationProcessingTarget(
                    topology.adaptingDestination(), orderCatalog, loadPlanRegistry,
                    new AdaptingVisitFactory(), adaptingArea, coordinator);
            thirdPartyTarget = new ThirdPartyStationProcessingTarget(
                    topology.thirdPartyDestination(), orderCatalog, loadPlanRegistry,
                    new ThirdPartyVisitFactory(productMaster), thirdPartyArea, coordinator);

            inboundP2pListener = new InboundLifecycleP2pToteCompletedListener(inboundLifecycle);
            operationalP2pListener = new OperationalLifecycleP2pToteCompletedListener(
                    manifestCatalog, inboundP2pListener, av02Inventory, av02Lifecycle);
            configureStationQueues();
            this.arrivalRegistry = new StationRoutedToteArrivalRegistry(stationQueues.values().stream()
                    .toList());
            this.transportPublisher = new SimulationWorldWarehouseTransportPublisher(
                    world, renderables);
        }

        static ScenarioFixture create(
                List<NotionalToteOrder> orders,
                List<InboundToteManifest> manifests) {
            return new ScenarioFixture(orders, manifests);
        }

        private void configureStationQueues() {
            StationRoutedToteArrivalQueue adaptingQueue = new StationRoutedToteArrivalQueue(
                    topology.adaptingDestination(), 8);
            StationRoutedToteArrivalQueue thirdPartyQueue = new StationRoutedToteArrivalQueue(
                    topology.thirdPartyDestination(), 8);
            StationRoutedToteArrivalQueue p2pQueue = new StationRoutedToteArrivalQueue(
                    topology.p2pDestination(), 8);
            StationRoutedToteArrivalQueue blockerQueue = new StationRoutedToteArrivalQueue(
                    topology.blockerDestination(), 8);
            stationQueues.put(adaptingQueue.destination(), adaptingQueue);
            stationQueues.put(thirdPartyQueue.destination(), thirdPartyQueue);
            stationQueues.put(p2pQueue.destination(), p2pQueue);
            stationQueues.put(blockerQueue.destination(), blockerQueue);
            bindings.add(new StationProcessingBinding(adaptingQueue, adaptingTarget));
            bindings.add(new StationProcessingBinding(thirdPartyQueue, thirdPartyTarget));
            bindings.add(new StationProcessingBinding(p2pQueue, p2pProcessingTarget()));
        }

        private StationProcessingTarget p2pProcessingTarget() {
            return new P2pStationProcessingTarget(
                    new StickyP2pArrivalAdmissionPolicy(
                            p2pLine,
                            () -> leases.snapshot(Map.of(
                                    p2pLine.lineId(), P2pLineActivitySnapshot.idle()))),
                    new P2pArrivalRouteBinding(topology.p2pTerminal(), topology.p2pTipper()),
                    new ContainedPackP2pTipperPayloadFactory(1f, 1f, 0f, 0f, 0f, 0f),
                    p2pTarget, coordinator);
        }

        private void compose() {
            if (composed) {
                return;
            }
            List<online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingCompletionController>
                    completionControllers = List.of(
                            new AdaptingStationProcessingController(
                                    "continuation-adapting",
                                    Set.of(topology.adaptingDestination()),
                                    loadPlanRegistry, adaptingArea, adaptingAreaController,
                                    inboundLifecycle, coordinator),
                            new ThirdPartyStationProcessingController(
                                    "continuation-third-party",
                                    Set.of(topology.thirdPartyDestination()),
                                    loadPlanRegistry, thirdPartyAreaController, coordinator));
            RouteBoundDetachedOutboundToteFactory detachedFactory =
                    new RouteBoundDetachedOutboundToteFactory(
                            topology.catalog(),
                            (request, plan) -> renderable(request.physicalToteId().value()),
                            1d, new Vec3(), 0f);
            routeLaunchController = new OsrOutboundRouteLaunchController(
                    launchQueue, transportQueue,
                    new LoadPlanOsrOutboundToteHydrator(loadPlanRegistry, detachedFactory));
            ingressController = new WarehouseTransportIngressController(
                    transportQueue, topology.catalog(), inFlightRegistry, transportPublisher);
            arrivalController = new WarehouseTransportArrivalController(
                    topology.catalog(), inFlightRegistry, arrivalRegistry);
            world.addController(routeLaunchController);
            world.addController(ingressController);
            world.registerListener(DetectionEvent.class, arrivalController.detectionHandler());
            world.addController(arrivalController);

            RoutedPhysicalTote bootstrap = bootstrapTote();
            TippingMachine tippingMachine = new TippingMachine("continuation-tipper", 0d, 0d, 0d);
            world.addTrackableObject(bootstrap.tote());
            stationP2pListener = new StationProcessingP2pToteCompletedListener(
                    operationalP2pListener, coordinator);
            tipperFlow = new ToteTrackTipperFlowController(
                    bootstrap.tote(),
                    toteId -> toteId.equals(bootstrap.physicalToteId().value())
                            ? bootstrap.loadPlan() : p2pTarget.getLoadPlanFor(toteId),
                    topology.p2pTipper(), 0.25f, -1.02f, tippingMachine,
                    acceptingDownstreamFlow(), 0.01d,
                    (tote, context) -> {
                        if (!tote.getId().equals(bootstrap.physicalToteId().value())) {
                            stationP2pListener.onToteCompleted(tote, context);
                        }
                    });
            world.addSimObject(tippingMachine);
            world.addController(tipperFlow);
            world.addController(new TipperInputQueueController(p2pInputQueue, tipperFlow));
            stationRuntime = new DspStationProcessingRuntimeFactory().create(
                    world, coordinator, bindings, completionControllers);
            continuationRuntime = new DspStationRouteContinuationRuntimeFactory().create(
                    world, coordinator, orderCatalog, loadPlanRegistry,
                    new DspRouteDeriver(new InMemoryProductMasterRepository(List.of(
                            new ProductMasterRecord("third-party-product", "Third Party", Optional.of("Y74"),
                                    Optional.of(PACK_DIMENSIONS)),
                            new ProductMasterRecord("adapted-product", "Adapted", Optional.empty(),
                                    Optional.of(PACK_DIMENSIONS)),
                            new ProductMasterRecord("regular-product", "Regular", Optional.empty(),
                                    Optional.of(PACK_DIMENSIONS))))),
                    new StationRouteContinuationSelector(),
                    new OperationalStationRouteContinuationTargetResolver(
                            adaptingArea, new AdaptingVisitFactory()), topology.catalog(),
                    transportQueue, transportPublisher);
            composed = true;
        }

        private RoutedPhysicalTote bootstrapTote() {
            PhysicalToteId id = new PhysicalToteId("continuation-bootstrap");
            ToteLoadPlan plan = new ToteLoadPlan(id, List.of());
            OperationalPhysicalToteIdentity identity = new OperationalPhysicalToteIdentity(
                    OperationalPhysicalToteSource.OSR, id, new OrderSheetKey("bootstrap", 1),
                    OrderType.FULL_PACK, "SC-1", PhysicalToteRole.INBOUND_PACK, 0);
            OperationalPhysicalToteReleaseRequest release = new OperationalPhysicalToteReleaseRequest(
                    identity, List.of("pharmacy-1"), Duration.ZERO, Optional.empty());
            OperationalRouteLaunchRequest request = new OperationalRouteLaunchRequest(
                    release, topology.p2pDestination());
            RenderableObject visual = renderable(id.value());
            Tote tote = new Tote(id.value(),
                    new RouteFollower(id.value(), topology.p2pTipper(), 0.25f, 1d),
                    visual, new Vec3(), 0f);
            tote.setInteractionMode(Tote.ToteMotionState.HELD);
            return new RoutedPhysicalTote(request, plan, tote, visual);
        }

        private P2pPhysicalToteAssignment pin(PhysicalToteId id) {
            leases.acquireLease(p2pLine.lineId(), "SC-1", P2pLineActivitySnapshot.idle());
            P2pPhysicalToteAssignment assignment = new P2pPhysicalToteAssignment(
                    id, "SC-1", p2pLine.lineId(), p2pLine.destination());
            leases.commitAssignment(assignment);
            return assignment;
        }

        private void activateInbound(PhysicalToteId id) {
            inboundLifecycle.activate(id, Duration.ZERO);
        }

        private Av02AllocatedTote allocateAv02(NotionalToteOrder order, PhysicalToteId id) {
            PhysicalToteRecord record = av02Lifecycle.allocateFor(order, Duration.ZERO, id);
            OperationalPhysicalToteIdentity identity = new OperationalPhysicalToteIdentity(
                    OperationalPhysicalToteSource.AV02, id, order.orderSheetKey(),
                    OrderType.EMPTY, order.serviceCentreId(), PhysicalToteRole.PRE_P2P, 0);
            return new Av02AllocatedTote(identity, record, "pharmacy-1");
        }

        private void allocateAndDepartAv02(NotionalToteOrder order, PhysicalToteId id) {
            av02Inventory.store(allocateAv02(order, id));
            av02Inventory.recordDeparture(id);
        }

        private void stageCollect(NotionalToteOrder order, OrderSheetKey sourceSheet) {
            order.items().stream()
                    .filter(item -> item.lineType() == DspOrderLineType.ADAPTED)
                    .forEach(item -> adaptedStore.stage(item, sourceSheet, order.serviceCentreId()));
        }

        private OperationalRouteLaunchRequest osrRequest(
                NotionalToteOrder order,
                PhysicalToteId id,
                OperationalRouteDestination destination,
                P2pPhysicalToteAssignment assignment,
                long sourceSequence) {
            ensureInitialPlan(order, id);
            OperationalPhysicalToteIdentity identity = new OperationalPhysicalToteIdentity(
                    OperationalPhysicalToteSource.OSR, id, order.orderSheetKey(),
                    order.orderType(), order.serviceCentreId(), PhysicalToteRole.INBOUND_PACK,
                    sourceSequence);
            OperationalPhysicalToteReleaseRequest release = new OperationalPhysicalToteReleaseRequest(
                    identity, List.of("pharmacy-1"), Duration.ZERO,
                    Optional.ofNullable(assignment));
            return new OperationalRouteLaunchRequest(release, destination);
        }

        private OperationalRouteLaunchRequest av02Request(
                NotionalToteOrder order,
                PhysicalToteId id,
                OperationalRouteDestination destination,
                P2pPhysicalToteAssignment assignment,
                long sourceSequence) {
            ensureInitialPlan(order, id);
            OperationalPhysicalToteIdentity identity = new OperationalPhysicalToteIdentity(
                    OperationalPhysicalToteSource.AV02, id, order.orderSheetKey(),
                    OrderType.EMPTY, order.serviceCentreId(), PhysicalToteRole.PRE_P2P,
                    sourceSequence);
            OperationalPhysicalToteReleaseRequest release = new OperationalPhysicalToteReleaseRequest(
                    identity, List.of("pharmacy-1"), Duration.ZERO, Optional.of(assignment));
            return new OperationalRouteLaunchRequest(release, destination);
        }

        private void ensureInitialPlan(NotionalToteOrder order, PhysicalToteId id) {
            if (loadPlanRegistry.getLoadPlanFor(id) != null) {
                return;
            }
            List<PackPlan> plans = order.orderType() == OrderType.EMPTY
                    ? List.of()
                    : List.of(new PackPlan("initial-pack-" + id.value(),
                            order.orderId(), PACK_DIMENSIONS));
            loadPlanRegistry.putLoadPlan(new ToteLoadPlan(id, plans));
        }

        private OperationalRouteLaunchRequest barrierRequest(
                NotionalToteOrder order, PhysicalToteId id) {
            return osrRequest(order, id, topology.blockerDestination(), null, 101);
        }

        private RoutedPhysicalTote launchAndClaim(OperationalRouteLaunchRequest request) {
            launchQueue.enqueue(request);
            step(0d);
            arriveTransportOnly(request.physicalToteId(), request.destination());
            step(0d);
            RoutedPhysicalTote arrived = coordinator.requireActiveClaim(request.physicalToteId())
                    .routedTote();
            latestById.put(request.physicalToteId(), arrived);
            stationHistory.add(arrived.destination());
            return arrived;
        }

        private RoutedPhysicalTote reenterAndClaim(
                PhysicalToteId id, OperationalRouteDestination destination) {
            step(0d);
            arriveTransportOnly(id, destination);
            step(0d);
            RoutedPhysicalTote arrived = coordinator.requireActiveClaim(id).routedTote();
            latestById.put(id, arrived);
            stationHistory.add(arrived.destination());
            return arrived;
        }

        private void arriveTransportOnly(PhysicalToteId id, OperationalRouteDestination destination) {
            Tote tote = world.tote(id.value());
            if (tote == null) {
                throw new AssertionError("No production-published tote for " + id.value()
                        + " launch=" + routeLaunchController.snapshot()
                        + " ingress=" + ingressController.snapshot()
                        + " queue=" + transportQueue.snapshot());
            }
            if (eventContext.getTrackedObjects().stream().noneMatch(candidate -> candidate == tote)) {
                eventContext.addTrackedObject(tote);
            }
            eventContext.setSimulationTimeSeconds(simulationTime);
            WarehouseRouteDefinition definition = topology.catalog().find(destination).orElseThrow();
            tote.getRouteFollower().setCurrentSegment(definition.terminalSegment());
            tote.getRouteFollower().setDistanceAlongSegment(definition.terminalSegment().length());
            arrivalController.handleDetection(
                    new DetectionEvent("terminal", simulationTime,
                            definition.terminalArrivalSensorId(), id.value(), DetectionType.ENTER),
                    eventContext);
            arrivalController.update(eventContext, 0d);
        }

        private void installTransportBackpressure(
                OperationalRouteLaunchRequest blocker,
                OperationalRouteLaunchRequest filler) {
            launchQueue.enqueue(blocker);
            step(0d); // blocker occupies the one in-flight slot.
            launchQueue.enqueue(filler);
            step(0d); // filler remains at the outbound FIFO head.
            assertEquals(1, transportQueue.snapshot().occupancy());
            assertEquals(1, inFlightRegistry.snapshot().occupancy());
        }

        private void advanceUntil(
                BooleanSupplier condition,
                SimulationWorld simulationWorld,
                double dtSeconds,
                int maxUpdates) {
            if (simulationWorld != world) {
                throw new IllegalArgumentException("Scenario advance must use its composed world");
            }
            for (int update = 0; update < maxUpdates; update++) {
                if (condition.getAsBoolean()) {
                    return;
                }
                step(dtSeconds);
            }
            if (!condition.getAsBoolean()) {
                throw new AssertionError("Bounded scenario did not reach its expected state");
            }
        }

        private void step(double dtSeconds) {
            world.update(dtSeconds);
            simulationTime += dtSeconds;
        }

        private Tote latestTote(PhysicalToteId id) {
            return latestById.get(id).tote();
        }

        private ToteLoadPlan currentPlan(PhysicalToteId id) {
            return loadPlanRegistry.getLoadPlanFor(id);
        }

        private int publishedToteCount(PhysicalToteId id) {
            return world.trackables.stream().filter(tote -> tote.getId().equals(id.value())).toList().size();
        }

        private ContinuationState continuationState() {
            Map<PhysicalToteId, IdentityRef<ToteLoadPlan>> planRefs = new LinkedHashMap<>();
            Map<PhysicalToteId, IdentityRef<RoutedPhysicalTote>> routedRefs = new LinkedHashMap<>();
            Map<PhysicalToteId, IdentityRef<RenderableObject>> renderableRefs = new LinkedHashMap<>();
            Map<PhysicalToteId, Integer> renderableOccurrences = new LinkedHashMap<>();
            Map<PhysicalToteId, ToteState> toteStates = new LinkedHashMap<>();
            Map<PhysicalToteId, Optional<P2pPhysicalToteAssignment>> assignments = new LinkedHashMap<>();
            Map<PhysicalToteId, WarehouseTransportPublicationState> publicationStates =
                    new LinkedHashMap<>();
            for (Map.Entry<PhysicalToteId, RoutedPhysicalTote> entry : latestById.entrySet()) {
                PhysicalToteId id = entry.getKey();
                RoutedPhysicalTote routed = entry.getValue();
                planRefs.put(id, IdentityRef.of(loadPlanRegistry.getLoadPlanFor(id)));
                routedRefs.put(id, IdentityRef.of(routed));
                renderableRefs.put(id, IdentityRef.of(routed.renderable()));
                renderableOccurrences.put(id, (int) renderables.stream()
                        .filter(renderable -> renderable == routed.renderable()).count());
                assignments.put(id, routed.p2pAssignment());
                publicationStates.put(id, transportPublisher.publicationState(routed));
                Tote tote = routed.tote();
                toteStates.put(id, new ToteState(
                        tote.getInteractionMode(), tote.areLidsOpen(), routed.renderable().isVisible(),
                        tote.getRouteFollower().getCurrentSegment(),
                        (float) tote.getRouteFollower().getDistanceAlongSegment(),
                        tote.getRouteFollower().getTravelDirection()));
            }
            Map<AdaptingBenchId, AdaptingBenchSnapshot> adapting = Map.of(
                    new AdaptingBenchId(topology.adaptingDestination().targetId()),
                    adaptingArea.bench(new AdaptingBenchId(topology.adaptingDestination().targetId()))
                            .snapshot());
            Map<OperationalRouteDestination, MachineWaitQueueSnapshot> p2p = Map.of(
                    topology.p2pDestination(), p2pInputQueue.snapshot());
            return new ContinuationState(
                    arrivalRegistry.snapshots(), coordinator.snapshot(), planRefs, routedRefs,
                    ledger.snapshot(), thirdPartyArea.snapshot(), adapting, p2p,
                    transportQueue.snapshot(), inFlightRegistry.snapshot(),
                    arrivalRegistry.snapshots(), toteStates, renderableRefs,
                    renderables.size(), renderableOccurrences, publicationStates, assignments,
                    outboundAllocator.snapshot(),
                    List.copyOf(bagReceiver.getReceivedBags()));
        }

        private OperationalRouteDestination thirdPartyDestination() {
            return topology.thirdPartyDestination();
        }

        private OperationalRouteDestination adaptingDestination() {
            return topology.adaptingDestination();
        }

        private OperationalRouteDestination p2pDestination() {
            return topology.p2pDestination();
        }

        private OperationalRouteDestination blockerDestination() {
            return topology.blockerDestination();
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
    }

    private record ToteState(
            Tote.ToteMotionState motion,
            boolean lidsOpen,
            boolean visible,
            RouteSegment routeSegment,
            float distance,
            TravelDirection direction) { }

    private record ContinuationState(
            List<StationRoutedToteArrivalQueueSnapshot> stationFifos,
            StationProcessingSnapshot coordinator,
            Map<PhysicalToteId, IdentityRef<ToteLoadPlan>> planRefs,
            Map<PhysicalToteId, IdentityRef<RoutedPhysicalTote>> routedRefs,
            PhysicalToteLifecycleSnapshot lifecycle,
            ThirdPartyAreaSnapshot thirdParty,
            Map<AdaptingBenchId, AdaptingBenchSnapshot> adapting,
            Map<OperationalRouteDestination, MachineWaitQueueSnapshot> p2pInputs,
            OsrOutboundTransportQueueSnapshot transport,
            WarehouseTransportInFlightSnapshot inFlight,
            List<StationRoutedToteArrivalQueueSnapshot> arrivals,
            Map<PhysicalToteId, ToteState> totes,
            Map<PhysicalToteId, IdentityRef<RenderableObject>> renderableRefs,
            int renderableListCount,
            Map<PhysicalToteId, Integer> renderableOccurrences,
            Map<PhysicalToteId, WarehouseTransportPublicationState> publicationStates,
            Map<PhysicalToteId, Optional<P2pPhysicalToteAssignment>> assignments,
            OutboundAllocationSnapshot outbound,
            List<online.davisfamily.warehouse.sim.totebag.bag.Bag> bags) { }

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

    private static final class RecordingSimulationWorld extends SimulationWorld {
        private final Map<String, Tote> trackablesById = new LinkedHashMap<>();
        private final List<Tote> trackables = new ArrayList<>();

        @Override
        public void addTrackableObject(TrackableObject object) {
            super.addTrackableObject(object);
            if (object instanceof Tote tote && trackablesById.putIfAbsent(tote.getId(), tote) == null) {
                trackables.add(tote);
            }
        }

        private Tote tote(String id) {
            return trackablesById.get(id);
        }
    }

    private record RouteTopology(
            WarehouseRouteCatalog catalog,
            TransferZoneMachine firstMachine,
            TransferZoneMachine secondMachine,
            List<WarehouseTransferRoutingTable.Entry> transferEntries,
            OperationalRouteDestination thirdPartyDestination,
            OperationalRouteDestination adaptingDestination,
            OperationalRouteDestination p2pDestination,
            OperationalRouteDestination blockerDestination,
            RouteSegment p2pTerminal,
            RouteSegment p2pTipper,
            List<TransferZoneMachine> transferMachines) {

        private static RouteTopology create() {
            RouteSegment entry = segment("common-entry", 2f);
            RouteSegment thirdPartyTerminal = segment("third-party-terminal", 2f);
            RouteSegment sharedBranch = segment("shared-branch", 2f);
            RouteSegment p2pTerminal = segment("p2p-terminal", 1f);
            RouteSegment p2pTipper = segment("p2p-tipper", 1f);
            RouteSegment adaptingTerminal = segment("adapting-terminal", 2f);
            entry.connectTo(thirdPartyTerminal);
            sharedBranch.connectTo(p2pTerminal);
            p2pTerminal.connectTo(p2pTipper);

            TransferZoneMachine firstMachine = machine("machine-1", entry, sharedBranch);
            TransferZoneMachine secondMachine = machine("machine-2", sharedBranch, adaptingTerminal);
            TransferTarget sharedTarget = new TransferTarget(sharedBranch, 0f, TravelDirection.FORWARD);
            TransferTarget adaptingTarget = new TransferTarget(
                    adaptingTerminal, 0.25f, TravelDirection.REVERSE);

            OperationalRouteDestination thirdParty = destination(StationType.THIRD_PARTY, "third-party");
            OperationalRouteDestination adapting = destination(StationType.ADAPTING, "adapting-bench");
            OperationalRouteDestination p2p = destination(StationType.P2P, "p2p-line");
            OperationalRouteDestination blocker = destination(StationType.P2P, "backpressure-blocker");
            WarehouseRouteCatalog catalog = new WarehouseRouteCatalog(List.of(
                    definition(thirdParty, entry, thirdPartyTerminal),
                    definition(adapting, entry, adaptingTerminal),
                    definition(p2p, entry, p2pTerminal),
                    definition(blocker, entry, p2pTerminal)));
            List<WarehouseTransferRoutingTable.Entry> entries = List.of(
                    new WarehouseTransferRoutingTable.Entry(firstMachine.getId(), thirdParty.targetId(),
                            TransferRoutingDecision.continueOnCurrentRoute()),
                    new WarehouseTransferRoutingTable.Entry(firstMachine.getId(), adapting.targetId(),
                            TransferRoutingDecision.transferTo(sharedTarget)),
                    new WarehouseTransferRoutingTable.Entry(firstMachine.getId(), p2p.targetId(),
                            TransferRoutingDecision.transferTo(sharedTarget)),
                    new WarehouseTransferRoutingTable.Entry(firstMachine.getId(), blocker.targetId(),
                            TransferRoutingDecision.transferTo(sharedTarget)),
                    new WarehouseTransferRoutingTable.Entry(secondMachine.getId(), adapting.targetId(),
                            TransferRoutingDecision.transferTo(adaptingTarget,
                                    TransferOrientationPolicy.ALIGN_TO_TARGET_TRAVEL)),
                    new WarehouseTransferRoutingTable.Entry(secondMachine.getId(), p2p.targetId(),
                            TransferRoutingDecision.continueOnCurrentRoute()),
                    new WarehouseTransferRoutingTable.Entry(secondMachine.getId(), blocker.targetId(),
                            TransferRoutingDecision.continueOnCurrentRoute()));
            return new RouteTopology(catalog, firstMachine, secondMachine, entries,
                    thirdParty, adapting, p2p, blocker, p2pTerminal, p2pTipper,
                    List.of(firstMachine, secondMachine));
        }

        private static WarehouseRouteDefinition definition(
                OperationalRouteDestination destination,
                RouteSegment entry,
                RouteSegment terminal) {
            return new WarehouseRouteDefinition(
                    destination, entry, 0f, TravelDirection.FORWARD,
                    destination.targetId() + "-sensor", terminal);
        }

        private static OperationalRouteDestination destination(StationType type, String id) {
            return new OperationalRouteDestination(type, id);
        }

        private static TransferZoneMachine machine(
                String id, RouteSegment source, RouteSegment target) {
            TransferZone zone = new TransferZone(
                    id + "-zone", 0f, 0.5f, source, target, 0f,
                    GuideSide.LEFT, GuideSide.RIGHT,
                    (online.davisfamily.warehouse.sim.transfer.strategy.TransferTargetDecisionStrategy)
                            (tote, machine) -> Optional.empty(),
                    new TransferMotionConfig(1d, 0f, 0.25f));
            return new TransferZoneMachine(id, id + "-approach", id + "-window", zone);
        }
    }
}
