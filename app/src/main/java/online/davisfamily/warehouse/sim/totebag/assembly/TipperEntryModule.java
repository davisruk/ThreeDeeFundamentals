package online.davisfamily.warehouse.sim.totebag.assembly;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

import online.davisfamily.threedee.behaviour.routing.RouteFollower;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.debug.SelectionInspectionRegistry;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.matrices.Vec4;
import online.davisfamily.threedee.model.Mesh;
import online.davisfamily.threedee.path.LinearSegment3;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.rendering.TriangleRenderer;
import online.davisfamily.threedee.rendering.appearance.OneColourStrategyImpl;
import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.rendering.model.tote.RenderableToteFactory;
import online.davisfamily.warehouse.rendering.model.tote.ToteEnvelope;
import online.davisfamily.warehouse.rendering.model.tote.ToteGeometry;
import online.davisfamily.warehouse.rendering.model.tracks.ConveyorRuntimeState;
import online.davisfamily.warehouse.rendering.model.tracks.RenderableTrackFactory;
import online.davisfamily.warehouse.rendering.model.tracks.RollerMeshFactory;
import online.davisfamily.warehouse.rendering.model.tracks.RouteTrackFactory;
import online.davisfamily.warehouse.rendering.model.tracks.TrackAppearance;
import online.davisfamily.warehouse.rendering.model.tracks.TrackSpec;
import online.davisfamily.warehouse.rendering.model.tracks.WarehouseSegmentMetadata;
import online.davisfamily.warehouse.rendering.model.tracks.WarehouseRouteBuilder;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.totebag.control.ToteTrackTipperFlowController;
import online.davisfamily.warehouse.sim.totebag.handoff.MachineHandoffPointId;
import online.davisfamily.warehouse.sim.totebag.handoff.PackHandoffPoint;
import online.davisfamily.warehouse.sim.totebag.handoff.PackHandoffPointProvider;
import online.davisfamily.warehouse.sim.totebag.handoff.PackReceiveTarget;
import online.davisfamily.warehouse.sim.totebag.layout.ContainedPackLayout;
import online.davisfamily.warehouse.sim.totebag.layout.TipperEntryLayoutSpec;
import online.davisfamily.warehouse.sim.totebag.machine.SortingMachine;
import online.davisfamily.warehouse.sim.totebag.machine.TippingMachine;
import online.davisfamily.warehouse.sim.totebag.pack.Pack;
import online.davisfamily.warehouse.sim.totebag.pack.Pack.PackContainmentState;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;
import online.davisfamily.warehouse.sim.totebag.transfer.TippingDischargeTransfer;

public class TipperEntryModule implements PackHandoffPointProvider {
    private static final float TRACK_Z = 0f;
    private static final float TRACK_LENGTH = 6.0f;
    private static final float TIPPER_LENGTH = 1.25f;
    private static final float TIPPER_START_X = (TRACK_LENGTH - TIPPER_LENGTH) * 0.5f;
    private static final float TIPPER_STOP_DISTANCE = TIPPER_START_X + (TIPPER_LENGTH * 0.5f);
    private static final float OUTFEED_Z = -1.24f;
    private static final float TIPPER_TIPPED_ANGLE_RADIANS = -1.02f;
    private static final float SLIDE_ENTRY_WIDTH = 0.90f;
    private static final float SLIDE_EXIT_WIDTH = 0.30f;
    private static final float SLIDE_LENGTH = 1.20f;
    private static final float SLIDE_THICKNESS = 0.03f;
    private static final float SORTER_INTAKE_CLEARANCE = 0.08f;
    private static final float CONTAINED_PACK_GAP_X = 0.012f;
    private static final float CONTAINED_PACK_GAP_Z = 0.012f;
    private static final float CONTAINED_PACK_GAP_Y = 0.010f;

    private final List<RenderableObject> objects;
    private final SelectionInspectionRegistry inspectionRegistry;
    private final TriangleRenderer tr;
    private final TipperEntryLayoutSpec layoutSpec;

    private final Tote tote;
    private final ToteLoadPlan toteLoadPlan;
    private final TippingMachine tippingMachine;
    private final SortingMachine sortingMachine;
    private final ToteTrackTipperFlowController flowController;
    private final TipperModule tipperModule;
    private final SortingModule sortingModule;

    private final RenderableObject toteRenderable;
    private final RenderableObject tipperAssemblyRenderable;
    private final RenderableObject tipperTrackRenderable;
    private final RenderableObject tipperSlideRenderable;
    private final Vec3 tipperAssemblyLocalOrigin;
    private final Vec3 dischargeToteInteriorLocal;
    private final Vec3 dischargeLidLocal;
    private final Vec3 dischargeSlideEntryLocal;
    private final float slideEntryWidth;
    private final ConveyorRuntimeState tipperTrackRuntimeState;
    private final float tippedAngleRadians;
    private final float toteInteriorHalfWidth;
    private final float toteInteriorHalfDepth;
    private final float toteInteriorFloorLocalY;
    private final Map<String, Vec3> containedPackLayoutById;
    private final Map<String, PackPlan> packPlansById = new LinkedHashMap<>();
    private final Map<String, RenderableObject> packRenderablesById = new LinkedHashMap<>();

    public TipperEntryModule(
            TriangleRenderer tr,
            SimulationWorld sim,
            List<RenderableObject> objects,
            SelectionInspectionRegistry inspectionRegistry,
            TipperEntryLayoutSpec layoutSpec,
            PackReceiveTarget sorterOutfeedTarget) {
        this.tr = tr;
        this.objects = objects;
        this.inspectionRegistry = inspectionRegistry;
        this.layoutSpec = layoutSpec != null ? layoutSpec : TipperEntryLayoutSpec.debugDefaults();

        ToteGeometry toteGeometry = new ToteGeometry();
        toteRenderable = RenderableToteFactory.createRenderableTote("tipper_demo_tote", tr, toteGeometry, true);
        toteInteriorHalfWidth = toteGeometry.getInnerBottomWidth() * 0.5f;
        toteInteriorHalfDepth = toteGeometry.getInnerBottomDepth() * 0.5f;
        toteInteriorFloorLocalY = 0.04f + (toteGeometry.getOuterHeight() - toteGeometry.getInnerHeight()) + 0.01f;
        ToteEnvelope toteEnvelope = new ToteEnvelope(
                toteGeometry.getOuterBottomWidth(),
                toteGeometry.getOuterBottomDepth(),
                toteGeometry.getOuterHeight());

        TrackSpec toteTrackSpec = new TrackSpec(
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
                0.080f
        );
        TrackSpec tipperTrackSpec = new TrackSpec(
                toteEnvelope,
                toteTrackSpec.sideClearance,
                toteTrackSpec.deckThickness,
                toteTrackSpec.deckTopY,
                false,
                toteTrackSpec.guideHeight,
                toteTrackSpec.guideThickness,
                toteTrackSpec.guideGap,
                toteTrackSpec.connectionGuideCutback,
                toteTrackSpec.targetGuideOpeningLength,
                toteTrackSpec.includeRollers,
                toteTrackSpec.rollerPitch,
                toteTrackSpec.rollerWidthInset,
                toteTrackSpec.rollerHeight,
                toteTrackSpec.rollerDepthAlongPath,
                toteTrackSpec.sampleStep
        );

        WarehouseRouteBuilder builder = new WarehouseRouteBuilder();
        Vec3 infeedStart = localToWorld(0f, 0f, TRACK_Z);
        Vec3 infeedEnd = localToWorld(TIPPER_START_X, 0f, TRACK_Z);
        Vec3 tipperEnd = localToWorld(TIPPER_START_X + TIPPER_LENGTH, 0f, TRACK_Z);
        Vec3 exitEnd = localToWorld(TRACK_LENGTH, 0f, TRACK_Z);
        RouteSegment infeedSegment = builder.segment(
                "tipper_infeed",
                new LinearSegment3(infeedStart, infeedEnd, false));
        RouteSegment tipperSegment = builder.segment(
                "tipper_track",
                new LinearSegment3(infeedEnd, tipperEnd, false));
        RouteSegment exitSegment = builder.segment(
                "tipper_exit",
                new LinearSegment3(tipperEnd, exitEnd, false));
        builder.renderWith(infeedSegment, toteTrackSpec);
        builder.renderWith(exitSegment, toteTrackSpec);
        builder.connectLoop(infeedSegment, tipperSegment);
        builder.connectLoop(tipperSegment, exitSegment);

        TrackAppearance trackAppearance = new TrackAppearance(
                new OneColourStrategyImpl(0xFF596A54),
                new OneColourStrategyImpl(0xFF2A2A2A),
                new OneColourStrategyImpl(0xFF2F2F2F),
                new OneColourStrategyImpl(0xFFB8B8B8),
                new OneColourStrategyImpl(0xFF596A54),
                new OneColourStrategyImpl(0xFF596A54));
        objects.addAll(RouteTrackFactory.createRenderableTracks(tr, builder.getSpecsAndSegments(), trackAppearance));

        float toteYOffset = toteTrackSpec.getLoadSurfaceHeight() + 0.02f;
        tote = new Tote(
                toteRenderable.id,
                new RouteFollower(toteRenderable.id, infeedSegment, 0f, 1.4d),
                toteRenderable,
                new Vec3(0f, toteYOffset, 0f),
                toteRenderable.yawOffsetRadians);
        sim.addTrackableObject(tote);

        toteLoadPlan = createDemoPlan(tote.getId());
        for (PackPlan plan : toteLoadPlan.getPackPlans()) {
            packPlansById.put(plan.packId(), plan);
        }
        containedPackLayoutById = new ContainedPackLayout(
                toteGeometry.getInnerBottomWidth(),
                toteGeometry.getInnerBottomDepth(),
                toteInteriorFloorLocalY,
                CONTAINED_PACK_GAP_X,
                CONTAINED_PACK_GAP_Z,
                CONTAINED_PACK_GAP_Y)
                        .layoutPackPlans(toteLoadPlan.getPackPlans());
        tippingMachine = new TippingMachine("tipper", 0.45d, 0.18d, 0.35d);
        sortingMachine = new SortingMachine("sorter", 0.22d);
        flowController = new ToteTrackTipperFlowController(
                tote,
                toteLoadPlan,
                tipperSegment,
                TIPPER_LENGTH * 0.5f,
                TIPPER_TIPPED_ANGLE_RADIANS,
                tippingMachine,
                sortingMachine,
                0.55d,
                sorterOutfeedTarget);

        sim.addSimObject(tippingMachine);
        sim.addSimObject(sortingMachine);
        sim.addController(flowController);

        tipperTrackRuntimeState = new ConveyorRuntimeState();
        tipperTrackRuntimeState.setRunning(true);
        float tipperTrackOverallWidth = tipperTrackSpec.getOverallWidth();
        slideEntryWidth = TIPPER_LENGTH;
        tipperAssemblyLocalOrigin = new Vec3(
                TIPPER_STOP_DISTANCE,
                0.02f,
                TRACK_Z - (tipperTrackOverallWidth * 0.5f));
        tipperAssemblyRenderable = createAnchor("tipper_assembly");
        Vec3 tipperAssemblyWorld = localToWorld(tipperAssemblyLocalOrigin);
        tipperAssemblyRenderable.transformation.xTranslation = tipperAssemblyWorld.x;
        tipperAssemblyRenderable.transformation.yTranslation = tipperAssemblyWorld.y;
        tipperAssemblyRenderable.transformation.zTranslation = tipperAssemblyWorld.z;
        tipperAssemblyRenderable.transformation.angleY = rigYaw();

        tipperTrackRenderable = createLocalTipperTrack(tr, tipperTrackSpec, trackAppearance, tipperTrackRuntimeState);
        tipperTrackRenderable.transformation.zTranslation = tipperTrackOverallWidth * 0.5f;
        tipperSlideRenderable = createFunnelSlideRenderable("tipper_slide", 0xFFFF00FF);
        tipperSlideRenderable.transformation.xTranslation = 0f;
        tipperSlideRenderable.transformation.yTranslation = 0.04f;
        tipperSlideRenderable.transformation.zTranslation = -0.02f;
        tipperSlideRenderable.addChild(createSlideGuide(
                "tipper_slide_left_guide",
                -(slideEntryWidth * 0.5f) + 0.015f,
                slideEntryWidth,
                SLIDE_EXIT_WIDTH));
        tipperSlideRenderable.addChild(createSlideGuide(
                "tipper_slide_right_guide",
                (slideEntryWidth * 0.5f) - 0.015f,
                slideEntryWidth,
                SLIDE_EXIT_WIDTH));

        tipperAssemblyRenderable.addChild(tipperTrackRenderable);
        tipperAssemblyRenderable.addChild(tipperSlideRenderable);

        tippedAngleRadians = TIPPER_TIPPED_ANGLE_RADIANS;
        dischargeToteInteriorLocal = new Vec3(0f, toteInteriorFloorLocalY + 0.06f, 0.10f);
        dischargeLidLocal = new Vec3(0f, 0.24f, -0.08f);
        dischargeSlideEntryLocal = new Vec3(
                0f,
                tipperSlideRenderable.transformation.yTranslation + 0.01f,
                tipperSlideRenderable.transformation.zTranslation - 0.02f);
        Vec3 sorterIntakeLocal = tipperAssemblyPointToWorldLocal(
                0f,
                tipperSlideRenderable.transformation.yTranslation - 0.04f,
                tipperSlideRenderable.transformation.zTranslation - SLIDE_LENGTH - SORTER_INTAKE_CLEARANCE,
                tippedAngleRadians);

        objects.add(toteRenderable);
        objects.add(tipperAssemblyRenderable);

        tipperModule = new TipperModule(
                tote,
                toteLoadPlan,
                tippingMachine,
                toteRenderable,
                tipperAssemblyRenderable,
                this::tipperPackDischargePoint);
        sortingModule = new SortingModule(
                tr,
                sortingMachine,
                layoutSpec.origin(),
                rigYaw(),
                sorterIntakeLocal);
        objects.add(sortingModule.getRenderable());

        registerInspectableObjects();
    }

    public void syncVisuals() {
        tipperTrackRuntimeState.setRunning(!flowController.isToteCaptured());
        syncTipperVisuals();
        ensurePackRenderablesExist();
        hideDetachedPacks();
        Set<String> placedPackIds = new HashSet<>();
        positionRemainingPacksInTote(placedPackIds);
        positionActiveDischarges(placedPackIds);
        sortingModule.syncQueuedPackVisuals(
                packRenderablesById,
                placedPackIds,
                (pack, renderable) -> detachFromToteIfNeeded(pack, renderable, null));
    }

    public ToteLoadPlan getToteLoadPlan() {
        return toteLoadPlan;
    }

    public TipperModule getTipperModule() {
        return tipperModule;
    }

    public SortingModule getSortingModule() {
        return sortingModule;
    }

    public RenderableObject getPackRenderable(String packId) {
        return packRenderablesById.get(packId);
    }

    @Override
    public PackHandoffPoint resolveHandoffPoint(MachineHandoffPointId pointId) {
        if (pointId == null) {
            throw new IllegalArgumentException("pointId must not be null");
        }
        return switch (pointId) {
            case TIPPER_PACK_DISCHARGE -> tipperModule.dischargePoint();
            case SORTER_PACK_INTAKE -> sortingModule.intakePoint();
            case SORTER_PACK_OUTFEED -> sortingModule.outfeedPoint();
        };
    }

    private void registerInspectableObjects() {
        inspectionRegistry.register(toteRenderable, () -> List.of(
                "Type: Tote",
                "Id: " + tipperModule.getTote().getId(),
                "Motion: " + tipperModule.getTote().getInteractionMode(),
                "Distance: " + (tipperModule.getTote().getLastSnapshot() == null
                        ? "None"
                        : String.format("%.3f", tipperModule.getTote().getLastSnapshot().distanceAlongSegment()))));

        inspectionRegistry.register(tipperModule.getAssemblyRenderable(), () -> List.of(
                "Type: Tipper",
                "State: " + tipperModule.getTippingMachine().getState(),
                "Captured tote: " + flowController.isToteCaptured(),
                "Remaining packs: " + tipperModule.getTippingMachine().getRemainingPackCount(),
                "Active discharges: " + flowController.getActiveDischarges().size()));

        inspectionRegistry.register(sortingModule.getRenderable(), () -> List.of(
                "Type: Sorter",
                "State: " + sortingModule.getSortingMachine().getState(),
                "Queued packs: " + sortingModule.getSortingMachine().getQueuedPacks().size(),
                "Completed output packs: " + flowController.getCompletedOutputPacks().size()));
    }

    private void syncTipperVisuals() {
        float targetAngle = tippedAngleRadians * flowController.getVisualTipProgress();
        tipperAssemblyRenderable.transformation.angleX = targetAngle;
    }

    private void ensurePackRenderablesExist() {
        for (PackPlan plan : toteLoadPlan.getPackPlans()) {
            packRenderablesById.computeIfAbsent(plan.packId(), ignored -> {
                RenderableObject renderable = createPackRenderable(
                        plan.packId(),
                        plan.dimensions(),
                        plan.correlationId());
                toteRenderable.addChild(renderable);
                inspectionRegistry.register(renderable, () -> List.of(
                        "Type: Pack",
                        "Id: " + plan.packId(),
                        "Correlation: " + plan.correlationId()));
                return renderable;
            });
        }
    }

    private void hideDetachedPacks() {
        for (RenderableObject renderable : packRenderablesById.values()) {
            if (toteRenderable.children.contains(renderable)) {
                continue;
            }
            renderable.transformation.xTranslation = -50f;
            renderable.transformation.yTranslation = -50f;
            renderable.transformation.zTranslation = -50f;
            renderable.transformation.angleX = 0f;
            renderable.transformation.angleY = 0f;
            renderable.transformation.angleZ = 0f;
        }
    }

    private void positionActiveDischarges(Set<String> placedPackIds) {
        for (TippingDischargeTransfer transfer : flowController.getActiveDischarges()) {
            RenderableObject renderable = packRenderablesById.get(transfer.getPack().getId());
            if (renderable == null) {
                continue;
            }
            detachFromToteIfNeeded(transfer.getPack(), renderable, transfer);
            Vec3 dischargeWorld = sampleDischargeWorldPosition(transfer.getProgress(), transfer.getPack());
            renderable.transformation.xTranslation = dischargeWorld.x;
            renderable.transformation.yTranslation = dischargeWorld.y;
            renderable.transformation.zTranslation = dischargeWorld.z;
            renderable.transformation.angleX = 0f;
            renderable.transformation.angleY = rigYaw();
            renderable.transformation.angleZ = 0f;
            placedPackIds.add(transfer.getPack().getId());
        }
    }

    private void positionRemainingPacksInTote(Set<String> placedPackIds) {
        for (PackPlan plan : toteLoadPlan.getPackPlans()) {
            if (isObserved(plan.packId())) {
                continue;
            }
            RenderableObject renderable = packRenderablesById.get(plan.packId());
            if (renderable == null) {
                continue;
            }
            ensureAttachedToTote(renderable);
            Vec3 local = containedPackLocalFor(plan.packId());
            if (local == null) {
                continue;
            }
            renderable.transformation.xTranslation = local.x;
            renderable.transformation.yTranslation = local.y;
            renderable.transformation.zTranslation = local.z;
            renderable.transformation.angleX = 0f;
            renderable.transformation.angleY = 0f;
            renderable.transformation.angleZ = 0f;
            placedPackIds.add(plan.packId());
        }
    }

    private ToteLoadPlan createDemoPlan(String toteId) {
        return new ToteLoadPlan(
                toteId,
                List.of(
                        new PackPlan("pack-a1", "bag-a", new PackDimensions(0.18f, 0.12f, 0.10f)),
                        new PackPlan("pack-b1", "bag-b", new PackDimensions(0.20f, 0.11f, 0.10f)),
                        new PackPlan("pack-a2", "bag-a", new PackDimensions(0.16f, 0.10f, 0.08f)),
                        new PackPlan("pack-c1", "bag-c", new PackDimensions(0.22f, 0.12f, 0.10f)),
                        new PackPlan("pack-b2", "bag-b", new PackDimensions(0.19f, 0.11f, 0.09f))));
    }

    private RenderableObject createPackRenderable(String packId, PackDimensions dimensions, String correlationId) {
        return RenderableObject.create(
                packId,
                tr,
                RollerMeshFactory.createBoxRollerMesh(
                        dimensions.length(),
                        dimensions.height(),
                        dimensions.width()),
                new ObjectTransformation(0f, 0f, 0f, -50f, -50f, -50f, new Mat4()),
                new OneColourStrategyImpl(colourForCorrelation(correlationId)),
                true);
    }

    private RenderableObject createBox(
            String id,
            float length,
            float height,
            float width,
            int colour) {
        return createBox(id, length, height, width, colour, true);
    }

    private RenderableObject createBox(
            String id,
            float length,
            float height,
            float width,
            int colour,
            boolean selectable) {
        return RenderableObject.create(
                id,
                tr,
                RollerMeshFactory.createBoxRollerMesh(length, height, width),
                new ObjectTransformation(0f, 0f, 0f, 0f, 0f, 0f, new Mat4()),
                new OneColourStrategyImpl(colour),
                selectable);
    }

    private RenderableObject createFunnelSlideRenderable(String id, int colour) {
        return RenderableObject.create(
                id,
                tr,
                createFunnelSlideMesh(),
                new ObjectTransformation(0f, 0f, 0f, 0f, 0f, 0f, new Mat4()),
                new OneColourStrategyImpl(colour),
                true);
    }

    private RenderableObject createSlideGuide(String id, float xOffset, float entryWidth, float exitWidth) {
        boolean left = xOffset < 0f;
        float wallThickness = 0.02f;
        float wallHeight = 0.07f;
        float yBottom = SLIDE_THICKNESS * 0.5f;
        float yTop = yBottom + wallHeight;
        float zStart = 0f;
        float zEnd = -SLIDE_LENGTH;
        float outerEntryX = left ? -(entryWidth * 0.5f) : (entryWidth * 0.5f);
        float outerExitX = left ? -(exitWidth * 0.5f) : (exitWidth * 0.5f);
        float innerEntryX = left ? outerEntryX + wallThickness : outerEntryX - wallThickness;
        float innerExitX = left ? outerExitX + wallThickness : outerExitX - wallThickness;
        float startMinX = Math.min(innerEntryX, outerEntryX);
        float startMaxX = Math.max(innerEntryX, outerEntryX);
        float endMinX = Math.min(innerExitX, outerExitX);
        float endMaxX = Math.max(innerExitX, outerExitX);

        Vec4[] vertices = new Vec4[] {
                new Vec4(startMinX, yBottom, zStart, 1f),
                new Vec4(startMaxX, yBottom, zStart, 1f),
                new Vec4(startMaxX, yTop, zStart, 1f),
                new Vec4(startMinX, yTop, zStart, 1f),
                new Vec4(endMinX, yBottom, zEnd, 1f),
                new Vec4(endMaxX, yBottom, zEnd, 1f),
                new Vec4(endMaxX, yTop, zEnd, 1f),
                new Vec4(endMinX, yTop, zEnd, 1f)
        };

        int[][] triangles = new int[][] {
                {0, 1, 2}, {0, 2, 3},
                {4, 7, 6}, {4, 6, 5},
                {0, 3, 7}, {0, 7, 4},
                {1, 5, 6}, {1, 6, 2},
                {0, 4, 5}, {0, 5, 1},
                {3, 2, 6}, {3, 6, 7}
        };
        return RenderableObject.create(
                id,
                tr,
                new Mesh(vertices, triangles, id + "_mesh"),
                new ObjectTransformation(0f, 0f, 0f, 0f, 0f, 0f, new Mat4()),
                new OneColourStrategyImpl(0xFF6E6A5A),
                true);
    }

    private int colourForCorrelation(String correlationId) {
        return switch (correlationId) {
            case "bag-a" -> 0xFFE67E22;
            case "bag-b" -> 0xFF4AA3DF;
            case "bag-c" -> 0xFF7ABF66;
            default -> 0xFFBBBBBB;
        };
    }

    private RenderableObject createLocalTipperTrack(
            TriangleRenderer tr,
            TrackSpec toteTrackSpec,
            TrackAppearance trackAppearance,
            ConveyorRuntimeState rollerRuntimeState) {
        RouteSegment localTipperSegment = new RouteSegment(
                "local_tipper_track",
                new LinearSegment3(
                        new Vec3(-TIPPER_LENGTH * 0.5f, 0f, 0f),
                        new Vec3(TIPPER_LENGTH * 0.5f, 0f, 0f),
                        false));
        RenderableObject renderable = RenderableTrackFactory.createRenderableTrack(
                tr,
                localTipperSegment,
                new WarehouseSegmentMetadata(),
                toteTrackSpec,
                trackAppearance,
                rollerRuntimeState);
        renderable.transformation.yTranslation = 0f;
        return renderable;
    }

    private RenderableObject createAnchor(String id) {
        return RenderableObject.create(
                id,
                tr,
                new online.davisfamily.threedee.model.Mesh(
                        new online.davisfamily.threedee.matrices.Vec4[] {
                                new online.davisfamily.threedee.matrices.Vec4(0f, 0f, 0f, 1f),
                                new online.davisfamily.threedee.matrices.Vec4(0f, 0f, 0f, 1f),
                                new online.davisfamily.threedee.matrices.Vec4(0f, 0f, 0f, 1f)
                        },
                        new int[][] { {0, 1, 2} },
                        "anchor"),
                new ObjectTransformation(0f, 0f, 0f, 0f, 0f, 0f, new Mat4()),
                triangleIndex -> 0,
                false);
    }

    private Vec3 localToWorld(float localX, float localY, float localZ) {
        return localToWorld(new Vec3(localX, localY, localZ));
    }

    private Vec3 localToWorld(Vec3 localPoint) {
        Vec3 rotated = Vec3.rotateY(localPoint, rigYaw());
        rotated.mutableAdd(layoutSpec.origin());
        return rotated;
    }

    private PackHandoffPoint tipperPackDischargePoint() {
        Vec3 dischargeWorld = localToWorld(tipperAssemblyPointToWorldLocal(
                0f,
                tipperSlideRenderable.transformation.yTranslation - 0.04f,
                tipperSlideRenderable.transformation.zTranslation - SLIDE_LENGTH,
                tippedAngleRadians));
        return new PackHandoffPoint(
                MachineHandoffPointId.TIPPER_PACK_DISCHARGE.name().toLowerCase(),
                dischargeWorld,
                rigYaw());
    }

    private Vec3 tipperAssemblyPointToWorldLocal(
            float localX,
            float localY,
            float localZ,
            float angleX) {
        Vec3 rotated = new Vec3(
                localX,
                rotatedY(localY, localZ, angleX),
                rotatedZ(localY, localZ, angleX));
        rotated.mutableAdd(tipperAssemblyLocalOrigin);
        return rotated;
    }

    private Vec3 sampleDischargeWorldPosition(double progress, Pack pack) {
        float angle = currentTipAngle();
        float clearance = (pack.getDimensions().height() * 0.5f) + 0.008f;
        Vec3 startWorld = findTransferStartWorld(pack);
        Vec3 sorterIntakeWorld = sortingModule.intakePoint().worldPosition();
        Vec3 toteInteriorAnchor = localToWorld(tipperAssemblyPointToWorldLocal(
                dischargeToteInteriorLocal.x,
                dischargeToteInteriorLocal.y,
                dischargeToteInteriorLocal.z,
                angle));
        Vec3 lidAnchor = localToWorld(tipperAssemblyPointToWorldLocal(
                dischargeLidLocal.x,
                dischargeLidLocal.y,
                dischargeLidLocal.z,
                angle));
        Vec3 slideEntryAnchor = localToWorld(tipperAssemblyPointToWorldLocal(
                dischargeSlideEntryLocal.x,
                dischargeSlideEntryLocal.y,
                dischargeSlideEntryLocal.z,
                angle));
        float slideMidX = Vec3.lerp(slideEntryAnchor.x, sorterIntakeWorld.x, 0.55f);
        float slideMidY = Vec3.lerp(slideEntryAnchor.y, sorterIntakeWorld.y + 0.08f, 0.45f);
        float slideMidZ = Vec3.lerp(slideEntryAnchor.z, sorterIntakeWorld.z, 0.55f);
        Vec3 startPoint = startWorld != null
                ? Vec3.copy(startWorld)
                : addClearance(
                        containedPackLocalFor(pack.getId()),
                        0.008f);
        Vec3[] path = new Vec3[] {
                startPoint,
                addClearance(toteInteriorAnchor, clearance),
                addClearance(lidAnchor, clearance),
                addClearance(slideEntryAnchor, clearance),
                addClearance(new Vec3(slideMidX, slideMidY, slideMidZ), clearance),
                addClearance(new Vec3(sorterIntakeWorld.x, sorterIntakeWorld.y + 0.06f, sorterIntakeWorld.z), clearance),
                addClearance(sorterIntakeWorld, clearance)
        };
        return samplePolyline(path, (float) progress);
    }

    private Vec3 addClearance(Vec3 localPoint, float clearance) {
        return new Vec3(localPoint.x, localPoint.y + clearance, localPoint.z);
    }

    private Vec3 samplePolyline(Vec3[] points, float progress) {
        if (points.length == 0) {
            return new Vec3();
        }
        if (points.length == 1) {
            return Vec3.copy(points[0]);
        }
        float clamped = Math.max(0f, Math.min(1f, progress));
        float totalLength = 0f;
        float[] segmentLengths = new float[points.length - 1];
        for (int i = 0; i < points.length - 1; i++) {
            float segmentLength = points[i].distanceTo(points[i + 1]);
            segmentLengths[i] = segmentLength;
            totalLength += segmentLength;
        }
        if (totalLength <= 0.0001f) {
            return Vec3.copy(points[points.length - 1]);
        }
        float targetDistance = totalLength * clamped;
        float distanceCovered = 0f;
        for (int i = 0; i < segmentLengths.length; i++) {
            float segmentLength = segmentLengths[i];
            if (targetDistance <= distanceCovered + segmentLength || i == segmentLengths.length - 1) {
                float segmentProgress = segmentLength <= 0.0001f
                        ? 1f
                        : (targetDistance - distanceCovered) / segmentLength;
                return Vec3.immutableLerp(points[i], points[i + 1], segmentProgress);
            }
            distanceCovered += segmentLength;
        }
        return Vec3.copy(points[points.length - 1]);
    }

    private boolean isObserved(String packId) {
        for (Pack pack : flowController.getObservedPacks()) {
            if (pack.getId().equals(packId)) {
                return true;
            }
        }
        return false;
    }

    private float currentTipAngle() {
        return tippedAngleRadians * tippingMachine.getTipProgress();
    }

    private Vec3 containedPackLocalFor(String packId) {
        return containedPackLayoutById.get(packId);
    }

    private void ensureAttachedToTote(RenderableObject renderable) {
        if (!toteRenderable.children.contains(renderable)) {
            objects.remove(renderable);
            toteRenderable.addChild(renderable);
        }
    }

    private void detachFromToteIfNeeded(Pack pack, RenderableObject renderable, TippingDischargeTransfer transfer) {
        if (!toteRenderable.children.contains(renderable)) {
            pack.setContainmentState(PackContainmentState.FREE);
            return;
        }
        Vec3 capturedWorld = capturePackWorldPosition(renderable);
        toteRenderable.removeChild(renderable);
        if (!objects.contains(renderable)) {
            objects.add(renderable);
        }
        renderable.transformation.xTranslation = capturedWorld.x;
        renderable.transformation.yTranslation = capturedWorld.y;
        renderable.transformation.zTranslation = capturedWorld.z;
        renderable.transformation.angleX = 0f;
        renderable.transformation.angleY = rigYaw();
        renderable.transformation.angleZ = 0f;
        pack.setContainmentState(PackContainmentState.FREE);
        if (transfer != null && transfer.getStartWorldPosition() == null) {
            transfer.setStartWorldPosition(capturedWorld);
        }
    }

    private Vec3 capturePackWorldPosition(RenderableObject packRenderable) {
        toteRenderable.transformation.setupModel();
        Vec3 out = new Vec3();
        toteRenderable.transformation.model.transformPoint(
                new Vec3(
                        packRenderable.transformation.xTranslation,
                        packRenderable.transformation.yTranslation,
                        packRenderable.transformation.zTranslation),
                out);
        return out;
    }

    private Vec3 findTransferStartWorld(Pack pack) {
        for (TippingDischargeTransfer transfer : flowController.getActiveDischarges()) {
            if (transfer.getPack() == pack) {
                return transfer.getStartWorldPosition();
            }
        }
        return null;
    }

    private float rotatedY(float localY, float localZ, float angleX) {
        return (float) ((localY * Math.cos(angleX)) - (localZ * Math.sin(angleX)));
    }

    private float rotatedZ(float localY, float localZ, float angleX) {
        return (float) ((localY * Math.sin(angleX)) + (localZ * Math.cos(angleX)));
    }

    private Mesh createFunnelSlideMesh() {
        float halfEntryWidth = slideEntryWidth * 0.5f;
        float halfExitWidth = SLIDE_EXIT_WIDTH * 0.5f;
        float yTop = SLIDE_THICKNESS * 0.5f;
        float yBottom = -SLIDE_THICKNESS * 0.5f;
        float zStart = 0f;
        float zEnd = -SLIDE_LENGTH;

        Vec4[] vertices = new Vec4[] {
                new Vec4(-halfEntryWidth, yBottom, zStart, 1f),
                new Vec4(halfEntryWidth, yBottom, zStart, 1f),
                new Vec4(halfEntryWidth, yTop, zStart, 1f),
                new Vec4(-halfEntryWidth, yTop, zStart, 1f),
                new Vec4(-halfExitWidth, yBottom, zEnd, 1f),
                new Vec4(halfExitWidth, yBottom, zEnd, 1f),
                new Vec4(halfExitWidth, yTop, zEnd, 1f),
                new Vec4(-halfExitWidth, yTop, zEnd, 1f)
        };

        int[][] triangles = new int[][] {
                {0, 1, 2}, {0, 2, 3},
                {4, 7, 6}, {4, 6, 5},
                {0, 3, 7}, {0, 7, 4},
                {1, 5, 6}, {1, 6, 2},
                {0, 4, 5}, {0, 5, 1},
                {3, 2, 6}, {3, 6, 7}
        };
        return new Mesh(vertices, triangles, "tipper_funnel_slide");
    }

    private float rigYaw() {
        return layoutSpec.yawRadians();
    }
}
