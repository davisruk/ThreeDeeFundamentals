package online.davisfamily.warehouse.sim.totebag.assembly;

import java.util.List;

import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.path.LinearSegment3;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.rendering.TriangleRenderer;
import online.davisfamily.threedee.rendering.appearance.OneColourStrategyImpl;
import online.davisfamily.warehouse.rendering.model.tote.ToteEnvelope;
import online.davisfamily.warehouse.rendering.model.tote.ToteGeometry;
import online.davisfamily.warehouse.rendering.model.tracks.RouteTrackFactory;
import online.davisfamily.warehouse.rendering.model.tracks.TrackAppearance;
import online.davisfamily.warehouse.rendering.model.tracks.TrackSpec;
import online.davisfamily.warehouse.rendering.model.tracks.WarehouseRouteBuilder;
import online.davisfamily.warehouse.sim.totebag.layout.TipperEntryLayoutSpec;

public class TipperTrackSectionInstaller {
    private static final float TRACK_Z = 0f;
    private static final float TRACK_LENGTH = 6.0f;
    private static final float TIPPER_LENGTH = 1.25f;
    private static final float TIPPER_START_X = (TRACK_LENGTH - TIPPER_LENGTH) * 0.5f;
    private static final float TIPPER_ASSEMBLY_CENTER_X = TIPPER_START_X + (TIPPER_LENGTH * 0.5f);
    private static final float TIPPER_STOP_DISTANCE = TIPPER_LENGTH * 0.5f;
    private static final float TIPPER_TIPPED_ANGLE_RADIANS = -1.02f;

    public TipperTrackSection install(
            TriangleRenderer tr,
            List<RenderableObject> objects,
            TipperEntryLayoutSpec layoutSpec) {
        if (tr == null || objects == null || layoutSpec == null) {
            throw new IllegalArgumentException("Tipper track install inputs must not be null");
        }

        ToteGeometry toteGeometry = new ToteGeometry();
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
        Vec3 infeedStart = localToWorld(layoutSpec, 0f, 0f, TRACK_Z);
        Vec3 infeedEnd = localToWorld(layoutSpec, TIPPER_START_X, 0f, TRACK_Z);
        Vec3 tipperEnd = localToWorld(layoutSpec, TIPPER_START_X + TIPPER_LENGTH, 0f, TRACK_Z);
        Vec3 exitEnd = localToWorld(layoutSpec, TRACK_LENGTH, 0f, TRACK_Z);
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

        float tipperTrackOverallWidth = tipperTrackSpec.getOverallWidth();
        Vec3 tipperAssemblyLocalOrigin = new Vec3(
                TIPPER_ASSEMBLY_CENTER_X,
                0.02f,
                TRACK_Z - (tipperTrackOverallWidth * 0.5f));

        return new TipperTrackSection(
                layoutSpec,
                infeedSegment,
                tipperSegment,
                exitSegment,
                tipperTrackSpec,
                trackAppearance,
                TIPPER_LENGTH,
                TIPPER_STOP_DISTANCE,
                TIPPER_TIPPED_ANGLE_RADIANS,
                toteTrackSpec.getLoadSurfaceHeight() + 0.02f,
                tipperTrackOverallWidth,
                tipperAssemblyLocalOrigin);
    }

    private Vec3 localToWorld(TipperEntryLayoutSpec layoutSpec, float localX, float localY, float localZ) {
        return localToWorld(layoutSpec, new Vec3(localX, localY, localZ));
    }

    private Vec3 localToWorld(TipperEntryLayoutSpec layoutSpec, Vec3 localPoint) {
        Vec3 rotated = Vec3.rotateY(localPoint, layoutSpec.yawRadians());
        rotated.mutableAdd(layoutSpec.origin());
        return rotated;
    }
}
