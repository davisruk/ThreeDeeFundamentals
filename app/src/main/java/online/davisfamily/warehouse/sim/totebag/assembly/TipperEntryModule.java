package online.davisfamily.warehouse.sim.totebag.assembly;

import java.util.List;
import java.util.Map;

import online.davisfamily.threedee.behaviour.routing.RouteFollower;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.debug.SelectionInspectionRegistry;
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
import online.davisfamily.warehouse.rendering.model.tracks.RouteTrackFactory;
import online.davisfamily.warehouse.rendering.model.tracks.TrackAppearance;
import online.davisfamily.warehouse.rendering.model.tracks.TrackSpec;
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
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

public class TipperEntryModule implements PackHandoffPointProvider {
    private static final float TRACK_Z = 0f;
    private static final float TRACK_LENGTH = 6.0f;
    private static final float TIPPER_LENGTH = 1.25f;
    private static final float TIPPER_START_X = (TRACK_LENGTH - TIPPER_LENGTH) * 0.5f;
    private static final float TIPPER_STOP_DISTANCE = TIPPER_START_X + (TIPPER_LENGTH * 0.5f);
    private static final float TIPPER_TIPPED_ANGLE_RADIANS = -1.02f;
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
    private final float slideEntryWidth;
    private final ConveyorRuntimeState tipperTrackRuntimeState;
    private final float toteInteriorFloorLocalY;
    private final Map<String, Vec3> containedPackLayoutById;
    private final TipperToSorterDischargeSeam dischargeSeam;
    private final TipperEntryPackVisuals packVisuals;
    private final TipperAssemblyFactory tipperAssemblyFactory;

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
        Vec3 tipperAssemblyLocalOrigin = new Vec3(
                TIPPER_STOP_DISTANCE,
                0.02f,
                TRACK_Z - (tipperTrackOverallWidth * 0.5f));
        Vec3 tipperAssemblyWorld = localToWorld(tipperAssemblyLocalOrigin);
        tipperAssemblyFactory = new TipperAssemblyFactory();
        TipperAssemblyFactory.BuildResult assemblyBuild = tipperAssemblyFactory.build(
                tr,
                tipperTrackSpec,
                trackAppearance,
                tipperTrackRuntimeState,
                tipperAssemblyWorld,
                rigYaw(),
                TIPPER_LENGTH,
                tipperTrackOverallWidth);
        tipperAssemblyRenderable = assemblyBuild.assemblyRenderable();
        slideEntryWidth = assemblyBuild.slideEntryWidth();

        Vec3 dischargeToteInteriorLocal = new Vec3(0f, toteInteriorFloorLocalY + 0.06f, 0.10f);
        Vec3 dischargeLidLocal = new Vec3(0f, 0.24f, -0.08f);
        Vec3 dischargeSlideEntryLocal = assemblyBuild.dischargeSlideEntryLocal();

        objects.add(toteRenderable);
        objects.add(tipperAssemblyRenderable);

        tipperModule = new TipperModule(
                tote,
                toteLoadPlan,
                tippingMachine,
                toteRenderable,
                tipperAssemblyRenderable,
                layoutSpec.origin(),
                tipperAssemblyLocalOrigin,
                dischargeToteInteriorLocal,
                dischargeLidLocal,
                dischargeSlideEntryLocal,
                TIPPER_TIPPED_ANGLE_RADIANS,
                rigYaw());
        sortingModule = new SortingModule(
                tr,
                sortingMachine,
                layoutSpec.origin(),
                rigYaw(),
                tipperModule.sorterIntakeMountLocalPoint());
        objects.add(sortingModule.getRenderable());
        dischargeSeam = new TipperToSorterDischargeSeam();
        packVisuals = new TipperEntryPackVisuals(
                tr,
                objects,
                inspectionRegistry,
                toteRenderable,
                toteLoadPlan,
                containedPackLayoutById,
                rigYaw(),
                dischargeSeam);

        registerInspectableObjects();
    }

    public void syncVisuals() {
        tipperTrackRuntimeState.setRunning(!flowController.isToteCaptured());
        tipperModule.syncVisuals(flowController.getVisualTipProgress());
        packVisuals.sync(flowController, tipperModule, sortingModule);
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
        return packVisuals.getPackRenderable(packId);
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

    private Vec3 localToWorld(float localX, float localY, float localZ) {
        return localToWorld(new Vec3(localX, localY, localZ));
    }

    private Vec3 localToWorld(Vec3 localPoint) {
        Vec3 rotated = Vec3.rotateY(localPoint, rigYaw());
        rotated.mutableAdd(layoutSpec.origin());
        return rotated;
    }

    private float rigYaw() {
        return layoutSpec.yawRadians();
    }
}
