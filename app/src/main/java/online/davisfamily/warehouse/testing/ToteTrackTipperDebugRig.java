package online.davisfamily.warehouse.testing;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private final Vec3 dischargeStartLocal;
    private final Vec3 sorterIntakeLocal;
    private final Vec3 sorterOutfeedBaseLocal;
    private final float sorterIntakeX;
    private final float sorterIntakeY;
    private final float sorterIntakeZ;
    private final ConveyorRuntimeState sorterOutfeedRuntimeState;
    private final ConveyorRuntimeState tipperTrackRuntimeState;
    private final float tippedAngleRadians;
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
        tipperSlideRenderable = createBox(
                "tipper_slide",
                TIPPER_LENGTH,
                0.03f,
                0.42f,
                0xFF8C8A7A);
        tipperSlideRenderable.transformation.xTranslation = 0f;
        tipperSlideRenderable.transformation.yTranslation = 0.03f;
        tipperSlideRenderable.transformation.zTranslation = -0.24f;

        tipperAssemblyRenderable.addChild(tipperTrackRenderable);
        tipperAssemblyRenderable.addChild(tipperSlideRenderable);

        tippedAngleRadians = TIPPER_TIPPED_ANGLE_RADIANS;
        dischargeStartLocal = new Vec3(
                TIPPER_STOP_DISTANCE + 0.30f,
                tipperAssemblyLocalOrigin.y + 0.36f,
                -0.18f);
        sorterIntakeLocal = tipperAssemblyPointToWorldLocal(
                tipperSlideRenderable.transformation.xTranslation + (TIPPER_LENGTH * 0.5f),
                tipperSlideRenderable.transformation.yTranslation,
                tipperSlideRenderable.transformation.zTranslation - (0.42f * 0.5f),
                tippedAngleRadians);
        Vec3 slideEndAtTip = localToWorld(sorterIntakeLocal);
        sorterIntakeX = slideEndAtTip.x;
        sorterIntakeY = slideEndAtTip.y;
        sorterIntakeZ = slideEndAtTip.z;

        sorterRenderable = createBox(
                "sorting_machine",
                1.00f,
                0.28f,
                0.82f,
                0xFF5B6E7A);
        Vec3 sorterLocalOrigin = new Vec3(
                sorterIntakeLocal.x + 0.22f,
                sorterIntakeLocal.y - 0.24f,
                sorterIntakeLocal.z - 0.24f);
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
                sorterLocalOrigin.x + 1.25f,
                sorterLocalOrigin.y - 0.10f,
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
        hideAllPacks();
        positionActiveDischarges();
        positionSorterQueue();
        positionSorterOutfeed();
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
        float targetAngle = tippedAngleRadians * tippingMachine.getTipProgress();
        tipperAssemblyRenderable.transformation.angleX = targetAngle;
    }

    private void ensurePackRenderablesExist() {
        for (Pack pack : flowController.getObservedPacks()) {
            packRenderablesById.computeIfAbsent(pack.getId(), ignored -> {
                RenderableObject renderable = createPackRenderable(pack);
                objects.add(renderable);
                inspectionRegistry.register(renderable, () -> List.of(
                        "Type: Pack",
                        "Id: " + pack.getId(),
                        "Correlation: " + pack.getCorrelationId(),
                        "State: " + pack.getState()));
                return renderable;
            });
        }
    }

    private void hideAllPacks() {
        for (RenderableObject renderable : packRenderablesById.values()) {
            renderable.transformation.xTranslation = -50f;
            renderable.transformation.yTranslation = -50f;
            renderable.transformation.zTranslation = -50f;
            renderable.transformation.angleX = 0f;
            renderable.transformation.angleY = 0f;
            renderable.transformation.angleZ = 0f;
        }
    }

    private void positionActiveDischarges() {
        Vec3 start = localToWorld(dischargeStartLocal);
        Vec3 end = localToWorld(sorterIntakeLocal.x, sorterIntakeLocal.y + 0.03f, sorterIntakeLocal.z);
        for (TippingDischargeTransfer transfer : flowController.getActiveDischarges()) {
            double progress = transfer.getProgress();
            float x = (float) (start.x + ((end.x - start.x) * progress));
            float y = (float) (start.y + ((end.y - start.y) * progress) + (Math.sin(progress * Math.PI) * 0.10f));
            float z = (float) (start.z + ((end.z - start.z) * progress));
            RenderableObject renderable = packRenderablesById.get(transfer.getPack().getId());
            if (renderable == null) {
                continue;
            }
            renderable.transformation.xTranslation = x;
            renderable.transformation.yTranslation = y;
            renderable.transformation.zTranslation = z;
            renderable.transformation.angleX = transfer.getSpinAngleX();
            renderable.transformation.angleY = transfer.getSpinAngleY();
            renderable.transformation.angleZ = transfer.getSpinAngleZ();
        }
    }

    private void positionSorterQueue() {
        int index = 0;
        for (Pack pack : sortingMachine.getQueuedPacks()) {
            RenderableObject renderable = packRenderablesById.get(pack.getId());
            if (renderable == null) {
                continue;
            }
            Vec3 queueWorld = localToWorld(
                    sorterIntakeLocal.x - (index * 0.12f),
                    sorterIntakeLocal.y - 0.04f,
                    sorterIntakeLocal.z + 0.04f);
            renderable.transformation.xTranslation = queueWorld.x;
            renderable.transformation.yTranslation = queueWorld.y;
            renderable.transformation.zTranslation = queueWorld.z;
            renderable.transformation.angleY = RIG_YAW_RADIANS + (float) (Math.PI / 2.0);
            index++;
        }
    }

    private void positionSorterOutfeed() {
        for (var entry : sorterOutfeedConveyor.getLaneEntries()) {
            RenderableObject renderable = packRenderablesById.get(entry.pack().getId());
            if (renderable == null) {
                continue;
            }
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

    private RenderableObject createPackRenderable(Pack pack) {
        PackDimensions dimensions = pack.getDimensions();
        return RenderableObject.create(
                pack.getId(),
                tr,
                RollerMeshFactory.createBoxRollerMesh(
                        dimensions.length(),
                        dimensions.height(),
                        dimensions.width()),
                new ObjectTransformation(0f, 0f, 0f, -50f, -50f, -50f, new Mat4()),
                new OneColourStrategyImpl(colourForCorrelation(pack.getCorrelationId())),
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

    private float rotatedY(float localY, float localZ, float angleX) {
        return (float) ((localY * Math.cos(angleX)) - (localZ * Math.sin(angleX)));
    }

    private float rotatedZ(float localY, float localZ, float angleX) {
        return (float) ((localY * Math.sin(angleX)) + (localZ * Math.cos(angleX)));
    }
}
