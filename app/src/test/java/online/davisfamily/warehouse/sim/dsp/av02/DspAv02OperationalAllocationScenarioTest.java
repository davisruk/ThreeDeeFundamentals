package online.davisfamily.warehouse.sim.dsp.av02;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
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
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptedLineStore;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptedLineStoreSnapshot;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingArea;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingAreaController;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingBench;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingBenchCompletion;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingBenchId;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingBenchSelection;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingBenchSnapshot;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingStorageMap;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingVisit;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingVisitFactory;
import online.davisfamily.warehouse.sim.dsp.adapting.DefaultCollectedPackPlanFactory;
import online.davisfamily.warehouse.sim.dsp.adapting.MapBackedToteLoadPlanRegistry;
import online.davisfamily.warehouse.sim.dsp.adapting.MutableToteLoadPlanRegistry;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingStationProcessingController;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingStationProcessingTarget;
import online.davisfamily.warehouse.sim.dsp.bagging.BagKey;
import online.davisfamily.warehouse.sim.dsp.bagging.BagPlanningResult;
import online.davisfamily.warehouse.sim.dsp.bagging.DspPackPlanFactory;
import online.davisfamily.warehouse.sim.dsp.bagging.PackSourceProvenance;
import online.davisfamily.warehouse.sim.dsp.bagging.PackProvenanceRegistry;
import online.davisfamily.warehouse.sim.dsp.bagging.PackProvenanceSnapshot;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedBag;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedPackTrace;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteLifecycleController;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifestCatalog;
import online.davisfamily.warehouse.sim.dsp.lifecycle.Av02ToteLifecycleController;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteAssignment;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteAssignmentEndReason;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteAssignmentStage;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleLedger;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleSnapshot;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleState;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRole;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.ProductMasterRecord;
import online.davisfamily.warehouse.sim.dsp.model.StartLocation;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.OsrInventoryConfig;
import online.davisfamily.warehouse.sim.dsp.osr.OsrInventorySnapshot;
import online.davisfamily.warehouse.sim.dsp.osr.OsrPhysicalInventory;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteLaunchRequest;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteIdentity;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteSource;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OsrOutboundRouteLaunchController;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OsrOutboundRouteLaunchControllerSnapshot;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OsrOutboundRouteLaunchQueue;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OsrOutboundRouteLaunchQueueSnapshot;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OsrOutboundRouteLaunchTargetRegistry;
import online.davisfamily.warehouse.sim.dsp.osr.release.OperationalPhysicalToteReleaseRequest;
import online.davisfamily.warehouse.sim.dsp.p2p.allocation.DeadlineAwareElasticStickyP2pLineAllocationPolicy;
import online.davisfamily.warehouse.sim.dsp.p2p.allocation.DspP2pElasticAllocationRuntime;
import online.davisfamily.warehouse.sim.dsp.p2p.allocation.DspP2pElasticAllocationRuntimeSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.allocation.ElasticRuntimeTestFixture;
import online.davisfamily.warehouse.sim.dsp.p2p.arrival.ContainedPackP2pTipperPayloadFactory;
import online.davisfamily.warehouse.sim.dsp.p2p.arrival.P2pArrivalRouteBinding;
import online.davisfamily.warehouse.sim.dsp.p2p.arrival.P2pStationProcessingTarget;
import online.davisfamily.warehouse.sim.dsp.p2p.arrival.P2pTipperArrivalTarget;
import online.davisfamily.warehouse.sim.dsp.p2p.arrival.P2pTipperArrivalTargetSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.arrival.StationProcessingP2pToteCompletedListener;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.InboundLifecycleP2pToteCompletedListener;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.OperationalLifecycleP2pToteCompletedListener;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineDefinition;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.StickyP2pArrivalAdmissionPolicy;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pPhysicalToteAssignment;
import online.davisfamily.warehouse.sim.dsp.runtime.DspSchedulerRuntimeState;
import online.davisfamily.warehouse.sim.dsp.runtime.operational.DspOperationalReleaseControllerSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspOrderStatus;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationCapacity;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;
import online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements;
import online.davisfamily.warehouse.sim.dsp.supply.DspSupplySnapshot;
import online.davisfamily.warehouse.sim.dsp.supply.ServiceCentreAuthorizationState;
import online.davisfamily.warehouse.sim.dsp.supply.ServiceCentreSupplySnapshot;
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClock;
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClockConfig;
import online.davisfamily.warehouse.sim.dsp.runtime.operational.DspOperationalReleaseRuntime;
import online.davisfamily.warehouse.sim.dsp.runtime.operational.DspOperationalReleaseRuntimeFactory;
import online.davisfamily.warehouse.sim.dsp.runtime.operational.SynchronousOperationalReleaseEvaluationSource;
import online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalReleaseEvaluation;
import online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalReleaseScheduler;
import online.davisfamily.warehouse.sim.dsp.scheduler.operational.OperationalReleaseBlockType;
import online.davisfamily.warehouse.sim.dsp.scheduler.operational.OperationalDependencyReadinessPolicy;
import online.davisfamily.warehouse.sim.dsp.scheduler.operational.OperationalRouteEntryAdmissionPolicy;
import online.davisfamily.warehouse.sim.dsp.scheduler.operational.PharmacyGroupedSourceSequenceRankingPolicy;
import online.davisfamily.warehouse.sim.dsp.routing.DspRouteDeriver;
import online.davisfamily.warehouse.sim.dsp.routing.InMemoryProductMasterRepository;
import online.davisfamily.warehouse.sim.dsp.transport.LoadPlanOsrOutboundToteHydrator;
import online.davisfamily.warehouse.sim.dsp.transport.OsrOutboundTransportQueue;
import online.davisfamily.warehouse.sim.dsp.transport.OsrOutboundTransportQueueSnapshot;
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;
import online.davisfamily.warehouse.sim.dsp.transport.routing.RouteBoundDetachedOutboundToteFactory;
import online.davisfamily.warehouse.sim.dsp.transport.routing.SimulationWorldWarehouseTransportPublisher;
import online.davisfamily.warehouse.sim.dsp.transport.routing.StationRoutedToteArrivalQueue;
import online.davisfamily.warehouse.sim.dsp.transport.routing.StationRoutedToteArrivalQueueSnapshot;
import online.davisfamily.warehouse.sim.dsp.transport.routing.StationRoutedToteArrivalRegistry;
import online.davisfamily.warehouse.sim.dsp.transport.routing.WarehouseRouteCatalog;
import online.davisfamily.warehouse.sim.dsp.transport.routing.WarehouseRouteDefinition;
import online.davisfamily.warehouse.sim.dsp.transport.routing.WarehouseTransportArrivalController;
import online.davisfamily.warehouse.sim.dsp.transport.routing.WarehouseTransportArrivalControllerSnapshot;
import online.davisfamily.warehouse.sim.dsp.transport.routing.WarehouseTransportIngressController;
import online.davisfamily.warehouse.sim.dsp.transport.routing.WarehouseTransportIngressControllerSnapshot;
import online.davisfamily.warehouse.sim.dsp.transport.routing.WarehouseTransportInFlightRegistry;
import online.davisfamily.warehouse.sim.dsp.transport.routing.WarehouseTransportInFlightSnapshot;
import online.davisfamily.warehouse.sim.dsp.transport.routing.WarehouseTransportPublisher;
import online.davisfamily.warehouse.sim.dsp.station.continuation.DspStationRouteContinuationRuntime;
import online.davisfamily.warehouse.sim.dsp.station.continuation.DspStationRouteContinuationRuntimeFactory;
import online.davisfamily.warehouse.sim.dsp.station.continuation.OperationalStationRouteContinuationTargetResolver;
import online.davisfamily.warehouse.sim.dsp.station.continuation.StationRouteContinuationControllerSnapshot;
import online.davisfamily.warehouse.sim.dsp.station.continuation.StationRouteContinuationSelector;
import online.davisfamily.warehouse.sim.dsp.station.processing.DspStationProcessingRuntime;
import online.davisfamily.warehouse.sim.dsp.station.processing.DspStationProcessingRuntimeFactory;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationArrivalClaimControllerSnapshot;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingBinding;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingCoordinator;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingDisposition;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingDispositionType;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingOrderCatalog;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingSnapshot;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ProductMasterThirdPartyPackPlanFactory;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyArea;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyAreaConfig;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyAreaController;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyAreaSnapshot;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyStationProcessingController;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyStationProcessingTarget;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyVisitFactory;
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
import online.davisfamily.warehouse.sim.dsp.outbound.DeterministicOutboundToteIdSource;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundAllocationSnapshot;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteAllocationController;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteAllocator;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteConfig;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteClosureReason;
import online.davisfamily.warehouse.sim.dsp.outbound.OutputSheetAllocator;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;
import online.davisfamily.warehouse.sim.totebag.handoff.BagReservation;
import online.davisfamily.warehouse.sim.totebag.plan.BagSpec;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;

/** Real allocation-boundary scenario for operational EMPTY work. */
class DspAv02OperationalAllocationScenarioTest {

    private static final String SERVICE_CENTRE_104 = "104";
    private static final String SERVICE_CENTRE_108 = "108";
    private static final int PRIORITY_104 = 999;
    private static final int PRIORITY_108 = 998;

    private static final OrderSheetKey EMPTY_DIRECT_104 = sheet("empty-direct-104");
    private static final OrderSheetKey EMPTY_THIRD_PARTY_104 = sheet("empty-third-party-104");
    private static final OrderSheetKey EMPTY_ADAPTED_104 = sheet("empty-adapted-104");
    private static final OrderSheetKey EMPTY_DIRECT_108 = sheet("empty-direct-108");
    private static final PreparedLineKey EMPTY_ADAPTED_LINE =
            new PreparedLineKey(EMPTY_ADAPTED_104.orderId(), "adapted-line-empty-104");

    @Test
    void shouldAllocateOnlyAuthorizedDependencyReadyEmptyWithinCapacity() {
        ScenarioFixture dependencyFixture = new ScenarioFixture(List.of(
                emptyOrder(
                        EMPTY_ADAPTED_104.orderId(), SERVICE_CENTRE_104, PRIORITY_104, 3,
                        "pharmacy-104-b", DspOrderLineType.ADAPTED)));
        dependencyFixture.authorizeAllEmptySheets();
        Av02AllocationSnapshot dependencyBlocked = dependencyFixture.freshAllocationSnapshot();
        assertEquals(List.of(Av02AllocationBlockReason.DEPENDENCY_NOT_READY),
                dependencyFixture.candidate(dependencyBlocked, EMPTY_ADAPTED_104).blockReasons());
        assertTrue(dependencyBlocked.command().isEmpty());
        AllocationState dependencyBefore = dependencyFixture.state();
        dependencyFixture.submitCurrentAllocationCommand();
        dependencyFixture.update();
        assertEquals(dependencyBefore, dependencyFixture.state());
        assertTrue(dependencyFixture.av02Inventory.snapshot().waitingTotes().isEmpty());
        assertTrue(dependencyFixture.lifecycle.snapshot().totes().isEmpty());

        ScenarioFixture fixture = new ScenarioFixture();

        fixture.submitCurrentAllocationCommand();
        AllocationState unauthorizedBefore = fixture.state();
        fixture.update();
        assertEquals(unauthorizedBefore, fixture.state());
        assertTrue(fixture.av02Inventory.snapshot().waitingTotes().isEmpty());
        assertTrue(fixture.lifecycle.snapshot().totes().isEmpty());
        assertNull(fixture.loadPlans.getLoadPlanFor(fixture.firstAv02Id()));

        fixture.authorizeAllEmptySheets();
        AllocationState authorizedBeforeCommand = fixture.state();
        fixture.update();
        assertEquals(authorizedBeforeCommand, fixture.state());

        Av02AllocationSnapshot mainSnapshot = fixture.freshAllocationSnapshot();
        Av02AllocationCandidate blockedAdapted = fixture.candidate(
                mainSnapshot, EMPTY_ADAPTED_104);
        assertEquals(List.of(Av02AllocationBlockReason.DEPENDENCY_NOT_READY),
                blockedAdapted.blockReasons());
        assertTrue(mainSnapshot.command().isPresent());
        assertEquals(EMPTY_DIRECT_104,
                mainSnapshot.command().orElseThrow().orderSheetKey());

        fixture.completePreparedInputThroughRealAdaptingStore();
        assertTrue(fixture.runtimeState.snapshot().preparedLineKeys()
                .contains(EMPTY_ADAPTED_LINE));
        assertTrue(fixture.adaptedStore.snapshot().stagedLineKeys()
                .contains(EMPTY_ADAPTED_LINE));
        assertTrue(fixture.av02Inventory.snapshot().waitingTotes().isEmpty());

        Av02AllocationSnapshot ready = fixture.freshAllocationSnapshot();
        assertTrue(fixture.candidate(ready, EMPTY_ADAPTED_104).eligible());
        assertEquals(EMPTY_DIRECT_104, ready.command().orElseThrow().orderSheetKey());

        fixture.submitCurrentAllocationCommand();
        fixture.update();

        PhysicalToteId allocatedId = fixture.firstAv02Id();
        Av02AllocatedTote allocated = fixture.av02Inventory.findWaiting(allocatedId)
                .orElseThrow();
        ToteLoadPlan emptyPlan = fixture.loadPlans.getLoadPlanFor(allocatedId);
        assertNotNull(emptyPlan);
        assertTrue(emptyPlan.getPackPlans().isEmpty());
        assertEquals(allocatedId, emptyPlan.physicalToteId());
        assertEquals(OperationalPhysicalToteSource.AV02, allocated.identity().source());
        assertEquals(OrderType.EMPTY, allocated.identity().orderType());
        assertEquals(PhysicalToteRole.PRE_P2P, allocated.identity().physicalToteRole());
        assertEquals(EMPTY_DIRECT_104, allocated.orderSheetKey());
        assertEquals(SERVICE_CENTRE_104, allocated.serviceCentreId());
        assertEquals(List.of("pharmacy-104-a"), allocated.pharmacyIds());
        assertEquals(1L, allocated.sourceSequenceNumber());
        assertEquals(1, fixture.av02Inventory.snapshot().occupancy());
        assertEquals(1, fixture.lifecycle.snapshot().totes().size());

        PhysicalToteAssignment assignment = fixture.lifecycle
                .activeAssignmentFor(EMPTY_DIRECT_104)
                .orElseThrow();
        assertEquals(allocatedId, assignment.physicalToteId());
        assertEquals(PhysicalToteAssignmentStage.PRE_P2P, assignment.stage());
        assertTrue(assignment.active());
        assertEquals(allocatedId, fixture.lifecycle.tote(allocatedId).orElseThrow().id());
        assertEquals(PhysicalToteRole.PRE_P2P,
                fixture.lifecycle.tote(allocatedId).orElseThrow().role());

        assertTrue(fixture.manifestCatalog.findByPhysicalToteId(allocatedId).isEmpty());
        assertTrue(fixture.osrInventory.snapshot().storedTotes().isEmpty());
        assertTrue(fixture.osrInventory.snapshot().departedTotes().isEmpty());
        assertTrue(fixture.av02Inventory.snapshot().departedTotes().isEmpty());
        assertTrue(fixture.lifecycle.activeAssignmentFor(EMPTY_DIRECT_108).isEmpty());

        Av02AllocationSnapshot capacityBlocked = fixture.freshAllocationSnapshot();
        assertTrue(capacityBlocked.command().isEmpty());
        assertEquals(
                List.of(EMPTY_THIRD_PARTY_104, EMPTY_ADAPTED_104, EMPTY_DIRECT_108),
                capacityBlocked.candidates().stream()
                        .map(Av02AllocationCandidate::orderSheetKey)
                        .toList());
        for (Av02AllocationCandidate candidate : capacityBlocked.candidates()) {
            assertFalse(candidate.eligible());
            assertEquals(List.of(Av02AllocationBlockReason.NO_AV02_CAPACITY),
                    candidate.blockReasons());
        }

        AllocationState capacityBefore = fixture.state();
        fixture.submitCurrentAllocationCommand();
        fixture.update();
        assertEquals(capacityBefore, fixture.state());
        assertEquals(1, fixture.av02Inventory.snapshot().occupancy());
        assertTrue(fixture.av02Inventory.findWaiting(allocatedId).isPresent());
        assertTrue(fixture.lifecycle.activeAssignmentFor(EMPTY_THIRD_PARTY_104).isEmpty());
        assertTrue(fixture.lifecycle.activeAssignmentFor(EMPTY_ADAPTED_104).isEmpty());
        assertTrue(fixture.lifecycle.activeAssignmentFor(EMPTY_DIRECT_108).isEmpty());
    }

    @Test
    void shouldRankOsrAndAv02ThroughOneOperationalReleaseBoundary() {
        assertMixedReleaseBoundary(false);
        assertMixedReleaseBoundary(true);
    }

    private void assertMixedReleaseBoundary(boolean osrFirst) {
        try (MixedRuntimeFixture fixture = new MixedRuntimeFixture(osrFirst)) {
            fixture.assertExactReleaseTargets();

            PhysicalToteId firstExpected = osrFirst
                    ? fixture.osrPhysicalToteId
                    : fixture.av02FirstPhysicalToteId;
            PhysicalToteId secondExpected = osrFirst
                    ? fixture.av02FirstPhysicalToteId
                    : fixture.osrPhysicalToteId;

            fixture.releaseOnce();
            fixture.assertAppliedRelease(firstExpected);
            if (osrFirst) {
                fixture.assertOsrReleased(fixture.osrPhysicalToteId);
                fixture.assertAv02Waiting(fixture.av02FirstPhysicalToteId);
            } else {
                fixture.assertAv02Released(fixture.av02FirstPhysicalToteId);
                fixture.assertThirdPartyFirstDestination(fixture.av02FirstPhysicalToteId);
                fixture.assertOsrWaiting(fixture.osrPhysicalToteId);
            }

            MixedRuntimeState beforeDeferral = fixture.mutableState();
            fixture.releaseOnce();
            DspOperationalReleaseEvaluation deferredEvaluation = fixture.runtime.controller()
                    .snapshot().lastEvaluation().orElseThrow();
            assertTrue(deferredEvaluation.releaseDecision().isEmpty());
            assertTrue(deferredEvaluation.blockedCandidates().stream()
                    .filter(candidate -> candidate.physicalToteId().equals(secondExpected))
                    .flatMap(candidate -> candidate.blocks().stream())
                    .anyMatch(block -> block.type() == OperationalReleaseBlockType.STATION_ADMISSION));
            assertEquals(beforeDeferral, fixture.mutableState());

            fixture.launchHead();
            fixture.releaseOnce();
            fixture.assertAppliedRelease(secondExpected);
            if (osrFirst) {
                fixture.assertAv02Released(fixture.av02FirstPhysicalToteId);
                fixture.assertThirdPartyFirstDestination(fixture.av02FirstPhysicalToteId);
            } else {
                fixture.assertOsrReleased(fixture.osrPhysicalToteId);
            }
            fixture.launchHead();

            fixture.assertAuthorized108AndRemaining104Work();
            fixture.allocateRemainingAv02ThroughProductionPath();
            fixture.releaseOnce();
            fixture.assertAppliedRelease(fixture.av02SecondPhysicalToteId);
            fixture.assertAv02Released(fixture.av02SecondPhysicalToteId);
            fixture.assertThirdPartyFirstDestination(fixture.av02SecondPhysicalToteId);
            fixture.launchHead();
        }
    }

    @Test
    void shouldPreserveAv02IdentityThroughThirdPartyAndAdaptingToP2pCompletion() {
        assertThirdPartyJourneyWithContinuationBackpressure();
        assertAdaptingJourneyToP2pCompletion();
    }

    @Test
    void shouldConsumeAv02InboundAndAllocateIndependentOutboundTote() {
        try (JourneyFixture fixture = JourneyFixture.createDirectP2p()) {
            RoutedPhysicalTote inbound = fixture.releaseAndHydrate();
            assertEquals(OperationalPhysicalToteSource.AV02, inbound.launchRequest().source());
            assertEquals(EMPTY_DIRECT_104, inbound.launchRequest().orderSheetKey());
            assertEquals(StationType.P2P, inbound.destination().stationType());
            assertEquals(fixture.p2pLine.lineId(),
                    inbound.launchRequest().p2pAssignment().orElseThrow().lineId());

            fixture.arriveAt(fixture.topology.p2pDestination());
            fixture.step(0d);
            assertSame(inbound,
                    fixture.coordinator.requireActiveClaim(fixture.physicalToteId).routedTote());
            assertTrue(fixture.p2pInputQueue.snapshot().toteIds()
                    .contains(fixture.physicalToteId.value()));
            assertTrue(fixture.bagReceiver.getReceivedBags().isEmpty());
            assertTrue(fixture.outboundAllocator.snapshot().allocatedBags().isEmpty());
            OperationalEmptyState beforeTipperCompletion = fixture.state();

            fixture.advanceUntil(
                    () -> fixture.allocation.lifecycle.tote(fixture.physicalToteId)
                            .orElseThrow().state() == PhysicalToteLifecycleState.CONSUMED_AT_P2P,
                    0.25d, 80, "direct EMPTY P2P completion");
            fixture.assertDirectP2pTerminalCompletion(inbound);
            assertEquals(PhysicalToteLifecycleState.ACTIVE_PRE_P2P,
                    beforeTipperCompletion.lifecycle().totes()
                            .get(fixture.physicalToteId).state());
            assertTrue(beforeTipperCompletion.outbound().allocatedBags().isEmpty());
            assertTrue(beforeTipperCompletion.bags().isEmpty());

            OperationalEmptyState beforeBagReceiver = fixture.state();
            PlannedBag plannedBag = directEmptyPlannedBag(fixture.order);
            String physicalPackId = plannedBag.physicalPackIds().getFirst();
            PlannedPackTrace packTrace = new PlannedPackTrace(
                    physicalPackId,
                    new PackSourceProvenance(
                            EMPTY_DIRECT_104,
                            fixture.order.items().getFirst().lineReference(),
                            fixture.order.items().getFirst().productId(),
                            SERVICE_CENTRE_104,
                            fixture.order.items().getFirst().pharmacyId(),
                            fixture.order.items().getFirst().patientId(),
                            fixture.order.items().getFirst().prescriptionId()),
                    fixture.physicalToteId,
                    EMPTY_DIRECT_104,
                    plannedBag.bagKey());
            BagPlanningResult planningResult = new BagPlanningResult(
                    List.of(plannedBag), List.of(), List.of(packTrace));
            Bag runtimeBag = runtimeBag(plannedBag);
            BagReservation reservation = fixture.bagReceiver.reserveIncomingBag(runtimeBag);
            fixture.bagReceiver.beginReceiving(reservation);
            fixture.bagReceiver.completeReceiving(reservation);
            fixture.assertStateUnchangedExceptBags(beforeBagReceiver, fixture.state());

            OutboundToteAllocationController outboundController =
                    new OutboundToteAllocationController(
                            fixture.p2pLine.lineId(), fixture.bagReceiver,
                            planningResult, fixture.outboundAllocator);
            SimulationContext outboundContext = new SimulationContext();
            outboundContext.setSimulationTimeSeconds(fixture.simulationTime + 1d);
            outboundController.update(outboundContext, 0.1d);

            assertTrue(fixture.bagReceiver.getReceivedBags().isEmpty());
            OutboundAllocationSnapshot allocatedSnapshot = fixture.outboundAllocator.snapshot();
            assertEquals(1, allocatedSnapshot.allocatedBags().size());
            var allocatedBag = allocatedSnapshot.allocatedBags().getFirst();
            assertSame(plannedBag, allocatedBag.plannedBag());
            assertEquals(fixture.p2pLine.lineId(),
                    allocatedSnapshot.openToteFor(fixture.p2pLine.lineId())
                            .orElseThrow().p2pLineId());
            assertEquals("outbound-" + fixture.p2pLine.lineId().value() + "-1",
                    allocatedBag.outboundPhysicalToteId().value());
            assertEquals(List.of(EMPTY_DIRECT_104), plannedBag.owningOrderSheetKeys());
            assertEquals(EMPTY_DIRECT_104, packTrace.sourceProvenance().sourceOrderSheetKey());
            assertEquals(fixture.physicalToteId, packTrace.inputPhysicalToteId());
            assertEquals(EMPTY_DIRECT_104, packTrace.fulfilmentOrderSheetKey());
            assertEquals(plannedBag.bagKey(), packTrace.bagKey());

            var outputSheet = allocatedBag.outputSheetAllocations().getFirst();
            assertEquals(EMPTY_DIRECT_104, outputSheet.sourceOwningSheetKey());
            assertEquals(EMPTY_DIRECT_104, outputSheet.outputSheetKey());
            assertFalse(outputSheet.generated());
            var outboundTote = allocatedSnapshot.openToteFor(fixture.p2pLine.lineId())
                    .orElseThrow();
            assertEquals(1, outboundTote.bagCount());
            assertEquals(SERVICE_CENTRE_104, outboundTote.serviceCentreId().orElseThrow());
            assertEquals("pharmacy-104-a", outboundTote.pharmacyId().orElseThrow());
            assertEquals(PhysicalToteLifecycleState.OUTBOUND_BAG_TOTE,
                    fixture.allocation.lifecycle.tote(outboundTote.physicalToteId())
                            .orElseThrow().state());
            assertEquals(PhysicalToteRole.OUTBOUND_BAG,
                    fixture.allocation.lifecycle.tote(outboundTote.physicalToteId())
                            .orElseThrow().role());
            assertEquals(PhysicalToteAssignmentStage.OUTBOUND_BAG,
                    fixture.allocation.lifecycle.activeAssignmentFor(EMPTY_DIRECT_104)
                            .orElseThrow().stage());
            assertEquals(fixture.physicalToteId,
                    fixture.allocation.lifecycle.assignmentHistoryFor(EMPTY_DIRECT_104)
                            .getFirst().physicalToteId());
            assertEquals(PhysicalToteLifecycleState.CONSUMED_AT_P2P,
                    fixture.allocation.lifecycle.tote(fixture.physicalToteId)
                            .orElseThrow().state());
            assertTrue(fixture.allocation.av02Inventory.snapshot().departedTotes().stream()
                    .noneMatch(tote -> tote.physicalToteId().equals(outboundTote.physicalToteId())));
            assertTrue(fixture.allocation.osrInventory.snapshot().storedTotes().stream()
                    .noneMatch(tote -> tote.physicalToteId().equals(outboundTote.physicalToteId())));
            assertTrue(fixture.allocation.osrInventory.snapshot().departedTotes().stream()
                    .noneMatch(tote -> tote.physicalToteId().equals(outboundTote.physicalToteId())));
            assertTrue(fixture.allocation.manifestCatalog
                    .findByPhysicalToteId(outboundTote.physicalToteId()).isEmpty());

            OperationalEmptyState afterAllocation = fixture.state();
            outboundController.update(outboundContext, 0.1d);
            fixture.assertStateUnchangedExceptReleaseDiagnostics(
                    afterAllocation, fixture.state());

            var closedTote = fixture.outboundAllocator
                    .closeForApplicableWorkCompletion(
                            fixture.p2pLine.lineId(),
                            Duration.ofNanos(Math.round((fixture.simulationTime + 2d)
                                    * 1_000_000_000d)))
                    .orElseThrow();
            OperationalEmptyState afterOutboundClose = fixture.state();
            assertEquals(OutboundToteClosureReason.APPLICABLE_WORK_COMPLETE,
                    closedTote.closureReason().orElseThrow());
            assertEquals(1, closedTote.bagCount());
            assertEquals(SERVICE_CENTRE_104, closedTote.serviceCentreId().orElseThrow());
            assertEquals("pharmacy-104-a", closedTote.pharmacyId().orElseThrow());
            assertEquals(PhysicalToteLifecycleState.OUTBOUND,
                    fixture.allocation.lifecycle.tote(closedTote.physicalToteId())
                            .orElseThrow().state());
            assertEquals(PhysicalToteAssignmentStage.OUTBOUND,
                    fixture.allocation.lifecycle.activeAssignmentFor(EMPTY_DIRECT_104)
                            .orElseThrow().stage());
            assertEquals(PhysicalToteAssignmentEndReason.OUTBOUND_TOTE_CLOSED,
                    fixture.allocation.lifecycle.assignmentHistoryFor(EMPTY_DIRECT_104)
                            .get(1).endReason().orElseThrow());
            assertEquals(PhysicalToteLifecycleState.CONSUMED_AT_P2P,
                    fixture.allocation.lifecycle.tote(fixture.physicalToteId)
                            .orElseThrow().state());
            assertTrue(afterOutboundClose.outbound().closedTotes().stream()
                    .anyMatch(tote -> tote.physicalToteId().equals(closedTote.physicalToteId())));
            assertTrue(fixture.allocation.av02Inventory.snapshot().departedTotes().stream()
                    .noneMatch(tote -> tote.physicalToteId().equals(closedTote.physicalToteId())));
            assertTrue(fixture.allocation.osrInventory.snapshot().storedTotes().stream()
                    .noneMatch(tote -> tote.physicalToteId().equals(closedTote.physicalToteId())));
            assertTrue(fixture.allocation.osrInventory.snapshot().departedTotes().stream()
                    .noneMatch(tote -> tote.physicalToteId().equals(closedTote.physicalToteId())));
            assertTrue(fixture.allocation.manifestCatalog
                    .findByPhysicalToteId(closedTote.physicalToteId()).isEmpty());
            assertEquals(physicalPackId,
                    allocatedBag.plannedBag().physicalPackIds().getFirst());
            assertEquals(fixture.physicalToteId, packTrace.inputPhysicalToteId());
        }
    }

    private void assertThirdPartyJourneyWithContinuationBackpressure() {
        try (JourneyFixture fixture = JourneyFixture.create(false)) {
            RoutedPhysicalTote first = fixture.releaseAndHydrate();
            RoutedIdentityState launchIdentity = fixture.identityState(first);
            assertEquals(OperationalPhysicalToteSource.AV02, first.launchRequest().source());
            assertEquals(EMPTY_THIRD_PARTY_104, first.launchRequest().orderSheetKey());
            assertEquals(StationType.THIRD_PARTY, first.destination().stationType());
            assertEquals("third-party-1", first.destination().targetId());
            assertEquals(fixture.order.sequenceNumber(),
                    first.launchRequest().identity().sourceSequenceNumber());
            assertEquals(Duration.ZERO, first.launchRequest().releaseTime());
            assertTrue(first.launchRequest().p2pAssignment().isPresent());
            assertEquals(fixture.p2pLine.lineId(),
                    first.launchRequest().p2pAssignment().orElseThrow().lineId());
            assertEquals(1, fixture.inFlightRegistry.snapshot().occupancy());
            assertSame(first, fixture.inFlightRegistry.find(fixture.physicalToteId).orElseThrow());

            fixture.arriveAt(fixture.topology.thirdPartyDestination());
            assertSame(first, fixture.thirdPartyQueue.peek().orElseThrow());
            assertTrue(fixture.coordinator.snapshot().activeClaims().isEmpty());
            fixture.step(0d);
            assertSame(first,
                    fixture.coordinator.requireActiveClaim(fixture.physicalToteId).routedTote());
            assertEquals(StationType.THIRD_PARTY,
                    fixture.coordinator.requireActiveClaim(fixture.physicalToteId)
                            .destination().stationType());
            assertTrue(fixture.p2pInputQueue.snapshot().toteIds().isEmpty());

            ToteLoadPlan originalPlan = first.loadPlan();
            RoutedPhysicalTote blocker = fixture.fillContinuationQueue();
            fixture.step(1d);
            StationProcessingDisposition disposition = fixture.coordinator.peekDisposition()
                    .orElseThrow();
            assertEquals(StationProcessingDispositionType.CONTINUE, disposition.type());
            ToteLoadPlan replacementPlan = disposition.currentLoadPlan();
            assertNotSame(originalPlan, replacementPlan);
            assertSame(replacementPlan,
                    fixture.allocation.loadPlans.getLoadPlanFor(fixture.physicalToteId));
            assertSame(replacementPlan, disposition.currentLoadPlan());
            assertEquals(List.of("pack-line-empty-third-party-104-1"),
                    replacementPlan.getPackPlans().stream().map(plan -> plan.packId()).toList());
            assertEquals(EMPTY_THIRD_PARTY_104,
                    fixture.provenanceRegistry.find("pack-line-empty-third-party-104-1")
                            .orElseThrow().sourceOrderSheetKey());
            assertSame(first, disposition.claim().routedTote());
            assertSame(first.tote(), disposition.claim().routedTote().tote());
            assertSame(first.renderable(), disposition.claim().routedTote().renderable());
            assertEquals(1, fixture.transportQueue.snapshot().occupancy());
            assertEquals(0, fixture.continuationRuntime.snapshot().continuedCount());

            OperationalEmptyState beforeBlockedRetry = fixture.state();
            fixture.step(0d);
            fixture.assertStateUnchangedExceptReleaseDiagnostics(
                    beforeBlockedRetry, fixture.state());
            assertSame(disposition, fixture.coordinator.peekDisposition().orElseThrow());
            assertTrue(fixture.continuationRuntime.snapshot().blocked());
            assertEquals(fixture.physicalToteId,
                    fixture.continuationRuntime.snapshot().blockedPhysicalToteId()
                            .orElseThrow());

            assertSame(blocker, fixture.transportQueue.dequeue().orElseThrow());
            fixture.releaseContinuationBlocker(blocker);
            fixture.step(0d);
            assertTrue(fixture.coordinator.pendingDispositions().isEmpty());
            RoutedPhysicalTote continued = fixture.transportQueue.peek().orElseThrow();
            assertEquals(fixture.topology.p2pDestination(), continued.destination());
            assertIdentityContinuity(first, continued, replacementPlan,
                    launchIdentity.releaseRequest());
            assertEquals(1, fixture.continuationRuntime.snapshot().continuedCount());

            fixture.step(0d);
            RoutedPhysicalTote reentered = fixture.inFlightRegistry.find(fixture.physicalToteId)
                    .orElseThrow();
            assertSame(continued, reentered);
            fixture.arriveAt(fixture.topology.p2pDestination());
            assertSame(continued, fixture.p2pQueue.peek().orElseThrow());
            fixture.step(0d);
            assertSame(continued,
                    fixture.coordinator.requireActiveClaim(fixture.physicalToteId).routedTote());
            assertTrue(fixture.p2pInputQueue.snapshot().toteIds()
                    .contains(fixture.physicalToteId.value()));
            assertEquals(PhysicalToteLifecycleState.ACTIVE_PRE_P2P,
                    fixture.allocation.lifecycle.tote(fixture.physicalToteId)
                            .orElseThrow().state());
            assertTrue(fixture.coordinator.pendingDispositions().isEmpty());
            assertEquals(0, fixture.outboundAllocator.snapshot().allocatedBags().size());

            OperationalEmptyState beforeWrongObject = fixture.state();
            assertThrows(IllegalStateException.class,
                    () -> fixture.p2pCompletionListener.onToteCompleted(
                            fixture.wrongTote(), fixture.eventContext()));
            fixture.assertStateUnchangedExceptReleaseDiagnostics(
                    beforeWrongObject, fixture.state());
            assertSame(continued,
                    fixture.coordinator.requireActiveClaim(fixture.physicalToteId).routedTote());

            fixture.advanceUntil(
                    () -> fixture.allocation.lifecycle.tote(fixture.physicalToteId)
                            .orElseThrow().state() == PhysicalToteLifecycleState.CONSUMED_AT_P2P,
                    0.25d, 80, "Third Party EMPTY P2P completion");
            fixture.assertTerminalCompletion(continued);
            assertIdentityContinuity(first, continued, replacementPlan,
                    launchIdentity.releaseRequest());
            assertEquals(List.of(fixture.topology.thirdPartyDestination(),
                    fixture.topology.p2pDestination()), fixture.stationHistory);
            assertEquals(1, fixture.ingressController.snapshot().initialPublicationCount());
            assertEquals(1, fixture.ingressController.snapshot().exactObjectReentryCount());

            OperationalEmptyState terminal = fixture.state();
            fixture.assertStateUnchangedExceptReleaseDiagnostics(terminal, fixture.state());
            assertThrows(IllegalStateException.class,
                    () -> fixture.p2pCompletionListener.onToteCompleted(
                            fixture.wrongTote(), fixture.eventContext()));
            fixture.assertStateUnchangedExceptReleaseDiagnostics(terminal, fixture.state());
            assertThrows(IllegalStateException.class,
                    () -> fixture.p2pCompletionListener.onToteCompleted(
                            fixture.wrongTote(), fixture.eventContext()));
            fixture.assertStateUnchangedExceptReleaseDiagnostics(terminal, fixture.state());
        }
    }

    private void assertAdaptingJourneyToP2pCompletion() {
        try (JourneyFixture fixture = JourneyFixture.create(true)) {
            assertTrue(fixture.allocation.runtimeState.snapshot().preparedLineKeys()
                    .contains(EMPTY_ADAPTED_LINE));
            assertTrue(fixture.allocation.adaptedStore.snapshot().stagedLineKeys()
                    .contains(EMPTY_ADAPTED_LINE));

            RoutedPhysicalTote first = fixture.releaseAndHydrate();
            RoutedIdentityState launchIdentity = fixture.identityState(first);
            assertEquals(OperationalPhysicalToteSource.AV02, first.launchRequest().source());
            assertEquals(EMPTY_ADAPTED_104, first.launchRequest().orderSheetKey());
            assertEquals(StationType.ADAPTING, first.destination().stationType());
            assertEquals("adapting-1", first.destination().targetId());
            assertTrue(first.launchRequest().p2pAssignment().isPresent());
            assertEquals(fixture.p2pLine.lineId(),
                    first.launchRequest().p2pAssignment().orElseThrow().lineId());
            assertTrue(fixture.manifestCatalog.findByPhysicalToteId(fixture.physicalToteId)
                    .isEmpty());

            fixture.arriveAt(fixture.topology.adaptingDestination());
            assertSame(first, fixture.adaptingQueue.peek().orElseThrow());
            fixture.step(0d);
            assertSame(first,
                    fixture.coordinator.requireActiveClaim(fixture.physicalToteId).routedTote());
            assertTrue(fixture.p2pInputQueue.snapshot().toteIds().isEmpty());

            ToteLoadPlan originalPlan = first.loadPlan();
            fixture.step(1d);
            ToteLoadPlan replacementPlan = fixture.allocation.loadPlans
                    .getLoadPlanFor(fixture.physicalToteId);
            assertNotSame(originalPlan, replacementPlan);
            assertEquals(List.of("pack-adapted-line-empty-104-1"),
                    replacementPlan.getPackPlans().stream().map(plan -> plan.packId()).toList());
            assertEquals(new OrderSheetKey("prepared-empty-adapted-104", 1),
                    fixture.provenanceRegistry.find("pack-adapted-line-empty-104-1")
                            .orElseThrow().sourceOrderSheetKey());
            assertFalse(fixture.allocation.adaptedStore.contains(EMPTY_ADAPTED_LINE));

            RoutedPhysicalTote continued = fixture.transportQueue.peek().orElseThrow();
            assertEquals(fixture.topology.p2pDestination(), continued.destination());
            assertIdentityContinuity(first, continued, replacementPlan,
                    launchIdentity.releaseRequest());
            assertEquals(1, fixture.continuationRuntime.snapshot().continuedCount());

            fixture.step(0d);
            assertTrue(fixture.coordinator.pendingDispositions().isEmpty());

            fixture.step(0d);
            assertSame(continued,
                    fixture.inFlightRegistry.find(fixture.physicalToteId).orElseThrow());
            fixture.arriveAt(fixture.topology.p2pDestination());
            assertSame(continued, fixture.p2pQueue.peek().orElseThrow());
            fixture.step(0d);
            assertSame(continued,
                    fixture.coordinator.requireActiveClaim(fixture.physicalToteId).routedTote());
            assertTrue(fixture.p2pInputQueue.snapshot().toteIds()
                    .contains(fixture.physicalToteId.value()));
            assertEquals(PhysicalToteLifecycleState.ACTIVE_PRE_P2P,
                    fixture.allocation.lifecycle.tote(fixture.physicalToteId)
                            .orElseThrow().state());
            assertTrue(fixture.coordinator.pendingDispositions().isEmpty());

            OperationalEmptyState beforeWrongObject = fixture.state();
            assertThrows(IllegalStateException.class,
                    () -> fixture.p2pCompletionListener.onToteCompleted(
                            fixture.wrongTote(), fixture.eventContext()));
            fixture.assertStateUnchangedExceptReleaseDiagnostics(
                    beforeWrongObject, fixture.state());

            fixture.advanceUntil(
                    () -> fixture.allocation.lifecycle.tote(fixture.physicalToteId)
                            .orElseThrow().state() == PhysicalToteLifecycleState.CONSUMED_AT_P2P,
                    0.25d, 80, "Adapting EMPTY P2P completion");
            fixture.assertTerminalCompletion(continued);
            assertEquals(List.of(fixture.topology.adaptingDestination(),
                    fixture.topology.p2pDestination()), fixture.stationHistory);
            assertEquals(1, fixture.ingressController.snapshot().initialPublicationCount());
            assertEquals(1, fixture.ingressController.snapshot().exactObjectReentryCount());

            OperationalEmptyState terminal = fixture.state();
            fixture.assertStateUnchangedExceptReleaseDiagnostics(terminal, fixture.state());
            assertThrows(IllegalStateException.class,
                    () -> fixture.p2pCompletionListener.onToteCompleted(
                            fixture.wrongTote(), fixture.eventContext()));
            fixture.assertStateUnchangedExceptReleaseDiagnostics(terminal, fixture.state());
            assertThrows(IllegalStateException.class,
                    () -> fixture.p2pCompletionListener.onToteCompleted(
                            fixture.wrongTote(), fixture.eventContext()));
            fixture.assertStateUnchangedExceptReleaseDiagnostics(terminal, fixture.state());
        }
    }

    private static void assertIdentityContinuity(
            RoutedPhysicalTote previous,
            RoutedPhysicalTote next,
            ToteLoadPlan expectedPlan,
            OperationalPhysicalToteReleaseRequest releaseRequest) {
        assertNotSame(previous, next);
        assertSame(releaseRequest, previous.launchRequest().releaseRequest());
        assertSame(releaseRequest, next.launchRequest().releaseRequest());
        assertSame(previous.launchRequest().identity(), next.launchRequest().identity());
        assertSame(previous.tote(), next.tote());
        assertSame(previous.renderable(), next.renderable());
        assertSame(previous.tote().getRouteFollower(), next.tote().getRouteFollower());
        assertSame(expectedPlan, next.loadPlan());
        assertSame(previous.launchRequest().p2pAssignment().orElseThrow(),
                next.launchRequest().p2pAssignment().orElseThrow());
        assertEquals(previous.launchRequest().source(), next.launchRequest().source());
        assertEquals(previous.physicalToteId(), next.physicalToteId());
        assertEquals(previous.launchRequest().orderSheetKey(), next.launchRequest().orderSheetKey());
        assertEquals(previous.launchRequest().orderType(), next.launchRequest().orderType());
        assertEquals(previous.launchRequest().serviceCentreId(),
                next.launchRequest().serviceCentreId());
        assertEquals(previous.launchRequest().pharmacyIds(),
                next.launchRequest().pharmacyIds());
        assertEquals(previous.launchRequest().identity().sourceSequenceNumber(),
                next.launchRequest().identity().sourceSequenceNumber());
        assertEquals(previous.launchRequest().releaseTime(),
                next.launchRequest().releaseTime());
    }

    private static OrderSheetKey sheet(String orderId) {
        return new OrderSheetKey(orderId, 1);
    }

    private static DspSchedulerOrderState state(NotionalToteOrder order) {
        return new DspSchedulerOrderState(
                order,
                new RouteRequirements(false, false, false, true, false, StartLocation.AV02),
                DspOrderStatus.WAITING);
    }

    private static NotionalToteOrder emptyOrder(
            String orderId,
            String serviceCentreId,
            int priority,
            long sequence,
            String pharmacyId,
            DspOrderLineType lineType) {
        String lineReference = orderId.equals(EMPTY_ADAPTED_104.orderId())
                ? "adapted-line-empty-104" : "line-" + orderId;
        return new NotionalToteOrder(
                orderId,
                orderId,
                serviceCentreId,
                1,
                OrderType.EMPTY,
                List.of(new DspOrderItem(
                        lineReference,
                        "product-" + lineReference,
                        1,
                        pharmacyId,
                        "patient-" + orderId,
                        "prescription-" + orderId,
                        lineType,
                        orderId,
                        1,
                        0)),
                priority,
                sequence);
    }

    private static NotionalToteOrder fullPackOrder(
            String orderId,
            String serviceCentreId,
            int priority,
            long sequence,
            String pharmacyId) {
        return new NotionalToteOrder(
                orderId,
                "notional-" + orderId,
                serviceCentreId,
                1,
                OrderType.FULL_PACK,
                List.of(new DspOrderItem(
                        "line-" + orderId,
                        "product-" + orderId,
                        1,
                        pharmacyId,
                        "patient-" + orderId,
                        "prescription-" + orderId,
                        DspOrderLineType.FULL_PACK,
                        orderId,
                        1,
                        0)),
                priority,
                sequence);
    }

    private static DspSchedulerOrderState mixedLogicalState(
            NotionalToteOrder order,
            StartLocation startLocation) {
        return new DspSchedulerOrderState(
                order,
                new RouteRequirements(true, false, false, true, false, startLocation),
                DspOrderStatus.WAITING);
    }

    private static List<OperationalRouteDestination> mixedDestinations(
            ElasticRuntimeTestFixture elasticFixture) {
        List<OperationalRouteDestination> destinations = new ArrayList<>();
        destinations.add(new OperationalRouteDestination(
                StationType.THIRD_PARTY, "third-party-1"));
        destinations.add(new OperationalRouteDestination(
                StationType.ADAPTING, "adapting-1"));
        destinations.addAll(elasticFixture.definitions().stream()
                .map(definition -> definition.destination())
                .toList());
        return List.copyOf(destinations);
    }

    private static DspOperationalReleaseScheduler elasticScheduler() {
        return new DspOperationalReleaseScheduler(
                new OperationalDependencyReadinessPolicy(),
                new OperationalRouteEntryAdmissionPolicy(),
                new PharmacyGroupedSourceSequenceRankingPolicy(),
                new DeadlineAwareElasticStickyP2pLineAllocationPolicy());
    }

    private static DspOrderItem preparedLine(NotionalToteOrder adaptedOrder) {
        return new DspOrderItem(
                adaptedOrder.items().getFirst().lineReference(),
                "prepared-product-empty-104",
                1,
                adaptedOrder.items().getFirst().pharmacyId(),
                "prepared-patient-empty-104",
                "prepared-prescription-empty-104",
                DspOrderLineType.ADAPTED,
                adaptedOrder.orderId(),
                1,
                0);
    }

    private static PlannedBag directEmptyPlannedBag(NotionalToteOrder order) {
        DspOrderItem item = order.items().getFirst();
        String packId = "pack-" + order.orderId() + "-1";
        return new PlannedBag(
                new BagKey(item.prescriptionId(), 1),
                order.serviceCentreId(),
                item.pharmacyId(),
                item.patientId(),
                item.prescriptionId(),
                List.of(packId),
                List.of(order.orderSheetKey()));
    }

    private static Bag runtimeBag(PlannedBag plannedBag) {
        String correlationId = plannedBag.bagKey().correlationId();
        return new Bag(
                "runtime-" + correlationId,
                correlationId,
                plannedBag.physicalPackIds().stream()
                        .map(packId -> new PackPlan(
                                packId,
                                correlationId,
                                new PackDimensions(0.20f, 0.10f, 0.08f)))
                        .toList(),
                new BagSpec(0.34f, 0.28f, 0.22f));
    }

    private enum JourneyStart {
        THIRD_PARTY,
        ADAPTING,
        P2P
    }

    private static final class JourneyFixture implements AutoCloseable {
        private static final PackDimensions PACK_DIMENSIONS =
                new PackDimensions(0.20f, 0.10f, 0.08f);

        private final ScenarioFixture allocation;
        private final RecordingSimulationWorld world;
        private final NotionalToteOrder order;
        private final JourneyStart firstStation;
        private final boolean adaptingFirst;
        private final boolean directP2p;
        private final PhysicalToteId physicalToteId = new PhysicalToteId("av02-000001");
        private final ElasticRuntimeTestFixture elasticFixture =
                new ElasticRuntimeTestFixture();
        private final InboundToteManifestCatalog manifestCatalog =
                new InboundToteManifestCatalog(List.of());
        private final InboundToteLifecycleController inboundLifecycle;
        private final Av02ToteLifecycleController av02Lifecycle;
        private final StationProcessingCoordinator coordinator =
                new StationProcessingCoordinator();
        private final StationProcessingOrderCatalog orderCatalog;
        private final PackProvenanceRegistry provenanceRegistry =
                new PackProvenanceRegistry();
        private final StoredBagReceiver bagReceiver = new StoredBagReceiver("journey-bags");
        private final OutputSheetAllocator outputSheetAllocator;
        private final OutboundToteAllocator outboundAllocator;
        private final JourneyTopology topology = JourneyTopology.create();
        private final P2pLineDefinition p2pLine;
        private final OsrOutboundRouteLaunchQueue launchQueue =
                new OsrOutboundRouteLaunchQueue("journey-launch", 1);
        private final OsrOutboundTransportQueue transportQueue =
                new OsrOutboundTransportQueue("journey-transport", 1);
        private final WarehouseTransportInFlightRegistry inFlightRegistry =
                new WarehouseTransportInFlightRegistry(1);
        private final List<RenderableObject> renderables = new ArrayList<>();
        private final Map<OperationalRouteDestination, StationRoutedToteArrivalQueue>
                stationQueues = new java.util.LinkedHashMap<>();
        private final StationRoutedToteArrivalRegistry arrivalRegistry;
        private final DspP2pElasticAllocationRuntime elasticRuntime;
        private final OsrOutboundRouteLaunchTargetRegistry routeTargetRegistry;
        private final DspOperationalReleaseRuntime operationalRuntime;
        private final OsrOutboundRouteLaunchController launchController;
        private final WarehouseTransportIngressController ingressController;
        private final WarehouseTransportArrivalController arrivalController;
        private final WarehouseTransportPublisher transportPublisher;
        private final ThirdPartyArea thirdPartyArea;
        private final ThirdPartyAreaController thirdPartyAreaController;
        private final AdaptingArea adaptingArea;
        private final AdaptingAreaController adaptingAreaController;
        private final P2pTipperArrivalTarget p2pTarget;
        private final TipperInputQueue p2pInputQueue =
                new TipperInputQueue("journey-p2p-input", 1);
        private final StationProcessingBinding thirdPartyBinding;
        private final StationProcessingBinding adaptingBinding;
        private final StationProcessingBinding p2pBinding;
        private final StationRoutedToteArrivalQueue thirdPartyQueue;
        private final StationRoutedToteArrivalQueue adaptingQueue;
        private final StationRoutedToteArrivalQueue p2pQueue;
        private final StationProcessingP2pToteCompletedListener p2pCompletionListener;
        private final SimulationContext eventContext = new SimulationContext();
        private final List<OperationalRouteDestination> stationHistory = new ArrayList<>();
        private final Map<PhysicalToteId, RoutedPhysicalTote> latestById =
                new java.util.LinkedHashMap<>();
        private final RoutedPhysicalTote bootstrap;
        private DspStationProcessingRuntime stationRuntime;
        private DspStationRouteContinuationRuntime continuationRuntime;
        private ToteTrackTipperFlowController tipperFlow;
        private boolean composed;
        private double simulationTime;

        private JourneyFixture(boolean adaptingFirst) {
            this(adaptingFirst ? JourneyStart.ADAPTING : JourneyStart.THIRD_PARTY);
        }

        private JourneyFixture(JourneyStart firstStation) {
            if (firstStation == null) {
                throw new IllegalArgumentException("firstStation must not be null");
            }
            this.firstStation = firstStation;
            this.adaptingFirst = firstStation == JourneyStart.ADAPTING;
            this.directP2p = firstStation == JourneyStart.P2P;
            OrderSheetKey sheet = adaptingFirst
                    ? EMPTY_ADAPTED_104
                    : directP2p ? EMPTY_DIRECT_104 : EMPTY_THIRD_PARTY_104;
            String pharmacy = adaptingFirst ? "pharmacy-104-b" : "pharmacy-104-a";
            DspOrderLineType lineType = adaptingFirst
                    ? DspOrderLineType.ADAPTED : DspOrderLineType.FULL_PACK;
            order = emptyOrder(sheet.orderId(), SERVICE_CENTRE_104, PRIORITY_104,
                    adaptingFirst ? 3 : directP2p ? 1 : 2, pharmacy, lineType);
            allocation = new ScenarioFixture(List.of(order));
            world = allocation.world;
            allocation.authorizeAllEmptySheets();
            if (adaptingFirst) {
                allocation.completePreparedInputThroughRealAdaptingStore();
            }
            allocation.submitCurrentAllocationCommand();
            allocation.update();
            assertTrue(allocation.av02Inventory.findWaiting(physicalToteId).isPresent());

            orderCatalog = new StationProcessingOrderCatalog(List.of(order));
            inboundLifecycle = new InboundToteLifecycleController(
                    allocation.lifecycle, manifestCatalog);
            av02Lifecycle = new Av02ToteLifecycleController(
                    allocation.lifecycle, new DeterministicAv02PhysicalToteIdAllocator());
            outputSheetAllocator = new OutputSheetAllocator(List.of(order.orderSheetKey()));
            outboundAllocator = new OutboundToteAllocator(
                    allocation.lifecycle, new DeterministicOutboundToteIdSource(),
                    outputSheetAllocator, new OutboundToteConfig(4));

            InMemoryProductMasterRepository products = new InMemoryProductMasterRepository(List.of(
                    new ProductMasterRecord(
                            order.items().getFirst().productId(),
                            adaptingFirst ? "Adapted EMPTY product" : "Third Party EMPTY product",
                            adaptingFirst ? Optional.empty() : Optional.of("Y74"),
                            Optional.of(PACK_DIMENSIONS))));
            DspPackPlanFactory packPlanFactory = new DspPackPlanFactory(provenanceRegistry);
            thirdPartyArea = new ThirdPartyArea(new ThirdPartyAreaConfig(0, 1, 1d));
            thirdPartyAreaController = new ThirdPartyAreaController(
                    thirdPartyArea, allocation.loadPlans,
                    new ProductMasterThirdPartyPackPlanFactory(
                            products,
                            (visit, lineWork) -> visit.orderSheetKey().orderId(),
                            packPlanFactory));

            AdaptingStorageMap storageMap = new AdaptingStorageMap();
            AdaptingBenchId adaptingBenchId = new AdaptingBenchId("adapting-1");
            storageMap.configureAvailableBenches(List.of(adaptingBenchId));
            storageMap.assignPharmacyToBench(pharmacy, adaptingBenchId);
            adaptingArea = new AdaptingArea(
                    List.of(new AdaptingBench(adaptingBenchId.value(), allocation.adaptedStore, 1d)),
                    0, storageMap);
            adaptingAreaController = new AdaptingAreaController(
                    adaptingArea,
                    allocation.runtimeState,
                    allocation.loadPlans,
                    new DefaultCollectedPackPlanFactory(
                            PACK_DIMENSIONS, packPlanFactory));

            p2pLine = elasticFixture.definitions().getFirst();
            thirdPartyQueue = new StationRoutedToteArrivalQueue(
                    topology.thirdPartyDestination(), 1);
            adaptingQueue = new StationRoutedToteArrivalQueue(
                    topology.adaptingDestination(), 1);
            p2pQueue = new StationRoutedToteArrivalQueue(
                    topology.p2pDestination(), 1);
            stationQueues.put(topology.thirdPartyDestination(), thirdPartyQueue);
            stationQueues.put(topology.adaptingDestination(), adaptingQueue);
            stationQueues.put(topology.p2pDestination(), p2pQueue);
            for (P2pLineDefinition definition : elasticFixture.definitions()) {
                stationQueues.putIfAbsent(definition.destination(),
                        new StationRoutedToteArrivalQueue(definition.destination(), 1));
            }
            arrivalRegistry = new StationRoutedToteArrivalRegistry(
                    stationQueues.values().stream().toList());
            transportPublisher = new SimulationWorldWarehouseTransportPublisher(
                    world, renderables);

            DspRouteDeriver routeDeriver = new DspRouteDeriver(products);
            elasticRuntime = elasticFixture.createRuntime(
                    this::logicalSnapshot,
                    manifestCatalog,
                    allocation.lifecycle::snapshot,
                    allocation.av02Inventory::snapshot,
                    this::supplySnapshot);
            routeTargetRegistry = new OsrOutboundRouteLaunchTargetRegistry(
                    launchQueue, mixedDestinations(elasticFixture));
            DspOperationalClock clock = new DspOperationalClock(
                    DspOperationalClockConfig.productionBaseline(LocalDate.of(2026, 8, 26)));
            operationalRuntime = new DspOperationalReleaseRuntimeFactory()
                    .createElasticWithAv02(
                            new SynchronousOperationalReleaseEvaluationSource(elasticScheduler()),
                            allocation.osrInventory,
                            inboundLifecycle,
                            manifestCatalog,
                            this::logicalSnapshot,
                            clock::initialSnapshot,
                            MixedRuntimeFixture::openAdmission,
                            routeTargetRegistry,
                            allocation.av02Inventory,
                            allocation.lifecycle,
                            allocation.loadPlans,
                            elasticRuntime);
            world.addController(operationalRuntime.controller());

            RouteBoundDetachedOutboundToteFactory detachedFactory =
                    new RouteBoundDetachedOutboundToteFactory(
                            topology.catalog,
                            (request, plan) -> renderable(request.physicalToteId().value()),
                            1d, new Vec3(), 0f);
            launchController = new OsrOutboundRouteLaunchController(
                    launchQueue, transportQueue,
                    new LoadPlanOsrOutboundToteHydrator(allocation.loadPlans, detachedFactory));
            ingressController = new WarehouseTransportIngressController(
                    transportQueue, topology.catalog, inFlightRegistry, transportPublisher);
            arrivalController = new WarehouseTransportArrivalController(
                    topology.catalog, inFlightRegistry, arrivalRegistry);

            ThirdPartyVisitFactory thirdPartyVisitFactory = new ThirdPartyVisitFactory(products);
            ThirdPartyStationProcessingTarget thirdPartyTarget =
                    new ThirdPartyStationProcessingTarget(
                            topology.thirdPartyDestination(), orderCatalog, allocation.loadPlans,
                            thirdPartyVisitFactory, thirdPartyArea, coordinator);
            AdaptingStationProcessingTarget adaptingTarget =
                    new AdaptingStationProcessingTarget(
                            topology.adaptingDestination(), orderCatalog, allocation.loadPlans,
                            new AdaptingVisitFactory(), adaptingArea, coordinator);
            p2pTarget = new P2pTipperArrivalTarget(
                    topology.p2pDestination(), p2pInputQueue);
            P2pStationProcessingTarget p2pProcessingTarget = new P2pStationProcessingTarget(
                    new StickyP2pArrivalAdmissionPolicy(
                            p2pLine, elasticRuntime::leaseSnapshot),
                    new P2pArrivalRouteBinding(topology.p2pTerminal(), topology.p2pTipper()),
                    new ContainedPackP2pTipperPayloadFactory(1f, 1f, 0f, 0f, 0f, 0f),
                    p2pTarget, coordinator);
            thirdPartyBinding = new StationProcessingBinding(thirdPartyQueue, thirdPartyTarget);
            adaptingBinding = new StationProcessingBinding(adaptingQueue, adaptingTarget);
            p2pBinding = new StationProcessingBinding(p2pQueue, p2pProcessingTarget);

            bootstrap = bootstrapTote();
            world.addTrackableObject(bootstrap.tote());
            InboundLifecycleP2pToteCompletedListener inboundP2pListener =
                    new InboundLifecycleP2pToteCompletedListener(inboundLifecycle);
            OperationalLifecycleP2pToteCompletedListener operationalP2pListener =
                    new OperationalLifecycleP2pToteCompletedListener(
                            manifestCatalog, inboundP2pListener,
                            allocation.av02Inventory, av02Lifecycle);
            p2pCompletionListener = new StationProcessingP2pToteCompletedListener(
                    operationalP2pListener, coordinator);
        }

        private static JourneyFixture create(boolean adaptingFirst) {
            return new JourneyFixture(adaptingFirst);
        }

        private static JourneyFixture createDirectP2p() {
            return new JourneyFixture(JourneyStart.P2P);
        }

        private RoutedPhysicalTote releaseAndHydrate() {
            world.update(0.1d);
            simulationTime += 0.1d;
            OperationalRouteLaunchRequest request = launchQueue.peek().orElseThrow();
            assertEquals(physicalToteId, request.physicalToteId());
            assertEquals(initialDestination().stationType(), request.destination().stationType());
            composeControllers();
            world.update(0d);
            refreshLatestFromInFlight();
            RoutedPhysicalTote routed = latestById.get(physicalToteId);
            if (routed == null) {
                throw new AssertionError("Initial AV02 route was not hydrated into transport");
            }
            assertSame(request, routed.launchRequest());
            assertSame(allocation.loadPlans.getLoadPlanFor(physicalToteId), routed.loadPlan());
            return routed;
        }

        private OperationalRouteDestination initialDestination() {
            return switch (firstStation) {
                case THIRD_PARTY -> topology.thirdPartyDestination();
                case ADAPTING -> topology.adaptingDestination();
                case P2P -> topology.p2pDestination();
            };
        }

        private void composeControllers() {
            if (composed) {
                return;
            }
            world.addController(launchController);
            world.addController(ingressController);
            world.registerListener(DetectionEvent.class, arrivalController.detectionHandler());
            world.addController(arrivalController);

            TippingMachine tippingMachine = new TippingMachine("journey-tipper", 0d, 0d, 0d);
            tipperFlow = new ToteTrackTipperFlowController(
                    bootstrap.tote(),
                    toteId -> toteId.equals(bootstrap.physicalToteId().value())
                            ? bootstrap.loadPlan() : p2pTarget.getLoadPlanFor(toteId),
                    topology.p2pTipper(), 0.25f, -1.02f, tippingMachine,
                    acceptingDownstreamFlow(), 0.01d,
                    (tote, context) -> {
                        if (!tote.getId().equals(bootstrap.physicalToteId().value())) {
                            p2pCompletionListener.onToteCompleted(tote, context);
                        }
                    });
            world.addSimObject(tippingMachine);
            world.addController(tipperFlow);
            world.addController(new TipperInputQueueController(p2pInputQueue, tipperFlow));

            stationRuntime = new DspStationProcessingRuntimeFactory().create(
                    world, coordinator,
                    List.of(thirdPartyBinding, adaptingBinding, p2pBinding),
                    List.of(
                            new AdaptingStationProcessingController(
                                    "journey-adapting",
                                    Set.of(topology.adaptingDestination()),
                                    allocation.loadPlans, adaptingArea, adaptingAreaController,
                                    inboundLifecycle, coordinator),
                            new ThirdPartyStationProcessingController(
                                    "journey-third-party",
                                    Set.of(topology.thirdPartyDestination()),
                                    allocation.loadPlans, thirdPartyAreaController, coordinator)));
            continuationRuntime = new DspStationRouteContinuationRuntimeFactory().create(
                    world, coordinator, orderCatalog, allocation.loadPlans,
                    new DspRouteDeriver(new InMemoryProductMasterRepository(List.of(
                            new ProductMasterRecord(
                                    order.items().getFirst().productId(),
                                    "Journey product",
                                    adaptingFirst ? Optional.empty() : Optional.of("Y74"),
                                    Optional.of(PACK_DIMENSIONS))))),
                    new StationRouteContinuationSelector(),
                    new OperationalStationRouteContinuationTargetResolver(
                            adaptingArea, new AdaptingVisitFactory()),
                    topology.catalog, transportQueue, transportPublisher);
            composed = true;
        }

        private void arriveAt(OperationalRouteDestination destination) {
            RoutedPhysicalTote routed = latestById.get(physicalToteId);
            if (routed == null) {
                throw new AssertionError("No latest routed tote for " + physicalToteId);
            }
            WarehouseRouteDefinition route = topology.catalog.find(destination).orElseThrow();
            routed.tote().getRouteFollower().setCurrentSegment(route.terminalSegment());
            routed.tote().getRouteFollower().setDistanceAlongSegment(route.terminalSegment().length());
            if (eventContext.getTrackedObjects().stream().noneMatch(object -> object == routed.tote())) {
                eventContext.addTrackedObject(routed.tote());
            }
            eventContext.setSimulationTimeSeconds(simulationTime);
            arrivalController.handleDetection(
                    new DetectionEvent(
                            "journey-terminal", simulationTime,
                            route.terminalArrivalSensorId(), physicalToteId.value(),
                            DetectionType.ENTER),
                    eventContext);
            arrivalController.update(eventContext, 0d);
            stationHistory.add(destination);
        }

        private RoutedPhysicalTote fillContinuationQueue() {
            RoutedPhysicalTote blocker = blockerTote();
            transportQueue.enqueue(blocker);
            inFlightRegistry.register(blocker);
            return blocker;
        }

        private void releaseContinuationBlocker(RoutedPhysicalTote blocker) {
            assertSame(blocker, inFlightRegistry.completeArrival(blocker));
        }

        private RoutedPhysicalTote blockerTote() {
            PhysicalToteId id = new PhysicalToteId("journey-blocker");
            OperationalPhysicalToteIdentity identity = new OperationalPhysicalToteIdentity(
                    OperationalPhysicalToteSource.OSR,
                    id,
                    new OrderSheetKey("journey-blocker", 1),
                    OrderType.FULL_PACK,
                    SERVICE_CENTRE_104,
                    PhysicalToteRole.INBOUND_PACK,
                    900);
            OperationalPhysicalToteReleaseRequest release =
                    new OperationalPhysicalToteReleaseRequest(
                            identity, List.of("pharmacy-104-a"), Duration.ZERO, Optional.empty());
            OperationalRouteLaunchRequest request = new OperationalRouteLaunchRequest(
                    release, topology.p2pBlockerDestination());
            RenderableObject visual = renderable(id.value());
            RouteSegment segment = topology.entrySegment();
            Tote tote = new Tote(id.value(),
                    new RouteFollower(id.value(), segment, 0f, 1d),
                    visual, new Vec3(), 0f);
            tote.closeLids();
            return new RoutedPhysicalTote(
                    request, new ToteLoadPlan(id, List.of()), tote, visual);
        }

        private RoutedPhysicalTote bootstrapTote() {
            PhysicalToteId id = new PhysicalToteId("journey-bootstrap");
            ToteLoadPlan plan = new ToteLoadPlan(id, List.of());
            OperationalPhysicalToteIdentity identity = new OperationalPhysicalToteIdentity(
                    OperationalPhysicalToteSource.OSR,
                    id,
                    new OrderSheetKey("journey-bootstrap", 1),
                    OrderType.FULL_PACK,
                    SERVICE_CENTRE_104,
                    PhysicalToteRole.INBOUND_PACK,
                    901);
            OperationalPhysicalToteReleaseRequest release =
                    new OperationalPhysicalToteReleaseRequest(
                            identity, List.of("pharmacy-104-a"), Duration.ZERO, Optional.empty());
            OperationalRouteLaunchRequest request = new OperationalRouteLaunchRequest(
                    release, topology.p2pDestination());
            RenderableObject visual = renderable(id.value());
            Tote tote = new Tote(
                    id.value(),
                    new RouteFollower(id.value(), topology.p2pTipper(), 0.25f, 1d),
                    visual, new Vec3(), 0f);
            tote.setInteractionMode(Tote.ToteMotionState.HELD);
            return new RoutedPhysicalTote(request, plan, tote, visual);
        }

        private Tote wrongTote() {
            RenderableObject visual = renderable(physicalToteId.value());
            return new Tote(
                    physicalToteId.value(),
                    new RouteFollower(physicalToteId.value(), topology.p2pTipper(), 0f, 1d),
                    visual, new Vec3(), 0f);
        }

        private SimulationContext eventContext() {
            return eventContext;
        }

        private RoutedIdentityState identityState(RoutedPhysicalTote routed) {
            return RoutedIdentityState.from(routed);
        }

        private OperationalEmptyState state() {
            RoutedPhysicalTote routed = latestById.get(physicalToteId);
            Map<PhysicalToteId, RoutedIdentityState> identities = new java.util.LinkedHashMap<>();
            if (routed != null) {
                identities.put(physicalToteId, identityState(routed));
            }
            return new OperationalEmptyState(
                    allocation.runtimeState.snapshot(),
                    allocation.supplySnapshot(),
                    allocation.av02Inventory.snapshot(),
                    allocation.osrInventory.snapshot(),
                    allocation.lifecycle.snapshot(),
                    elasticSnapshot(),
                    operationalRuntime.controller().snapshot(),
                    launchQueue.snapshot(),
                    launchController.snapshot(),
                    transportQueue.snapshot(),
                    ingressController.snapshot(),
                    inFlightRegistry.snapshot(),
                    arrivalController.snapshot(),
                    arrivalRegistry.snapshots(),
                    stationRuntime == null ? List.of() : stationRuntime.claimantSnapshots(),
                    coordinator.snapshot(),
                    thirdPartyArea.snapshot(),
                    adaptingArea.bench(new AdaptingBenchId("adapting-1")).snapshot(),
                    allocation.adaptedStore.snapshot(),
                    p2pTarget.snapshot(),
                    p2pInputQueue.snapshot(),
                    continuationRuntime == null ? null : continuationRuntime.snapshot(),
                    identities,
                    world.trackableCount(physicalToteId.value()),
                    renderables.size(),
                    allocation.loadPlans.getLoadPlanFor(physicalToteId),
                    provenanceRegistry.snapshot(),
                    outboundAllocator.snapshot(),
                    List.copyOf(bagReceiver.getReceivedBags()));
        }

        private DspP2pElasticAllocationRuntimeSnapshot elasticSnapshot() {
            try {
                return elasticRuntime.operationalSnapshot();
            } catch (IllegalArgumentException | IllegalStateException terminalPlannerAbsence) {
                // A completed AV02 line can leave its lease visible for one terminal callback
                // while the planner has no remaining service-centre work to describe. Once the
                // source sheet is reused for an independent outbound assignment, the same
                // planner has no AV02 allocation state to describe.
                return null;
            }
        }

        private void assertStateUnchangedExceptReleaseDiagnostics(
                OperationalEmptyState before,
                OperationalEmptyState after) {
            assertEquals(before.scheduler(), after.scheduler());
            assertEquals(before.supply(), after.supply());
            assertEquals(before.av02Inventory(), after.av02Inventory());
            assertEquals(before.osrInventory(), after.osrInventory());
            assertEquals(before.lifecycle(), after.lifecycle());
            assertEquals(before.elastic(), after.elastic());
            assertEquals(before.launchQueue(), after.launchQueue());
            assertEquals(before.launchController(), after.launchController());
            assertEquals(before.transportQueue(), after.transportQueue());
            assertEquals(before.ingress(), after.ingress());
            assertEquals(before.inFlight(), after.inFlight());
            assertEquals(before.arrival(), after.arrival());
            assertEquals(before.stationArrivals(), after.stationArrivals());
            assertEquals(before.claimants(), after.claimants());
            assertEquals(before.coordinator(), after.coordinator());
            assertEquals(before.thirdParty(), after.thirdParty());
            assertEquals(before.adaptingBench(), after.adaptingBench());
            assertEquals(before.adaptedStore(), after.adaptedStore());
            assertEquals(before.p2pTarget(), after.p2pTarget());
            assertEquals(before.p2pInput(), after.p2pInput());
            assertEquals(before.continuation(), after.continuation());
            assertEquals(before.identities(), after.identities());
            assertEquals(before.worldTrackableCount(), after.worldTrackableCount());
            assertEquals(before.renderableCount(), after.renderableCount());
            assertSame(before.currentPlan(), after.currentPlan());
            assertEquals(before.provenance(), after.provenance());
            assertEquals(before.outbound(), after.outbound());
            assertEquals(before.bags(), after.bags());
        }

        private void assertStateUnchangedExceptBags(
                OperationalEmptyState before,
                OperationalEmptyState after) {
            assertEquals(before.scheduler(), after.scheduler());
            assertEquals(before.supply(), after.supply());
            assertEquals(before.av02Inventory(), after.av02Inventory());
            assertEquals(before.osrInventory(), after.osrInventory());
            assertEquals(before.lifecycle(), after.lifecycle());
            assertEquals(before.elastic(), after.elastic());
            assertEquals(before.operational(), after.operational());
            assertEquals(before.launchQueue(), after.launchQueue());
            assertEquals(before.launchController(), after.launchController());
            assertEquals(before.transportQueue(), after.transportQueue());
            assertEquals(before.ingress(), after.ingress());
            assertEquals(before.inFlight(), after.inFlight());
            assertEquals(before.arrival(), after.arrival());
            assertEquals(before.stationArrivals(), after.stationArrivals());
            assertEquals(before.claimants(), after.claimants());
            assertEquals(before.coordinator(), after.coordinator());
            assertEquals(before.thirdParty(), after.thirdParty());
            assertEquals(before.adaptingBench(), after.adaptingBench());
            assertEquals(before.adaptedStore(), after.adaptedStore());
            assertEquals(before.p2pTarget(), after.p2pTarget());
            assertEquals(before.p2pInput(), after.p2pInput());
            assertEquals(before.continuation(), after.continuation());
            assertEquals(before.identities(), after.identities());
            assertEquals(before.worldTrackableCount(), after.worldTrackableCount());
            assertEquals(before.renderableCount(), after.renderableCount());
            assertSame(before.currentPlan(), after.currentPlan());
            assertEquals(before.provenance(), after.provenance());
            assertEquals(before.outbound(), after.outbound());
        }

        private void assertDirectP2pTerminalCompletion(RoutedPhysicalTote routed) {
            assertTrue(coordinator.pendingDispositions().isEmpty());
            assertTrue(coordinator.snapshot().activeClaims().isEmpty());
            assertEquals(1, coordinator.snapshot().completedCount());
            assertEquals(0, coordinator.snapshot().acknowledgedContinuationCount());
            assertEquals(1, coordinator.snapshot().acknowledgedConsumeCount());
            assertEquals(PhysicalToteLifecycleState.CONSUMED_AT_P2P,
                    allocation.lifecycle.tote(physicalToteId).orElseThrow().state());
            assertEquals(PhysicalToteAssignmentEndReason.CONSUMED_AT_P2P,
                    allocation.lifecycle.assignmentHistoryFor(order.orderSheetKey()).getLast()
                            .endReason().orElseThrow());
            assertFalse(routed.renderable().isVisible());
            assertFalse(routed.tote().areLidsOpen());
            assertEquals(Tote.ToteMotionState.HELD, routed.tote().getInteractionMode());
            assertTrue(thirdPartyQueue.snapshot().entries().isEmpty());
            assertTrue(adaptingQueue.snapshot().entries().isEmpty());
            assertTrue(p2pQueue.snapshot().entries().isEmpty());
            assertTrue(p2pInputQueue.snapshot().toteIds().isEmpty());
            assertTrue(transportQueue.snapshot().entries().isEmpty());
            assertTrue(inFlightRegistry.snapshot().entries().isEmpty());
            assertEquals(1, ingressController.snapshot().initialPublicationCount());
            assertEquals(0, ingressController.snapshot().exactObjectReentryCount());
            assertEquals(1, world.trackableCount(physicalToteId.value()));
            assertEquals(1, renderables.size());
            assertEquals(0, outboundAllocator.snapshot().allocatedBags().size());
            assertTrue(bagReceiver.getReceivedBags().isEmpty());
            assertTrue(allocation.av02Inventory.findWaiting(physicalToteId).isEmpty());
            assertTrue(allocation.av02Inventory.snapshot().departedTotes().stream()
                    .anyMatch(tote -> tote.physicalToteId().equals(physicalToteId)));
            assertTrue(allocation.osrInventory.snapshot().storedTotes().isEmpty());
            assertTrue(allocation.osrInventory.snapshot().departedTotes().isEmpty());
            assertTrue(manifestCatalog.findByPhysicalToteId(physicalToteId).isEmpty());
        }

        private void assertTerminalCompletion(RoutedPhysicalTote routed) {
            assertTrue(coordinator.pendingDispositions().isEmpty());
            assertTrue(coordinator.snapshot().activeClaims().isEmpty());
            assertEquals(2, coordinator.snapshot().completedCount());
            assertEquals(1, coordinator.snapshot().acknowledgedContinuationCount());
            assertEquals(1, coordinator.snapshot().acknowledgedConsumeCount());
            assertEquals(PhysicalToteLifecycleState.CONSUMED_AT_P2P,
                    allocation.lifecycle.tote(physicalToteId).orElseThrow().state());
            assertEquals(PhysicalToteAssignmentEndReason.CONSUMED_AT_P2P,
                    allocation.lifecycle.assignmentHistoryFor(order.orderSheetKey()).getLast()
                            .endReason().orElseThrow());
            assertFalse(routed.renderable().isVisible());
            assertFalse(routed.tote().areLidsOpen());
            assertEquals(Tote.ToteMotionState.HELD, routed.tote().getInteractionMode());
            assertTrue(thirdPartyQueue.snapshot().entries().isEmpty());
            assertTrue(adaptingQueue.snapshot().entries().isEmpty());
            assertTrue(p2pQueue.snapshot().entries().isEmpty());
            assertTrue(p2pInputQueue.snapshot().toteIds().isEmpty());
            assertTrue(transportQueue.snapshot().entries().isEmpty());
            assertTrue(inFlightRegistry.snapshot().entries().isEmpty());
            assertEquals(1, ingressController.snapshot().initialPublicationCount());
            assertEquals(1, ingressController.snapshot().exactObjectReentryCount());
            assertEquals(1, world.trackableCount(physicalToteId.value()));
            assertEquals(1, renderables.size());
            assertEquals(0, outboundAllocator.snapshot().allocatedBags().size());
            assertTrue(bagReceiver.getReceivedBags().isEmpty());
        }

        private void advanceUntil(
                BooleanSupplier condition,
                double stepSeconds,
                int maximumSteps,
                String terminalState) {
            if (condition == null || !Double.isFinite(stepSeconds) || stepSeconds <= 0d
                    || maximumSteps <= 0 || terminalState == null || terminalState.isBlank()) {
                throw new IllegalArgumentException("Invalid bounded journey progression request");
            }
            for (int index = 0; index < maximumSteps; index++) {
                if (condition.getAsBoolean()) {
                    return;
                }
                step(stepSeconds);
            }
            if (!condition.getAsBoolean()) {
                throw new AssertionError("Journey did not reach " + terminalState);
            }
        }

        private void step(double dtSeconds) {
            world.update(dtSeconds);
            simulationTime += dtSeconds;
            refreshLatestFromInFlight();
        }

        private void refreshLatestFromInFlight() {
            inFlightRegistry.find(physicalToteId).ifPresent(
                    routed -> latestById.put(physicalToteId, routed));
        }

        private WarehouseSchedulerSnapshot logicalSnapshot() {
            RouteRequirements requirements = switch (firstStation) {
                case THIRD_PARTY ->
                        new RouteRequirements(true, false, false, true, false, StartLocation.AV02);
                case ADAPTING ->
                        new RouteRequirements(false, true, false, true, false, StartLocation.AV02);
                case P2P ->
                        new RouteRequirements(false, false, false, true, false, StartLocation.AV02);
            };
            return new WarehouseSchedulerSnapshot(
                    List.of(new DspSchedulerOrderState(order, requirements, DspOrderStatus.WAITING)),
                    Map.of(), allocation.runtimeState.snapshot().preparedLineKeys(), Optional.empty());
        }

        private DspSupplySnapshot supplySnapshot() {
            ServiceCentreSupplySnapshot serviceCentre = new ServiceCentreSupplySnapshot(
                    SERVICE_CENTRE_104,
                    PRIORITY_104,
                    ServiceCentreAuthorizationState.AUTHORIZED,
                    Optional.of(Duration.ZERO),
                    0,
                    0,
                    0,
                    0,
                    Set.of(order.orderSheetKey()),
                    List.of());
            return new DspSupplySnapshot(
                    "journey-supply",
                    0,
                    1200,
                    0,
                    Optional.empty(),
                    Optional.of(Duration.ZERO),
                    Set.of(order.orderSheetKey()),
                    List.of(serviceCentre),
                    0);
        }

        @Override
        public void close() {
            operationalRuntime.close();
            elasticRuntime.close();
            if (continuationRuntime != null) {
                continuationRuntime.close();
            }
            if (stationRuntime != null) {
                stationRuntime.close();
            }
        }
    }

    private static final class ScenarioFixture {
        private final RecordingSimulationWorld world = new RecordingSimulationWorld();
        private final PhysicalToteLifecycleLedger lifecycle = new PhysicalToteLifecycleLedger();
        private final Av02PhysicalToteInventory av02Inventory =
                new Av02PhysicalToteInventory(new Av02AllocationConfig(1));
        private final OsrPhysicalInventory osrInventory =
                new OsrPhysicalInventory(new OsrInventoryConfig(2, List.of()));
        private final InboundToteManifestCatalog manifestCatalog =
                new InboundToteManifestCatalog(List.of());
        private final MutableToteLoadPlanRegistry loadPlans =
                new MapBackedToteLoadPlanRegistry();
        private final List<NotionalToteOrder> orders;
        private final DspSchedulerRuntimeState runtimeState;
        private final AdaptedLineStore adaptedStore = new AdaptedLineStore();
        private final AdaptingArea adaptingArea;
        private final AdaptingAreaController adaptingAreaController;
        private final Av02AllocationSnapshotFactory snapshotFactory =
                new Av02AllocationSnapshotFactory();
        private final AtomicReference<Optional<AllocateEmptyToteAtAv02Command>> command =
                new AtomicReference<>(Optional.empty());
        private final Av02AllocationController allocationController;
        private final Set<OrderSheetKey> authorizedEmptySheets = new LinkedHashSet<>();
        private long allocationSnapshotSequence;

        private ScenarioFixture() {
            this(List.of(
                    emptyOrder(
                            EMPTY_DIRECT_104.orderId(), SERVICE_CENTRE_104, PRIORITY_104, 1,
                            "pharmacy-104-a", DspOrderLineType.FULL_PACK),
                    emptyOrder(
                            EMPTY_THIRD_PARTY_104.orderId(), SERVICE_CENTRE_104, PRIORITY_104, 2,
                            "pharmacy-104-a", DspOrderLineType.FULL_PACK),
                    emptyOrder(
                            EMPTY_ADAPTED_104.orderId(), SERVICE_CENTRE_104, PRIORITY_104, 3,
                            "pharmacy-104-b", DspOrderLineType.ADAPTED),
                    emptyOrder(
                            EMPTY_DIRECT_108.orderId(), SERVICE_CENTRE_108, PRIORITY_108, 1,
                            "pharmacy-108-a", DspOrderLineType.FULL_PACK)));
        }

        private ScenarioFixture(List<NotionalToteOrder> orders) {
            if (orders == null || orders.isEmpty()) {
                throw new IllegalArgumentException("orders must not be null or empty");
            }
            this.orders = List.copyOf(orders);
            List<DspSchedulerOrderState> states = this.orders.stream()
                    .map(DspAv02OperationalAllocationScenarioTest::state)
                    .toList();
            runtimeState = new DspSchedulerRuntimeState(new WarehouseSchedulerSnapshot(
                    states, Map.of(), Set.of(), Optional.empty()));

            AdaptingStorageMap storageMap = new AdaptingStorageMap();
            AdaptingBenchId benchId = new AdaptingBenchId("av02-preparation-bench");
            storageMap.assignPharmacyToBench("pharmacy-104-b", benchId);
            adaptingArea = new AdaptingArea(
                    List.of(new AdaptingBench(benchId.value(), adaptedStore, 0d)), 0, storageMap);
            adaptingAreaController = new AdaptingAreaController(adaptingArea, runtimeState);

            allocationController = new Av02AllocationController(
                    () -> command.getAndSet(Optional.empty()),
                    this::freshAllocationSnapshot,
                    av02Inventory,
                    lifecycle,
                    new DeterministicAv02PhysicalToteIdAllocator(),
                    loadPlans);
            world.addController(allocationController);
        }

        private PhysicalToteId firstAv02Id() {
            return new PhysicalToteId("av02-000001");
        }

        private void authorizeAllEmptySheets() {
            orders.stream().map(NotionalToteOrder::orderSheetKey).forEach(authorizedEmptySheets::add);
        }

        private void submitCurrentAllocationCommand() {
            allocationSnapshotSequence++;
            command.set(freshAllocationSnapshot().command());
        }

        private Av02AllocationSnapshot freshAllocationSnapshot() {
            return snapshotFactory.create(
                    allocationSnapshotSequence,
                    runtimeState.snapshot(),
                    supplySnapshot(),
                    av02Inventory.snapshot(),
                    lifecycle.snapshot());
        }

        private Av02AllocationCandidate candidate(
                Av02AllocationSnapshot snapshot,
                OrderSheetKey orderSheetKey) {
            return snapshot.candidates().stream()
                    .filter(candidate -> candidate.orderSheetKey().equals(orderSheetKey))
                    .findFirst()
                    .orElseThrow();
        }

        private void completePreparedInputThroughRealAdaptingStore() {
            NotionalToteOrder adaptedOrder = orders.stream()
                    .filter(order -> order.orderSheetKey().equals(EMPTY_ADAPTED_104))
                    .findFirst()
                    .orElseThrow();
            DspOrderItem preparedLine = preparedLine(adaptedOrder);
            AdaptingVisit visit = AdaptingVisit.store(
                    new PhysicalToteId("prepared-source-empty-104"),
                    new OrderSheetKey("prepared-empty-adapted-104", 1),
                    SERVICE_CENTRE_104,
                    List.of(preparedLine));
            AdaptingBenchSelection selection = adaptingArea.submitVisit(visit);
            assertTrue(selection.accepted());
            AdaptingBench bench = adaptingArea.bench(selection.benchId());
            bench.startProcessing();
            AdaptingBenchCompletion completion = adaptingAreaController
                    .applyBenchCompletion(new AdaptingBenchId(selection.benchId().value()))
                    .orElseThrow();
            assertEquals(visit, completion.visit());
        }

        private DspSupplySnapshot supplySnapshot() {
            return new DspSupplySnapshot(
                    "av02-scenario-supply",
                    0,
                    1200,
                    0,
                    Optional.empty(),
                    Optional.of(Duration.ZERO),
                    authorizedEmptySheets,
                    List.of(
                            serviceCentre(SERVICE_CENTRE_104, PRIORITY_104),
                            serviceCentre(SERVICE_CENTRE_108, PRIORITY_108)),
                    0);
        }

        private ServiceCentreSupplySnapshot serviceCentre(String serviceCentreId, int priority) {
            Set<OrderSheetKey> authorizedForCentre = new LinkedHashSet<>();
            if (serviceCentreId.equals(SERVICE_CENTRE_104)) {
                authorizedForCentre.addAll(authorizedEmptySheets.stream()
                        .filter(key -> orders.stream().anyMatch(order ->
                                order.orderSheetKey().equals(key)
                                        && order.serviceCentreId().equals(SERVICE_CENTRE_104)))
                        .toList());
            } else {
                authorizedForCentre.addAll(authorizedEmptySheets.stream()
                        .filter(key -> key.equals(EMPTY_DIRECT_108))
                        .toList());
            }
            ServiceCentreAuthorizationState state = authorizedForCentre.isEmpty()
                    ? ServiceCentreAuthorizationState.HELD_UPSTREAM
                    : ServiceCentreAuthorizationState.AUTHORIZED;
            return new ServiceCentreSupplySnapshot(
                    serviceCentreId,
                    priority,
                    state,
                    Optional.of(Duration.ZERO),
                    0,
                    0,
                    0,
                    0,
                    authorizedForCentre,
                    List.of());
        }

        private void update() {
            world.update(0d);
        }

        private AllocationState state() {
            return new AllocationState(
                    runtimeState.snapshot(),
                    supplySnapshot(),
                    av02Inventory.snapshot(),
                    osrInventory.snapshot(),
                    lifecycle.snapshot(),
                    adaptedStore.snapshot(),
                    loadPlanState(),
                    allocationController.lastAllocatedTote());
        }

        private Map<PhysicalToteId, ToteLoadPlan> loadPlanState() {
            Map<PhysicalToteId, ToteLoadPlan> plans = new java.util.LinkedHashMap<>();
            for (PhysicalToteId id : List.of(firstAv02Id())) {
                ToteLoadPlan plan = loadPlans.getLoadPlanFor(id);
                if (plan != null) {
                    plans.put(id, plan);
                }
            }
            return Map.copyOf(plans);
        }
    }

    private static final class MixedRuntimeFixture implements AutoCloseable {
        private final ScenarioFixture allocationFixture;
        private final PhysicalToteId av02FirstPhysicalToteId =
                new PhysicalToteId("av02-000001");
        private final PhysicalToteId av02SecondPhysicalToteId =
                new PhysicalToteId("av02-000002");
        private final PhysicalToteId osrPhysicalToteId =
                new PhysicalToteId("osr-000001");
        private final NotionalToteOrder av02FirstOrder;
        private final NotionalToteOrder av02SecondOrder;
        private final InboundToteManifest osrManifest;
        private final InboundToteManifestCatalog manifestCatalog;
        private final InboundToteLifecycleController osrLifecycleController;
        private final ElasticRuntimeTestFixture elasticFixture =
                new ElasticRuntimeTestFixture();
        private final DspP2pElasticAllocationRuntime elasticRuntime;
        private final OsrOutboundRouteLaunchQueue launchQueue =
                new OsrOutboundRouteLaunchQueue("mixed-operational-launch", 1);
        private final OsrOutboundRouteLaunchTargetRegistry routeTargetRegistry;
        private final DspOperationalReleaseRuntime runtime;
        private final OsrOutboundTransportQueue transportQueue =
                new OsrOutboundTransportQueue("mixed-operational-transport", 4);
        private final OsrOutboundRouteLaunchController launchController;
        private long evaluationCount;

        private MixedRuntimeFixture(boolean osrFirst) {
            allocationFixture = new ScenarioFixture(List.of(
                    emptyOrder(
                            EMPTY_DIRECT_104.orderId(), SERVICE_CENTRE_104, PRIORITY_104, 1,
                            "pharmacy-104-a", DspOrderLineType.FULL_PACK),
                    emptyOrder(
                            EMPTY_THIRD_PARTY_104.orderId(), SERVICE_CENTRE_104, PRIORITY_104, 2,
                            "pharmacy-104-a", DspOrderLineType.FULL_PACK),
                    emptyOrder(
                            EMPTY_ADAPTED_104.orderId(), SERVICE_CENTRE_104, PRIORITY_104, 3,
                            "pharmacy-104-b", DspOrderLineType.ADAPTED),
                    emptyOrder(
                            EMPTY_DIRECT_108.orderId(), SERVICE_CENTRE_108, PRIORITY_108, 1,
                            "pharmacy-108-a", DspOrderLineType.FULL_PACK)));
            allocationFixture.authorizeAllEmptySheets();
            allocationFixture.submitCurrentAllocationCommand();
            allocationFixture.update();

            av02FirstOrder = allocationFixture.orders.stream()
                    .filter(order -> order.orderSheetKey().equals(EMPTY_DIRECT_104))
                    .findFirst()
                    .orElseThrow();
            av02SecondOrder = allocationFixture.orders.stream()
                    .filter(order -> order.orderSheetKey().equals(EMPTY_THIRD_PARTY_104))
                    .findFirst()
                    .orElseThrow();
            assertTrue(allocationFixture.av02Inventory.findWaiting(av02FirstPhysicalToteId)
                    .isPresent());

            NotionalToteOrder osrOrder = fullPackOrder(
                    "osr-order",
                    SERVICE_CENTRE_104,
                    PRIORITY_104,
                    osrFirst ? 0 : 4,
                    "pharmacy-104-a");
            osrManifest = new InboundToteManifest(
                    osrPhysicalToteId,
                    osrOrder.orderSheetKey(),
                    osrOrder.orderType(),
                    osrOrder.serviceCentreId(),
                    osrOrder.items(),
                    osrOrder.sequenceNumber());
            manifestCatalog = new InboundToteManifestCatalog(List.of(osrManifest));
            osrLifecycleController = new InboundToteLifecycleController(
                    allocationFixture.lifecycle,
                    manifestCatalog);
            allocationFixture.osrInventory.store(osrManifest);
            allocationFixture.loadPlans.putLoadPlan(
                    new ToteLoadPlan(osrPhysicalToteId, List.of()));

            routeTargetRegistry = new OsrOutboundRouteLaunchTargetRegistry(
                    launchQueue,
                    mixedDestinations(elasticFixture));
            elasticRuntime = elasticFixture.createRuntime(
                    this::elasticLogicalSnapshot,
                    manifestCatalog,
                    allocationFixture.lifecycle::snapshot,
                    allocationFixture.av02Inventory::snapshot,
                    this::elasticSupplySnapshot);
            DspOperationalClock clock = new DspOperationalClock(
                    DspOperationalClockConfig.productionBaseline(
                            LocalDate.of(2026, 8, 26)));
            runtime = new DspOperationalReleaseRuntimeFactory().createElasticWithAv02(
                    new SynchronousOperationalReleaseEvaluationSource(elasticScheduler()),
                    allocationFixture.osrInventory,
                    osrLifecycleController,
                    manifestCatalog,
                    this::logicalSnapshot,
                    clock::initialSnapshot,
                    MixedRuntimeFixture::openAdmission,
                    routeTargetRegistry,
                    allocationFixture.av02Inventory,
                    allocationFixture.lifecycle,
                    allocationFixture.loadPlans,
                    elasticRuntime);
            launchController = new OsrOutboundRouteLaunchController(
                    launchQueue,
                    transportQueue,
                    new LoadPlanOsrOutboundToteHydrator(
                            allocationFixture.loadPlans,
                            this::detached));
        }

        private void assertExactReleaseTargets() {
            List<OperationalRouteDestination> expected = mixedDestinations(elasticFixture);
            assertEquals(expected, routeTargetRegistry.destinations());
            assertEquals(expected, routeTargetRegistry.targets().stream()
                    .map(target -> target.destination())
                    .toList());
            assertEquals(expected, routeTargetRegistry.av02Targets().stream()
                    .map(target -> target.destination())
                    .toList());
            assertEquals(expected.size(), runtime.routeTargetAdmissionSnapshots().size());
            assertEquals(7, expected.size());
        }

        private void assertAuthorized108AndRemaining104Work() {
            Av02AllocationSnapshot snapshot = allocationFixture.freshAllocationSnapshot();
            Av02AllocationCandidate thirdParty = allocationFixture.candidate(
                    snapshot, EMPTY_THIRD_PARTY_104);
            Av02AllocationCandidate direct108 = allocationFixture.candidate(
                    snapshot, EMPTY_DIRECT_108);
            assertTrue(thirdParty.eligible());
            assertTrue(direct108.eligible());
            assertTrue(allocationFixture.supplySnapshot()
                    .authorizedEmptyOrderSheetKeys()
                    .contains(EMPTY_DIRECT_108));
            assertTrue(logicalSnapshot().orderStates().stream()
                    .anyMatch(state -> state.order().orderSheetKey().equals(EMPTY_DIRECT_108)));
        }

        private void releaseOnce() {
            runtime.controller().update(new SimulationContext(), 0.1d);
            evaluationCount++;
        }

        private void assertAppliedRelease(PhysicalToteId expectedPhysicalToteId) {
            var controllerSnapshot = runtime.controller().snapshot();
            assertEquals(evaluationCount - 1, controllerSnapshot.lastCompletedEvaluationSequence()
                    .orElseThrow());
            DspOperationalReleaseEvaluation evaluation = controllerSnapshot.lastEvaluation()
                    .orElseThrow();
            var decision = evaluation.releaseDecision().orElseThrow();
            assertEquals(expectedPhysicalToteId,
                    decision.candidate().physicalCandidate().physicalToteId());
            assertEquals(SERVICE_CENTRE_104,
                    decision.candidate().physicalCandidate().serviceCentreId());
            assertTrue(controllerSnapshot.lastCommandApplicationResult()
                    .orElseThrow().applied(),
                    () -> controllerSnapshot.lastCommandApplicationResult()
                            .orElseThrow().reason());
            assertEquals(1, launchQueue.snapshot().occupancy());
            OperationalRouteLaunchRequest request = launchQueue.peek().orElseThrow();
            assertEquals(expectedPhysicalToteId, request.physicalToteId());
            assertEquals(SERVICE_CENTRE_104, request.serviceCentreId());
            assertEquals(List.of("pharmacy-104-a"), request.pharmacyIds());
            assertEquals(decision.command().releaseTargetId(), request.destination().targetId());
            assertTrue(request.p2pAssignment().isPresent());
            assertSame(decision.command().proposedP2pAssignment().orElseThrow(),
                    request.p2pAssignment().orElseThrow());
        }

        private void assertAv02Waiting(PhysicalToteId physicalToteId) {
            assertTrue(allocationFixture.av02Inventory.findWaiting(physicalToteId).isPresent());
            assertTrue(allocationFixture.av02Inventory.snapshot().departedTotes().stream()
                    .noneMatch(tote -> tote.physicalToteId().equals(physicalToteId)));
        }

        private void assertOsrWaiting(PhysicalToteId physicalToteId) {
            assertTrue(allocationFixture.osrInventory.snapshot().findStored(physicalToteId)
                    .isPresent());
            assertFalse(allocationFixture.osrInventory.snapshot().hasDeparted(physicalToteId));
        }

        private void assertAv02Released(PhysicalToteId physicalToteId) {
            assertTrue(allocationFixture.av02Inventory.findWaiting(physicalToteId).isEmpty());
            assertEquals(1, allocationFixture.av02Inventory.snapshot().departedTotes().stream()
                    .filter(tote -> tote.physicalToteId().equals(physicalToteId))
                    .count());
            var lifecycleRecord = allocationFixture.lifecycle.tote(physicalToteId).orElseThrow();
            assertEquals(PhysicalToteRole.PRE_P2P, lifecycleRecord.role());
            assertEquals(PhysicalToteLifecycleState.ACTIVE_PRE_P2P, lifecycleRecord.state());
            List<PhysicalToteAssignment> assignments = allocationFixture.lifecycle
                    .activeAssignmentsFor(physicalToteId);
            assertEquals(1, assignments.size());
            assertEquals(PhysicalToteAssignmentStage.PRE_P2P, assignments.getFirst().stage());
            assertTrue(manifestCatalog.findByPhysicalToteId(physicalToteId).isEmpty());
            assertFalse(allocationFixture.osrInventory.snapshot().hasDeparted(physicalToteId));

            P2pPhysicalToteAssignment assignment = elasticRuntime.operationalSnapshot()
                    .leases().findAssignment(physicalToteId).orElseThrow();
            assertEquals(physicalToteId, assignment.physicalToteId());
            assertEquals(SERVICE_CENTRE_104, assignment.serviceCentreId());
            assertEquals(StationType.P2P, assignment.destination().stationType());
            OperationalRouteLaunchRequest request = launchQueue.peek().orElseThrow();
            assertEquals(OperationalPhysicalToteSource.AV02, request.source());
            assertEquals(physicalToteId, request.identity().physicalToteId());
            assertSame(request.p2pAssignment().orElseThrow(), assignment);
        }

        private void assertOsrReleased(PhysicalToteId physicalToteId) {
            assertTrue(allocationFixture.osrInventory.snapshot().findStored(physicalToteId)
                    .isEmpty());
            assertEquals(1, allocationFixture.osrInventory.snapshot().departedTotes().stream()
                    .filter(manifest -> manifest.physicalToteId().equals(physicalToteId))
                    .count());
            var lifecycleRecord = allocationFixture.lifecycle.tote(physicalToteId).orElseThrow();
            assertEquals(PhysicalToteRole.INBOUND_PACK, lifecycleRecord.role());
            assertEquals(PhysicalToteLifecycleState.INBOUND_PACK_TOTE, lifecycleRecord.state());
            PhysicalToteAssignment assignment = allocationFixture.lifecycle
                    .activeAssignmentFor(osrManifest.orderSheetKey()).orElseThrow();
            assertEquals(physicalToteId, assignment.physicalToteId());
            assertEquals(PhysicalToteAssignmentStage.INBOUND_PACK, assignment.stage());
            assertTrue(manifestCatalog.findByPhysicalToteId(physicalToteId).isPresent());
            OperationalRouteLaunchRequest request = launchQueue.peek().orElseThrow();
            assertEquals(OperationalPhysicalToteSource.OSR, request.source());
            assertEquals(physicalToteId, request.identity().physicalToteId());
            P2pPhysicalToteAssignment p2pAssignment = elasticRuntime.operationalSnapshot()
                    .leases().findAssignment(physicalToteId).orElseThrow();
            assertSame(request.p2pAssignment().orElseThrow(), p2pAssignment);
        }

        private void assertThirdPartyFirstDestination(PhysicalToteId physicalToteId) {
            OperationalRouteLaunchRequest request = launchQueue.peek().orElseThrow();
            assertEquals(physicalToteId, request.physicalToteId());
            assertEquals(new OperationalRouteDestination(
                    StationType.THIRD_PARTY, "third-party-1"), request.destination());
            assertTrue(request.p2pAssignment().isPresent());
            assertEquals(StationType.P2P,
                    request.p2pAssignment().orElseThrow().destination().stationType());
            assertFalse(request.destination().equals(
                    request.p2pAssignment().orElseThrow().destination()));
        }

        private MixedRuntimeState mutableState() {
            Map<PhysicalToteId, ToteLoadPlan> plans = new java.util.LinkedHashMap<>();
            for (PhysicalToteId physicalToteId : List.of(
                    av02FirstPhysicalToteId, av02SecondPhysicalToteId, osrPhysicalToteId)) {
                ToteLoadPlan plan = allocationFixture.loadPlans
                        .getLoadPlanFor(physicalToteId);
                if (plan != null) {
                    plans.put(physicalToteId, plan);
                }
            }
            return new MixedRuntimeState(
                    allocationFixture.av02Inventory.snapshot(),
                    allocationFixture.osrInventory.snapshot(),
                    allocationFixture.lifecycle.snapshot(),
                    elasticRuntime.operationalSnapshot(),
                    allocationFixture.supplySnapshot(),
                    logicalSnapshot(),
                    allocationFixture.adaptedStore.snapshot(),
                    Map.copyOf(plans),
                    runtime.routeTargetAdmissionSnapshots(),
                    launchQueue.snapshot(),
                    transportQueue.snapshot());
        }

        private void launchHead() {
            OperationalRouteLaunchRequest head = launchQueue.peek().orElseThrow();
            launchController.update(new SimulationContext(), 0.1d);
            assertTrue(launchQueue.peek().isEmpty());
            RoutedPhysicalTote routedTote = transportQueue.peek().orElseThrow();
            assertSame(head, routedTote.launchRequest());
            assertEquals(head.physicalToteId(), routedTote.physicalToteId());
            assertSame(allocationFixture.loadPlans.getLoadPlanFor(head.physicalToteId()),
                    routedTote.loadPlan());
            assertEquals(head.destination(), routedTote.destination());
            assertSame(routedTote, transportQueue.dequeue().orElseThrow());
        }

        private void allocateRemainingAv02ThroughProductionPath() {
            Av02AllocationSnapshot snapshot = allocationFixture.freshAllocationSnapshot();
            assertTrue(allocationFixture.candidate(snapshot, EMPTY_THIRD_PARTY_104).eligible());
            assertTrue(allocationFixture.candidate(snapshot, EMPTY_DIRECT_108).eligible());
            allocationFixture.submitCurrentAllocationCommand();
            allocationFixture.update();
            Av02AllocatedTote allocated = allocationFixture.av02Inventory
                    .findWaiting(av02SecondPhysicalToteId).orElseThrow();
            assertEquals(EMPTY_THIRD_PARTY_104, allocated.orderSheetKey());
            assertEquals(av02SecondOrder.serviceCentreId(), allocated.serviceCentreId());
        }

        private WarehouseSchedulerSnapshot logicalSnapshot() {
            return new WarehouseSchedulerSnapshot(logicalStates(true), Map.of(), Set.of(),
                    Optional.empty());
        }

        private WarehouseSchedulerSnapshot elasticLogicalSnapshot() {
            return new WarehouseSchedulerSnapshot(logicalStates(false), Map.of(), Set.of(),
                    Optional.empty());
        }

        private List<DspSchedulerOrderState> logicalStates(boolean include108) {
            List<DspSchedulerOrderState> states = new ArrayList<>();
            NotionalToteOrder osrOrder = fullPackOrder(
                    osrManifest.orderSheetKey().orderId(),
                    osrManifest.serviceCentreId(),
                    PRIORITY_104,
                    osrManifest.sourceSequenceNumber(),
                    "pharmacy-104-a");
            states.add(mixedLogicalState(osrOrder, StartLocation.OSR));
            allocationFixture.orders.stream()
                    .filter(order -> include108 || !order.serviceCentreId()
                            .equals(SERVICE_CENTRE_108))
                    .map(order -> mixedLogicalState(order, StartLocation.AV02))
                    .forEach(states::add);
            return List.copyOf(states);
        }

        private DspSupplySnapshot elasticSupplySnapshot() {
            DspSupplySnapshot source = allocationFixture.supplySnapshot();
            ServiceCentreSupplySnapshot serviceCentre = allocationFixture.serviceCentre(
                    SERVICE_CENTRE_104, PRIORITY_104);
            return new DspSupplySnapshot(
                    source.policyId(),
                    source.lowWaterMark(),
                    source.osrCapacity(),
                    source.osrOccupancy(),
                    source.activeInboundServiceCentreId(),
                    source.nextPhysicalAdmissionElapsedTime(),
                    serviceCentre.authorizedEmptyOrderSheetKeys(),
                    List.of(serviceCentre),
                    source.admittedAfterStartupCount());
        }

        private RoutedPhysicalTote detached(
                OperationalRouteLaunchRequest request,
                ToteLoadPlan loadPlan) {
            String physicalToteId = request.physicalToteId().value();
            RenderableObject renderable = RenderableObject.create(
                    physicalToteId,
                    null,
                    anchorMesh(),
                    new Mat4.ObjectTransformation(
                            0f, 0f, 0f, 0f, 0f, 0f, new Mat4()),
                    triangleIndex -> 0,
                    false);
            RouteSegment routeSegment = new RouteSegment(
                    "mixed-operational-route-" + physicalToteId,
                    new online.davisfamily.threedee.path.LinearSegment3(
                            new Vec3(0f, 0f, 0f),
                            new Vec3(1f, 0f, 0f),
                            false));
            Tote tote = new Tote(
                    physicalToteId,
                    new RouteFollower(physicalToteId, routeSegment, 0f, 1d),
                    renderable,
                    new Vec3(),
                    0f);
            return new RoutedPhysicalTote(request, loadPlan, tote, renderable);
        }

        @Override
        public void close() {
            runtime.close();
            elasticRuntime.close();
        }

        private static StationAdmissionSnapshot openAdmission(
                StationType stationType,
                DspSchedulerOrderState candidate,
                WarehouseSchedulerSnapshot snapshot) {
            String targetId = stationType == StationType.THIRD_PARTY
                    ? "third-party-1"
                    : stationType == StationType.ADAPTING ? "adapting-1" : "p2p-1";
            return new StationAdmissionSnapshot(
                    stationType,
                    new StationCapacity(1, 1),
                    new StationSnapshot(stationType, 0, 0),
                    true,
                    "",
                    Optional.of(targetId));
        }
    }

    private static final class RecordingSimulationWorld extends SimulationWorld {
        private final Map<String, Tote> trackablesById = new java.util.LinkedHashMap<>();
        private final List<Tote> trackables = new ArrayList<>();

        @Override
        public void addTrackableObject(TrackableObject object) {
            super.addTrackableObject(object);
            if (object instanceof Tote tote
                    && trackablesById.putIfAbsent(tote.getId(), tote) == null) {
                trackables.add(tote);
            }
        }

        private int trackableCount(String toteId) {
            return (int) trackables.stream()
                    .filter(tote -> tote.getId().equals(toteId))
                    .count();
        }
    }

    private record JourneyTopology(
            WarehouseRouteCatalog catalog,
            RouteSegment entrySegment,
            RouteSegment thirdPartyTerminal,
            RouteSegment adaptingTerminal,
            RouteSegment p2pTerminal,
            RouteSegment p2pTipper,
            OperationalRouteDestination thirdPartyDestination,
            OperationalRouteDestination adaptingDestination,
            OperationalRouteDestination p2pDestination,
            OperationalRouteDestination p2pBlockerDestination) {

        private static JourneyTopology create() {
            RouteSegment entry = segment("journey-common-entry", 0f, 2f);
            RouteSegment thirdPartyTerminal = segment("journey-third-party-terminal", 0f, 1f);
            RouteSegment adaptingTerminal = segment("journey-adapting-terminal", 0f, 1f);
            RouteSegment p2pTerminal = segment("journey-p2p-terminal", 0f, 1f);
            RouteSegment p2pTipper = segment("journey-p2p-tipper", 1f, 2f);
            p2pTerminal.connectTo(p2pTipper);

            OperationalRouteDestination thirdParty =
                    new OperationalRouteDestination(StationType.THIRD_PARTY, "third-party-1");
            OperationalRouteDestination adapting =
                    new OperationalRouteDestination(StationType.ADAPTING, "adapting-1");
            OperationalRouteDestination p2p =
                    new OperationalRouteDestination(StationType.P2P, "p2p-1");
            OperationalRouteDestination blocker =
                    new OperationalRouteDestination(StationType.P2P, "p2p-2");

            List<WarehouseRouteDefinition> definitions = new ArrayList<>();
            definitions.add(definition(thirdParty, entry, thirdPartyTerminal));
            definitions.add(definition(adapting, entry, adaptingTerminal));
            definitions.add(definition(p2p, entry, p2pTerminal));
            for (int index = 2; index <= 5; index++) {
                OperationalRouteDestination destination =
                        new OperationalRouteDestination(StationType.P2P, "p2p-" + index);
                RouteSegment terminal = index == 2
                        ? segment("journey-p2p-2-terminal", 0f, 1f)
                        : segment("journey-p2p-" + index + "-terminal", 0f, 1f);
                definitions.add(definition(destination, entry, terminal));
            }
            return new JourneyTopology(
                    new WarehouseRouteCatalog(definitions),
                    entry,
                    thirdPartyTerminal,
                    adaptingTerminal,
                    p2pTerminal,
                    p2pTipper,
                    thirdParty,
                    adapting,
                    p2p,
                    blocker);
        }

        private static WarehouseRouteDefinition definition(
                OperationalRouteDestination destination,
                RouteSegment entry,
                RouteSegment terminal) {
            return new WarehouseRouteDefinition(
                    destination,
                    entry,
                    0f,
                    TravelDirection.FORWARD,
                    "journey-" + destination.targetId() + "-sensor",
                    terminal);
        }

        private static RouteSegment segment(String label, float start, float end) {
            return new RouteSegment(label, new online.davisfamily.threedee.path.LinearSegment3(
                    new Vec3(start, 0f, 0f),
                    new Vec3(end, 0f, 0f),
                    false));
        }
    }

    private record ToteState(
            Tote.ToteMotionState motion,
            boolean lidsOpen,
            boolean visible,
            RouteSegment routeSegment,
            float distance) {
    }

    private record RoutedIdentityState(
            OperationalRouteLaunchRequest launchRequest,
            OperationalPhysicalToteReleaseRequest releaseRequest,
            online.davisfamily.warehouse.sim.dsp.osr.release.launch
                    .OperationalPhysicalToteIdentity identity,
            OperationalPhysicalToteSource source,
            PhysicalToteId physicalToteId,
            OrderSheetKey orderSheetKey,
            OrderType orderType,
            PhysicalToteRole role,
            String serviceCentreId,
            List<String> pharmacyIds,
            long sourceSequenceNumber,
            Duration releaseTime,
            Optional<P2pPhysicalToteAssignment> p2pAssignment,
            IdentityRef<ToteLoadPlan> loadPlan,
            IdentityRef<Tote> tote,
            IdentityRef<RenderableObject> renderable,
            IdentityRef<RouteFollower> routeFollower,
            OperationalRouteDestination destination,
            ToteState toteState) {

        private static RoutedIdentityState from(RoutedPhysicalTote routed) {
            OperationalRouteLaunchRequest request = routed.launchRequest();
            var identity = request.identity();
            Tote tote = routed.tote();
            return new RoutedIdentityState(
                    request,
                    request.releaseRequest(),
                    identity,
                    request.source(),
                    routed.physicalToteId(),
                    request.orderSheetKey(),
                    request.orderType(),
                    identity.physicalToteRole(),
                    request.serviceCentreId(),
                    request.pharmacyIds(),
                    identity.sourceSequenceNumber(),
                    request.releaseTime(),
                    request.p2pAssignment(),
                    IdentityRef.of(routed.loadPlan()),
                    IdentityRef.of(tote),
                    IdentityRef.of(routed.renderable()),
                    IdentityRef.of(tote.getRouteFollower()),
                    routed.destination(),
                    new ToteState(
                            tote.getInteractionMode(),
                            tote.areLidsOpen(),
                            routed.renderable().isVisible(),
                            tote.getRouteFollower().getCurrentSegment(),
                            (float) tote.getRouteFollower().getDistanceAlongSegment()));
        }
    }

    private record OperationalEmptyState(
            WarehouseSchedulerSnapshot scheduler,
            DspSupplySnapshot supply,
            Av02InventorySnapshot av02Inventory,
            OsrInventorySnapshot osrInventory,
            PhysicalToteLifecycleSnapshot lifecycle,
            DspP2pElasticAllocationRuntimeSnapshot elastic,
            DspOperationalReleaseControllerSnapshot operational,
            OsrOutboundRouteLaunchQueueSnapshot launchQueue,
            OsrOutboundRouteLaunchControllerSnapshot launchController,
            OsrOutboundTransportQueueSnapshot transportQueue,
            WarehouseTransportIngressControllerSnapshot ingress,
            WarehouseTransportInFlightSnapshot inFlight,
            WarehouseTransportArrivalControllerSnapshot arrival,
            List<StationRoutedToteArrivalQueueSnapshot> stationArrivals,
            List<StationArrivalClaimControllerSnapshot> claimants,
            StationProcessingSnapshot coordinator,
            ThirdPartyAreaSnapshot thirdParty,
            AdaptingBenchSnapshot adaptingBench,
            AdaptedLineStoreSnapshot adaptedStore,
            P2pTipperArrivalTargetSnapshot p2pTarget,
            MachineWaitQueueSnapshot p2pInput,
            StationRouteContinuationControllerSnapshot continuation,
            Map<PhysicalToteId, RoutedIdentityState> identities,
            int worldTrackableCount,
            int renderableCount,
            ToteLoadPlan currentPlan,
            PackProvenanceSnapshot provenance,
            OutboundAllocationSnapshot outbound,
            List<Bag> bags) {
    }

    private static final class IdentityRef<T> {
        private final T reference;

        private IdentityRef(T reference) {
            this.reference = reference;
        }

        private static <T> IdentityRef<T> of(T reference) {
            return new IdentityRef<>(reference);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof IdentityRef<?> value && value.reference == reference;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(reference);
        }
    }

    private record MixedRuntimeState(
            Av02InventorySnapshot av02Inventory,
            OsrInventorySnapshot osrInventory,
            PhysicalToteLifecycleSnapshot lifecycle,
            DspP2pElasticAllocationRuntimeSnapshot elastic,
            DspSupplySnapshot supply,
            WarehouseSchedulerSnapshot scheduler,
            AdaptedLineStoreSnapshot adaptedStore,
            Map<PhysicalToteId, ToteLoadPlan> loadPlans,
            List<online.davisfamily.warehouse.sim.dsp.osr.release.launch
                    .OperationalRouteTargetAdmissionSnapshot> targetAdmissions,
            online.davisfamily.warehouse.sim.dsp.osr.release.launch
                    .OsrOutboundRouteLaunchQueueSnapshot launchQueue,
            online.davisfamily.warehouse.sim.dsp.transport.OsrOutboundTransportQueueSnapshot
                    transportQueue) {
    }

    private static Mesh anchorMesh() {
        return new Mesh(
                new Vec4[] {
                        new Vec4(0f, 0f, 0f, 1f),
                        new Vec4(0f, 0f, 0f, 1f),
                        new Vec4(0f, 0f, 0f, 1f)
                },
                new int[][] { {0, 1, 2} },
                "anchor");
    }

    private static RenderableObject renderable(String id) {
        return RenderableObject.create(
                id,
                null,
                anchorMesh(),
                new Mat4.ObjectTransformation(
                        0f, 0f, 0f, 0f, 0f, 0f, new Mat4()),
                triangleIndex -> 0,
                false);
    }

    private static TipperDownstreamFlow acceptingDownstreamFlow() {
        return new TipperDownstreamFlow() {
            @Override
            public boolean canAcceptDischargedPack(Pack pack) {
                return true;
            }

            @Override
            public void acceptDischargedPack(Pack pack) {
            }

            @Override
            public void update(double dtSeconds) {
            }

            @Override
            public boolean keepsTipperOccupied() {
                return false;
            }
        };
    }

    private record AllocationState(
            WarehouseSchedulerSnapshot scheduler,
            DspSupplySnapshot supply,
            Av02InventorySnapshot av02Inventory,
            OsrInventorySnapshot osrInventory,
            PhysicalToteLifecycleSnapshot lifecycle,
            AdaptedLineStoreSnapshot adaptedStore,
            Map<PhysicalToteId, ToteLoadPlan> loadPlans,
            Optional<Av02AllocatedTote> lastAllocatedTote) {
    }
}
