package online.davisfamily.warehouse.testing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.SimulationController;
import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.rendering.model.tote.RenderableToteFactory;
import online.davisfamily.warehouse.rendering.model.tote.ToteEnvelope;
import online.davisfamily.warehouse.rendering.model.tote.ToteGeometry;
import online.davisfamily.warehouse.rendering.model.tracks.RollerMeshFactory;
import online.davisfamily.warehouse.rendering.model.tracks.RouteTrackFactory;
import online.davisfamily.warehouse.rendering.model.tracks.TrackAppearance;
import online.davisfamily.warehouse.rendering.model.tracks.TrackDriveType;
import online.davisfamily.warehouse.rendering.model.tracks.TrackSpec;
import online.davisfamily.warehouse.rendering.model.tracks.WarehouseRouteBuilder;
import online.davisfamily.warehouse.sim.dsp.adapting.MapBackedToteLoadPlanRegistry;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.ProductMasterRecord;
import online.davisfamily.warehouse.sim.dsp.model.StartLocation;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.routing.InMemoryProductMasterRepository;
import online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements;
import online.davisfamily.warehouse.sim.dsp.runtime.DspSchedulerRuntimeState;
import online.davisfamily.warehouse.sim.dsp.runtime.SchedulerCommandApplicationResult;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspDependencyEvaluator;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspOrderStatus;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspReleaseScheduler;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;
import online.davisfamily.warehouse.sim.dsp.scheduler.ReleaseDecision;
import online.davisfamily.warehouse.sim.dsp.scheduler.ServiceCentrePriority;
import online.davisfamily.warehouse.sim.dsp.scheduler.ServiceCentreWindowPolicy;
import online.davisfamily.warehouse.sim.dsp.scheduler.SnapshotStationAdmissionResolver;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationCapacity;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ProductMasterThirdPartyPackPlanFactory;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyArea;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyAreaConfig;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyAreaController;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyAreaSnapshot;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyLineWork;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyStationAdmissionResolver;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyVisit;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyVisitFactory;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperTotePayload;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;
import online.davisfamily.warehouse.testing.scheduler.ScheduledDebugToteInjectorController;
import online.davisfamily.warehouse.testing.scheduler.ScheduledTipperToteRelease;
import online.davisfamily.warehouse.testing.scheduler.ScheduledTipperToteReleaseCatalog;
import online.davisfamily.warehouse.testing.scheduler.ScheduledToteReleaseTarget;
import online.davisfamily.warehouse.testing.scheduler.SchedulerDebugInspectable;

public class ThirdPartyDebugRig implements DebugSceneRuntime {
    private static final String SERVICE_CENTRE_ID = "SC-THIRD-PARTY";
    private static final String ADAPTED_ORDER_ID = "third-party-adapted";
    private static final String ADAPTED_NOTIONAL_TOTE_ID = "tote-third-party-adapted";
    private static final String ADAPTED_PHYSICAL_TOTE_ID = "tote-third-party-adapted";
    private static final String ASSOCIATED_ORDER_ID = "third-party-associated";
    private static final String ASSOCIATED_NOTIONAL_TOTE_ID = "tote-third-party-associated";
    private static final String ASSOCIATED_PHYSICAL_TOTE_ID = "tote-third-party-associated";
    private static final String REGULAR_ORDER_ID = "regular-full-pack";
    private static final String REGULAR_NOTIONAL_TOTE_ID = "tote-regular-full-pack";
    private static final String REGULAR_PHYSICAL_TOTE_ID = "tote-regular-full-pack";
    private static final String LATER_ORDER_ID = "third-party-later";
    private static final String LATER_NOTIONAL_TOTE_ID = "tote-third-party-later";
    private static final String LATER_PHYSICAL_TOTE_ID = "tote-third-party-later";
    private static final String THIRD_PARTY_PRODUCT_ID = "100001";
    private static final String SECOND_THIRD_PARTY_PRODUCT_ID = "100002";
    private static final String REGULAR_PRODUCT_ID = "100003";
    private static final String ADAPTED_PRODUCT_ID = "100004";
    private static final PackDimensions PACK_DIMENSIONS = new PackDimensions(0.18f, 0.08f, 0.05f);
    private static final double PROCESSING_DURATION_SECONDS = 15d;
    private static final float TOTE_SPEED = 1.2f;

    private final MapBackedToteLoadPlanRegistry loadPlanRegistry = new MapBackedToteLoadPlanRegistry();
    private final Map<String, NotionalToteOrder> ordersById = new LinkedHashMap<>();
    private final Map<String, NotionalToteOrder> ordersByToteId = new LinkedHashMap<>();
    private final Map<String, ThirdPartyVisit> visitsByOrderId = new LinkedHashMap<>();
    private final Map<String, Tote> liveTotesById = new LinkedHashMap<>();
    private final ThirdPartyArea area;
    private final ThirdPartyAreaController areaController;
    private final ThirdPartyAreaStopController stopController;
    private final DspSchedulerRuntimeState runtimeState;
    private final ScheduledDebugToteInjectorController injectorController;
    private final RouteSegment sourceSegment;
    private final RouteSegment waitingSegment;
    private final RouteSegment areaSegment;
    private final RouteSegment sinkSegment;
    private final RenderableObject areaRenderable;

    public ThirdPartyDebugRig(
            TriangleRenderer tr,
            SimulationWorld sim,
            List<RenderableObject> objects,
            SelectionInspectionRegistry inspectionRegistry) {
        if (tr == null || sim == null || objects == null || inspectionRegistry == null) {
            throw new IllegalArgumentException("Third Party debug rig inputs must not be null");
        }

        ToteGeometry toteGeometry = new ToteGeometry();
        TrackSpec trackSpec = rollerSpec(toteGeometry);
        TrackAppearance appearance = new TrackAppearance(
                new OneColourStrategyImpl(0xFF52646B),
                new OneColourStrategyImpl(0xFF292929),
                new OneColourStrategyImpl(0xFF303030),
                new OneColourStrategyImpl(0xFFB8B8B8),
                new OneColourStrategyImpl(0xFF52646B),
                new OneColourStrategyImpl(0xFF52646B));
        WarehouseRouteBuilder builder = new WarehouseRouteBuilder();
        sourceSegment = builder.segment("third_party_source", line(-8f, -4f));
        waitingSegment = builder.segment("third_party_waiting", line(-4f, 0f));
        areaSegment = builder.segment("third_party_area_route", line(0f, 8f));
        sinkSegment = builder.segment("third_party_downstream_sink", line(8f, 13f));
        builder.renderWith(sourceSegment, trackSpec)
                .renderWith(waitingSegment, trackSpec)
                .renderWith(areaSegment, trackSpec)
                .renderWith(sinkSegment, trackSpec)
                .connectLoop(sourceSegment, waitingSegment)
                .connectLoop(waitingSegment, areaSegment)
                .connectLoop(areaSegment, sinkSegment);

        List<RouteTrackFactory.SpecAndSegment> trackSpecs = builder.getSpecsAndSegments();
        List<RenderableObject> tracks = RouteTrackFactory.createRenderableTracks(tr, trackSpecs, appearance);
        for (int i = 0; i < tracks.size(); i++) {
            RenderableObject track = tracks.get(i);
            track.id = trackSpecs.get(i).segment().getLabel();
            objects.add(track);
            registerTrackInspection(inspectionRegistry, track, trackSpecs.get(i).segment());
        }

        InMemoryProductMasterRepository productRepository = new InMemoryProductMasterRepository(products());
        ThirdPartyVisitFactory visitFactory = new ThirdPartyVisitFactory(productRepository);
        area = new ThirdPartyArea(new ThirdPartyAreaConfig(1, 1, PROCESSING_DURATION_SECONDS));
        areaController = new ThirdPartyAreaController(
                area,
                loadPlanRegistry,
                new ProductMasterThirdPartyPackPlanFactory(
                        productRepository,
                        (visit, lineWork) -> visit.orderSheetKey().orderId()));
        stopController = new ThirdPartyAreaStopController(
                area,
                areaController,
                visitFactory,
                List.of(
                        new ThirdPartyAreaStop("third-party-stop-1", areaSegment, 5.5f),
                        new ThirdPartyAreaStop("third-party-stop-2", areaSegment, 2f)));

        List<DspSchedulerOrderState> orderStates = createOrderStates();
        for (DspSchedulerOrderState orderState : orderStates) {
            NotionalToteOrder order = orderState.order();
            ordersById.put(order.orderId(), order);
            PhysicalToteId physicalToteId = physicalToteIdFor(order);
            ordersByToteId.put(physicalToteId.value(), order);
            visitFactory.create(physicalToteId, order)
                    .ifPresent(visit -> visitsByOrderId.put(order.orderId(), visit));
        }

        Set<PreparedLineKey> preparedLines = Set.of(new PreparedLineKey(
                ASSOCIATED_ORDER_ID,
                "associated-adapted-line"));
        runtimeState = new DspSchedulerRuntimeState(new WarehouseSchedulerSnapshot(
                orderStates,
                Map.of(
                        StationType.ADAPTING, openAdmission(StationType.ADAPTING),
                        StationType.P2P, openAdmission(StationType.P2P)),
                preparedLines,
                Optional.empty()));

        ScheduledTipperToteReleaseCatalog releaseCatalog = createReleaseCatalog(tr, toteGeometry);
        DspReleaseScheduler scheduler = new DspReleaseScheduler(
                new ServiceCentreWindowPolicy(new ServiceCentrePriority(List.of(SERVICE_CENTRE_ID))),
                new DspDependencyEvaluator(),
                new ThirdPartyStationAdmissionResolver(
                        new SnapshotStationAdmissionResolver(),
                        visitFactory,
                        area::snapshot));
        injectorController = new ScheduledDebugToteInjectorController(
                scheduler,
                runtimeState,
                releaseCatalog,
                new ThirdPartyReleaseTarget(sim, objects, inspectionRegistry));

        sim.addController(injectorController);
        sim.addController(stopController);
        sim.addController(new DownstreamSinkController());

        areaRenderable = marker(
                "third_party_area",
                tr,
                new Vec3(1.35f, 0.42f, 4f),
                0.65f,
                0.80f,
                6.5f,
                0xFF356B59);
        makeSelectableHierarchy(areaRenderable, areaRenderable);
        objects.add(areaRenderable);
        inspectionRegistry.register(areaRenderable, this::describeArea);

        RenderableObject adaptingBoundary = marker(
                "third_party_adapting_boundary",
                tr,
                new Vec3(-0.85f, 0.18f, 11.5f),
                0.35f,
                0.25f,
                0.80f,
                0xFF4D7F52);
        RenderableObject p2pBoundary = marker(
                "third_party_p2p_boundary",
                tr,
                new Vec3(0.85f, 0.18f, 11.5f),
                0.35f,
                0.25f,
                0.80f,
                0xFF4F617F);
        objects.add(adaptingBoundary);
        objects.add(p2pBoundary);
        registerBoundaryInspection(inspectionRegistry, adaptingBoundary, "Adapting");
        registerBoundaryInspection(inspectionRegistry, p2pBoundary, "P2P");

        RenderableObject schedulerAnchor = marker(
                "third_party_scheduler_anchor",
                tr,
                new Vec3(0.85f, 0.18f, -6.5f),
                0.35f,
                0.20f,
                0.35f,
                0xFF66527F);
        objects.add(schedulerAnchor);
        SchedulerDebugInspectable schedulerInspectable = new SchedulerDebugInspectable(injectorController);
        inspectionRegistry.register(schedulerAnchor, schedulerInspectable::describe);
    }

    @Override
    public void syncVisuals() {
    }

    @Override
    public void close() {
        injectorController.close();
    }

    ThirdPartyAreaSnapshot areaSnapshot() {
        return area.snapshot();
    }

    ToteLoadPlan loadPlanFor(String toteId) {
        return loadPlanRegistry.getLoadPlanFor(toteId);
    }

    RenderableObject areaRenderable() {
        return areaRenderable;
    }

    WarehouseSchedulerSnapshot schedulerSnapshot() {
        return runtimeState.snapshot();
    }

    Set<String> completedThirdPartyToteIds() {
        return stopController.completedToteIds();
    }

    private List<DspSchedulerOrderState> createOrderStates() {
        NotionalToteOrder adapted = order(
                ADAPTED_ORDER_ID,
                ADAPTED_NOTIONAL_TOTE_ID,
                OrderType.ADAPTED,
                0L,
                line(
                        "adapted-third-party-line",
                        THIRD_PARTY_PRODUCT_ID,
                        DspOrderLineType.ADAPTED,
                        ASSOCIATED_ORDER_ID));
        NotionalToteOrder associated = order(
                ASSOCIATED_ORDER_ID,
                ASSOCIATED_NOTIONAL_TOTE_ID,
                OrderType.ASSOCIATED,
                1L,
                line(
                        "associated-direct-line",
                        SECOND_THIRD_PARTY_PRODUCT_ID,
                        DspOrderLineType.FULL_PACK,
                        ASSOCIATED_ORDER_ID),
                line(
                        "associated-adapted-line",
                        ADAPTED_PRODUCT_ID,
                        DspOrderLineType.ADAPTED,
                        ASSOCIATED_ORDER_ID));
        NotionalToteOrder regular = order(
                REGULAR_ORDER_ID,
                REGULAR_NOTIONAL_TOTE_ID,
                OrderType.FULL_PACK,
                2L,
                line("regular-line", REGULAR_PRODUCT_ID, DspOrderLineType.FULL_PACK, REGULAR_ORDER_ID));
        NotionalToteOrder later = order(
                LATER_ORDER_ID,
                LATER_NOTIONAL_TOTE_ID,
                OrderType.FULL_PACK,
                3L,
                line("later-third-party-line", THIRD_PARTY_PRODUCT_ID, DspOrderLineType.FULL_PACK, LATER_ORDER_ID));

        return List.of(
                state(adapted, route(true, true, false)),
                state(associated, route(true, true, true)),
                state(regular, route(false, false, true)),
                state(later, route(true, false, true)));
    }

    private ScheduledTipperToteReleaseCatalog createReleaseCatalog(TriangleRenderer tr, ToteGeometry toteGeometry) {
        List<ScheduledTipperToteRelease> releases = new ArrayList<>();
        for (NotionalToteOrder order : ordersById.values()) {
            List<PackPlan> initialPacks = initialPacksFor(order);
            ToteLoadPlan loadPlan = new ToteLoadPlan(physicalToteIdFor(order), initialPacks);
            loadPlanRegistry.putLoadPlan(loadPlan);
            releases.add(new ScheduledTipperToteRelease(
                    order.orderId(),
                    loadPlan,
                    () -> createPayload(tr, toteGeometry, loadPlan)));
        }
        return new ScheduledTipperToteReleaseCatalog(releases);
    }

    private PhysicalToteId physicalToteIdFor(NotionalToteOrder order) {
        return switch (order.orderId()) {
            case ADAPTED_ORDER_ID -> new PhysicalToteId(ADAPTED_PHYSICAL_TOTE_ID);
            case ASSOCIATED_ORDER_ID -> new PhysicalToteId(ASSOCIATED_PHYSICAL_TOTE_ID);
            case REGULAR_ORDER_ID -> new PhysicalToteId(REGULAR_PHYSICAL_TOTE_ID);
            case LATER_ORDER_ID -> new PhysicalToteId(LATER_PHYSICAL_TOTE_ID);
            default -> throw new IllegalArgumentException("No physical tote configured for " + order.orderId());
        };
    }

    private List<PackPlan> initialPacksFor(NotionalToteOrder order) {
        return switch (order.orderId()) {
            case ASSOCIATED_ORDER_ID -> List.of(new PackPlan(
                    "pack-associated-adapted-ready",
                    order.orderId(),
                    PACK_DIMENSIONS));
            case REGULAR_ORDER_ID -> List.of(new PackPlan(
                    "pack-regular-existing",
                    order.orderId(),
                    PACK_DIMENSIONS));
            default -> List.of();
        };
    }

    private TipperTotePayload createPayload(
            TriangleRenderer tr,
            ToteGeometry toteGeometry,
            ToteLoadPlan loadPlan) {
        RenderableObject toteRenderable = RenderableToteFactory.createRenderableTote(
                loadPlan.getToteId(),
                tr,
                toteGeometry,
                true);
        Tote tote = new Tote(
                loadPlan.getToteId(),
                new RouteFollower(loadPlan.getToteId(), sourceSegment, 0f, TOTE_SPEED),
                toteRenderable,
                new Vec3(),
                toteRenderable.yawOffsetRadians);
        tote.closeLids();
        tote.snapToRouteDistance(0f);
        return new TipperTotePayload(tote, toteRenderable, 0f, Map.of());
    }

    private List<String> describeArea() {
        ThirdPartyAreaSnapshot snapshot = area.snapshot();
        List<String> lines = new ArrayList<>();
        lines.add("Type: Third Party Area");
        lines.add("Processing: " + snapshot.activeCount() + " / " + snapshot.config().maxConcurrentVisits());
        lines.add("Waiting: " + snapshot.waitingCount() + " / " + snapshot.config().waitingCapacity());
        lines.add("Waiting totes: " + formatList(snapshot.waitingPhysicalToteIds().stream()
                .map(PhysicalToteId::value)
                .toList()));
        if (snapshot.activeVisits().isEmpty()) {
            lines.add("Active visits: none");
        } else {
            snapshot.activeVisits().forEach(active -> {
                lines.add("Active order/tote: " + active.orderSheetKey().orderId()
                        + " / " + active.physicalToteId().value());
                appendVisitDetails(lines, visitsByOrderId.get(active.orderSheetKey().orderId()));
            });
        }
        areaController.lastCompletion().ifPresentOrElse(
                completion -> {
                    lines.add("Last completion: " + completion.visit().orderSheetKey().orderId()
                            + " / " + completion.visit().physicalToteId().value());
                    appendVisitDetails(lines, completion.visit());
                },
                () -> lines.add("Last completion: none"));
        return List.copyOf(lines);
    }

    private static void appendVisitDetails(List<String> lines, ThirdPartyVisit visit) {
        if (visit == null) {
            return;
        }
        lines.add("Work: " + visit.lineWork().stream()
                .map(line -> line.workType().name())
                .distinct()
                .collect(Collectors.joining(", ")));
        for (ThirdPartyLineWork lineWork : visit.lineWork()) {
            lines.add("Line: " + lineWork.lineReference()
                    + " product " + lineWork.productId()
                    + " bin " + lineWork.binLocation());
        }
    }

    private void registerToteInspection(
            SelectionInspectionRegistry inspectionRegistry,
            RenderableObject renderable,
            Tote tote,
            NotionalToteOrder order) {
        makeSelectableHierarchy(renderable, renderable);
        inspectionRegistry.register(renderable, () -> {
            ToteLoadPlan loadPlan = loadPlanRegistry.getLoadPlanFor(tote.getId());
            ThirdPartyVisit visit = visitsByOrderId.get(order.orderId());
            List<String> lines = new ArrayList<>();
            lines.add("Type: DSP tote");
            lines.add("Order/tote: " + order.orderId() + " / " + tote.getId());
            lines.add("Order type: " + order.orderType());
            lines.add("Destination: " + destinationFor(order));
            lines.add("Motion: " + tote.getInteractionMode());
            lines.add("Segment: " + (tote.getLastSnapshot() == null
                    ? "none"
                    : tote.getLastSnapshot().currentSegment().getLabel()));
            lines.add("Pack plans: " + (loadPlan == null
                    ? "none"
                    : formatList(loadPlan.getPackPlans().stream().map(PackPlan::packId).toList())));
            appendVisitDetails(lines, visit);
            return List.copyOf(lines);
        });
    }

    private static String destinationFor(NotionalToteOrder order) {
        return order.orderType() == OrderType.ADAPTED ? "Adapting" : "P2P";
    }

    private static void registerTrackInspection(
            SelectionInspectionRegistry inspectionRegistry,
            RenderableObject track,
            RouteSegment segment) {
        makeSelectableHierarchy(track, track);
        inspectionRegistry.register(track, () -> List.of(
                "Type: Track",
                "Segment: " + segment.getLabel(),
                "Length: " + segment.length()));
    }

    private static void registerBoundaryInspection(
            SelectionInspectionRegistry inspectionRegistry,
            RenderableObject marker,
            String destination) {
        makeSelectableHierarchy(marker, marker);
        inspectionRegistry.register(marker, () -> List.of(
                "Type: Downstream boundary",
                "Destination: " + destination));
    }

    private static String formatList(List<String> values) {
        return values.isEmpty() ? "none" : String.join(", ", values);
    }

    private static void makeSelectableHierarchy(RenderableObject root, RenderableObject selectionTarget) {
        root.setSelectable(true);
        root.setSelectionTarget(selectionTarget);
        for (RenderableObject child : root.children) {
            makeSelectableHierarchy(child, selectionTarget);
        }
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
                new ObjectTransformation(0f, 0f, 0f, position.x, position.y, position.z, new Mat4()),
                new OneColourStrategyImpl(colourArgb),
                true);
    }

    private static LinearSegment3 line(float startZ, float endZ) {
        return new LinearSegment3(new Vec3(0f, 0f, startZ), new Vec3(0f, 0f, endZ), false);
    }

    private static TrackSpec rollerSpec(ToteGeometry toteGeometry) {
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

    private static List<ProductMasterRecord> products() {
        return List.of(
                product(THIRD_PARTY_PRODUCT_ID, "Y74"),
                product(SECOND_THIRD_PARTY_PRODUCT_ID, "Y75"),
                product(REGULAR_PRODUCT_ID, null),
                product(ADAPTED_PRODUCT_ID, null));
    }

    private static ProductMasterRecord product(String productId, String binLocation) {
        return new ProductMasterRecord(
                productId,
                "Fixture product " + productId,
                Optional.ofNullable(binLocation),
                Optional.of(PACK_DIMENSIONS));
    }

    private static DspSchedulerOrderState state(NotionalToteOrder order, RouteRequirements route) {
        return new DspSchedulerOrderState(order, route, DspOrderStatus.WAITING);
    }

    private static RouteRequirements route(boolean thirdParty, boolean adapting, boolean p2p) {
        return new RouteRequirements(thirdParty, adapting, false, p2p, false, StartLocation.OSR);
    }

    private static NotionalToteOrder order(
            String orderId,
            String toteId,
            OrderType orderType,
            long sequenceNumber,
            DspOrderItem... items) {
        return new NotionalToteOrder(
                orderId,
                toteId,
                SERVICE_CENTRE_ID,
                1,
                orderType,
                List.of(items),
                sequenceNumber);
    }

    private static DspOrderItem line(
            String lineReference,
            String productId,
            DspOrderLineType lineType,
            String referenceOrderId) {
        return new DspOrderItem(
                lineReference,
                productId,
                1,
                "0000310",
                lineType,
                referenceOrderId,
                1,
                0);
    }

    private static StationAdmissionSnapshot openAdmission(StationType stationType) {
        return new StationAdmissionSnapshot(
                stationType,
                new StationCapacity(1, 1),
                new StationSnapshot(stationType, 0, 0),
                true,
                "");
    }

    private final class ThirdPartyReleaseTarget implements ScheduledToteReleaseTarget {
        private final SimulationWorld sim;
        private final List<RenderableObject> objects;
        private final SelectionInspectionRegistry inspectionRegistry;

        private ThirdPartyReleaseTarget(
                SimulationWorld sim,
                List<RenderableObject> objects,
                SelectionInspectionRegistry inspectionRegistry) {
            this.sim = sim;
            this.objects = objects;
            this.inspectionRegistry = inspectionRegistry;
        }

        @Override
        public boolean canAcceptRelease() {
            return liveTotesById.values().stream().noneMatch(tote -> {
                if (tote.getLastSnapshot() == null) {
                    return false;
                }
                RouteSegment segment = tote.getLastSnapshot().currentSegment();
                if (tote.getInteractionMode() == Tote.ToteMotionState.MOVING) {
                    return segment == sourceSegment
                            || segment == waitingSegment
                            || (segment == areaSegment && stopController.activeToteIds().contains(tote.getId()));
                }
                return tote.getInteractionMode() == Tote.ToteMotionState.HELD
                        && segment == areaSegment
                        && tote.getLastSnapshot().distanceAlongSegment() <= 2.1f;
            });
        }

        @Override
        public SchedulerCommandApplicationResult release(ReleaseDecision decision, TipperTotePayload payload) {
            if (decision == null || payload == null) {
                throw new IllegalArgumentException("decision and payload must not be null");
            }
            NotionalToteOrder order = ordersById.get(decision.orderId());
            if (order == null) {
                return SchedulerCommandApplicationResult.rejectedResult(
                        "No Third Party fixture order for " + decision.orderId());
            }
            Tote tote = payload.getTote();
            if (!stopController.registerTote(tote, order)) {
                return SchedulerCommandApplicationResult.deferredResult(
                        "No Third Party route stop is currently available");
            }

            if (!objects.contains(payload.getToteRenderable())) {
                objects.add(payload.getToteRenderable());
            }
            sim.addTrackableObject(tote);
            liveTotesById.put(tote.getId(), tote);
            registerToteInspection(inspectionRegistry, payload.getToteRenderable(), tote, order);
            return SchedulerCommandApplicationResult.appliedResult();
        }
    }

    private final class DownstreamSinkController implements SimulationController {
        private final Map<String, Integer> p2pSlotsByToteId = new LinkedHashMap<>();

        @Override
        public void update(SimulationContext context, double dtSeconds) {
            for (Tote tote : liveTotesById.values()) {
                if (tote.getInteractionMode() != Tote.ToteMotionState.MOVING
                        || tote.getLastSnapshot() == null
                        || tote.getLastSnapshot().currentSegment() != sinkSegment
                        || tote.getLastSnapshot().distanceAlongSegment() < 4f) {
                    continue;
                }
                NotionalToteOrder order = ordersByToteId.get(tote.getId());
                tote.setInteractionMode(Tote.ToteMotionState.HELD);
                tote.closeLids();
                if (order != null && order.orderType() == OrderType.ADAPTED) {
                    tote.setVisualOffset(-0.85f, 0f, 0f);
                } else {
                    int slot = p2pSlotsByToteId.computeIfAbsent(tote.getId(), ignored -> p2pSlotsByToteId.size());
                    tote.setVisualOffset(0.85f, 0f, -0.55f * slot);
                }
            }
        }
    }
}
