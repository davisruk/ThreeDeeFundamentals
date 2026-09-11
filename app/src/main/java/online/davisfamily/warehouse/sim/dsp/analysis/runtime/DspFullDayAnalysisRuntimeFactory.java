package online.davisfamily.warehouse.sim.dsp.analysis.runtime;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import online.davisfamily.threedee.behaviour.routing.RouteFollower.TravelDirection;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.path.LinearSegment3;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.sim.dsp.adapting.*;
import online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayCompletionEvaluator;
import online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayCutoffController;
import online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayRuntimeState;
import online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayLoadedInput;
import online.davisfamily.warehouse.sim.dsp.analysis.DspUncalibratedFullDayProfile;
import online.davisfamily.warehouse.sim.dsp.analysis.DspUncalibratedFullDayProfile.AdaptingBenchDefinition;
import online.davisfamily.warehouse.sim.dsp.analysis.DspUncalibratedFullDayProfile.QueueCapacities;
import online.davisfamily.warehouse.sim.dsp.analysis.DspUncalibratedFullDayProfile.P2pPlaceholderDurations;
import online.davisfamily.warehouse.sim.dsp.analysis.metrics.*;
import online.davisfamily.warehouse.sim.dsp.av02.*;
import online.davisfamily.warehouse.sim.dsp.bagging.*;
import online.davisfamily.warehouse.sim.dsp.io.LoadedDspData;
import online.davisfamily.warehouse.sim.dsp.io.LoadedDspSchedulerRuntimeFactory;
import online.davisfamily.warehouse.sim.dsp.lifecycle.*;
import online.davisfamily.warehouse.sim.dsp.model.*;
import online.davisfamily.warehouse.sim.dsp.osr.*;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.*;
import online.davisfamily.warehouse.sim.dsp.outbound.*;
import online.davisfamily.warehouse.sim.dsp.p2p.*;
import online.davisfamily.warehouse.sim.dsp.p2p.allocation.*;
import online.davisfamily.warehouse.sim.dsp.p2p.arrival.*;
import online.davisfamily.warehouse.sim.dsp.p2p.bag.*;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.*;
import online.davisfamily.warehouse.sim.dsp.routing.DspRouteDeriver;
import online.davisfamily.warehouse.sim.dsp.routing.InMemoryProductMasterRepository;
import online.davisfamily.warehouse.sim.dsp.runtime.DspSchedulerRuntimeState;
import online.davisfamily.warehouse.sim.dsp.runtime.operational.*;
import online.davisfamily.warehouse.sim.dsp.schedule.DspServiceCentreTimetable;
import online.davisfamily.warehouse.sim.dsp.scheduler.*;
import online.davisfamily.warehouse.sim.dsp.scheduler.operational.*;
import online.davisfamily.warehouse.sim.dsp.station.continuation.*;
import online.davisfamily.warehouse.sim.dsp.station.processing.*;
import online.davisfamily.warehouse.sim.dsp.supply.*;
import online.davisfamily.warehouse.sim.dsp.thirdparty.*;
import online.davisfamily.warehouse.sim.dsp.time.*;
import online.davisfamily.warehouse.sim.dsp.transport.*;
import online.davisfamily.warehouse.sim.dsp.transport.routing.*;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteToBagWorkPlanProvider;

/** Composition root for one complete, headless full-day DSP analysis runtime. */
public final class DspFullDayAnalysisRuntimeFactory {
    private static final OperationalRouteDestination THIRD_PARTY_DESTINATION =
            new OperationalRouteDestination(StationType.THIRD_PARTY, "third-party-1");

    public DspFullDayAnalysisRuntime create(
            DspFullDayLoadedInput input,
            DspUncalibratedFullDayProfile profile) {
        return create(new SimulationWorld(), input, profile);
    }

    public DspFullDayAnalysisRuntime create(
            DspUncalibratedFullDayProfile profile,
            DspFullDayLoadedInput input) {
        return create(input, profile);
    }

    public DspFullDayAnalysisRuntime create(
            SimulationWorld simulationWorld,
            DspFullDayLoadedInput input,
            DspUncalibratedFullDayProfile profile) {
        validateInputs(simulationWorld, input, profile);

        List<AutoCloseable> closeables = new ArrayList<>();
        try {
            LoadedDspData data = input.data();
            BagPlanningResult bagPlan = input.bagPlan();
            QueueCapacities queues = profile.queueCapacities();

            DspRouteDeriver routeDeriver = new DspRouteDeriver(
                    new InMemoryProductMasterRepository(data.products()));
            List<OperationalRouteDestination> destinations = destinations(profile);
            Map<StationType, StationAdmissionSnapshot> initialAdmissions =
                    initialStationAdmissions(profile, destinations);
            DspSchedulerRuntimeState schedulerState = new LoadedDspSchedulerRuntimeFactory(
                    routeDeriver).createRuntimeState(
                            data, initialAdmissions, Optional.empty());

            InboundToteManifestCatalog manifestCatalog = new InboundToteManifestCatalog(
                    data.inboundToteManifests());
            PhysicalToteLifecycleLedger lifecycleLedger = new PhysicalToteLifecycleLedger();
            InboundToteLifecycleController inboundLifecycle = new InboundToteLifecycleController(
                    lifecycleLedger, manifestCatalog);
            MutableToteLoadPlanRegistry loadPlans = new MapBackedToteLoadPlanRegistry();
            seedLoadPlans(loadPlans, data, bagPlan);

            OsrBootstrapState osrBootstrap = new OsrInventoryBootstrapFactory().create(
                    data, profile.osrInventoryConfig());
            OsrPhysicalInventory osrInventory = osrBootstrap.inventory();
            DspServiceCentreSupplyPlan supplyPlan = new DspServiceCentreSupplyPlanFactory().create(
                    data, profile.osrInventoryConfig());
            DspServiceCentreSupplyCoordinator supplyCoordinator =
                    new DspServiceCentreSupplyCoordinator(
                            supplyPlan,
                            profile.serviceCentreSupplyConfig(),
                            profile.inboundToteArrivalPolicy(),
                            osrBootstrap);

            DspOperationalClockController clockController = new DspOperationalClockController(
                    new DspOperationalClock(profile.operationalClockConfig()));
            simulationWorld.addController(clockController);
            DspServiceCentreSupplyController supplyController =
                    new DspServiceCentreSupplyController(
                            clockController::snapshot, supplyCoordinator);
            simulationWorld.addController(supplyController);

            Av02PhysicalToteInventory av02Inventory = new Av02PhysicalToteInventory(
                    profile.av02AllocationConfig());
            DeterministicAv02PhysicalToteIdAllocator av02IdAllocator =
                    new DeterministicAv02PhysicalToteIdAllocator();

            Set<OrderSheetKey> knownOrderSheetKeys = new LinkedHashSet<>();
            data.orders().forEach(order -> knownOrderSheetKeys.add(order.orderSheetKey()));
            bagPlan.plannedBags().forEach(bag -> knownOrderSheetKeys.addAll(
                    bag.owningOrderSheetKeys()));
            OutboundToteAllocator outboundAllocator = new OutboundToteAllocator(
                    lifecycleLedger,
                    new DeterministicOutboundToteIdSource(),
                    new OutputSheetAllocator(knownOrderSheetKeys),
                    profile.outboundToteConfig());

            StationProcessingCoordinator stationCoordinator = new StationProcessingCoordinator();
            StationProcessingOrderCatalog orderCatalog = new StationProcessingOrderCatalog(
                    data.orders());
            PackProvenanceRegistry provenanceRegistry = new PackProvenanceRegistry();
            for (PlannedPackTrace trace : bagPlan.packTraces()) {
                provenanceRegistry.register(trace.physicalPackId(), trace.sourceProvenance());
            }
            DspPackPlanFactory packPlanFactory = new DspPackPlanFactory(provenanceRegistry);

            ThirdPartyArea thirdPartyArea = new ThirdPartyArea(profile.thirdPartyAreaConfig());
            ThirdPartyVisitFactory thirdPartyVisitFactory = new ThirdPartyVisitFactory(
                    new InMemoryProductMasterRepository(data.products()));
            ThirdPartyAreaController thirdPartyAreaController = new ThirdPartyAreaController(
                    thirdPartyArea,
                    loadPlans,
                    new ProductMasterThirdPartyPackPlanFactory(
                            new InMemoryProductMasterRepository(data.products()),
                            (visit, lineWork) -> resolveThirdPartyCorrelation(
                                    bagPlan, visit, lineWork),
                            packPlanFactory));

            AdaptingStorageMap storageMap = new AdaptingStorageMap();
            List<AdaptingBenchId> adaptingBenchIds = profile.adaptingBenchDefinitions().stream()
                    .map(AdaptingBenchDefinition::benchId)
                    .toList();
            storageMap.configureAvailableBenches(adaptingBenchIds);
            AdaptedLineStore adaptedLineStore = new AdaptedLineStore(
                    new AdaptingStorageLayout(profile.adaptingStorageConfig(), storageMap));
            List<AdaptingBench> adaptingBenches = new ArrayList<>();
            for (AdaptingBenchDefinition definition : profile.adaptingBenchDefinitions()) {
                adaptingBenches.add(new AdaptingBench(
                        definition.id(), adaptedLineStore, definition.processingDurationSeconds()));
            }
            AdaptingArea adaptingArea = new AdaptingArea(
                    adaptingBenches,
                    queues.adaptingQueueCapacityPerBench(),
                    storageMap);
            AdaptingAreaController adaptingAreaController = new AdaptingAreaController(
                    adaptingArea,
                    schedulerState,
                    loadPlans,
                    new DefaultCollectedPackPlanFactory(packPlanFactory));

            RouteTopology topology = buildRouteTopology(destinations, profile);
            Map<OperationalRouteDestination, StationRoutedToteArrivalQueue> arrivalQueues =
                    new LinkedHashMap<>();
            for (OperationalRouteDestination destination : destinations) {
                arrivalQueues.put(destination, new StationRoutedToteArrivalQueue(
                        destination, queues.stationQueueCapacity()));
            }

            LiveWorkPlanProvider workPlanProvider = new LiveWorkPlanProvider(bagPlan);
            P2pBagCorrelationRequirementCatalog requirementCatalog =
                    P2pBagCorrelationRequirementCatalogFactory.from(bagPlan);
            P2pBagCorrelationAssignmentRegistry correlationAssignments =
                    new P2pBagCorrelationAssignmentRegistry();

            InboundLifecycleP2pToteCompletedListener inboundP2pListener =
                    new InboundLifecycleP2pToteCompletedListener(inboundLifecycle);
            OperationalLifecycleP2pToteCompletedListener operationalP2pListener =
                    new OperationalLifecycleP2pToteCompletedListener(
                            manifestCatalog,
                            inboundP2pListener,
                            av02Inventory,
                            new Av02ToteLifecycleController(lifecycleLedger, av02IdAllocator));
            StationProcessingP2pToteCompletedListener p2pCompletedListener =
                    new StationProcessingP2pToteCompletedListener(
                            operationalP2pListener, stationCoordinator);

            AtomicReference<DspP2pElasticAllocationRuntime> elasticReference =
                    new AtomicReference<>();
            List<DspHeadlessP2pLineRuntime> lineRuntimes = new ArrayList<>();
            Map<P2pLineId, P2pLineActivityProbe> activityProbes = new LinkedHashMap<>();
            P2pPlaceholderDurations durations = profile.p2pPlaceholderDurations();
            for (P2pLineDefinition definition : profile.p2pLineDefinitions()) {
                StationRoutedToteArrivalQueue queue = arrivalQueues.get(definition.destination());
                RouteSegment terminal = topology.terminalSegments().get(definition.destination());
                RouteSegment tipper = topology.tipperSegments().get(definition.destination());
                P2pArrivalRouteBinding binding = new P2pArrivalRouteBinding(terminal, tipper);
                DspHeadlessP2pLineConfig lineConfig = new DspHeadlessP2pLineConfig(
                        definition,
                        queue,
                        queues.tipperInputQueueCapacity(),
                        new StickyP2pArrivalAdmissionPolicy(
                                definition,
                                () -> requireElastic(elasticReference).leaseSnapshot()),
                        binding,
                        tipper,
                        new ContainedPackP2pTipperPayloadFactory(1f, 1f, 0f, 0f, 0f, 0f),
                        stationCoordinator,
                        new AssignedLineWorkPlanProvider(
                                definition.lineId(), workPlanProvider, correlationAssignments),
                        bagPlan,
                        outboundAllocator,
                        p2pCompletedListener,
                        durations);
                DspHeadlessP2pLineRuntime lineRuntime = new DspHeadlessP2pLineRuntimeFactory()
                        .create(simulationWorld, lineConfig);
                lineRuntimes.add(lineRuntime);
                activityProbes.put(definition.lineId(), lineRuntime.activityProbe());
                closeables.add(lineRuntime);
            }

            DspP2pElasticAllocationRuntime elasticRuntime =
                    new DspP2pElasticAllocationRuntimeFactory().createWithoutArrivalConsumers(
                            simulationWorld,
                            profile.p2pLineDefinitions(),
                            activityProbes,
                            schedulerState::snapshot,
                            manifestCatalog,
                            lifecycleLedger::snapshot,
                            av02Inventory::snapshot,
                            clockController::snapshot,
                            supplyController::snapshot,
                            profile.timetable(),
                            () -> bagPlan,
                            outboundAllocator,
                            profile.p2pElasticAllocationConfig(),
                            requirementCatalog,
                            correlationAssignments);
            elasticReference.set(elasticRuntime);
            closeables.add(elasticRuntime);

            DspAv02AllocationRuntimeController av02AllocationController =
                    new DspAv02AllocationRuntimeController(
                            schedulerState::snapshot,
                            supplyController::snapshot,
                            lifecycleLedger::snapshot,
                            av02Inventory,
                            lifecycleLedger,
                            av02IdAllocator,
                            loadPlans);
            simulationWorld.addController(av02AllocationController);

            OsrOutboundRouteLaunchQueue launchQueue = new OsrOutboundRouteLaunchQueue(
                    "dsp-osr-outbound-launch",
                    queues.transportCapacity());
            OsrOutboundRouteLaunchTargetRegistry routeTargetRegistry =
                    new OsrOutboundRouteLaunchTargetRegistry(launchQueue, destinations);

            StationAdmissionResolver stationAdmissionResolver = stationAdmissionResolver(
                    profile,
                    adaptingArea,
                    thirdPartyVisitFactory,
                    thirdPartyAreaController,
                    topology,
                    lineRuntimes,
                    requirementCatalog,
                    correlationAssignments);
            DspOperationalReleaseScheduler operationalScheduler = new DspOperationalReleaseScheduler(
                    new OperationalDependencyReadinessPolicy(),
                    new OperationalRouteEntryAdmissionPolicy(),
                    new PharmacyGroupedSourceSequenceRankingPolicy(),
                    new DeadlineAwareElasticStickyP2pLineAllocationPolicy(),
                    requirementCatalog,
                    elasticRuntime::correlationAssignmentSnapshot);
            DspOperationalReleaseRuntime operationalRuntime =
                    new DspOperationalReleaseRuntimeFactory().createElasticWithAv02(
                            new SynchronousOperationalReleaseEvaluationSource(operationalScheduler),
                            osrInventory,
                            inboundLifecycle,
                            manifestCatalog,
                            schedulerState::snapshot,
                            clockController::snapshot,
                            stationAdmissionResolver,
                            routeTargetRegistry,
                            av02Inventory,
                            lifecycleLedger,
                            loadPlans,
                            elasticRuntime);
            simulationWorld.addController(operationalRuntime.controller());
            closeables.add(operationalRuntime);

            List<RenderableObject> renderables = new ArrayList<>();
            WarehouseTransportPublisher publisher = new SimulationWorldWarehouseTransportPublisher(
                    simulationWorld, renderables);
            DetachedToteRenderableFactory detachedRenderableFactory = (request, ignoredPlan) ->
                    RenderableObject.create(
                            request.physicalToteId().value(),
                            null,
                            null,
                            new Mat4.ObjectTransformation(0f, 0f, 0f, 0f, 0f, 0f, new Mat4()),
                            null,
                            false);
            DspWarehouseTransportRuntime transportRuntime = new DspWarehouseTransportRuntimeFactory()
                    .create(
                            simulationWorld,
                            launchQueue,
                            loadPlans,
                            topology.catalog(),
                            detachedRenderableFactory,
                            profile.routeSpeed(),
                            new Vec3(),
                            0f,
                            queues.transportCapacity(),
                            queues.inFlightCapacity(),
                            publisher,
                            List.of(),
                            List.of(),
                            (machine, strategy) -> { },
                            arrivalQueues.values().stream().toList());
            closeables.add(transportRuntime);
            simulationWorld.addController(new DspHeadlessWarehouseRouteSensorController(
                    transportRuntime, topology.catalog(), profile.routeSpeed()));

            List<StationProcessingBinding> stationBindings = new ArrayList<>();
            ThirdPartyStationProcessingTarget thirdPartyTarget =
                    new ThirdPartyStationProcessingTarget(
                            THIRD_PARTY_DESTINATION,
                            orderCatalog,
                            loadPlans,
                            thirdPartyVisitFactory,
                            thirdPartyArea,
                            stationCoordinator);
            stationBindings.add(new StationProcessingBinding(
                    arrivalQueues.get(THIRD_PARTY_DESTINATION), thirdPartyTarget));

            Set<OperationalRouteDestination> adaptingDestinations = new LinkedHashSet<>();
            for (AdaptingBenchDefinition definition : profile.adaptingBenchDefinitions()) {
                OperationalRouteDestination destination = new OperationalRouteDestination(
                        StationType.ADAPTING, definition.id());
                adaptingDestinations.add(destination);
                AdaptingStationProcessingTarget target = new AdaptingStationProcessingTarget(
                        destination,
                        orderCatalog,
                        loadPlans,
                        new AdaptingVisitFactory(),
                        adaptingArea,
                        stationCoordinator);
                stationBindings.add(new StationProcessingBinding(
                        arrivalQueues.get(destination), target));
            }

            for (int index = 0; index < lineRuntimes.size(); index++) {
                stationBindings.add(lineRuntimes.get(index).stationProcessingBinding());
            }
            List<StationProcessingCompletionController> completionControllers = List.of(
                    new AdaptingStationProcessingController(
                            "dsp-adapting",
                            adaptingDestinations,
                            loadPlans,
                            adaptingArea,
                            adaptingAreaController,
                            inboundLifecycle,
                            stationCoordinator),
                    new ThirdPartyStationProcessingController(
                            "dsp-third-party",
                            Set.of(THIRD_PARTY_DESTINATION),
                            loadPlans,
                            thirdPartyAreaController,
                            stationCoordinator));
            DspStationProcessingRuntime stationRuntime = new DspStationProcessingRuntimeFactory().create(
                    simulationWorld,
                    stationCoordinator,
                    stationBindings,
                    completionControllers);
            closeables.add(stationRuntime);

            DspStationRouteContinuationRuntime continuationRuntime =
                    new DspStationRouteContinuationRuntimeFactory().create(
                            simulationWorld,
                            stationCoordinator,
                            orderCatalog,
                            loadPlans,
                            routeDeriver,
                            new StationRouteContinuationSelector(),
                            new OperationalStationRouteContinuationTargetResolver(
                                    adaptingArea, new AdaptingVisitFactory()),
                            topology.catalog(),
                            transportRuntime.outboundTransportQueue(),
                            publisher);
            closeables.add(continuationRuntime);

            DspFullDayCompletionEvaluator completionEvaluator = new DspFullDayCompletionEvaluator(
                    profile.timetable(),
                    profile.p2pElasticAllocationConfig().downstreamHandlingDuration());
            AtomicReference<DspFullDayRuntimeState> runtimeState = new AtomicReference<>(
                    DspFullDayRuntimeState.RUNNING);
            CompletionSnapshotSource completionSource = new CompletionSnapshotSource(
                    input,
                    manifestCatalog,
                    av02Inventory,
                    osrInventory,
                    lifecycleLedger,
                    supplyController,
                    elasticRuntime,
                    lineRuntimes,
                    stationRuntime,
                    transportRuntime,
                    launchQueue,
                    outboundAllocator,
                    completionEvaluator,
                    clockController::snapshot);
            Runnable closeApplicableOutputs = () -> closeApplicableOutputs(
                    lineRuntimes, bagPlan, outboundAllocator, clockController.snapshot());
            DspFullDayCutoffController cutoffController = new DspFullDayCutoffController(
                    clockController::snapshot,
                    completionSource,
                    closeApplicableOutputs,
                    lineRuntimes,
                    runtimeState::set);
            simulationWorld.addController(cutoffController);

            DspFullDayMetricsCollector metricsCollector = new DspFullDayMetricsCollector(
                    profile.profileId(),
                    profile.serviceCentreSupplyPolicyId(),
                    profile.orderEligibilityPolicyId(),
                    profile.candidateRankingPolicyId(),
                    profile.p2pLineAllocationPolicyId(),
                    profile.outboundAllocationPolicyId(),
                    profile.calibrationStatus(),
                    profile.completionMilestone(),
                    profile.inboundToteArrivalPolicy().interval(),
                    profile.metricSampleInterval(),
                    profile.serviceCentreSupplyConfig().lowWaterMark(),
                    input.report(),
                    new DspFullDayMetricsCollector.SnapshotSuppliers(
                            clockController::snapshot,
                            supplyController::snapshot,
                            osrInventory::snapshot,
                            av02Inventory::snapshot,
                            lifecycleLedger::snapshot,
                            elasticRuntime::operationalSnapshot,
                            () -> lineRuntimes.stream()
                                    .map(DspHeadlessP2pLineRuntime::snapshot)
                                    .toList(),
                            operationalRuntime.controller()::snapshot,
                            transportRuntime::inFlightSnapshot,
                            () -> transportRuntime.ingressController().snapshot(),
                            () -> transportRuntime.arrivalController().snapshot(),
                            transportRuntime::outboundTransportSnapshot,
                            transportRuntime::stationArrivalSnapshots,
                            stationRuntime::coordinatorSnapshot,
                            stationRuntime::claimantSnapshots,
                            outboundAllocator::snapshot,
                            schedulerState::snapshot,
                            completionSource::get,
                            runtimeState::get));
            simulationWorld.addController(metricsCollector);

            return new DspFullDayAnalysisRuntime(
                    simulationWorld,
                    clockController,
                    schedulerState,
                    supplyController,
                    manifestCatalog,
                    loadPlans,
                    lifecycleLedger,
                    osrInventory,
                    av02Inventory,
                    outboundAllocator,
                    osrInventory::snapshot,
                    av02Inventory::snapshot,
                    lifecycleLedger::snapshot,
                    av02AllocationController,
                    elasticRuntime,
                    operationalRuntime,
                    transportRuntime,
                    stationRuntime,
                    continuationRuntime,
                    lineRuntimes,
                    cutoffController,
                    completionEvaluator,
                    completionSource,
                    metricsCollector,
                    runtimeState);
        } catch (RuntimeException exception) {
            closeReverse(closeables, exception);
            throw exception;
        }
    }

    private static void validateInputs(
            SimulationWorld simulationWorld,
            DspFullDayLoadedInput input,
            DspUncalibratedFullDayProfile profile) {
        if (simulationWorld == null) {
            throw new IllegalArgumentException("simulationWorld must not be null");
        }
        if (input == null) {
            throw new IllegalArgumentException("input must not be null");
        }
        if (profile == null) {
            throw new IllegalArgumentException("profile must not be null");
        }
        if (!profile.timetable().equals(input.timetable())) {
            throw new IllegalArgumentException("profile timetable must match loaded input timetable");
        }
        if (input.data().orders().isEmpty()) {
            throw new IllegalArgumentException("input contains no retained simulated work");
        }
        for (NotionalToteOrder order : input.data().orders()) {
            if (profile.timetable().find(order.serviceCentreId()).isEmpty()) {
                throw new IllegalArgumentException(
                        "No timetable entry for loaded service centre " + order.serviceCentreId());
            }
            if (profile.timetable().require(order.serviceCentreId()).priority()
                    != order.orderPriority()) {
                throw new IllegalArgumentException(
                        "Timetable priority does not match loaded service centre "
                                + order.serviceCentreId());
            }
        }
        if (profile.p2pLineDefinitions().size() != DspUncalibratedFullDayProfile.P2P_LINE_COUNT
                || profile.prlCountPerLine() != DspUncalibratedFullDayProfile.PRL_COUNT_PER_LINE) {
            throw new IllegalArgumentException("Full-day runtime requires five lines with 31 PRLs each");
        }
        if (!profile.operationalClockConfig().hardCutoffDateTime()
                .isAfter(profile.operationalClockConfig().normalEndDateTime())) {
            throw new IllegalArgumentException("hard cutoff must be after normal operating end");
        }
        List<OperationalRouteDestination> destinations = destinations(profile);
        Set<String> targetIds = new LinkedHashSet<>();
        for (OperationalRouteDestination destination : destinations) {
            if (!targetIds.add(destination.targetId())) {
                throw new IllegalArgumentException(
                        "Operational route target IDs must be unique: " + destination.targetId());
            }
        }
    }

    private static List<OperationalRouteDestination> destinations(
            DspUncalibratedFullDayProfile profile) {
        List<OperationalRouteDestination> destinations = new ArrayList<>();
        destinations.add(THIRD_PARTY_DESTINATION);
        for (AdaptingBenchDefinition definition : profile.adaptingBenchDefinitions()) {
            destinations.add(new OperationalRouteDestination(StationType.ADAPTING, definition.id()));
        }
        profile.p2pLineDefinitions().forEach(definition -> destinations.add(definition.destination()));
        return List.copyOf(destinations);
    }

    private static Map<StationType, StationAdmissionSnapshot> initialStationAdmissions(
            DspUncalibratedFullDayProfile profile,
            List<OperationalRouteDestination> destinations) {
        QueueCapacities queues = profile.queueCapacities();
        int adaptingQueueLimit = Math.multiplyExact(
                queues.adaptingQueueCapacityPerBench(), profile.adaptingBenchDefinitions().size());
        int p2pQueueLimit = Math.multiplyExact(
                queues.stationQueueCapacity(), profile.p2pLineDefinitions().size());
        Map<StationType, StationAdmissionSnapshot> admissions = new EnumMap<>(StationType.class);
        admissions.put(
                StationType.THIRD_PARTY,
                new StationAdmissionSnapshot(
                        StationType.THIRD_PARTY,
                        new StationCapacity(
                                profile.thirdPartyAreaConfig().maxConcurrentVisits(),
                                profile.thirdPartyAreaConfig().waitingCapacity()),
                        new StationSnapshot(StationType.THIRD_PARTY, 0, 0),
                        true,
                        "",
                        Optional.of(THIRD_PARTY_DESTINATION.targetId())));
        OperationalRouteDestination adaptingTarget = destinations.stream()
                .filter(destination -> destination.stationType() == StationType.ADAPTING)
                .findFirst()
                .orElseThrow();
        admissions.put(
                StationType.ADAPTING,
                new StationAdmissionSnapshot(
                        StationType.ADAPTING,
                        new StationCapacity(
                                profile.adaptingBenchDefinitions().size(), adaptingQueueLimit),
                        new StationSnapshot(StationType.ADAPTING, 0, 0),
                        true,
                        "",
                        Optional.of(adaptingTarget.targetId())));
        OperationalRouteDestination p2pTarget = destinations.stream()
                .filter(destination -> destination.stationType() == StationType.P2P)
                .findFirst()
                .orElseThrow();
        admissions.put(
                StationType.P2P,
                new StationAdmissionSnapshot(
                        StationType.P2P,
                        new StationCapacity(
                                profile.p2pLineDefinitions().size(), p2pQueueLimit),
                        new StationSnapshot(StationType.P2P, 0, 0),
                        true,
                        "",
                        Optional.of(p2pTarget.targetId())));
        return Map.copyOf(admissions);
    }

    private static void seedLoadPlans(
            MutableToteLoadPlanRegistry loadPlans,
            LoadedDspData data,
            BagPlanningResult bagPlan) {
        Set<PhysicalToteId> seeded = new LinkedHashSet<>();
        for (ToteLoadPlan plan : bagPlan.p2pToteLoadPlans()) {
            loadPlans.putLoadPlan(plan);
            seeded.add(plan.physicalToteId());
        }
        for (InboundToteManifest manifest : data.inboundToteManifests()) {
            if (seeded.add(manifest.physicalToteId())) {
                loadPlans.putLoadPlan(new ToteLoadPlan(manifest.physicalToteId(), List.of()));
            }
        }
    }

    private static RouteTopology buildRouteTopology(
            List<OperationalRouteDestination> destinations,
            DspUncalibratedFullDayProfile profile) {
        RouteSegment commonEntry = new RouteSegment(
                "dsp-common-entry",
                new LinearSegment3(new Vec3(0f, 0f, 0f), new Vec3(1f, 0f, 0f), false));
        Map<OperationalRouteDestination, RouteSegment> terminals = new LinkedHashMap<>();
        Map<OperationalRouteDestination, RouteSegment> tippers = new LinkedHashMap<>();
        List<WarehouseRouteDefinition> definitions = new ArrayList<>();
        int index = 1;
        for (OperationalRouteDestination destination : destinations) {
            RouteSegment terminal = new RouteSegment(
                    "dsp-terminal-" + destination.targetId(),
                    new LinearSegment3(
                            new Vec3(1f, index, 0f),
                            new Vec3(2f, index, 0f),
                            false));
            commonEntry.connectTo(terminal);
            terminals.put(destination, terminal);
            RouteSegment tipper = null;
            if (destination.stationType() == StationType.P2P) {
                tipper = new RouteSegment(
                        "dsp-tipper-entry-" + destination.targetId(),
                        new LinearSegment3(
                                new Vec3(2f, index, 0f),
                                new Vec3(3f, index, 0f),
                                false));
                terminal.connectTo(tipper);
                tippers.put(destination, tipper);
            }
            definitions.add(new WarehouseRouteDefinition(
                    destination,
                    commonEntry,
                    0f,
                    TravelDirection.FORWARD,
                    "dsp-terminal-sensor-" + destination.targetId(),
                    terminal));
            index++;
        }
        return new RouteTopology(
                new WarehouseRouteCatalog(definitions), terminals, tippers);
    }

    private static StationAdmissionResolver stationAdmissionResolver(
            DspUncalibratedFullDayProfile profile,
            AdaptingArea adaptingArea,
            ThirdPartyVisitFactory thirdPartyVisitFactory,
            ThirdPartyAreaController thirdPartyAreaController,
            RouteTopology topology,
            List<DspHeadlessP2pLineRuntime> lineRuntimes,
            P2pBagCorrelationRequirementCatalog requirementCatalog,
            P2pBagCorrelationAssignmentRegistry correlationAssignments) {
        StationAdmissionResolver base = new SnapshotStationAdmissionResolver();
        StationAdmissionResolver adapting = new AdaptingStationAdmissionResolver(
                base,
                adaptingArea,
                new StationCapacity(
                        profile.adaptingBenchDefinitions().size(),
                        Math.multiplyExact(
                                profile.queueCapacities().adaptingQueueCapacityPerBench(),
                                profile.adaptingBenchDefinitions().size())));
        StationAdmissionResolver thirdParty = new ThirdPartyStationAdmissionResolver(
                adapting,
                thirdPartyVisitFactory,
                thirdPartyAreaController::areaSnapshot,
                THIRD_PARTY_DESTINATION.targetId());
        DspFullDayP2pAdmissionSnapshotSource p2pAdmissionSnapshotSource =
                new DspFullDayP2pAdmissionSnapshotSource(
                        lineRuntimes.size() * DspHeadlessP2pLineRuntimeFactory.PRL_COUNT_PER_LINE,
                        requirementCatalog,
                        correlationAssignments);
        return new P2pStationAdmissionResolver(
                thirdParty,
                new StaticP2pAdmission(P2pAdmissionResult.acceptedResult()),
                p2pAdmissionSnapshotSource::snapshot,
                new StationCapacity(
                        profile.p2pLineDefinitions().size(),
                        Math.multiplyExact(
                                profile.queueCapacities().stationQueueCapacity(),
                                profile.p2pLineDefinitions().size())),
                () -> new StationSnapshot(
                        StationType.P2P,
                        lineRuntimes.stream()
                                .map(DspHeadlessP2pLineRuntime::snapshot)
                                .map(DspHeadlessP2pLineRuntimeSnapshot::activity)
                                .mapToInt(activity -> activity.packPath().nonIdlePrlCount())
                                .sum(),
                        lineRuntimes.stream()
                                .map(DspHeadlessP2pLineRuntime::snapshot)
                                .mapToInt(snapshot -> snapshot.activity().input().stationArrivalCount())
                                .sum()),
                profile.p2pLineDefinitions().getFirst().destination().targetId());
    }

    private static String resolveThirdPartyCorrelation(
            BagPlanningResult bagPlan,
            ThirdPartyVisit visit,
            ThirdPartyLineWork lineWork) {
        Set<String> correlations = new LinkedHashSet<>();
        for (PlannedPackTrace trace : bagPlan.packTraces()) {
            PackSourceProvenance source = trace.sourceProvenance();
            if (source.sourceOrderSheetKey().equals(visit.orderSheetKey())
                    && source.lineReference().equals(lineWork.lineReference())) {
                correlations.add(trace.bagKey().correlationId());
            }
        }
        if (correlations.size() != 1) {
            throw new IllegalStateException(
                    "Third Party line has no unique planned bag correlation: "
                            + lineWork.lineReference());
        }
        return correlations.iterator().next();
    }

    private static DspP2pElasticAllocationRuntime requireElastic(
            AtomicReference<DspP2pElasticAllocationRuntime> reference) {
        DspP2pElasticAllocationRuntime runtime = reference.get();
        if (runtime == null) {
            throw new IllegalStateException("Elastic P2P runtime has not been composed");
        }
        return runtime;
    }

    private static void closeApplicableOutputs(
            List<DspHeadlessP2pLineRuntime> lineRuntimes,
            BagPlanningResult bagPlan,
            OutboundToteAllocator outboundAllocator,
            DspOperationalClockSnapshot clockSnapshot) {
        Set<BagKey> allocatedBagKeys = outboundAllocator.snapshot().allocatedBags().stream()
                .map(AllocatedOutboundBag::bagKey)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        for (DspHeadlessP2pLineRuntime lineRuntime : lineRuntimes) {
            DspHeadlessP2pLineRuntimeSnapshot snapshot = lineRuntime.snapshot();
            Optional<OutboundToteSnapshot> open = snapshot.activity().openOutboundTote();
            if (!snapshot.processingDrained() || open.isEmpty() || !open.orElseThrow().assigned()) {
                continue;
            }
            String serviceCentreId = open.orElseThrow().serviceCentreId().orElseThrow();
            boolean allBagsAllocated = bagPlan.plannedBags().stream()
                    .filter(bag -> bag.serviceCentreId().equals(serviceCentreId))
                    .allMatch(bag -> allocatedBagKeys.contains(bag.bagKey()));
            if (allBagsAllocated) {
                lineRuntime.closeOutboundToteForApplicableWorkCompletion(
                        clockSnapshot.elapsedSimulationTime());
            }
        }
    }

    private static void closeReverse(
            List<AutoCloseable> closeables,
            RuntimeException original) {
        for (int index = closeables.size() - 1; index >= 0; index--) {
            try {
                closeables.get(index).close();
            } catch (Exception cleanupFailure) {
                original.addSuppressed(cleanupFailure);
            }
        }
    }

    private record RouteTopology(
            WarehouseRouteCatalog catalog,
            Map<OperationalRouteDestination, RouteSegment> terminalSegments,
            Map<OperationalRouteDestination, RouteSegment> tipperSegments) {
        private RouteTopology {
            terminalSegments = Map.copyOf(terminalSegments);
            tipperSegments = Map.copyOf(tipperSegments);
        }
    }

    private static final class LiveWorkPlanProvider implements ToteToBagWorkPlanProvider {
        private final Map<String, Integer> expectedPackCounts;

        private LiveWorkPlanProvider(BagPlanningResult bagPlan) {
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (PlannedBag bag : bagPlan.plannedBags()) {
                counts.merge(bag.bagKey().correlationId(), bag.physicalPackIds().size(), Integer::sum);
            }
            Map<String, Integer> loadPlanCounts = new LinkedHashMap<>();
            for (ToteLoadPlan plan : bagPlan.p2pToteLoadPlans()) {
                plan.packPlansByCorrelationId().forEach((correlation, packs) -> {
                    loadPlanCounts.merge(correlation, packs.size(), Integer::sum);
                });
            }
            loadPlanCounts.forEach((correlation, count) -> {
                Integer expected = counts.get(correlation);
                if (expected == null) {
                    throw new IllegalArgumentException(
                            "P2P load plan contains an unknown bag correlation " + correlation);
                }
                if (expected.intValue() != count) {
                    throw new IllegalArgumentException(
                            "P2P load-plan pack count does not match planned bag for correlation "
                                    + correlation);
                }
            });
            expectedPackCounts = Map.copyOf(counts);
        }

        @Override
        public java.util.OptionalInt expectedPackCount(String correlationId) {
            Integer count = expectedPackCounts.get(correlationId);
            return count == null ? java.util.OptionalInt.empty() : java.util.OptionalInt.of(count);
        }

        @Override
        public Set<String> expectedCorrelationIds() {
            return expectedPackCounts.keySet();
        }
    }

    private static final class AssignedLineWorkPlanProvider
            implements ToteToBagWorkPlanProvider {
        private final P2pLineId lineId;
        private final LiveWorkPlanProvider allWork;
        private final P2pBagCorrelationAssignmentRegistry assignments;

        private AssignedLineWorkPlanProvider(
                P2pLineId lineId,
                LiveWorkPlanProvider allWork,
                P2pBagCorrelationAssignmentRegistry assignments) {
            this.lineId = lineId;
            this.allWork = allWork;
            this.assignments = assignments;
        }

        @Override
        public java.util.OptionalInt expectedPackCount(String correlationId) {
            if (assignments.lineFor(correlationId).filter(lineId::equals).isEmpty()) {
                return java.util.OptionalInt.empty();
            }
            return allWork.expectedPackCount(correlationId);
        }

        @Override
        public Set<String> expectedCorrelationIds() {
            return allWork.expectedCorrelationIds().stream()
                    .filter(correlation -> assignments.lineFor(correlation)
                            .filter(lineId::equals)
                            .isPresent())
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        }
    }

    private static final class CompletionSnapshotSource
            implements Supplier<List<online.davisfamily.warehouse.sim.dsp.analysis.DspServiceCentreCompletionSnapshot>> {
        private final DspFullDayLoadedInput input;
        private final InboundToteManifestCatalog manifestCatalog;
        private final Av02PhysicalToteInventory av02Inventory;
        private final OsrPhysicalInventory osrInventory;
        private final PhysicalToteLifecycleLedger lifecycleLedger;
        private final DspServiceCentreSupplyController supplyController;
        private final DspP2pElasticAllocationRuntime elasticRuntime;
        private final List<DspHeadlessP2pLineRuntime> lineRuntimes;
        private final DspStationProcessingRuntime stationRuntime;
        private final DspWarehouseTransportRuntime transportRuntime;
        private final OsrOutboundRouteLaunchQueue launchQueue;
        private final OutboundToteAllocator outboundAllocator;
        private final DspFullDayCompletionEvaluator evaluator;
        private final Supplier<DspOperationalClockSnapshot> clockSnapshotSupplier;
        private final Map<String, Duration> firstCompletionTimes = new LinkedHashMap<>();

        private CompletionSnapshotSource(
                DspFullDayLoadedInput input,
                InboundToteManifestCatalog manifestCatalog,
                Av02PhysicalToteInventory av02Inventory,
                OsrPhysicalInventory osrInventory,
                PhysicalToteLifecycleLedger lifecycleLedger,
                DspServiceCentreSupplyController supplyController,
                DspP2pElasticAllocationRuntime elasticRuntime,
                List<DspHeadlessP2pLineRuntime> lineRuntimes,
                DspStationProcessingRuntime stationRuntime,
                DspWarehouseTransportRuntime transportRuntime,
                OsrOutboundRouteLaunchQueue launchQueue,
                OutboundToteAllocator outboundAllocator,
                DspFullDayCompletionEvaluator evaluator,
                Supplier<DspOperationalClockSnapshot> clockSnapshotSupplier) {
            this.input = input;
            this.manifestCatalog = manifestCatalog;
            this.av02Inventory = av02Inventory;
            this.osrInventory = osrInventory;
            this.lifecycleLedger = lifecycleLedger;
            this.supplyController = supplyController;
            this.elasticRuntime = elasticRuntime;
            this.lineRuntimes = List.copyOf(lineRuntimes);
            this.stationRuntime = stationRuntime;
            this.transportRuntime = transportRuntime;
            this.launchQueue = launchQueue;
            this.outboundAllocator = outboundAllocator;
            this.evaluator = evaluator;
            this.clockSnapshotSupplier = clockSnapshotSupplier;
        }

        @Override
        public List<online.davisfamily.warehouse.sim.dsp.analysis.DspServiceCentreCompletionSnapshot> get() {
            DspOperationalClockSnapshot clock = evaluatorClock();
            DspSupplySnapshot supply = supplyController.snapshot();
            Map<PhysicalToteId, String> serviceCentreByPhysicalTote = serviceCentreByPhysicalTote(
                    supply);
            OutboundAllocationSnapshot outbound = outboundAllocator.snapshot();
            Set<BagKey> allocatedBags = outbound.allocatedBags().stream()
                    .map(AllocatedOutboundBag::bagKey)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            List<online.davisfamily.warehouse.sim.dsp.analysis.DspServiceCentreCompletionSnapshot> result = new ArrayList<>();
            for (ServiceCentreSupplySnapshot centre : supply.serviceCentres()) {
                String id = centre.serviceCentreId();
                List<String> unsupported = unsupportedFor(id);
                int blocked = (int) centre.physicalTotes().stream()
                        .filter(tote -> tote.state() == PhysicalToteSupplyState.BLOCKED_BY_OSR_CAPACITY)
                        .count();
                int nonTerminalInbound = (int) manifestCatalog.manifests().stream()
                        .filter(manifest -> manifest.serviceCentreId().equals(id))
                        .filter(manifest -> lifecycleLedger.snapshot().totes()
                                .get(manifest.physicalToteId()) != null)
                        .filter(manifest -> !lifecycleLedger.snapshot().totes()
                                .get(manifest.physicalToteId()).terminal())
                        .count();
                int remainingPhysicalTotes = remainingPhysicalTotes(
                        id, serviceCentreByPhysicalTote);
                List<PlannedBag> plannedForCentre = input.bagPlan().plannedBags().stream()
                        .filter(bag -> bag.serviceCentreId().equals(id))
                        .toList();
                int remainingBags = (int) plannedForCentre.stream()
                        .filter(bag -> !allocatedBags.contains(bag.bagKey()))
                        .count();
                int remainingPacks = plannedForCentre.stream()
                        .filter(bag -> !allocatedBags.contains(bag.bagKey()))
                        .mapToInt(bag -> bag.physicalPackIds().size())
                        .sum();
                StationProcessingSnapshot station = stationRuntime.coordinatorSnapshot();
                int activeClaims = station.activeClaims().stream()
                        .filter(claim -> belongsToCentre(
                                claim.physicalToteId(), id, serviceCentreByPhysicalTote))
                        .mapToInt(ignored -> 1)
                        .sum();
                int pendingDispositions = station.pendingDispositions().stream()
                        .filter(disposition -> belongsToCentre(
                                disposition.physicalToteId(), id, serviceCentreByPhysicalTote))
                        .mapToInt(ignored -> 1)
                        .sum();
                int transportEnvelopes = transportEnvelopeCount(
                        id, serviceCentreByPhysicalTote);
                int tipperInput = tipperInputCount(id);
                int p2pAssignments = p2pAssignmentCount(id);
                int openOutbound = (int) outbound.openTotesByLine().values().stream()
                        .filter(tote -> tote.serviceCentreId().filter(id::equals).isPresent())
                        .count();
                int unallocatedCompleted = lineRuntimes.stream()
                        .map(DspHeadlessP2pLineRuntime::snapshot)
                        .flatMap(snapshot -> snapshot.completedBagCorrelationIds().stream())
                        .map(input.bagPlan()::findBagByCorrelationId)
                        .flatMap(Optional::stream)
                        .filter(bag -> bag.serviceCentreId().equals(id))
                        .filter(bag -> !allocatedBags.contains(bag.bagKey()))
                        .mapToInt(ignored -> 1)
                        .sum();
                DspFullDayCompletionEvaluator.Observation observation =
                        new DspFullDayCompletionEvaluator.Observation(
                                id,
                                clock,
                                centre.authorizationState() == ServiceCentreAuthorizationState.SUPPLY_COMPLETE,
                                centre.upstreamWaitingCount(),
                                blocked,
                                osrInventory.snapshot().storedTotesForServiceCentre(id).size(),
                                (int) av02Inventory.snapshot().waitingTotes().stream()
                                        .filter(tote -> tote.serviceCentreId().equals(id))
                                        .count(),
                                nonTerminalInbound,
                                remainingPhysicalTotes,
                                remainingPacks,
                                remainingBags,
                                activeClaims,
                                pendingDispositions,
                                transportEnvelopes,
                                tipperInput,
                                p2pAssignments,
                                openOutbound,
                                unallocatedCompleted,
                                unsupported,
                                Optional.ofNullable(firstCompletionTimes.get(id)));
                var snapshot = evaluator.evaluate(observation);
                if (snapshot.complete()) {
                    firstCompletionTimes.putIfAbsent(id, snapshot.completionElapsedTime().orElseThrow());
                }
                result.add(snapshot);
            }
            return List.copyOf(result);
        }

        private DspOperationalClockSnapshot evaluatorClock() {
            DspOperationalClockSnapshot snapshot = clockSnapshotSupplier.get();
            if (snapshot == null) {
                throw new IllegalStateException("clockSnapshotSupplier returned null");
            }
            return snapshot;
        }

        private Map<PhysicalToteId, String> serviceCentreByPhysicalTote(
                DspSupplySnapshot supply) {
            Map<PhysicalToteId, String> result = new HashMap<>();
            for (InboundToteManifest manifest : manifestCatalog.manifests()) {
                result.put(manifest.physicalToteId(), manifest.serviceCentreId());
            }
            Av02InventorySnapshot av02 = av02Inventory.snapshot();
            av02.waitingTotes().forEach(tote -> result.put(tote.physicalToteId(), tote.serviceCentreId()));
            av02.departedTotes().forEach(tote -> result.put(tote.physicalToteId(), tote.serviceCentreId()));
            OutboundAllocationSnapshot outbound = outboundAllocator.snapshot();
            outbound.openTotesByLine().values().forEach(tote -> tote.serviceCentreId()
                    .ifPresent(id -> result.put(tote.physicalToteId(), id)));
            outbound.closedTotes().forEach(tote -> tote.serviceCentreId()
                    .ifPresent(id -> result.put(tote.physicalToteId(), id)));
            for (RoutedPhysicalTote tote : transportRuntime.activeRoutedTotes()) {
                result.put(tote.physicalToteId(), tote.launchRequest().serviceCentreId());
            }
            return result;
        }

        private int remainingPhysicalTotes(String serviceCentreId, Map<PhysicalToteId, String> serviceCentreByPhysicalTote) {
            int count = 0;
            PhysicalToteLifecycleSnapshot snapshot = lifecycleLedger.snapshot();
            for (PhysicalToteRecord tote : snapshot.totes().values()) {
                if (!tote.terminal()
                        && serviceCentreId.equals(serviceCentreByPhysicalTote.get(tote.id()))) {
                    count++;
                }
            }
            return count;
        }

        private int transportEnvelopeCount(
                String serviceCentreId,
                Map<PhysicalToteId, String> serviceCentreByPhysicalTote) {
            Set<PhysicalToteId> ids = new LinkedHashSet<>();
            transportRuntime.activeRoutedTotes().forEach(tote -> {
                if (belongsToCentre(tote.physicalToteId(), serviceCentreId, serviceCentreByPhysicalTote)) {
                    ids.add(tote.physicalToteId());
                }
            });
            launchQueue.snapshot().entries().forEach(entry -> {
                if (belongsToCentre(entry.physicalToteId(), serviceCentreId, serviceCentreByPhysicalTote)) {
                    ids.add(entry.physicalToteId());
                }
            });
            transportRuntime.outboundTransportSnapshot().entries().forEach(entry -> {
                if (belongsToCentre(entry.physicalToteId(), serviceCentreId, serviceCentreByPhysicalTote)) {
                    ids.add(entry.physicalToteId());
                }
            });
            for (StationRoutedToteArrivalQueueSnapshot queue : transportRuntime.stationArrivalSnapshots()) {
                queue.entries().forEach(entry -> {
                    if (belongsToCentre(entry.physicalToteId(), serviceCentreId, serviceCentreByPhysicalTote)) {
                        ids.add(entry.physicalToteId());
                    }
                });
            }
            return ids.size();
        }

        private int tipperInputCount(String serviceCentreId) {
            int count = 0;
            for (DspHeadlessP2pLineRuntime line : lineRuntimes) {
                DspHeadlessP2pLineRuntimeSnapshot snapshot = line.snapshot();
                Optional<String> owner = elasticRuntime.leaseSnapshot().findLine(
                        line.lineDefinition().lineId()).flatMap(P2pLineLeaseSnapshot::serviceCentreId);
                int lineInput = snapshot.activity().input().stationArrivalCount()
                        + snapshot.activity().input().tipperInputCount()
                        + snapshot.activity().input().activeTipperDischargeCount()
                        + (snapshot.activity().input().activeTipperTote() ? 1 : 0);
                if (owner.isEmpty() || owner.orElseThrow().equals(serviceCentreId)) {
                    count += lineInput;
                }
            }
            return count;
        }

        private int p2pAssignmentCount(String serviceCentreId) {
            PhysicalToteLifecycleSnapshot lifecycle = lifecycleLedger.snapshot();
            return elasticRuntime.leaseSnapshot().lines().stream()
                    .flatMap(line -> line.physicalAssignments().stream())
                    .filter(assignment -> assignment.serviceCentreId().equals(serviceCentreId))
                    .filter(assignment -> {
                        PhysicalToteRecord tote = lifecycle.totes().get(
                                assignment.physicalToteId());
                        // The lease registry deliberately retains historical assignments for
                        // auditability. Only an assignment whose physical tote is still live
                        // blocks full-day completion; a missing lifecycle record is treated
                        // conservatively as still active.
                        return tote == null || !tote.terminal();
                    })
                    .mapToInt(ignored -> 1)
                    .sum();
        }

        private List<String> unsupportedFor(String serviceCentreId) {
            List<String> values = new ArrayList<>();
            input.report().unresolvedProductLines().stream()
                    .filter(issue -> issue.serviceCentreId().equals(serviceCentreId))
                    .forEach(issue -> values.add(
                            "Unresolved product " + issue.productId() + " for " + issue.orderId()
                                    + "/" + issue.lineReference()));
            if (input.report().ignoredManualMessageCount() > 0
                    || input.report().ignoredManualLineCount() > 0) {
                values.add("MANUAL work is outside the supported full-day runtime");
            }
            if (input.report().omittedOrderCount() > 0) {
                values.add("One or more orders were omitted from the loaded runtime");
            }
            return List.copyOf(values);
        }

        private static boolean belongsToCentre(
                PhysicalToteId physicalToteId,
                String serviceCentreId,
                Map<PhysicalToteId, String> serviceCentreByPhysicalTote) {
            String owner = serviceCentreByPhysicalTote.get(physicalToteId);
            return owner == null || owner.equals(serviceCentreId);
        }
    }
}
