package online.davisfamily.warehouse.sim.totebag.assembly;

import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.matrices.Vec4;
import online.davisfamily.threedee.model.Mesh;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.rendering.TriangleRenderer;
import online.davisfamily.threedee.rendering.appearance.OneColourStrategyImpl;
import online.davisfamily.warehouse.rendering.model.tracks.RollerMeshFactory;
import online.davisfamily.warehouse.sim.totebag.handoff.MachineHandoffPointId;
import online.davisfamily.warehouse.sim.totebag.handoff.PackHandoffPoint;
import online.davisfamily.warehouse.sim.totebag.machine.SortingMachine;
import online.davisfamily.warehouse.sim.totebag.pack.Pack;

public class SortingModule {
    private static final float SORTER_YAW_RADIANS = (float) Math.toRadians(180d);
    private static final float SORTER_QUEUE_VERTICAL_STEP = 0.10f;
    private static final Vec3 SORTER_OUTFEED_ANCHOR_LOCAL = new Vec3(0.08f, -0.22f, 0.00f);
    private static final Vec3 SORTER_HOPPER_MOUTH_LOCAL = new Vec3(0.00f, 0.24f, 0.00f);
    private static final int SORTER_BODY_COLOUR = 0xFF5B6E7A;
    private static final int SORTER_FRAME_COLOUR = 0xFF39454D;
    private static final int SORTER_PANEL_COLOUR = 0xFF8EA1AB;
    private static final int SORTER_INTAKE_COLOUR = 0xFF6F7F87;
    private static final int SORTER_OUTFEED_COLOUR = 0xFF444F55;
    private static final float SORTER_HOPPER_WALL_THICKNESS = 0.020f;

    private final SortingMachine sortingMachine;
    private final RenderableObject renderable;
    private final float rigYaw;

    public SortingModule(
            TriangleRenderer tr,
            SortingMachine sortingMachine,
            Vec3 layoutOrigin,
            float rigYaw,
            Vec3 intakeLocalPoint) {
        if (tr == null
                || sortingMachine == null
                || layoutOrigin == null
                || intakeLocalPoint == null) {
            throw new IllegalArgumentException("SortingModule inputs must not be null");
        }
        this.sortingMachine = sortingMachine;
        this.rigYaw = rigYaw;

        Vec3 sorterLocalOrigin = new Vec3(
                intakeLocalPoint.x,
                intakeLocalPoint.y - 0.12f,
                intakeLocalPoint.z - 0.12f);
        renderable = createSorterRenderable(tr, "sorting_machine");
        Vec3 sorterWorldOrigin = rigLocalToWorld(layoutOrigin, sorterLocalOrigin, rigYaw);
        renderable.transformation.xTranslation = sorterWorldOrigin.x;
        renderable.transformation.yTranslation = sorterWorldOrigin.y;
        renderable.transformation.zTranslation = sorterWorldOrigin.z;
        renderable.transformation.angleY = rigYaw + SORTER_YAW_RADIANS;
    }

    public SortingMachine getSortingMachine() {
        return sortingMachine;
    }

    public RenderableObject getRenderable() {
        return renderable;
    }

    public PackHandoffPoint intakePoint() {
        return new PackHandoffPoint(
                MachineHandoffPointId.SORTER_PACK_INTAKE.name().toLowerCase(),
                localPointToWorld(SORTER_HOPPER_MOUTH_LOCAL),
                renderable.transformation.angleY);
    }

    public PackHandoffPoint outfeedPoint() {
        return new PackHandoffPoint(
                MachineHandoffPointId.SORTER_PACK_OUTFEED.name().toLowerCase(),
                localPointToWorld(SORTER_OUTFEED_ANCHOR_LOCAL),
                rigYaw + SORTER_YAW_RADIANS + (float) Math.PI);
    }

    public void syncQueuedPackVisuals(
            Map<String, RenderableObject> packRenderablesById,
            Set<String> placedPackIds,
            BiConsumer<Pack, RenderableObject> detachFromTote) {
        int index = 0;
        for (Pack pack : sortingMachine.getQueuedPacks()) {
            RenderableObject renderable = packRenderablesById.get(pack.getId());
            if (renderable == null) {
                continue;
            }
            detachFromTote.accept(pack, renderable);
            Vec3 conveyorEntryWorld = outfeedPoint().worldPosition();
            Vec3 queueWorld = new Vec3(
                    conveyorEntryWorld.x,
                    conveyorEntryWorld.y + 0.05f + (index * SORTER_QUEUE_VERTICAL_STEP),
                    conveyorEntryWorld.z);
            renderable.transformation.xTranslation = queueWorld.x;
            renderable.transformation.yTranslation = queueWorld.y;
            renderable.transformation.zTranslation = queueWorld.z;
            renderable.transformation.angleY = rigYaw + SORTER_YAW_RADIANS + (float) Math.PI;
            index++;
            placedPackIds.add(pack.getId());
        }
    }

    private Vec3 localPointToWorld(Vec3 sorterLocalPoint) {
        Vec3 rotated = Vec3.rotateY(sorterLocalPoint, renderable.transformation.angleY);
        rotated.mutableAdd(new Vec3(
                renderable.transformation.xTranslation,
                renderable.transformation.yTranslation,
                renderable.transformation.zTranslation));
        return rotated;
    }

    private Vec3 rigLocalToWorld(Vec3 layoutOrigin, Vec3 localPoint, float rigYaw) {
        Vec3 rotated = Vec3.rotateY(localPoint, rigYaw);
        rotated.mutableAdd(layoutOrigin);
        return rotated;
    }

    private RenderableObject createSorterRenderable(TriangleRenderer tr, String id) {
        RenderableObject root = createAnchor(tr, id);
        root.transformation.yTranslation = 0.18f;

        RenderableObject leftDeck = createBox(
                tr,
                id + "_left_deck",
                0.62f,
                0.05f,
                0.12f,
                SORTER_BODY_COLOUR,
                true);
        leftDeck.transformation.xTranslation = 0f;
        leftDeck.transformation.yTranslation = 0f;
        leftDeck.transformation.zTranslation = -0.18f;
        root.addChild(leftDeck);

        RenderableObject rightDeck = createBox(
                tr,
                id + "_right_deck",
                0.62f,
                0.05f,
                0.12f,
                SORTER_BODY_COLOUR,
                false);
        rightDeck.transformation.xTranslation = 0f;
        rightDeck.transformation.yTranslation = 0f;
        rightDeck.transformation.zTranslation = 0.18f;
        root.addChild(rightDeck);

        RenderableObject frontBridge = createBox(
                tr,
                id + "_front_bridge",
                0.14f,
                0.05f,
                0.24f,
                SORTER_BODY_COLOUR,
                false);
        frontBridge.transformation.xTranslation = -0.24f;
        frontBridge.transformation.yTranslation = 0f;
        frontBridge.transformation.zTranslation = 0f;
        root.addChild(frontBridge);

        RenderableObject rearBridge = createBox(
                tr,
                id + "_rear_bridge",
                0.14f,
                0.05f,
                0.24f,
                SORTER_BODY_COLOUR,
                false);
        rearBridge.transformation.xTranslation = 0.24f;
        rearBridge.transformation.yTranslation = 0f;
        rearBridge.transformation.zTranslation = 0f;
        root.addChild(rearBridge);

        RenderableObject hopperCollar = createBox(
                tr,
                id + "_hopper_collar",
                0.20f,
                0.03f,
                0.16f,
                SORTER_OUTFEED_COLOUR,
                false);
        hopperCollar.transformation.xTranslation = 0.00f;
        hopperCollar.transformation.yTranslation = -0.02f;
        hopperCollar.transformation.zTranslation = 0.00f;
        root.addChild(hopperCollar);

        RenderableObject leftHousing = createBox(
                tr,
                id + "_left_housing",
                0.54f,
                0.18f,
                0.08f,
                SORTER_BODY_COLOUR,
                false);
        leftHousing.transformation.xTranslation = -0.01f;
        leftHousing.transformation.yTranslation = -0.11f;
        leftHousing.transformation.zTranslation = -0.20f;
        root.addChild(leftHousing);

        RenderableObject rightHousing = createBox(
                tr,
                id + "_right_housing",
                0.54f,
                0.18f,
                0.08f,
                SORTER_BODY_COLOUR,
                false);
        rightHousing.transformation.xTranslation = -0.01f;
        rightHousing.transformation.yTranslation = -0.11f;
        rightHousing.transformation.zTranslation = 0.20f;
        root.addChild(rightHousing);

        RenderableObject leftFoot = createBox(
                tr,
                id + "_left_foot",
                0.48f,
                0.05f,
                0.06f,
                SORTER_FRAME_COLOUR,
                false);
        leftFoot.transformation.xTranslation = -0.03f;
        leftFoot.transformation.yTranslation = -0.22f;
        leftFoot.transformation.zTranslation = -0.20f;
        root.addChild(leftFoot);

        RenderableObject rightFoot = createBox(
                tr,
                id + "_right_foot",
                0.48f,
                0.05f,
                0.06f,
                SORTER_FRAME_COLOUR,
                false);
        rightFoot.transformation.xTranslation = -0.03f;
        rightFoot.transformation.yTranslation = -0.22f;
        rightFoot.transformation.zTranslation = 0.20f;
        root.addChild(rightFoot);

        RenderableObject intakeHopper = createFunnelHopperRenderable(
                tr,
                id + "_intake_hopper",
                0.34f,
                0.18f,
                0.22f,
                0.12f,
                0.14f,
                SORTER_HOPPER_WALL_THICKNESS,
                SORTER_INTAKE_COLOUR);
        intakeHopper.transformation.xTranslation = 0.00f;
        intakeHopper.transformation.yTranslation = 0.12f;
        intakeHopper.transformation.zTranslation = -0.01f;
        root.addChild(intakeHopper);

        RenderableObject meteringCover = createBox(
                tr,
                id + "_metering_cover",
                0.16f,
                0.06f,
                0.18f,
                SORTER_PANEL_COLOUR,
                false);
        meteringCover.transformation.xTranslation = 0.24f;
        meteringCover.transformation.yTranslation = 0.08f;
        meteringCover.transformation.zTranslation = 0.12f;
        root.addChild(meteringCover);

        RenderableObject meteringPedestal = createBox(
                tr,
                id + "_metering_pedestal",
                0.10f,
                0.04f,
                0.10f,
                SORTER_OUTFEED_COLOUR,
                false);
        meteringPedestal.transformation.xTranslation = 0.18f;
        meteringPedestal.transformation.yTranslation = 0.03f;
        meteringPedestal.transformation.zTranslation = 0.08f;
        root.addChild(meteringPedestal);

        RenderableObject leftFrame = createBox(
                tr,
                id + "_left_frame",
                0.66f,
                0.03f,
                0.03f,
                SORTER_FRAME_COLOUR,
                false);
        leftFrame.transformation.xTranslation = 0f;
        leftFrame.transformation.yTranslation = -0.02f;
        leftFrame.transformation.zTranslation = -0.14f;
        root.addChild(leftFrame);

        RenderableObject rightFrame = createBox(
                tr,
                id + "_right_frame",
                0.66f,
                0.03f,
                0.03f,
                SORTER_FRAME_COLOUR,
                false);
        rightFrame.transformation.xTranslation = 0f;
        rightFrame.transformation.yTranslation = -0.02f;
        rightFrame.transformation.zTranslation = 0.14f;
        root.addChild(rightFrame);

        return root;
    }

    private RenderableObject createFunnelHopperRenderable(
            TriangleRenderer tr,
            String id,
            float topLength,
            float bottomLength,
            float topWidth,
            float bottomWidth,
            float height,
            float wallThickness,
            int colour) {
        float topY = height * 0.5f;
        float bottomY = -height * 0.5f;
        float topHalfLength = topLength * 0.5f;
        float bottomHalfLength = bottomLength * 0.5f;
        float topHalfWidth = topWidth * 0.5f;
        float bottomHalfWidth = bottomWidth * 0.5f;
        float innerTopHalfLength = topHalfLength - wallThickness;
        float innerBottomHalfLength = bottomHalfLength - wallThickness;
        float innerTopHalfWidth = topHalfWidth - wallThickness;
        float innerBottomHalfWidth = bottomHalfWidth - wallThickness;

        if (innerTopHalfLength <= 0f || innerBottomHalfLength <= 0f
                || innerTopHalfWidth <= 0f || innerBottomHalfWidth <= 0f) {
            throw new IllegalArgumentException("wallThickness too large for hopper dimensions");
        }

        Vec4[] vertices = new Vec4[] {
                new Vec4(-topHalfLength, topY, -topHalfWidth, 1f),
                new Vec4(topHalfLength, topY, -topHalfWidth, 1f),
                new Vec4(topHalfLength, topY, topHalfWidth, 1f),
                new Vec4(-topHalfLength, topY, topHalfWidth, 1f),
                new Vec4(-bottomHalfLength, bottomY, -bottomHalfWidth, 1f),
                new Vec4(bottomHalfLength, bottomY, -bottomHalfWidth, 1f),
                new Vec4(bottomHalfLength, bottomY, bottomHalfWidth, 1f),
                new Vec4(-bottomHalfLength, bottomY, bottomHalfWidth, 1f),

                new Vec4(-innerTopHalfLength, topY, -innerTopHalfWidth, 1f),
                new Vec4(innerTopHalfLength, topY, -innerTopHalfWidth, 1f),
                new Vec4(innerTopHalfLength, topY, innerTopHalfWidth, 1f),
                new Vec4(-innerTopHalfLength, topY, innerTopHalfWidth, 1f),
                new Vec4(-innerBottomHalfLength, bottomY, -innerBottomHalfWidth, 1f),
                new Vec4(innerBottomHalfLength, bottomY, -innerBottomHalfWidth, 1f),
                new Vec4(innerBottomHalfLength, bottomY, innerBottomHalfWidth, 1f),
                new Vec4(-innerBottomHalfLength, bottomY, innerBottomHalfWidth, 1f)
        };

        int[][] triangles = new int[][] {
                {0, 5, 4}, {0, 1, 5},
                {1, 6, 5}, {1, 2, 6},
                {2, 7, 6}, {2, 3, 7},
                {3, 4, 7}, {3, 0, 4},

                {8, 12, 13}, {8, 13, 9},
                {9, 13, 14}, {9, 14, 10},
                {10, 14, 15}, {10, 15, 11},
                {11, 15, 12}, {11, 12, 8},

                {0, 8, 9}, {0, 9, 1},
                {1, 9, 10}, {1, 10, 2},
                {2, 10, 11}, {2, 11, 3},
                {3, 11, 8}, {3, 8, 0},

                {4, 13, 12}, {4, 5, 13},
                {5, 14, 13}, {5, 6, 14},
                {6, 15, 14}, {6, 7, 15},
                {7, 12, 15}, {7, 4, 12}
        };

        return RenderableObject.create(
                id,
                tr,
                new Mesh(vertices, triangles, id + "_mesh"),
                new Mat4.ObjectTransformation(0f, 0f, 0f, 0f, 0f, 0f, new Mat4()),
                new OneColourStrategyImpl(colour),
                false);
    }

    private RenderableObject createBox(
            TriangleRenderer tr,
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
                new Mat4.ObjectTransformation(0f, 0f, 0f, 0f, 0f, 0f, new Mat4()),
                new OneColourStrategyImpl(colour),
                selectable);
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
                new Mat4.ObjectTransformation(0f, 0f, 0f, 0f, 0f, 0f, new Mat4()),
                triangleIndex -> 0,
                false);
    }
}
