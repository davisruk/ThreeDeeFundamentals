package online.davisfamily.warehouse.testing;

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
import online.davisfamily.warehouse.rendering.model.tracks.StraightConveyorFactory;
import online.davisfamily.warehouse.rendering.model.tracks.StraightConveyorFactory.ConveyorVisualSpeed;
import online.davisfamily.warehouse.rendering.model.tracks.StraightConveyorFactory.StraightConveyorSpec;
import online.davisfamily.warehouse.rendering.model.tracks.TrackAppearance;
import online.davisfamily.warehouse.rendering.model.tracks.TrackSpec;
import online.davisfamily.warehouse.rendering.model.tracks.WarehouseSegmentMetadata;
import online.davisfamily.warehouse.rendering.model.tracks.WarehouseRouteBuilder;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.totebag.control.ToteTrackTipperFlowController;
import online.davisfamily.warehouse.sim.totebag.conveyor.ConveyorOccupancyModel;
import online.davisfamily.warehouse.sim.totebag.conveyor.PdcConveyor;
import online.davisfamily.warehouse.sim.totebag.machine.SortingMachine;
import online.davisfamily.warehouse.sim.totebag.machine.SortingMachineState;
import online.davisfamily.warehouse.sim.totebag.machine.TippingMachine;
import online.davisfamily.warehouse.sim.totebag.machine.TippingMachineState;
import online.davisfamily.warehouse.sim.totebag.pack.Pack;
import online.davisfamily.warehouse.sim.totebag.pack.Pack.PackContainmentState;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;
import online.davisfamily.warehouse.sim.totebag.transfer.TippingDischargeTransfer;

public class ToteTrackTipperDebugRig {
    private static final Vec3 RIG_ORIGIN = new Vec3(0f, 0f, 0f);
    private static final float RIG_YAW_RADIANS = 0f;
    private static final float TRACK_Z = 0f;
    private static final float TRACK_LENGTH = 6.0f;
    private static final float TIPPER_LENGTH = 1.25f;
    private static final float TIPPER_START_X = (TRACK_LENGTH - TIPPER_LENGTH) * 0.5f;
    private static final float TIPPER_STOP_DISTANCE = TIPPER_START_X + (TIPPER_LENGTH * 0.5f);
    private static final float SORTER_Z = -1.24f;
    private static final float OUTFEED_Z = -1.24f;
    private static final float TIPPER_TIPPED_ANGLE_RADIANS = -1.02f;
    private static final float SORTER_OUTFEED_TOP_Y_OFFSET = 0.10f;
    private static final float SLIDE_ENTRY_WIDTH = 0.90f;
    private static final float SLIDE_EXIT_WIDTH = 0.30f;
    private static final float SLIDE_LENGTH = 1.20f;
    private static final float SLIDE_THICKNESS = 0.03f;
    private static final float SORTER_INTAKE_CLEARANCE = 0.08f;

    private final List<RenderableObject> objects;
    private final SelectionInspectionRegistry inspectionRegistry;
    private final TriangleRenderer tr;

    private final Tote tote;
    private final ToteLoadPlan toteLoadPlan;
    private final TippingMachine tippingMachine;
    private final SortingMachine sortingMachine;
    private final PdcConveyor sorterOutfeedConveyor;
    private final ToteTrackTipperFlowController flowController;

    private final RenderableObject toteRenderable;
    private final RenderableObject tipperAssemblyRenderable;
    private final RenderableObject tipperTrackRenderable;
    private final RenderableObject tipperSlideRenderable;
    private final RenderableObject sorterRenderable;
    private final RenderableObject sorterOutfeedRenderable;
    private final Vec3 tipperAssemblyLocalOrigin;
    private final Vec3 dischargeToteInteriorLocal;
    private final Vec3 dischargeLidLocal;
    private final Vec3 dischargeSlideEntryLocal;
    private final Vec3 sorterIntakeLocal;
    private final Vec3 sorterOutfeedBaseLocal;
    private final float slideEntryWidth;
    private final float sorterIntakeX;
    private final float sorterIntakeY;
    private final float sorterIntakeZ;
    private final ConveyorRuntimeState sorterOutfeedRuntimeState;
    private final ConveyorRuntimeState tipperTrackRuntimeState;
    private final float tippedAngleRadians;
    private final float toteInteriorHalfWidth;
    private final float toteInteriorHalfDepth;
    private final float toteInteriorFloorLocalY;
    private final Map<String, PackPlan> packPlansById = new LinkedHashMap<>();
    private final Map<String, RenderableObject> packRenderablesById = new LinkedHashMap<>();

    public ToteTrackTipperDebugRig(
            TriangleRenderer tr,
            SimulationWorld sim,
            List<RenderableObject> objects,
            SelectionInspectionRegistry inspectionRegistry) {
        this.tr = tr;
        this.objects = objects;
        this.inspectionRegistry = inspectionRegistry;

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
        tippingMachine = new TippingMachine("tipper", 0.45d, 0.18d, 0.35d);
        sortingMachine = new SortingMachine("sorter", 0.22d);
        sorterOutfeedConveyor = new PdcConveyor(
                "sorter_outfeed",
                new ConveyorOccupancyModel(2.2f, 0.04f, 0f),
                0.85f);
        flowController = new ToteTrackTipperFlowController(
                tote,
                toteLoadPlan,
                tipperSegment,
                TIPPER_LENGTH * 0.5f,
                TIPPER_TIPPED_ANGLE_RADIANS,
                tippingMachine,
                sortingMachine,
                sorterOutfeedConveyor,
                0.55d);

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
        tipperAssemblyRenderable.transformation.angleY = RIG_YAW_RADIANS;

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
        sorterIntakeLocal = tipperAssemblyPointToWorldLocal(
                0f,
                tipperSlideRenderable.transformation.yTranslation - 0.04f,
                tipperSlideRenderable.transformation.zTranslation - SLIDE_LENGTH - SORTER_INTAKE_CLEARANCE,
                tippedAngleRadians);
        Vec3 slideEndAtTip = localToWorld(sorterIntakeLocal);
        sorterIntakeX = slideEndAtTip.x;
        sorterIntakeY = slideEndAtTip.y;
        sorterIntakeZ = slideEndAtTip.z;

        sorterRenderable = createBox(
                "sorting_machine",
                0.56f,
                0.24f,
                0.34f,
                0xFF5B6E7A);
        Vec3 sorterLocalOrigin = new Vec3(
                sorterIntakeLocal.x,
                sorterIntakeLocal.y - 0.12f,
                sorterIntakeLocal.z - 0.12f);
        Vec3 sorterWorldOrigin = localToWorld(sorterLocalOrigin);
        sorterRenderable.transformation.xTranslation = sorterWorldOrigin.x;
        sorterRenderable.transformation.yTranslation = sorterWorldOrigin.y;
        sorterRenderable.transformation.zTranslation = sorterWorldOrigin.z;
        sorterRenderable.transformation.angleY = RIG_YAW_RADIANS;

        sorterOutfeedRuntimeState = new ConveyorRuntimeState();
        sorterOutfeedRuntimeState.setRunning(true);
        sorterOutfeedRenderable = StraightConveyorFactory.create(
                "sorter_outfeed_visual",
                tr,
                new StraightConveyorSpec(
                        2.4f,
                        0.30f,
                        0.05f,
                        0.008f,
                        0.10f,
                        0.08f,
                        0.004f,
                        ConveyorVisualSpeed.fixed(0.85d)),
                sorterOutfeedRuntimeState,
                trackAppearance);
        sorterOutfeedBaseLocal = new Vec3(
                sorterLocalOrigin.x + 0.82f,
                sorterLocalOrigin.y - 0.08f,
                OUTFEED_Z);
        Vec3 sorterOutfeedWorld = localToWorld(sorterOutfeedBaseLocal);
        sorterOutfeedRenderable.transformation.xTranslation = sorterOutfeedWorld.x;
        sorterOutfeedRenderable.transformation.yTranslation = sorterOutfeedWorld.y;
        sorterOutfeedRenderable.transformation.zTranslation = sorterOutfeedWorld.z;
        sorterOutfeedRenderable.transformation.angleY = RIG_YAW_RADIANS;

        objects.add(toteRenderable);
        objects.add(tipperAssemblyRenderable);
        objects.add(sorterRenderable);
        objects.add(sorterOutfeedRenderable);

        registerInspectableObjects();
    }

    public void syncVisuals() {
        sorterOutfeedRuntimeState.setRunning(
                !sorterOutfeedConveyor.getLaneEntries().isEmpty() || !flowController.getActiveDischarges().isEmpty());
        tipperTrackRuntimeState.setRunning(!flowController.isToteCaptured());
        syncTipperVisuals();
        ensurePackRenderablesExist();
        hideDetachedPacks();
        Set<String> placedPackIds = new HashSet<>();
        positionRemainingPacksInTote(placedPackIds);
        positionActiveDischarges(placedPackIds);
        positionSorterQueue(placedPackIds);
        positionSorterOutfeed(placedPackIds);
    }

    private void registerInspectableObjects() {
        inspectionRegistry.register(toteRenderable, () -> List.of(
                "Type: Tote",
                "Id: " + tote.getId(),
                "Motion: " + tote.getInteractionMode(),
                "Distance: " + (tote.getLastSnapshot() == null
                        ? "None"
                        : String.format("%.3f", tote.getLastSnapshot().distanceAlongSegment()))));

        inspectionRegistry.register(tipperAssemblyRenderable, () -> List.of(
                "Type: Tipper",
                "State: " + tippingMachine.getState(),
                "Captured tote: " + flowController.isToteCaptured(),
                "Remaining packs: " + tippingMachine.getRemainingPackCount(),
                "Active discharges: " + flowController.getActiveDischarges().size()));

        inspectionRegistry.register(sorterRenderable, () -> List.of(
                "Type: Sorter",
                "State: " + sortingMachine.getState(),
                "Queued packs: " + sortingMachine.getQueuedPacks().size(),
                "Outfeed packs: " + sorterOutfeedConveyor.getLaneEntries().size()));

        inspectionRegistry.register(sorterOutfeedRenderable, () -> List.of(
                "Type: Sorter outfeed",
                "Running: " + sorterOutfeedRuntimeState.isRunning(),
                "Lane packs: " + sorterOutfeedConveyor.getLaneEntries().size(),
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
            renderable.transformation.angleY = RIG_YAW_RADIANS;
            renderable.transformation.angleZ = 0f;
            placedPackIds.add(transfer.getPack().getId());
        }
    }

    private void positionRemainingPacksInTote(Set<String> placedPackIds) {
        float angle = currentTipAngle();
        for (PackPlan plan : toteLoadPlan.getPackPlans()) {
            if (isObserved(plan.packId())) {
                continue;
            }
            RenderableObject renderable = packRenderablesById.get(plan.packId());
            if (renderable == null) {
                continue;
            }
            ensureAttachedToTote(renderable);
            float localX = dischargeStartXFor(plan.packId());
            float localZ = dischargeStartZFor(plan.packId());
            Vec3 local = packInsideToteLocal(localX, localZ, plan.dimensions().height() * 0.5f, angle);
            renderable.transformation.xTranslation = local.x;
            renderable.transformation.yTranslation = local.y;
            renderable.transformation.zTranslation = local.z;
            renderable.transformation.angleX = 0f;
            renderable.transformation.angleY = 0f;
            renderable.transformation.angleZ = 0f;
            placedPackIds.add(plan.packId());
        }
    }

    private void positionSorterQueue(Set<String> placedPackIds) {
        int index = 0;
        for (Pack pack : sortingMachine.getQueuedPacks()) {
            RenderableObject renderable = packRenderablesById.get(pack.getId());
            if (renderable == null) {
                continue;
            }
            detachFromToteIfNeeded(pack, renderable, null);
            Vec3 queueWorld = localToWorld(
                    sorterIntakeLocal.x,
                    sorterIntakeLocal.y - 0.015f,
                    sorterIntakeLocal.z + (index * 0.08f));
            renderable.transformation.xTranslation = queueWorld.x;
            renderable.transformation.yTranslation = queueWorld.y;
            renderable.transformation.zTranslation = queueWorld.z;
            renderable.transformation.angleY = RIG_YAW_RADIANS;
            index++;
            placedPackIds.add(pack.getId());
        }
    }

    private void positionSorterOutfeed(Set<String> placedPackIds) {
        return;
/*
        for (var entry : sorterOutfeedConveyor.getLaneEntries()) {
            RenderableObject renderable = packRenderablesById.get(entry.pack().getId());
            if (renderable == null) {
                continue;
            }
            detachFromToteIfNeeded(entry.pack(), renderable, null);
            Vec3 outfeedWorld = localToWorld(
                    sorterOutfeedBaseLocal.x + 0.38f + entry.frontDistance()
                            - (entry.pack().getDimensions().length() * 0.5f),
                    sorterOutfeedBaseLocal.y
                            + SORTER_OUTFEED_TOP_Y_OFFSET
                            + (entry.pack().getDimensions().height() * 0.5f)
                            + 0.002f,
                    sorterOutfeedBaseLocal.z);
            renderable.transformation.xTranslation = outfeedWorld.x;
            renderable.transformation.yTranslation = outfeedWorld.y;
            renderable.transformation.zTranslation = outfeedWorld.z;
            renderable.transformation.angleY = RIG_YAW_RADIANS;
            placedPackIds.add(entry.pack().getId());
        }
*/
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
        return RenderableObject.create(
                id,
                tr,
                RollerMeshFactory.createBoxRollerMesh(length, height, width),
                new ObjectTransformation(0f, 0f, 0f, 0f, 0f, 0f, new Mat4()),
                new OneColourStrategyImpl(colour),
                true);
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
        Vec3 rotated = Vec3.rotateY(localPoint, RIG_YAW_RADIANS);
        rotated.mutableAdd(RIG_ORIGIN);
        return rotated;
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
        Vec3 toteInteriorAnchor = tipperAssemblyPointToWorldLocal(
                dischargeToteInteriorLocal.x,
                dischargeToteInteriorLocal.y,
                dischargeToteInteriorLocal.z,
                angle);
        Vec3 lidAnchor = tipperAssemblyPointToWorldLocal(
                dischargeLidLocal.x,
                dischargeLidLocal.y,
                dischargeLidLocal.z,
                angle);
        Vec3 slideEntryAnchor = tipperAssemblyPointToWorldLocal(
                dischargeSlideEntryLocal.x,
                dischargeSlideEntryLocal.y,
                dischargeSlideEntryLocal.z,
                angle);
        float slideMidX = Vec3.lerp(slideEntryAnchor.x, sorterIntakeLocal.x, 0.55f);
        float slideMidY = Vec3.lerp(slideEntryAnchor.y, sorterIntakeLocal.y + 0.08f, 0.45f);
        float slideMidZ = Vec3.lerp(slideEntryAnchor.z, sorterIntakeLocal.z, 0.55f);
        Vec3 startPoint = startWorld != null
                ? Vec3.copy(startWorld)
                : addClearance(
                        packInsideToteLocal(
                                dischargeStartXFor(pack.getId()),
                                dischargeStartZFor(pack.getId()),
                                pack.getDimensions().height() * 0.5f,
                                angle),
                        0.008f);
        Vec3[] path = new Vec3[] {
                startPoint,
                addClearance(toteInteriorAnchor, clearance),
                addClearance(lidAnchor, clearance),
                addClearance(slideEntryAnchor, clearance),
                addClearance(new Vec3(slideMidX, slideMidY, slideMidZ), clearance),
                addClearance(new Vec3(sorterIntakeLocal.x, sorterIntakeLocal.y + 0.06f, sorterIntakeLocal.z), clearance),
                addClearance(sorterIntakeLocal, clearance)
        };
        return samplePolyline(path, (float) progress);
    }

    private float dischargeStartXFor(String packId) {
        int hash = Math.abs(packId.hashCode());
        float t = (hash & 0xFF) / 255f;
        return Vec3.lerp(-toteInteriorHalfWidth * 0.55f, toteInteriorHalfWidth * 0.55f, t);
    }

    private float dischargeStartZFor(String packId) {
        int hash = Math.abs(packId.hashCode() >>> 8);
        float t = (hash & 0xFF) / 255f;
        return Vec3.lerp(toteInteriorHalfDepth * 0.18f, toteInteriorHalfDepth * 0.48f, t);
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

    private Vec3 packInsideToteLocal(float localX, float localZ, float halfHeight, float angleX) {
        return new Vec3(
                localX,
                toteInteriorFloorLocalY + halfHeight,
                localZ);
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
        renderable.transformation.angleY = RIG_YAW_RADIANS;
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
}
