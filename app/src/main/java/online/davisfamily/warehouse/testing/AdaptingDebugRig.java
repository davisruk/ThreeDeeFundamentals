package online.davisfamily.warehouse.testing;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import online.davisfamily.threedee.behaviour.routing.RouteFollower;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.debug.SelectionInspectionRegistry;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.path.LinearSegment3;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.rendering.TriangleRenderer;
import online.davisfamily.threedee.rendering.appearance.OneColourStrategyImpl;
import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.rendering.model.tote.RenderableToteFactory;
import online.davisfamily.warehouse.rendering.model.tote.ToteEnvelope;
import online.davisfamily.warehouse.rendering.model.tote.ToteGeometry;
import online.davisfamily.warehouse.rendering.model.tracks.RouteTrackFactory;
import online.davisfamily.warehouse.rendering.model.tracks.RouteTrackFactory.SpecAndSegment;
import online.davisfamily.warehouse.rendering.model.tracks.TrackAppearance;
import online.davisfamily.warehouse.rendering.model.tracks.TrackDriveType;
import online.davisfamily.warehouse.rendering.model.tracks.TrackSpec;
import online.davisfamily.warehouse.rendering.model.tracks.WarehouseRouteBuilder;
import online.davisfamily.warehouse.rendering.model.tracks.RollerMeshFactory;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptedLineStore;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingArea;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingAreaAdmissionSnapshot;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingAreaController;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingBench;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingBenchAdmissionSnapshot;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingBenchId;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingCollectVisitFactory;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingStorageMap;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingStationAdmissionResolver;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingVisit;
import online.davisfamily.warehouse.sim.dsp.adapting.MapBackedToteLoadPlanRegistry;
import online.davisfamily.warehouse.sim.dsp.adapting.DefaultCollectedPackPlanFactory;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.StartLocation;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements;
import online.davisfamily.warehouse.sim.dsp.runtime.DspSchedulerRuntimeState;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspDependencyEvaluator;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspOrderStatus;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspReleaseScheduler;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;
import online.davisfamily.warehouse.sim.dsp.scheduler.ReleaseDecision;
import online.davisfamily.warehouse.sim.dsp.scheduler.ServiceCentrePriority;
import online.davisfamily.warehouse.sim.dsp.scheduler.ServiceCentreWindowPolicy;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationCapacity;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.transfer.TransferMotionConfig;
import online.davisfamily.warehouse.sim.transfer.TransferRoutingDecision;
import online.davisfamily.warehouse.sim.transfer.TransferTarget;
import online.davisfamily.warehouse.sim.transfer.TransferZone;
import online.davisfamily.warehouse.sim.transfer.TransferZoneMachine;
import online.davisfamily.warehouse.sim.transfer.mechanism.SteeringConveyorMechanism;
import online.davisfamily.warehouse.sim.transfer.strategy.TransferTargetDecisionStrategy;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperTotePayload;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;
import online.davisfamily.warehouse.testing.scheduler.ScheduledDebugToteInjectorController;
import online.davisfamily.warehouse.testing.scheduler.ScheduledTipperToteRelease;
import online.davisfamily.warehouse.testing.scheduler.ScheduledTipperToteReleaseCatalog;
import online.davisfamily.warehouse.testing.scheduler.ScheduledToteReleaseTarget;
import online.davisfamily.warehouse.testing.scheduler.SchedulerDebugInspectable;

public class AdaptingDebugRig implements DebugSceneRuntime {
    private static final String SERVICE_CENTRE_ID = "SC-ADAPTING";
    private static final PackDimensions PACK_DIMENSIONS = new PackDimensions(0.20f, 0.10f, 0.08f);
    private static final float TOTE_SPEED = 0.9f;
    private static final float BENCH_PATH_LENGTH = 5.4f;
    private static final List<Float> BENCH_SENSOR_DISTANCES = List.of(1.0f, 2.7f, 4.4f);
    private static final float BENCH_WALKWAY_CLEARANCE = 0.30f;

    private final AdaptedLineStore adaptedLineStore = new AdaptedLineStore();
    private final AdaptingStorageMap adaptingStorageMap = createStorageMap();
    private final AdaptingArea adaptingArea = new AdaptingArea(createBenches(adaptedLineStore), 0, adaptingStorageMap);
    private final MapBackedToteLoadPlanRegistry toteLoadPlanRegistry = new MapBackedToteLoadPlanRegistry();
    private final DspSchedulerRuntimeState runtimeState;
    private final AdaptingAreaController areaController;
    private final Map<String, AdaptingVisit> visitsByOrderId = new LinkedHashMap<>();
    private final Map<String, AdaptingBenchJourney> journeysByToteId = new LinkedHashMap<>();
    private final RouteSegment mainApproach;
    private final RouteSegment mainTransfer;
    private final RouteSegment mainDestination;
    private final RouteSegment spine;
    private final RouteSegment inlineTransfer;
    private final RouteSegment bench1Path;
    private final RouteSegment bench2Path;
    private final RouteSegment mainReturn1Transfer;
    private final RouteSegment mainReturn2Transfer;
    private final RouteSegment bench1ReturnLink;
    private final RouteSegment bench2ReturnLink;
    private final TransferTarget mainBranchTarget;
    private final TransferTarget bench1Target;
    private final TransferTarget bench2Target;
    private final TransferTarget bench1ReturnTarget;
    private final TransferTarget bench2ReturnTarget;
    private final SteeringTransferMachineGeometry mainTransferGeometry;
    private final SteeringTransferMachineGeometry inlineTransferGeometry;
    private final SteeringTransferMachineGeometry mainReturn1Geometry;
    private final SteeringTransferMachineGeometry mainReturn2Geometry;
    private final Map<AdaptingBenchId, AdaptingBenchStop> benchStopsById;
    private final RouteFollower.TravelDirection mainDirection = RouteFollower.TravelDirection.FORWARD;
    private final MainDivertStrategy mainDivertStrategy = new MainDivertStrategy();
    private final BenchSelectStrategy benchSelectStrategy = new BenchSelectStrategy();

    public AdaptingDebugRig(
            TriangleRenderer tr,
            SimulationWorld sim,
            List<RenderableObject> objects,
            SelectionInspectionRegistry inspectionRegistry) {
        if (tr == null || sim == null || objects == null || inspectionRegistry == null) {
            throw new IllegalArgumentException("Adapting debug rig inputs must not be null");
        }

        ToteGeometry toteGeometry = new ToteGeometry();
        TrackAppearance appearance = new TrackAppearance(
                new OneColourStrategyImpl(0xFF596A54),
                new OneColourStrategyImpl(0xFF2A2A2A),
                new OneColourStrategyImpl(0xFF303030),
                new OneColourStrategyImpl(0xFFB8B8B8),
                new OneColourStrategyImpl(0xFF596A54),
                new OneColourStrategyImpl(0xFF596A54));
        TrackSpec sourceSpec = rollerSpec(toteGeometry, true);
        TrackSpec transferSpec = rollerSpec(toteGeometry, false);
        TrackSpec linkSpec = rollerSpecForLink(toteGeometry);

        ToteEnvelope toteEnvelope = new ToteEnvelope(
                toteGeometry.getOuterBottomWidth(),
                toteGeometry.getOuterBottomDepth(),
                toteGeometry.getOuterHeight());
        float transferLength = toteEnvelope.bottomDepth;

        float mainTransferCentreZ = 0.5f;
        Vec3 mainTransferStart = new Vec3(0f, 0f, mainTransferCentreZ - (transferLength * 0.5f));
        Vec3 mainTransferEnd = new Vec3(0f, 0f, mainTransferCentreZ + (transferLength * 0.5f));
        mainTransferGeometry = SteeringTransferMachineGeometry.fromTransferWindow(
                mainTransferStart,
                mainTransferEnd,
                transferLength,
                transferSpec.getOverallWidth());
        inlineTransferGeometry = SteeringTransferMachineGeometry.fromTransferWindow(
                new Vec3(-2.5f, 0f, 0.5f),
                new Vec3(-2.5f - transferLength, 0f, 0.5f),
                transferLength,
                transferSpec.getOverallWidth());

        WarehouseRouteBuilder builder = new WarehouseRouteBuilder();

        mainApproach = builder.segment("adapting_main_approach", new LinearSegment3(
                new Vec3(0f, 0f, -8f),
                mainTransferStart,
                false));
        mainTransfer = builder.segment("adapting_main_transfer", new LinearSegment3(
                mainTransferStart,
                mainTransferEnd,
                false));
        mainDestination = builder.segment("adapting_main_destination", new LinearSegment3(
                mainTransferEnd,
                new Vec3(0f, 0f, 8f),
                false));
        spine = builder.segment("adapting_spine", new LinearSegment3(
                mainTransferGeometry.leftTargetAttachmentPoint(),
                new Vec3(-2.5f, 0f, 0.5f),
                false));
        inlineTransfer = builder.segment("adapting_inline_transfer", new LinearSegment3(
                new Vec3(-2.5f, 0f, 0.5f),
                new Vec3(-2.5f - transferLength, 0f, 0.5f),
                false));
        Vec3 bench1LaneStart = inlineTransferGeometry.rightTargetAttachmentPoint();
        Vec3 bench2LaneStart = inlineTransferGeometry.leftTargetAttachmentPoint();
        Vec3 bench1TransferStart = new Vec3(bench1LaneStart.x, 0f, bench1LaneStart.z + BENCH_PATH_LENGTH);
        Vec3 bench1TransferEnd = new Vec3(bench1LaneStart.x, 0f, bench1TransferStart.z + transferLength);
        Vec3 bench2TransferStart = new Vec3(bench2LaneStart.x, 0f, bench2LaneStart.z - BENCH_PATH_LENGTH);
        Vec3 bench2TransferEnd = new Vec3(bench2LaneStart.x, 0f, bench2TransferStart.z - transferLength);

        bench1Path = builder.segment("adapting_bench_1_path", new LinearSegment3(
                bench1LaneStart,
                bench1TransferStart,
                false));
        bench2Path = builder.segment("adapting_bench_2_path", new LinearSegment3(
                bench2LaneStart,
                bench2TransferStart,
                false));
        mainReturn1Transfer = builder.segment("main-return-1", new LinearSegment3(
                bench1TransferStart,
                bench1TransferEnd,
                false));
        mainReturn2Transfer = builder.segment("main-return-2", new LinearSegment3(
                bench2TransferStart,
                bench2TransferEnd,
                false));

        mainReturn1Geometry = SteeringTransferMachineGeometry.fromTransferWindow(
                bench1TransferStart,
                bench1TransferEnd,
                transferLength,
                transferSpec.getOverallWidth());
        mainReturn2Geometry = SteeringTransferMachineGeometry.fromTransferWindow(
                bench2TransferStart,
                bench2TransferEnd,
                transferLength,
                transferSpec.getOverallWidth());

        Vec3 bench1ReturnStart = mainReturn1Geometry.rightTargetAttachmentPoint();
        float mainDestinationReturnEntryDistance = clamp(
                bench1ReturnStart.z - mainDestination.getGeometry().getStartPoint().z,
                0.75f,
                mainDestination.length() - 0.75f);
        Vec3 bench1ReturnEnd = leftEdgePoint(mainDestination, sourceSpec, mainDestinationReturnEntryDistance);
        Vec3 bench2ReturnStart = mainReturn2Geometry.leftTargetAttachmentPoint();
        float mainApproachReturnEntryDistance = clamp(
                Math.abs(bench2ReturnStart.z - mainApproach.getGeometry().getStartPoint().z),
                0.75f,
                mainApproach.length() - 0.75f);
        Vec3 bench2ReturnEnd = leftEdgePoint(mainApproach, sourceSpec, mainApproachReturnEntryDistance);

        bench1ReturnLink = builder.segment("adapting_bench_1_return", new LinearSegment3(
                bench1ReturnStart,
                bench1ReturnEnd,
                true));
        bench2ReturnLink = builder.segment("adapting_bench_2_return", new LinearSegment3(
                bench2ReturnStart,
                bench2ReturnEnd,
                true));

        builder.renderWith(mainApproach, sourceSpec)
                .renderWith(mainTransfer, transferSpec)
                .renderWith(mainDestination, sourceSpec)
                .renderWith(spine, sourceSpec)
                .renderWith(inlineTransfer, transferSpec)
                .renderWith(bench1Path, sourceSpec)
                .renderWith(bench2Path, sourceSpec)
                .renderWith(mainReturn1Transfer, transferSpec)
                .renderWith(mainReturn2Transfer, transferSpec)
                .renderWith(bench1ReturnLink, linkSpec)
                .renderWith(bench2ReturnLink, linkSpec);
        builder.connectLoop(mainApproach, mainTransfer);
        builder.connectLoop(mainTransfer, mainDestination);
        builder.connectLoop(spine, inlineTransfer);
        builder.connectLoop(bench1Path, mainReturn1Transfer);
        builder.connectLoop(bench2Path, mainReturn2Transfer);
        builder.connectLinkInto(
                bench1ReturnLink,
                mainDestination,
                mainDestinationReturnEntryDistance,
                online.davisfamily.warehouse.rendering.model.tracks.GuideSide.LEFT);
        builder.connectLinkInto(
                bench2ReturnLink,
                mainApproach,
                mainApproachReturnEntryDistance,
                online.davisfamily.warehouse.rendering.model.tracks.GuideSide.LEFT);

        mainBranchTarget = new TransferTarget(spine, 0f, RouteFollower.TravelDirection.FORWARD);
        bench1Target = new TransferTarget(bench1Path, 0f, RouteFollower.TravelDirection.FORWARD);
        bench2Target = new TransferTarget(bench2Path, 0f, RouteFollower.TravelDirection.FORWARD);
        bench1ReturnTarget = new TransferTarget(bench1ReturnLink, 0f, RouteFollower.TravelDirection.FORWARD);
        bench2ReturnTarget = new TransferTarget(bench2ReturnLink, 0f, RouteFollower.TravelDirection.FORWARD);
        benchStopsById = createBenchStops();

        builder.addStandaloneTransfer(
                "adapting_main_divert",
                mainTransfer,
                List.of(mainBranchTarget),
                mainDivertStrategy,
                new TransferMotionConfig(0.35, 0f, 0f));
        builder.addStandaloneTransfer(
                "adapting_inline_select",
                inlineTransfer,
                List.of(bench1Target, bench2Target),
                benchSelectStrategy,
                new TransferMotionConfig(0.35, 0f, 0f));
        builder.addStandaloneTransfer(
                "main-return-1-transfer",
                mainReturn1Transfer,
                List.of(bench1ReturnTarget),
                new FixedTransferTargetStrategy(bench1ReturnTarget),
                new TransferMotionConfig(0.35, 0f, 0f));
        builder.addStandaloneTransfer(
                "main-return-2-transfer",
                mainReturn2Transfer,
                List.of(bench2ReturnTarget),
                new FixedTransferTargetStrategy(bench2ReturnTarget),
                new TransferMotionConfig(0.35, 0f, 0f));

        List<SpecAndSegment> trackSpecs = builder.getSpecsAndSegments();
        List<RenderableObject> tracks = RouteTrackFactory.createRenderableTracks(tr, trackSpecs, appearance);
        for (int i = 0; i < tracks.size(); i++) {
            RenderableObject track = tracks.get(i);
            objects.add(track);
            registerTrackInspection(inspectionRegistry, track, trackSpecs.get(i).segment());
        }
        installTransferMachines(
                tr,
                sim,
                objects,
                inspectionRegistry,
                builder,
                mainApproach,
                mainTransfer,
                spine,
                inlineTransfer,
                mainReturn1Transfer,
                mainReturn2Transfer);

        RouteRequirements storeRoute = new RouteRequirements(false, true, false, false, false, StartLocation.OSR);
        RouteRequirements collectRoute = new RouteRequirements(false, true, false, true, false, StartLocation.OSR);

        DspSchedulerOrderState storeOne = orderState(adaptedOrder(
                "adapt-store-1",
                "tote-store-1",
                adaptedPreparedLine("line-c1", "collect-1", "0000310")),
                storeRoute);
        DspSchedulerOrderState storeTwo = orderState(adaptedOrder(
                "adapt-store-2",
                "tote-store-2",
                adaptedPreparedLine("line-unused", "collect-2", "0000388")),
                storeRoute);
        DspSchedulerOrderState collectOne = orderState(associatedCollectOrder(
                "collect-1",
                "tote-collect-1",
                "line-c1",
                "0000310"),
                collectRoute);
        DspSchedulerOrderState collectTwo = orderState(associatedCollectOrder(
                "collect-2",
                "tote-collect-2",
                "line-unused",
                "0000388"),
                collectRoute);

        runtimeState = new DspSchedulerRuntimeState(new WarehouseSchedulerSnapshot(
                List.of(storeOne, storeTwo, collectOne, collectTwo),
                Map.of(StationType.P2P, new StationAdmissionSnapshot(
                        StationType.P2P,
                        new StationCapacity(1, 1),
                        new StationSnapshot(StationType.P2P, 0, 0),
                        true,
                        "")),
                java.util.Set.of(),
                Optional.empty()));
        areaController = new AdaptingAreaController(
                adaptingArea,
                runtimeState,
                toteLoadPlanRegistry,
                new DefaultCollectedPackPlanFactory(PACK_DIMENSIONS));

        ScheduledTipperToteReleaseCatalog releaseCatalog = new ScheduledTipperToteReleaseCatalog(List.of(
                releaseFor(tr, toteGeometry, storeOne.order(), List.of(new PackPlan("pack-store-1", "bag-store-1", PACK_DIMENSIONS))),
                releaseFor(tr, toteGeometry, storeTwo.order(), List.of(new PackPlan("pack-store-2", "bag-store-2", PACK_DIMENSIONS))),
                releaseFor(tr, toteGeometry, collectOne.order(), List.of(new PackPlan("pack-existing-1", "bag-existing", PACK_DIMENSIONS))),
                releaseFor(tr, toteGeometry, collectTwo.order(), List.of(new PackPlan("pack-existing-2", "bag-existing-2", PACK_DIMENSIONS)))));

        visitsByOrderId.put(storeOne.order().orderId(), AdaptingVisit.store(storeOne.order().notionalToteId(), storeOne.order().items()));
        visitsByOrderId.put(storeTwo.order().orderId(), AdaptingVisit.store(storeTwo.order().notionalToteId(), storeTwo.order().items()));
        visitsByOrderId.put(collectOne.order().orderId(), new AdaptingCollectVisitFactory().create(
                collectOne.order().notionalToteId(),
                collectOne.order()));
        visitsByOrderId.put(collectTwo.order().orderId(), new AdaptingCollectVisitFactory().create(
                collectTwo.order().notionalToteId(),
                collectTwo.order()));
        toteLoadPlanRegistry.putLoadPlan(releaseCatalog.findByOrderId(collectOne.order().orderId()).orElseThrow().toteLoadPlan());
        toteLoadPlanRegistry.putLoadPlan(releaseCatalog.findByOrderId(collectTwo.order().orderId()).orElseThrow().toteLoadPlan());

        DspReleaseScheduler scheduler = new DspReleaseScheduler(
                new ServiceCentreWindowPolicy(new ServiceCentrePriority(List.of(SERVICE_CENTRE_ID))),
                new DspDependencyEvaluator(),
                new AdaptingStationAdmissionResolver(
                        new online.davisfamily.warehouse.sim.dsp.scheduler.SnapshotStationAdmissionResolver(),
                        adaptingArea,
                        new StationCapacity(6, 0)));

        ScheduledDebugToteInjectorController injectorController = new ScheduledDebugToteInjectorController(
                scheduler,
                runtimeState,
                releaseCatalog,
                new AdaptingReleaseTarget(sim, objects));
        sim.addController(injectorController);
        sim.addController(new AdaptingBenchStopController(
                adaptingArea,
                areaController,
                journeysByToteId,
                benchStopsById,
                mainDestination,
                AdaptingDebugRig::hideTote));

        for (AdaptingBenchStop stop : benchStopsById.values()) {
            RenderableObject benchMarker = createBenchMarkerForStop(tr, stop, linkSpec);
            objects.add(benchMarker);
            registerBenchInspection(inspectionRegistry, benchMarker, stop.benchId());
        }
        RenderableObject schedulerAnchor = createBenchMarker("adapting_scheduler_anchor", tr, new Vec3(0.8f, 0.18f, -6.5f), 0xFF4F5F7F);
        objects.add(schedulerAnchor);
        SchedulerDebugInspectable schedulerInspectable = new SchedulerDebugInspectable(injectorController);
        inspectionRegistry.register(schedulerAnchor, () -> schedulerInspectable.describe());
    }

    @Override
    public void syncVisuals() {
    }

    private Map<AdaptingBenchId, AdaptingBenchStop> createBenchStops() {
        Map<AdaptingBenchId, AdaptingBenchStop> stops = new LinkedHashMap<>();
        for (int i = 0; i < BENCH_SENSOR_DISTANCES.size(); i++) {
            AdaptingBenchId upperBench = new AdaptingBenchId("bench-" + (i + 1));
            AdaptingBenchId lowerBench = new AdaptingBenchId("bench-" + (i + 4));
            stops.put(upperBench, new AdaptingBenchStop(upperBench, bench1Path, BENCH_SENSOR_DISTANCES.get(i)));
            stops.put(lowerBench, new AdaptingBenchStop(lowerBench, bench2Path, BENCH_SENSOR_DISTANCES.get(i)));
        }
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(stops));
    }

    private RouteSegment benchPathFor(AdaptingBenchId benchId) {
        return isUpperBench(benchId) ? bench1Path : bench2Path;
    }

    private RouteSegment adjacentMainSegmentFor(AdaptingBenchId benchId) {
        return isUpperBench(benchId) ? mainDestination : mainApproach;
    }

    private RenderableObject createBenchMarkerForStop(
            TriangleRenderer tr,
            AdaptingBenchStop stop,
            TrackSpec linkSpec) {
        Vec3 stopPoint = stop.segment().getGeometry().sampleByDistance(stop.sensorDistance());
        RouteSegment adjacentSegment = adjacentMainSegmentFor(stop.benchId());
        float adjacentDistance = distanceAlongAdjacentSegment(stop.benchId(), stopPoint.z);
        Vec3 adjacentPoint = adjacentSegment.getGeometry().sampleByDistance(adjacentDistance);
        float gapLength = Math.abs(stopPoint.x - adjacentPoint.x);
        float benchLength = Math.max(0.50f, gapLength - (2f * BENCH_WALKWAY_CLEARANCE));
        Vec3 benchCentre = new Vec3(
                (stopPoint.x + adjacentPoint.x) * 0.5f,
                0.18f,
                stopPoint.z);
        return createBenchMarker(
                "adapting_" + stop.benchId().value() + "_marker",
                tr,
                benchCentre,
                benchLength,
                0.12f,
                linkSpec.getOverallWidth(),
                0xFF4F7F4F);
    }

    private float distanceAlongAdjacentSegment(AdaptingBenchId benchId, float z) {
        if (isUpperBench(benchId)) {
            return clamp(z - mainDestination.getGeometry().getStartPoint().z, 0f, mainDestination.length());
        }
        return clamp(
                Math.abs(z - mainApproach.getGeometry().getStartPoint().z),
                0f,
                mainApproach.length());
    }

    private void installTransferMachines(
            TriangleRenderer tr,
            SimulationWorld sim,
            List<RenderableObject> objects,
            SelectionInspectionRegistry inspectionRegistry,
            WarehouseRouteBuilder builder,
            RouteSegment mainApproach,
            RouteSegment mainTransfer,
            RouteSegment spine,
            RouteSegment inlineTransfer,
            RouteSegment mainReturn1Transfer,
            RouteSegment mainReturn2Transfer) {
        for (TransferZone zone : builder.getMetadata(mainTransfer).getTransferZones()) {
            WarehouseTrackFactory.attachSteeringMechanismForZone(
                    zone,
                    mainTransferGeometry,
                    tr,
                    objects,
                    inspectionRegistry,
                    0xFF444444,
                    0xFFB8B8B8);
            TransferZoneMachine.createTransferZoneMachine(
                    sim,
                    mainApproach,
                    Math.max(0f, mainApproach.length() - 1.5f),
                    mainTransfer,
                    zone,
                    zone.getTargetDecisionStrategy());
        }
        for (TransferZone zone : builder.getMetadata(inlineTransfer).getTransferZones()) {
            SteeringConveyorMechanism mechanism = WarehouseTrackFactory.attachSteeringMechanismForZone(
                    zone,
                    inlineTransferGeometry,
                    tr,
                    objects,
                    inspectionRegistry,
                    0xFF444444,
                    0xFFB8B8B8);
            benchSelectStrategy.bindMechanism(
                    mechanism,
                    yawForTransferTarget(bench1Target),
                    yawForTransferTarget(bench2Target));
            TransferZoneMachine.createTransferZoneMachine(
                    sim,
                    spine,
                    Math.max(0f, spine.length() - 1.5f),
                    inlineTransfer,
                    zone,
                    zone.getTargetDecisionStrategy());
        }
        for (TransferZone zone : builder.getMetadata(mainReturn1Transfer).getTransferZones()) {
            SteeringConveyorMechanism mechanism = WarehouseTrackFactory.attachSteeringMechanismForZone(
                    zone,
                    mainReturn1Geometry,
                    tr,
                    objects,
                    inspectionRegistry,
                    0xFF444444,
                    0xFFB8B8B8);
            mechanism.setBranchYawRadians(yawForTransferTarget(bench1ReturnTarget));
            mechanism.command(online.davisfamily.warehouse.sim.transfer.TransferOutcome.BRANCH);
            TransferZoneMachine.createTransferZoneMachine(
                    sim,
                    null,
                    0f,
                    mainReturn1Transfer,
                    zone,
                    zone.getTargetDecisionStrategy());
        }
        for (TransferZone zone : builder.getMetadata(mainReturn2Transfer).getTransferZones()) {
            SteeringConveyorMechanism mechanism = WarehouseTrackFactory.attachSteeringMechanismForZone(
                    zone,
                    mainReturn2Geometry,
                    tr,
                    objects,
                    inspectionRegistry,
                    0xFF444444,
                    0xFFB8B8B8);
            mechanism.setBranchYawRadians(yawForTransferTarget(bench2ReturnTarget));
            mechanism.command(online.davisfamily.warehouse.sim.transfer.TransferOutcome.BRANCH);
            TransferZoneMachine.createTransferZoneMachine(
                    sim,
                    null,
                    0f,
                    mainReturn2Transfer,
                    zone,
                    zone.getTargetDecisionStrategy());
        }
    }

    private static float localXYawFromDirection(Vec3 direction) {
        Vec3 horizontal = new Vec3(direction.x, 0f, direction.z);
        if (horizontal.lengthSquared() == 0f) {
            return 0f;
        }
        horizontal.mutableNormalize();
        return Vec3.yawFromDirection(horizontal) - (float) (Math.PI / 2.0);
    }

    private static float yawForTransferTarget(TransferTarget target) {
        Vec3 direction = target.segment().getGeometry().sampleOrientationDirectionByDistance(target.entryDistance());
        if (target.travelDirection() == RouteFollower.TravelDirection.REVERSE) {
            direction = direction.scale(-1f);
        }
        return localXYawFromDirection(direction);
    }

    private ScheduledTipperToteRelease releaseFor(
            TriangleRenderer tr,
            ToteGeometry toteGeometry,
            NotionalToteOrder order,
            List<PackPlan> packPlans) {
        ToteLoadPlan toteLoadPlan = new ToteLoadPlan(order.notionalToteId(), packPlans);
        return new ScheduledTipperToteRelease(order.orderId(), toteLoadPlan, () -> createPayload(tr, toteGeometry, toteLoadPlan));
    }

    private TipperTotePayload createPayload(TriangleRenderer tr, ToteGeometry toteGeometry, ToteLoadPlan toteLoadPlan) {
        RenderableObject toteRenderable = RenderableToteFactory.createRenderableTote(
                toteLoadPlan.getToteId(),
                tr,
                toteGeometry,
                true);
        Tote tote = new Tote(
                toteLoadPlan.getToteId(),
                new RouteFollower(toteLoadPlan.getToteId(), mainApproach, 0f, TOTE_SPEED),
                toteRenderable,
                new Vec3(),
                toteRenderable.yawOffsetRadians);
        tote.closeLids();
        return new TipperTotePayload(tote, toteRenderable, 0f, Map.of());
    }

    private void registerBenchInspection(
            SelectionInspectionRegistry inspectionRegistry,
            RenderableObject marker,
            AdaptingBenchId benchId) {
        inspectionRegistry.register(marker, () -> {
            AdaptingBenchAdmissionSnapshot admission = benchAdmission(benchId);
            var benchSnapshot = adaptingArea.bench(benchId).snapshot();
            return List.of(
                    "Type: Adapting bench",
                    "Bench id: " + benchId.value(),
                    "State: " + benchSnapshot.state(),
                    "Queue: " + admission.queueSnapshot().toteIds().size() + " / " + admission.queueSnapshot().capacity(),
                    "Staged adapted lines: " + adaptedLineStore.snapshot().stagedLineCount(),
                    "Active visit: " + (benchSnapshot.activeVisitType() == null ? "none" : benchSnapshot.activeVisitType()),
                    "Active tote: " + (benchSnapshot.activeToteId().isBlank() ? "none" : benchSnapshot.activeToteId()));
        });
    }

    private void registerTrackInspection(
            SelectionInspectionRegistry inspectionRegistry,
            RenderableObject trackRenderable,
            RouteSegment segment) {
        makeSelectableHierarchy(trackRenderable, trackRenderable);
        inspectionRegistry.register(trackRenderable, () -> List.of(
                "Type: Track",
                "Segment: " + segment.getLabel(),
                "Length: " + segment.length(),
                "Link: " + segment.getGeometry().isLinkSegment()));
    }

    private static void makeSelectableHierarchy(RenderableObject root, RenderableObject selectionTarget) {
        root.setSelectable(true);
        root.setSelectionTarget(selectionTarget);
        for (RenderableObject child : root.children) {
            makeSelectableHierarchy(child, selectionTarget);
        }
    }

    private AdaptingBenchAdmissionSnapshot benchAdmission(AdaptingBenchId benchId) {
        AdaptingAreaAdmissionSnapshot snapshot = adaptingArea.admissionSnapshotFor(dummyStoreVisit());
        return snapshot.benchAdmissions().stream()
                .filter(admission -> admission.benchId().equals(benchId))
                .findFirst()
                .orElseThrow();
    }

    private AdaptingVisit dummyStoreVisit() {
        return AdaptingVisit.store(
                "dummy",
                List.of(new DspOrderItem(
                        "dummy-line",
                        "dummy-product",
                        1,
                        "0000000",
                        DspOrderLineType.ADAPTED,
                        "dummy-order",
                        1,
                        0)));
    }

    private static RenderableObject createBenchMarker(String id, TriangleRenderer tr, Vec3 worldPosition, int colourArgb) {
        return createBenchMarker(id, tr, worldPosition, 0.30f, 0.12f, 0.30f, colourArgb);
    }

    private static RenderableObject createBenchMarker(
            String id,
            TriangleRenderer tr,
            Vec3 worldPosition,
            float length,
            float height,
            float width,
            int colourArgb) {
        RenderableObject marker = RenderableObject.create(
                id,
                tr,
                RollerMeshFactory.createBoxRollerMesh(length, height, width),
                new ObjectTransformation(0f, 0f, 0f, worldPosition.x, worldPosition.y, worldPosition.z, new Mat4()),
                new OneColourStrategyImpl(colourArgb),
                true);
        return marker;
    }

    private static List<AdaptingBench> createBenches(AdaptedLineStore store) {
        return List.of(
                new AdaptingBench("bench-1", store, 2.5d),
                new AdaptingBench("bench-2", store, 2.5d),
                new AdaptingBench("bench-3", store, 2.5d),
                new AdaptingBench("bench-4", store, 2.5d),
                new AdaptingBench("bench-5", store, 2.5d),
                new AdaptingBench("bench-6", store, 2.5d));
    }

    private static AdaptingStorageMap createStorageMap() {
        AdaptingStorageMap storageMap = new AdaptingStorageMap();
        storageMap.configureAvailableBenches(List.of(
                new AdaptingBenchId("bench-1"),
                new AdaptingBenchId("bench-2"),
                new AdaptingBenchId("bench-3"),
                new AdaptingBenchId("bench-4"),
                new AdaptingBenchId("bench-5"),
                new AdaptingBenchId("bench-6")));
        storageMap.assignPharmacyToBench("0000310", new AdaptingBenchId("bench-1"));
        storageMap.assignPharmacyToBench("0000388", new AdaptingBenchId("bench-4"));
        return storageMap;
    }

    private static boolean isUpperBench(AdaptingBenchId benchId) {
        return benchNumber(benchId) <= 3;
    }

    private static int benchNumber(AdaptingBenchId benchId) {
        return Integer.parseInt(benchId.value().substring("bench-".length()));
    }

    private static Vec3 leftEdgePoint(
            RouteSegment segment,
            TrackSpec destinationSpec,
            float distanceAlongSegment) {
        Vec3 centrePoint = segment.getGeometry().sampleByDistance(distanceAlongSegment);
        Vec3 tangent = segment.getGeometry().sampleTangentByDistance(distanceAlongSegment);
        Vec3 rightDirection = new Vec3(tangent.z, 0f, -tangent.x).normalize();
        float offset = destinationSpec.getOverallWidth() * 0.5f;
        return centrePoint.add(rightDirection.scale(-offset));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static TrackSpec rollerSpec(ToteGeometry toteGeometry, boolean includeGuides) {
        ToteEnvelope toteEnvelope = new ToteEnvelope(
                toteGeometry.getOuterBottomWidth(),
                toteGeometry.getOuterBottomDepth(),
                toteGeometry.getOuterHeight());
        return new TrackSpec(
                toteEnvelope,
                0.030f,
                0.040f,
                0.000f,
                includeGuides,
                0.050f,
                0.010f,
                0.000f,
                0.5f,
                1.0f,
                TrackDriveType.ROLLER,
                true,
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

    private static TrackSpec rollerSpecForLink(ToteGeometry toteGeometry) {
        ToteEnvelope toteEnvelope = new ToteEnvelope(
                toteGeometry.getOuterBottomWidth(),
                toteGeometry.getOuterBottomDepth(),
                toteGeometry.getOuterHeight());
        return new TrackSpec(
                toteEnvelope,
                0.030f,
                0.040f,
                0.000f,
                true,
                0.050f,
                0.010f,
                0.000f,
                0.5f,
                1.0f,
                true,
                0.080f,
                0.010f,
                0.025f,
                0.018f,
                0.080f);
    }

    private static DspSchedulerOrderState orderState(NotionalToteOrder order, RouteRequirements routeRequirements) {
        return new DspSchedulerOrderState(order, routeRequirements, DspOrderStatus.WAITING);
    }

    private static NotionalToteOrder adaptedOrder(String orderId, String toteId, DspOrderItem... items) {
        return new NotionalToteOrder(orderId, toteId, SERVICE_CENTRE_ID, 1, OrderType.ADAPTED, List.of(items), 0L);
    }

    private static NotionalToteOrder associatedCollectOrder(String orderId, String toteId, String adaptedLineId, String pharmacyId) {
        return new NotionalToteOrder(
                orderId,
                toteId,
                SERVICE_CENTRE_ID,
                1,
                OrderType.ASSOCIATED,
                List.of(new DspOrderItem(
                        adaptedLineId,
                        "product-" + adaptedLineId,
                        1,
                        pharmacyId,
                        DspOrderLineType.ADAPTED,
                        orderId,
                        1,
                        0)),
                2L);
    }

    private static DspOrderItem adaptedPreparedLine(String lineId, String targetOrderId, String pharmacyId) {
        return new DspOrderItem(
                lineId,
                "product-" + lineId,
                1,
                pharmacyId,
                DspOrderLineType.ADAPTED,
                targetOrderId,
                1,
                0);
    }

    private final class AdaptingReleaseTarget implements ScheduledToteReleaseTarget {
        private final SimulationWorld sim;
        private final List<RenderableObject> objects;

        private AdaptingReleaseTarget(SimulationWorld sim, List<RenderableObject> objects) {
            this.sim = sim;
            this.objects = objects;
        }

        @Override
        public boolean canAcceptRelease() {
            return journeysByToteId.values().stream().noneMatch(this::occupiesIngressPath);
        }

        @Override
        public online.davisfamily.warehouse.sim.dsp.runtime.SchedulerCommandApplicationResult release(
                ReleaseDecision decision,
                TipperTotePayload payload) {
            if (decision == null || payload == null) {
                throw new IllegalArgumentException("decision and payload must not be null");
            }
            String selectedBenchId = decision.selectedStationTargets()
                    .selectedTargetIdFor(StationType.ADAPTING)
                    .orElseThrow(() -> new IllegalStateException("No adapting bench selected for " + decision.orderId()));
            AdaptingVisit visit = visitsByOrderId.get(decision.orderId());
            if (visit == null) {
                return online.davisfamily.warehouse.sim.dsp.runtime.SchedulerCommandApplicationResult.rejectedResult(
                        "No adapting visit registered for order " + decision.orderId());
            }

            AdaptingBenchId benchId = new AdaptingBenchId(selectedBenchId);
            var selection = adaptingArea.submitVisit(visit);
            if (!selection.accepted() || !benchId.equals(selection.benchId())) {
                return online.davisfamily.warehouse.sim.dsp.runtime.SchedulerCommandApplicationResult.rejectedResult(
                        "Adapting area could not accept " + decision.orderId());
            }

            payload.getTote().getRouteFollower().setCurrentSegment(mainApproach);
            payload.getTote().getRouteFollower().setDistanceAlongSegment(0f);
            payload.getTote().getRouteFollower().setTravelDirection(mainDirection);
            payload.getTote().setInteractionMode(Tote.ToteMotionState.MOVING);
            payload.getTote().snapToRouteDistance(0f);

            if (!objects.contains(payload.getToteRenderable())) {
                objects.add(payload.getToteRenderable());
            }
            sim.addTrackableObject(payload.getTote());

            journeysByToteId.put(payload.getTote().getId(), new AdaptingBenchJourney(
                    decision.orderId(),
                    payload.getTote(),
                    visit,
                    benchId,
                    benchPathFor(benchId),
                    AdaptingJourneyPhase.TO_BENCH));
            return online.davisfamily.warehouse.sim.dsp.runtime.SchedulerCommandApplicationResult.appliedResult();
        }

        private boolean occupiesIngressPath(AdaptingBenchJourney journey) {
            if (journey == null || journey.tote().getLastSnapshot() == null) {
                return false;
            }
            if (journey.phase() != AdaptingJourneyPhase.TO_BENCH) {
                return false;
            }
            RouteSegment currentSegment = journey.tote().getLastSnapshot().currentSegment();
            return currentSegment == mainApproach
                    || currentSegment == mainTransfer
                    || currentSegment == spine
                    || currentSegment == inlineTransfer;
        }
    }

    private final class MainDivertStrategy implements TransferTargetDecisionStrategy {
        @Override
        public Optional<TransferRoutingDecision> decide(Tote tote, TransferZoneMachine machine) {
            AdaptingBenchJourney journey = journeysByToteId.get(tote.getId());
            if (journey == null || journey.phase() != AdaptingJourneyPhase.TO_BENCH) {
                return Optional.of(TransferRoutingDecision.continueOnCurrentRoute());
            }
            return Optional.of(TransferRoutingDecision.transferTo(mainBranchTarget));
        }
    }

    private final class BenchSelectStrategy implements TransferTargetDecisionStrategy {
        private SteeringConveyorMechanism mechanism;
        private float bench1YawRadians;
        private float bench2YawRadians;

        private void bindMechanism(
                SteeringConveyorMechanism mechanism,
                float bench1YawRadians,
                float bench2YawRadians) {
            this.mechanism = mechanism;
            this.bench1YawRadians = bench1YawRadians;
            this.bench2YawRadians = bench2YawRadians;
            this.mechanism.setBranchYawRadians(bench1YawRadians);
        }

        @Override
        public Optional<TransferRoutingDecision> decide(Tote tote, TransferZoneMachine machine) {
            AdaptingBenchJourney journey = journeysByToteId.get(tote.getId());
            if (journey == null || journey.phase() != AdaptingJourneyPhase.TO_BENCH) {
                return Optional.of(TransferRoutingDecision.continueOnCurrentRoute());
            }
            boolean bench1 = isUpperBench(journey.benchId());
            if (mechanism != null) {
                mechanism.setBranchYawRadians(bench1 ? bench1YawRadians : bench2YawRadians);
            }
            TransferTarget target = bench1 ? bench1Target : bench2Target;
            return Optional.of(TransferRoutingDecision.transferTo(target));
        }
    }

    private static final class FixedTransferTargetStrategy implements TransferTargetDecisionStrategy {
        private final TransferTarget target;

        private FixedTransferTargetStrategy(TransferTarget target) {
            this.target = target;
        }

        @Override
        public Optional<TransferRoutingDecision> decide(Tote tote, TransferZoneMachine machine) {
            return Optional.of(TransferRoutingDecision.transferTo(target));
        }
    }

    private static void hideTote(Tote tote) {
        tote.setInteractionMode(Tote.ToteMotionState.HELD);
        tote.closeLids();
        tote.getRenderable().setVisible(false);
        tote.getRenderable().transformation.xTranslation = -50f;
        tote.getRenderable().transformation.yTranslation = -50f;
        tote.getRenderable().transformation.zTranslation = -50f;
    }
}
