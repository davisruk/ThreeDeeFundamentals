package online.davisfamily.warehouse.testing;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import online.davisfamily.threedee.behaviour.routing.RouteFollower.TravelDirection;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.debug.SelectionInspectionRegistry;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.path.LinearSegment3;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.rendering.TriangleRenderer;
import online.davisfamily.threedee.rendering.appearance.OneColourStrategyImpl;
import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.SimulationController;
import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.threedee.sim.framework.events.DetectionEvent;
import online.davisfamily.threedee.sim.framework.objects.sensors.Sensor;
import online.davisfamily.threedee.sim.framework.objects.sensors.WindowSensor;
import online.davisfamily.threedee.sim.framework.objects.sensors.WindowSensorAreaImpl;
import online.davisfamily.warehouse.rendering.model.tote.RenderableToteFactory;
import online.davisfamily.warehouse.rendering.model.tote.ToteEnvelope;
import online.davisfamily.warehouse.rendering.model.tote.ToteGeometry;
import online.davisfamily.warehouse.rendering.model.tracks.RenderableTrackFactory;
import online.davisfamily.warehouse.rendering.model.tracks.RollerMeshFactory;
import online.davisfamily.warehouse.rendering.model.tracks.TrackAppearance;
import online.davisfamily.warehouse.rendering.model.tracks.TrackDriveType;
import online.davisfamily.warehouse.rendering.model.tracks.TrackSpec;
import online.davisfamily.warehouse.rendering.model.tracks.WarehouseSegmentMetadata;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseRequest;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OsrOutboundRouteLaunchQueue;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OsrOutboundRouteLaunchRequest;
import online.davisfamily.warehouse.sim.dsp.p2p.arrival.ContainedPackP2pTipperPayloadFactory;
import online.davisfamily.warehouse.sim.dsp.p2p.arrival.DspP2pArrivalConsumerRuntime;
import online.davisfamily.warehouse.sim.dsp.p2p.arrival.DspP2pArrivalConsumerRuntimeFactory;
import online.davisfamily.warehouse.sim.dsp.p2p.arrival.P2pArrivalConsumerBinding;
import online.davisfamily.warehouse.sim.dsp.p2p.arrival.P2pArrivalRouteBinding;
import online.davisfamily.warehouse.sim.dsp.p2p.arrival.P2pTipperArrivalTarget;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pBaggingActivitySnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pInputActivitySnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineActivityProbe;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineActivitySnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineDefinition;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineLeaseCatalogSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineLeaseRegistry;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pPackPathActivitySnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pPhysicalToteAssignment;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.StickyP2pArrivalAdmissionPolicy;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteSnapshot;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.transport.routing.DspWarehouseTransportRuntime;
import online.davisfamily.warehouse.sim.dsp.transport.routing.DspWarehouseTransportRuntimeFactory;
import online.davisfamily.warehouse.sim.dsp.transport.routing.StationRoutedToteArrivalQueue;
import online.davisfamily.warehouse.sim.dsp.transport.routing.StationRoutedToteArrivalQueueSnapshot;
import online.davisfamily.warehouse.sim.dsp.transport.routing.WarehouseRouteCatalog;
import online.davisfamily.warehouse.sim.dsp.transport.routing.WarehouseRouteDefinition;
import online.davisfamily.warehouse.sim.dsp.transport.routing.WarehouseTransferRoutingTable;
import online.davisfamily.warehouse.sim.events.TransferCompletedEvent;
import online.davisfamily.warehouse.sim.sensor.MembershipSensor;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperInputQueue;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperTotePayload;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;
import online.davisfamily.warehouse.sim.transfer.TransferMotionConfig;
import online.davisfamily.warehouse.sim.transfer.TransferOrientationPolicy;
import online.davisfamily.warehouse.sim.transfer.TransferRoutingDecision;
import online.davisfamily.warehouse.sim.transfer.TransferTarget;
import online.davisfamily.warehouse.sim.transfer.TransferZone;
import online.davisfamily.warehouse.sim.transfer.TransferZoneController;
import online.davisfamily.warehouse.sim.transfer.TransferZoneMachine;
import online.davisfamily.warehouse.sim.transfer.strategy.TransferTargetDecisionStrategy;

public final class DspWarehouseTransportDebugRig implements DebugSceneRuntime {
    static final String THIRD_PARTY_TARGET_ID = "warehouse-third-party";
    static final String ADAPTING_TARGET_ID = "warehouse-adapting";
    static final String P2P_TARGET_ID = "warehouse-p2p";

    private static final double RELEASE_INTERVAL_SECONDS = 3d;
    private static final double P2P_HOLD_SECONDS = 12d;
    private static final double ROUTE_SPEED = 1d;
    private static final String DEBUG_ACTIVE_PHARMACY_ID = "pharmacy-104-1";
    private final List<RenderableObject> objects;
    private final SelectionInspectionRegistry inspectionRegistry;
    private final OsrOutboundRouteLaunchQueue launchQueue =
            new OsrOutboundRouteLaunchQueue("warehouse-debug-launch", 8);
    private final Map<String, ToteLoadPlan> loadPlans = new LinkedHashMap<>();
    private final List<OsrOutboundRouteLaunchRequest> scheduledRequests;
    private final Map<String, StationRoutedToteArrivalQueue> arrivalQueues =
            new LinkedHashMap<>();
    private final Map<String, RenderableObject> toteRenderables = new LinkedHashMap<>();
    private final List<String> consumedP2pToteIds = new ArrayList<>();
    private final RenderableObject transportInspectionMarker;
    private final Map<String, RenderableObject> queueMarkers = new LinkedHashMap<>();
    private final DspWarehouseTransportRuntime runtime;
    private final TipperInputQueue p2pInputQueue;
    private final DspP2pArrivalConsumerRuntime p2pArrivalRuntime;
    private final List<P2pLineDefinition> p2pLineDefinitions;
    private final Map<P2pLineId, P2pLineActivityProbe> p2pActivityProbes;
    private final P2pLineLeaseRegistry p2pLeaseRegistry;
    private boolean observedP2pBackpressure;
    private boolean observedSeparatedP2pBackpressure;
    private boolean p2pCapacityReturned;

    public DspWarehouseTransportDebugRig(
            TriangleRenderer tr,
            SimulationWorld sim,
            List<RenderableObject> objects,
            SelectionInspectionRegistry inspectionRegistry) {
        if (tr == null || sim == null || objects == null || inspectionRegistry == null) {
            throw new IllegalArgumentException("debug rig dependencies must not be null");
        }
        this.objects = objects;
        this.inspectionRegistry = inspectionRegistry;

        ToteGeometry toteGeometry = new ToteGeometry();
        TrackSpec trackSpec = rollerTrackSpec(toteGeometry, true);
        TrackSpec transferSpec = rollerTrackSpec(toteGeometry, false);
        TrackAppearance appearance = trackAppearance();

        RouteSegment entry = segment("osr_outbound_approach", 0f, -6f, 0f, -2f);
        RouteSegment firstTransferSegment = segment(
                "warehouse_transfer_1_segment", 0f, -2f, 0f, -1.4f);
        RouteSegment thirdParty = segment(
                "third_party_terminal", 0f, -1.4f, 0f, 3.8f);
        RouteSegment sharedBranch = segment(
                "warehouse_shared_branch", 0.65f, -1.7f, 4f, -1.7f);
        RouteSegment secondTransferSegment = segment(
                "warehouse_transfer_2_segment", 4f, -1.7f, 4.6f, -1.7f);
        RouteSegment p2p = segment("p2p_terminal", 4.6f, -1.7f, 8.3f, -1.7f);
        RouteSegment adapting = segment(
                "adapting_terminal", 4.3f, -1.05f, 4.3f, 3.8f);

        entry.connectTo(firstTransferSegment);
        firstTransferSegment.connectTo(thirdParty);
        sharedBranch.connectTo(secondTransferSegment);
        secondTransferSegment.connectTo(p2p);

        TransferTarget sharedTarget = new TransferTarget(sharedBranch, 0f, TravelDirection.FORWARD);
        TransferTarget adaptingTarget = new TransferTarget(adapting, 0f, TravelDirection.FORWARD);
        TransferTargetDecisionStrategy unconfiguredStrategy = (tote, machine) -> Optional.empty();
        TransferMotionConfig transferMotion = new TransferMotionConfig(0.65d, 0.08f, 0.45f);
        TransferZone firstZone = new TransferZone(
                "warehouse_transfer_1",
                0f,
                firstTransferSegment.length(),
                firstTransferSegment,
                sharedBranch,
                0f,
                online.davisfamily.warehouse.rendering.model.tracks.GuideSide.RIGHT,
                online.davisfamily.warehouse.rendering.model.tracks.GuideSide.RIGHT,
                unconfiguredStrategy,
                transferMotion);
        TransferZone secondZone = new TransferZone(
                "warehouse_transfer_2",
                0f,
                secondTransferSegment.length(),
                secondTransferSegment,
                adapting,
                0f,
                online.davisfamily.warehouse.rendering.model.tracks.GuideSide.RIGHT,
                online.davisfamily.warehouse.rendering.model.tracks.GuideSide.RIGHT,
                unconfiguredStrategy,
                transferMotion);

        List<RouteSegment> ordinarySegments = List.of(
                entry, thirdParty, sharedBranch, p2p, adapting);
        ordinarySegments.forEach(segment -> addTrack(
                tr, segment, trackSpec, appearance, objects, inspectionRegistry));
        addTrack(tr, firstTransferSegment, transferSpec, appearance, objects, inspectionRegistry);
        addTrack(tr, secondTransferSegment, transferSpec, appearance, objects, inspectionRegistry);

        attachMechanism(firstZone, firstTransferSegment, transferSpec, tr, objects, inspectionRegistry);
        attachMechanism(secondZone, secondTransferSegment, transferSpec, tr, objects, inspectionRegistry);
        TransferZoneMachine firstMachine = createUncontrolledMachine(
                sim, entry, Math.max(0f, entry.length() - 1.8f), firstTransferSegment, firstZone);
        TransferZoneMachine secondMachine = createUncontrolledMachine(
                sim, sharedBranch, Math.max(0f, sharedBranch.length() - 1.8f),
                secondTransferSegment, secondZone);

        OperationalRouteDestination thirdPartyDestination = destination(
                StationType.THIRD_PARTY, THIRD_PARTY_TARGET_ID);
        OperationalRouteDestination adaptingDestination = destination(
                StationType.ADAPTING, ADAPTING_TARGET_ID);
        OperationalRouteDestination p2pDestination = destination(StationType.P2P, P2P_TARGET_ID);
        WarehouseRouteCatalog routeCatalog = new WarehouseRouteCatalog(List.of(
                definition(thirdPartyDestination, entry, thirdParty),
                definition(adaptingDestination, entry, adapting),
                definition(p2pDestination, entry, p2p)));

        addTerminalSensor(sim, definition(routeCatalog, thirdPartyDestination));
        addTerminalSensor(sim, definition(routeCatalog, adaptingDestination));
        addTerminalSensor(sim, definition(routeCatalog, p2pDestination));

        StationRoutedToteArrivalQueue thirdPartyQueue = queue(thirdPartyDestination, 2);
        StationRoutedToteArrivalQueue adaptingQueue = queue(adaptingDestination, 2);
        StationRoutedToteArrivalQueue p2pQueue = queue(p2pDestination, 1);
        p2pInputQueue = new TipperInputQueue("warehouse-p2p-input", 1);

        List<P2pLineDefinition> lineDefinitions = new ArrayList<>();
        lineDefinitions.add(new P2pLineDefinition(
                new P2pLineId("warehouse-p2p-line-1"), p2pDestination));
        for (int lineNumber = 2; lineNumber <= 5; lineNumber++) {
            lineDefinitions.add(new P2pLineDefinition(
                    new P2pLineId("warehouse-p2p-line-" + lineNumber),
                    destination(
                            StationType.P2P,
                            "warehouse-p2p-placeholder-" + lineNumber)));
        }
        p2pLineDefinitions = List.copyOf(lineDefinitions);
        p2pLeaseRegistry = new P2pLineLeaseRegistry(p2pLineDefinitions);
        P2pLineDefinition debugLine = p2pLineDefinitions.getFirst();
        OutboundToteSnapshot debugOpenOutboundTote = new OutboundToteSnapshot(
                new PhysicalToteId("warehouse-outbound-104-1"),
                debugLine.lineId(),
                Optional.of("104"),
                Optional.of(DEBUG_ACTIVE_PHARMACY_ID),
                10,
                List.of(),
                Optional.empty());
        Map<P2pLineId, P2pLineActivityProbe> activityProbes = new LinkedHashMap<>();
        activityProbes.put(
                debugLine.lineId(),
                () -> new P2pLineActivitySnapshot(
                        new P2pInputActivitySnapshot(
                                p2pQueue.snapshot().occupancy(),
                                p2pInputQueue.snapshot().toteIds().size(),
                                false,
                                0),
                        P2pPackPathActivitySnapshot.idle(),
                        P2pBaggingActivitySnapshot.idle(),
                        Optional.of(debugOpenOutboundTote)));
        p2pLineDefinitions.stream().skip(1).forEach(definition ->
                activityProbes.put(
                        definition.lineId(), P2pLineActivitySnapshot::idle));
        p2pActivityProbes = Map.copyOf(activityProbes);

        List<WarehouseTransferRoutingTable.Entry> transferEntries = List.of(
                entry(firstMachine, thirdPartyDestination, TransferRoutingDecision.continueOnCurrentRoute()),
                entry(firstMachine, adaptingDestination, TransferRoutingDecision.transferTo(
                        sharedTarget, TransferOrientationPolicy.PRESERVE_TOTE_ORIENTATION)),
                entry(firstMachine, p2pDestination, TransferRoutingDecision.transferTo(
                        sharedTarget, TransferOrientationPolicy.PRESERVE_TOTE_ORIENTATION)),
                entry(secondMachine, adaptingDestination, TransferRoutingDecision.transferTo(
                        adaptingTarget, TransferOrientationPolicy.PRESERVE_TOTE_ORIENTATION)),
                entry(secondMachine, p2pDestination, TransferRoutingDecision.continueOnCurrentRoute()));

        P2pPhysicalToteAssignment firstP2pAssignment = assignment(
                "transport-p2p-1", debugLine);
        P2pPhysicalToteAssignment secondP2pAssignment = assignment(
                "transport-p2p-2", debugLine);
        p2pLeaseRegistry.acquireLease(
                debugLine.lineId(), "104", P2pLineActivitySnapshot.idle());
        p2pLeaseRegistry.commitAssignment(firstP2pAssignment);
        p2pLeaseRegistry.commitAssignment(secondP2pAssignment);
        scheduledRequests = List.of(
                request("transport-p2p-1", p2pDestination, 1, firstP2pAssignment),
                request("transport-third-party", thirdPartyDestination, 2),
                request("transport-adapting", adaptingDestination, 3),
                request("transport-p2p-2", p2pDestination, 4, secondP2pAssignment));
        scheduledRequests.forEach(request -> loadPlans.put(
                request.physicalToteId().value(),
                new ToteLoadPlan(request.physicalToteId(), List.of())));

        runtime = new DspWarehouseTransportRuntimeFactory().create(
                sim,
                objects,
                launchQueue,
                loadPlans::get,
                routeCatalog,
                (request, loadPlan) -> createInspectableTote(
                        request, tr, toteGeometry, inspectionRegistry),
                ROUTE_SPEED,
                new Vec3(0f, trackSpec.getLoadSurfaceY() + 0.02f, 0f),
                0f,
                8,
                8,
                List.of(firstMachine, secondMachine),
                transferEntries,
                (machine, strategy) -> installTransferController(sim, machine, strategy),
                List.of(thirdPartyQueue, adaptingQueue, p2pQueue));

        transportInspectionMarker = marker(
                "warehouse_transport_state", tr, new Vec3(-0.75f, 0.10f, -5.4f),
                0.45f, 0.06f, 0.45f, 0xFF2E86AB);
        objects.add(transportInspectionMarker);
        registerTransportInspection(transportInspectionMarker);
        addQueueMarker(tr, thirdPartyDestination, new Vec3(-0.75f, 0.10f, 3.2f), 0xFFE8B04A);
        addQueueMarker(tr, adaptingDestination, new Vec3(5.05f, 0.10f, 3.2f), 0xFF58A66A);
        addQueueMarker(tr, p2pDestination, new Vec3(7.8f, 0.10f, -2.45f), 0xFFB75BC7);

        sim.addController(new ScheduledLaunchController());
        sim.addController(new TerminalQueuePresentationController());
        float toteInteriorFloorLocalY = 0.04f
                + (toteGeometry.getOuterHeight() - toteGeometry.getInnerHeight())
                + 0.01f;
        P2pTipperArrivalTarget p2pTarget = new P2pTipperArrivalTarget(
                p2pDestination, p2pInputQueue);
        p2pArrivalRuntime = new DspP2pArrivalConsumerRuntimeFactory().create(
                sim,
                List.of(new P2pArrivalConsumerBinding(
                        p2pQueue,
                        new StickyP2pArrivalAdmissionPolicy(
                                debugLine, this::p2pLeaseSnapshot),
                        new P2pArrivalRouteBinding(p2p, p2p),
                        new ContainedPackP2pTipperPayloadFactory(
                                toteGeometry.getInnerBottomWidth(),
                                toteGeometry.getInnerBottomDepth(),
                                toteInteriorFloorLocalY,
                                0.012f,
                                0.012f,
                                0.010f),
                        p2pTarget)));
        sim.addController(new P2pInputCapacityReturnController());
        sim.addController(new BackpressureObservationController());
    }

    @Override
    public void syncVisuals() {
    }

    @Override
    public void close() {
        p2pArrivalRuntime.close();
        runtime.close();
    }

    DspWarehouseTransportRuntime runtime() {
        return runtime;
    }

    RenderableObject transportInspectionMarker() {
        return transportInspectionMarker;
    }

    RenderableObject queueMarker(String targetId) {
        return queueMarkers.get(targetId);
    }

    List<String> expectedPhysicalToteIds() {
        return scheduledRequests.stream().map(request -> request.physicalToteId().value()).toList();
    }

    boolean observedP2pBackpressure() {
        return observedP2pBackpressure;
    }

    boolean observedSeparatedP2pBackpressure() {
        return observedSeparatedP2pBackpressure;
    }

    boolean p2pCapacityReturned() {
        return p2pCapacityReturned;
    }

    List<String> consumedP2pToteIds() {
        return List.copyOf(consumedP2pToteIds);
    }

    int p2pInputOccupancy() {
        return p2pInputQueue.snapshot().toteIds().size();
    }

    long acceptedP2pArrivalCount() {
        return p2pArrivalRuntime.controllerSnapshots().get(0).successfulAcceptanceCount();
    }

    private StationRoutedToteArrivalQueue queue(
            OperationalRouteDestination destination, int capacity) {
        StationRoutedToteArrivalQueue queue = new StationRoutedToteArrivalQueue(destination, capacity);
        arrivalQueues.put(destination.targetId(), queue);
        return queue;
    }

    private RenderableObject createInspectableTote(
            OsrOutboundRouteLaunchRequest request,
            TriangleRenderer tr,
            ToteGeometry toteGeometry,
            SelectionInspectionRegistry registry) {
        RenderableObject renderable = RenderableToteFactory.createRenderableTote(
                request.physicalToteId().value(), tr, toteGeometry, true);
        toteRenderables.put(request.physicalToteId().value(), renderable);
        makeSelectableHierarchy(renderable, renderable);
        registry.register(renderable, () -> describeTote(request));
        return renderable;
    }

    private List<String> describeTote(OsrOutboundRouteLaunchRequest request) {
        return runtime.inFlightSnapshot().entries().stream()
                .filter(entry -> entry.physicalToteId().equals(request.physicalToteId()))
                .findFirst()
                .map(entry -> List.of(
                        "Type: Routed physical tote",
                        "Physical tote: " + entry.physicalToteId().value(),
                        "Destination: " + entry.destination().targetId(),
                        "Segment: " + entry.currentRouteSegmentLabel(),
                        "Motion: " + entry.motionState(),
                        "Arrival pending: " + entry.arrivalPending()))
                .orElseGet(() -> List.of(
                        "Type: Routed physical tote",
                        "Physical tote: " + request.physicalToteId().value(),
                        "Destination: " + request.destination().targetId(),
                        "Transport ownership: " + transportOwnership(request)));
    }

    private String transportOwnership(OsrOutboundRouteLaunchRequest request) {
        String toteId = request.physicalToteId().value();
        if (p2pInputQueue.contains(toteId)) {
            return "P2P tipper input queue";
        }
        StationRoutedToteArrivalQueue arrivalQueue = arrivalQueues.get(
                request.destination().targetId());
        if (arrivalQueue != null && arrivalQueue.contains(request.physicalToteId())) {
            return "station arrival queue";
        }
        return "downstream placeholder";
    }

    private void registerTransportInspection(RenderableObject marker) {
        makeSelectableHierarchy(marker, marker);
        inspectionRegistry.register(marker, () -> {
            var launch = runtime.routeLaunchController().snapshot();
            var ingress = runtime.ingressController().snapshot();
            var inFlight = runtime.inFlightSnapshot();
            var arrival = runtime.arrivalController().snapshot();
            StationRoutedToteArrivalQueueSnapshot p2pStation =
                    arrivalQueues.get(P2P_TARGET_ID).snapshot();
            var p2pInput = p2pInputQueue.snapshot();
            List<String> description = new ArrayList<>(List.of(
                    "Type: DSP warehouse transport",
                    "Launch: " + launch.launchOccupancy() + " / " + launch.launchCapacity(),
                    "Transport: " + ingress.transportOccupancy() + " / " + ingress.transportCapacity(),
                    "In flight: " + inFlight.occupancy() + " / " + inFlight.capacity(),
                    "In-flight totes: " + inFlight.entries().stream()
                            .map(entry -> entry.physicalToteId().value() + " -> "
                                    + entry.destination().targetId())
                            .toList(),
                    "Pending arrivals: " + arrival.pendingArrivals().stream()
                            .map(pending -> pending.physicalToteId().value())
                            .toList(),
                    "P2P station arrival: " + p2pStation.occupancy()
                            + " / " + p2pStation.capacity(),
                    "P2P tipper input: " + p2pInput.toteIds().size()
                            + " / " + p2pInput.capacity(),
                    "Blocked: " + (arrival.blocked() ? arrival.blockedReason() : "none")));
            p2pLeaseSnapshot().lines().forEach(line -> description.add(
                    "P2P lease " + line.definition().lineId().value()
                            + ": owner=" + line.serviceCentreId().orElse("none")
                            + ", pharmacy=" + line.activePharmacyId().orElse("none")
                            + ", quiescent=" + line.activity().quiescent()
                            + ", blockers=[arrival="
                                    + line.activity().input().stationArrivalCount()
                                    + ", tipperInput="
                                    + line.activity().input().tipperInputCount()
                                    + ", processingDrained="
                                    + line.activity().processingDrained() + "]"
                            + ", assignments=" + line.physicalAssignments().stream()
                                    .map(value -> value.physicalToteId().value()).toList()
                            + ", closure=none"
                            + ", transition=" + p2pLeaseRegistry
                                    .lastTransitionFor(line.definition().lineId())
                                    .map(value -> value.details()).orElse("none")));
            return List.copyOf(description);
        });
    }

    private P2pLineLeaseCatalogSnapshot p2pLeaseSnapshot() {
        Map<P2pLineId, P2pLineActivitySnapshot> activities = new LinkedHashMap<>();
        for (P2pLineDefinition definition : p2pLineDefinitions) {
            activities.put(
                    definition.lineId(),
                    p2pActivityProbes.get(definition.lineId()).snapshot());
        }
        return p2pLeaseRegistry.snapshot(activities);
    }

    private void addQueueMarker(
            TriangleRenderer tr,
            OperationalRouteDestination destination,
            Vec3 position,
            int colour) {
        RenderableObject marker = marker(
                destination.targetId() + "_queue_state", tr, position,
                0.45f, 0.06f, 0.45f, colour);
        queueMarkers.put(destination.targetId(), marker);
        objects.add(marker);
        makeSelectableHierarchy(marker, marker);
        inspectionRegistry.register(marker, () -> describeQueue(destination.targetId()));
    }

    private List<String> describeQueue(String targetId) {
        StationRoutedToteArrivalQueueSnapshot snapshot = arrivalQueues.get(targetId).snapshot();
        List<String> description = new ArrayList<>(List.of(
                "Type: Station routed-tote arrival queue",
                "Destination: " + snapshot.destination().stationType(),
                "Target: " + snapshot.destination().targetId(),
                "Queue: " + snapshot.occupancy() + " / " + snapshot.capacity(),
                "Totes: " + snapshot.entries().stream()
                        .map(entry -> entry.physicalToteId().value()).toList()));
        if (P2P_TARGET_ID.equals(targetId)) {
            var inputSnapshot = p2pInputQueue.snapshot();
            description.add("Tipper input: " + inputSnapshot.toteIds().size()
                    + " / " + inputSnapshot.capacity());
            description.add("Tipper totes: " + inputSnapshot.toteIds());
        }
        return List.copyOf(description);
    }

    private final class ScheduledLaunchController implements SimulationController {
        private int nextRequestIndex;
        private double nextReleaseTimeSeconds;

        @Override
        public void update(SimulationContext context, double dtSeconds) {
            if (nextRequestIndex >= scheduledRequests.size()
                    || context.getSimulationTimeSeconds() < nextReleaseTimeSeconds
                    || !launchQueue.canAccept()) {
                return;
            }
            launchQueue.enqueue(scheduledRequests.get(nextRequestIndex));
            nextRequestIndex++;
            nextReleaseTimeSeconds = context.getSimulationTimeSeconds() + RELEASE_INTERVAL_SECONDS;
        }
    }

    private final class TerminalQueuePresentationController implements SimulationController {
        @Override
        public void update(SimulationContext context, double dtSeconds) {
            arrivalQueues.forEach((targetId, queue) -> queue.peek().ifPresent(routedTote -> {
                if (P2P_TARGET_ID.equals(targetId)) {
                    routedTote.tote().setVisualOffset(0.45f, 0f, 0f);
                } else {
                    routedTote.tote().setVisualOffset(0f, 0f, 0.45f);
                }
            }));
            TipperTotePayload payload = p2pInputQueue.peekPayload();
            if (payload != null) {
                payload.getTote().setVisualOffset(0.90f, 0f, 0f);
            }
        }
    }

    private final class P2pInputCapacityReturnController implements SimulationController {
        private double occupiedSinceSeconds = Double.NaN;

        @Override
        public void update(SimulationContext context, double dtSeconds) {
            if (p2pCapacityReturned || p2pInputQueue.peekPayload() == null) {
                return;
            }
            if (Double.isNaN(occupiedSinceSeconds)) {
                occupiedSinceSeconds = context.getSimulationTimeSeconds();
                return;
            }
            if (context.getSimulationTimeSeconds() - occupiedSinceSeconds < P2P_HOLD_SECONDS) {
                return;
            }
            var consumed = p2pInputQueue.dequeuePayload();
            consumed.getToteRenderable().setVisible(false);
            consumedP2pToteIds.add(consumed.getTote().getId());
            p2pCapacityReturned = true;
        }
    }

    private final class BackpressureObservationController implements SimulationController {
        @Override
        public void update(SimulationContext context, double dtSeconds) {
            var queued = arrivalQueues.get(P2P_TARGET_ID).peek().orElse(null);
            var inputPayload = p2pInputQueue.peekPayload();
            if (queued == null || inputPayload == null) {
                return;
            }
            observedP2pBackpressure = true;
            RenderableObject queuedRenderable = toteRenderables.get(
                    queued.physicalToteId().value());
            RenderableObject inputRenderable = inputPayload.getToteRenderable();
            if (queuedRenderable == null) {
                return;
            }
            float deltaX = queuedRenderable.transformation.xTranslation
                    - inputRenderable.transformation.xTranslation;
            float deltaZ = queuedRenderable.transformation.zTranslation
                    - inputRenderable.transformation.zTranslation;
            if (((deltaX * deltaX) + (deltaZ * deltaZ)) > 0.04f) {
                observedSeparatedP2pBackpressure = true;
            }
        }
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
                destination.targetId() + "_arrival_sensor",
                terminal);
    }

    private static WarehouseRouteDefinition definition(
            WarehouseRouteCatalog catalog,
            OperationalRouteDestination destination) {
        return catalog.find(destination).orElseThrow();
    }

    private static OperationalRouteDestination destination(
            StationType stationType, String targetId) {
        return new OperationalRouteDestination(stationType, targetId);
    }

    private static WarehouseTransferRoutingTable.Entry entry(
            TransferZoneMachine machine,
            OperationalRouteDestination destination,
            TransferRoutingDecision decision) {
        return new WarehouseTransferRoutingTable.Entry(
                machine.getId(), destination.targetId(), decision);
    }

    private static void addTerminalSensor(
            SimulationWorld sim, WarehouseRouteDefinition definition) {
        float end = definition.terminalSegment().length();
        float start = Math.max(0f, end - 0.65f);
        sim.addSensor(new WindowSensor(
                definition.terminalArrivalSensorId(),
                new WindowSensorAreaImpl(
                        definition.terminalArrivalSensorId() + "_area",
                        definition.terminalSegment(),
                        start,
                        Math.max(start, end - 0.15f))));
    }

    private static TransferZoneMachine createUncontrolledMachine(
            SimulationWorld sim,
            RouteSegment approachSegment,
            float approachStartDistance,
            RouteSegment transferWindowSegment,
            TransferZone zone) {
        String approachSensorId = zone.getId() + "_member_sensor";
        Sensor approachSensor = new MembershipSensor(
                approachSensorId,
                new WindowSensorAreaImpl(
                        zone.getId() + "_member_sensor_area",
                        approachSegment,
                        approachStartDistance,
                        approachSegment.length()));
        String windowSensorId = zone.getId() + "_sensor";
        Sensor windowSensor = new WindowSensor(
                windowSensorId,
                new WindowSensorAreaImpl(
                        zone.getId() + "_window_sensor_area",
                        transferWindowSegment,
                        0f,
                        transferWindowSegment.length()));
        sim.addSensor(approachSensor);
        sim.addSensor(windowSensor);
        return new TransferZoneMachine(
                "Transfer_Machine_" + zone.getId(),
                approachSensorId,
                windowSensorId,
                zone);
    }

    private static void installTransferController(
            SimulationWorld sim,
            TransferZoneMachine machine,
            TransferTargetDecisionStrategy strategy) {
        TransferZoneController controller = new TransferZoneController(machine, strategy);
        sim.addController(controller);
        sim.registerListener(DetectionEvent.class, controller.detectionHandler());
        sim.registerListener(TransferCompletedEvent.class, controller.completionHandler());
    }

    private static void attachMechanism(
            TransferZone zone,
            RouteSegment transferSegment,
            TrackSpec transferSpec,
            TriangleRenderer tr,
            List<RenderableObject> objects,
            SelectionInspectionRegistry inspectionRegistry) {
        SteeringTransferMachineGeometry geometry = SteeringTransferMachineGeometry.fromTransferWindow(
                transferSegment.getGeometry().sampleByDistance(0f),
                transferSegment.getGeometry().sampleByDistance(transferSegment.length()),
                transferSegment.length(),
                transferSpec.getRunningWidth());
        WarehouseTrackFactory.attachSteeringMechanismForZone(
                zone, geometry, tr, objects, inspectionRegistry, 0xFF42484D, 0xFFE1E5E8);
    }

    private static void addTrack(
            TriangleRenderer tr,
            RouteSegment segment,
            TrackSpec spec,
            TrackAppearance appearance,
            List<RenderableObject> objects,
            SelectionInspectionRegistry inspectionRegistry) {
        RenderableObject track = RenderableTrackFactory.createRenderableTrack(
                tr, segment, new WarehouseSegmentMetadata(), spec, appearance);
        objects.add(track);
        makeSelectableHierarchy(track, track);
        inspectionRegistry.register(track, () -> List.of(
                "Type: Warehouse transport track",
                "Segment: " + segment.getLabel(),
                "Length: " + String.format("%.2f", segment.length())));
    }

    private static RouteSegment segment(
            String label, float startX, float startZ, float endX, float endZ) {
        return new RouteSegment(
                label,
                new LinearSegment3(
                        new Vec3(startX, 0f, startZ),
                        new Vec3(endX, 0f, endZ),
                        false));
    }

    private static OsrOutboundRouteLaunchRequest request(
            String physicalToteId,
            OperationalRouteDestination destination,
            long sequence) {
        InboundToteManifest manifest = new InboundToteManifest(
                new PhysicalToteId(physicalToteId),
                new OrderSheetKey("order-" + physicalToteId, 1),
                OrderType.FULL_PACK,
                "104",
                List.of(new DspOrderItem(
                        "line-" + physicalToteId,
                        "product-" + physicalToteId,
                        1)),
                sequence);
        return new OsrOutboundRouteLaunchRequest(
                new OsrProcessingReleaseRequest(manifest, Duration.ofSeconds(sequence)),
                destination);
    }

    private static OsrOutboundRouteLaunchRequest request(
            String physicalToteId,
            OperationalRouteDestination destination,
            long sequence,
            P2pPhysicalToteAssignment assignment) {
        InboundToteManifest manifest = new InboundToteManifest(
                new PhysicalToteId(physicalToteId),
                new OrderSheetKey("order-" + physicalToteId, 1),
                OrderType.FULL_PACK,
                "104",
                List.of(new DspOrderItem(
                        "line-" + physicalToteId,
                        "product-" + physicalToteId,
                        1)),
                sequence);
        return new OsrOutboundRouteLaunchRequest(
                new OsrProcessingReleaseRequest(
                        manifest,
                        Duration.ofSeconds(sequence),
                        Optional.of(assignment)),
                destination);
    }

    private static P2pPhysicalToteAssignment assignment(
            String physicalToteId,
            P2pLineDefinition line) {
        return new P2pPhysicalToteAssignment(
                new PhysicalToteId(physicalToteId),
                "104",
                line.lineId(),
                line.destination());
    }

    private static TrackSpec rollerTrackSpec(ToteGeometry toteGeometry, boolean guides) {
        return new TrackSpec(
                new ToteEnvelope(
                        toteGeometry.getOuterBottomWidth(),
                        toteGeometry.getOuterBottomDepth(),
                        toteGeometry.getOuterHeight()),
                0.030f,
                0.040f,
                0f,
                guides,
                0.050f,
                0.010f,
                0f,
                0.5f,
                1f,
                TrackDriveType.ROLLER,
                guides,
                0.080f,
                0.010f,
                0.025f,
                0.018f,
                0.010f,
                0.012f,
                0.080f,
                0.220f,
                0.050f,
                0.004f,
                true,
                false,
                0.080f);
    }

    private static TrackAppearance trackAppearance() {
        return new TrackAppearance(
                new OneColourStrategyImpl(0xFF4F5B61),
                new OneColourStrategyImpl(0xFF25292C),
                new OneColourStrategyImpl(0xFF30363A),
                new OneColourStrategyImpl(0xFFC8D0D4),
                new OneColourStrategyImpl(0xFF8A969D),
                new OneColourStrategyImpl(0xFF42484D));
    }

    private static RenderableObject marker(
            String id,
            TriangleRenderer tr,
            Vec3 position,
            float length,
            float height,
            float width,
            int colourArgb) {
        return RenderableObject.create(
                id,
                tr,
                RollerMeshFactory.createBoxRollerMesh(length, height, width),
                new ObjectTransformation(
                        0f, 0f, 0f,
                        position.x, position.y, position.z,
                        new Mat4()),
                new OneColourStrategyImpl(colourArgb),
                true);
    }

    private static void makeSelectableHierarchy(
            RenderableObject root, RenderableObject selectionTarget) {
        root.setSelectable(true);
        root.setSelectionTarget(selectionTarget);
        for (RenderableObject child : root.children) {
            makeSelectableHierarchy(child, selectionTarget);
        }
    }
}
