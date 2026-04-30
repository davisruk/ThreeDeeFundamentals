package online.davisfamily.warehouse.sim.totebag.assembly;

import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.matrices.Vec4;
import online.davisfamily.threedee.model.Mesh;
import online.davisfamily.threedee.path.LinearSegment3;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.rendering.TriangleRenderer;
import online.davisfamily.threedee.rendering.appearance.OneColourStrategyImpl;
import online.davisfamily.warehouse.rendering.model.tracks.ConveyorRuntimeState;
import online.davisfamily.warehouse.rendering.model.tracks.RenderableTrackFactory;
import online.davisfamily.warehouse.rendering.model.tracks.TrackAppearance;
import online.davisfamily.warehouse.rendering.model.tracks.TrackSpec;
import online.davisfamily.warehouse.rendering.model.tracks.WarehouseSegmentMetadata;

public class TipperAssemblyFactory {
    private static final float SLIDE_EXIT_WIDTH = 0.30f;
    private static final float SLIDE_LENGTH = 1.20f;
    private static final float SLIDE_THICKNESS = 0.03f;

    public record BuildResult(
            RenderableObject assemblyRenderable,
            float slideEntryWidth,
            Vec3 dischargeSlideEntryLocal) {
    }

    public BuildResult build(
            TriangleRenderer tr,
            TrackSpec tipperTrackSpec,
            TrackAppearance trackAppearance,
            ConveyorRuntimeState tipperTrackRuntimeState,
            Vec3 tipperAssemblyWorld,
            float rigYaw,
            float tipperLength,
            float tipperTrackOverallWidth) {
        RenderableObject tipperAssemblyRenderable = createAnchor(tr, "tipper_assembly");
        tipperAssemblyRenderable.transformation.xTranslation = tipperAssemblyWorld.x;
        tipperAssemblyRenderable.transformation.yTranslation = tipperAssemblyWorld.y;
        tipperAssemblyRenderable.transformation.zTranslation = tipperAssemblyWorld.z;
        tipperAssemblyRenderable.transformation.angleY = rigYaw;

        float slideEntryWidth = tipperLength;
        RenderableObject tipperTrackRenderable = createLocalTipperTrack(
                tr,
                tipperTrackSpec,
                trackAppearance,
                tipperTrackRuntimeState,
                tipperLength);
        tipperTrackRenderable.transformation.zTranslation = tipperTrackOverallWidth * 0.5f;

        RenderableObject tipperSlideRenderable = createFunnelSlideRenderable(tr, "tipper_slide", 0xFFFF00FF, slideEntryWidth);
        tipperSlideRenderable.transformation.xTranslation = 0f;
        tipperSlideRenderable.transformation.yTranslation = 0.04f;
        tipperSlideRenderable.transformation.zTranslation = -0.02f;
        tipperSlideRenderable.addChild(createSlideGuide(
                tr,
                "tipper_slide_left_guide",
                -(slideEntryWidth * 0.5f) + 0.015f,
                slideEntryWidth,
                SLIDE_EXIT_WIDTH));
        tipperSlideRenderable.addChild(createSlideGuide(
                tr,
                "tipper_slide_right_guide",
                (slideEntryWidth * 0.5f) - 0.015f,
                slideEntryWidth,
                SLIDE_EXIT_WIDTH));

        tipperAssemblyRenderable.addChild(tipperTrackRenderable);
        tipperAssemblyRenderable.addChild(tipperSlideRenderable);

        Vec3 dischargeSlideEntryLocal = new Vec3(
                0f,
                tipperSlideRenderable.transformation.yTranslation + 0.01f,
                tipperSlideRenderable.transformation.zTranslation - 0.02f);

        return new BuildResult(tipperAssemblyRenderable, slideEntryWidth, dischargeSlideEntryLocal);
    }

    private RenderableObject createLocalTipperTrack(
            TriangleRenderer tr,
            TrackSpec tipperTrackSpec,
            TrackAppearance trackAppearance,
            ConveyorRuntimeState rollerRuntimeState,
            float tipperLength) {
        RouteSegment localTipperSegment = new RouteSegment(
                "local_tipper_track",
                new LinearSegment3(
                        new Vec3(-tipperLength * 0.5f, 0f, 0f),
                        new Vec3(tipperLength * 0.5f, 0f, 0f),
                        false));
        RenderableObject renderable = RenderableTrackFactory.createRenderableTrack(
                tr,
                localTipperSegment,
                new WarehouseSegmentMetadata(),
                tipperTrackSpec,
                trackAppearance,
                rollerRuntimeState);
        renderable.transformation.yTranslation = 0f;
        return renderable;
    }

    private RenderableObject createFunnelSlideRenderable(
            TriangleRenderer tr,
            String id,
            int colour,
            float slideEntryWidth) {
        return RenderableObject.create(
                id,
                tr,
                createFunnelSlideMesh(slideEntryWidth),
                new ObjectTransformation(0f, 0f, 0f, 0f, 0f, 0f, new Mat4()),
                new OneColourStrategyImpl(colour),
                true);
    }

    private RenderableObject createSlideGuide(
            TriangleRenderer tr,
            String id,
            float xOffset,
            float entryWidth,
            float exitWidth) {
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

    private RenderableObject createAnchor(TriangleRenderer tr, String id) {
        return RenderableObject.create(
                id,
                tr,
                new Mesh(
                        new Vec4[] {
                                new Vec4(0f, 0f, 0f, 1f),
                                new Vec4(0f, 0f, 0f, 1f),
                                new Vec4(0f, 0f, 0f, 1f)
                        },
                        new int[][] { {0, 1, 2} },
                        "anchor"),
                new ObjectTransformation(0f, 0f, 0f, 0f, 0f, 0f, new Mat4()),
                triangleIndex -> 0,
                false);
    }

    private Mesh createFunnelSlideMesh(float slideEntryWidth) {
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
