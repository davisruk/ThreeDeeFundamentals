package online.davisfamily.warehouse.sim.totebag.assembly;

import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.matrices.Vec4;
import online.davisfamily.threedee.model.Mesh;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.rendering.TriangleRenderer;
import online.davisfamily.threedee.rendering.appearance.OneColourStrategyImpl;
import online.davisfamily.warehouse.rendering.model.tracks.ConveyorRuntimeState;
import online.davisfamily.warehouse.rendering.model.tracks.StraightConveyorFactory;
import online.davisfamily.warehouse.rendering.model.tracks.StraightConveyorFactory.ConveyorVisualSpeed;
import online.davisfamily.warehouse.rendering.model.tracks.StraightConveyorFactory.StraightConveyorSpec;
import online.davisfamily.warehouse.rendering.model.tracks.TrackAppearance;
import online.davisfamily.warehouse.sim.totebag.handoff.MachineHandoffPointId;
import online.davisfamily.warehouse.sim.totebag.handoff.PackHandoffPoint;
import online.davisfamily.warehouse.sim.totebag.layout.ToteToBagAttachmentPose;
import online.davisfamily.warehouse.sim.totebag.machine.BaggingMachine;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.BagSpec;

public class BaggingModule {
    private static final int BODY_COLOUR = 0xFF7A6A56;
    private static final int PANEL_COLOUR = 0xFFB89B7A;
    private static final int CHUTE_COLOUR = 0xFF8A8A8A;
    private static final int OPENING_COLOUR = 0xFF2E2A26;

    private static final float BODY_LENGTH = 0.80f;
    private static final float BODY_HEIGHT = 0.56f;
    private static final float BODY_WIDTH = 0.90f;

    private static final float INTAKE_ONE_LENGTH = 0.58f;
    private static final float INTAKE_ONE_WIDTH = 0.16f;
    private static final float INTAKE_TWO_LENGTH = 0.28f;
    private static final float INTAKE_TWO_WIDTH = 0.14f;
    private static final float CONVEYOR_ROLLER_RADIUS = 0.030f;
    private static final float CONVEYOR_BELT_THICKNESS = 0.010f;
    private static final float CONVEYOR_RETURN_DROP = 0.08f;
    private static final float PCR_ROLLER_RADIUS = 0.050f;
    private static final float PCR_BELT_THICKNESS = 0.010f;
    private static final float INTAKE_ONE_ANGLE_Z = (float) Math.toRadians(10d);
    private static final float INTAKE_ONE_START_GAP_X = 0.03f;
    private static final float INTAKE_ONE_START_TOP_DROP = 0.005f;
    private static final float INTAKE_TRANSFER_GAP_X = 0.02f;
    private static final float INTAKE_OPENING_LENGTH = 0.06f;
    private static final float INTAKE_OPENING_HEIGHT = 0.30f;
    private static final float INTAKE_OPENING_WIDTH = 0.24f;
    private static final float OUTFEED_OPENING_LENGTH = 0.06f;
    private static final float OUTFEED_OPENING_HEIGHT = 0.36f;
    private static final float OUTFEED_OPENING_WIDTH = 0.28f;
    private static final float CHUTE_LENGTH = 0.58f;

    private static final float BODY_FACE_OFFSET_X = 0.02f;

    private final BaggingMachine baggingMachine;
    private final RenderableObject renderable;
    private final Vec3 intakePointLocal;
    private final Vec3 bagChuteStartLocal;
    private final Vec3 bagOutfeedLocal;
    private final IntakePathGeometry intakePathGeometry;

    public BaggingModule(
            TriangleRenderer tr,
            BaggingMachine baggingMachine,
            ToteToBagAttachmentPose mountPose,
            ToteToBagAttachmentPose pcrOutfeedPose,
            TrackAppearance conveyorAppearance) {
        if (tr == null
                || baggingMachine == null
                || mountPose == null
                || pcrOutfeedPose == null
                || conveyorAppearance == null) {
            throw new IllegalArgumentException("BaggingModule inputs must not be null");
        }
        this.baggingMachine = baggingMachine;
        this.renderable = createAnchor(tr, "bagging_machine");
        renderable.transformation.xTranslation = mountPose.x();
        renderable.transformation.yTranslation = mountPose.y();
        renderable.transformation.zTranslation = mountPose.z();
        renderable.transformation.angleY = mountPose.yawRadians();
        IntakeAndOutfeedAnchors anchors = addChildren(tr, conveyorAppearance, pcrOutfeedPose);
        this.intakePointLocal = anchors.intakePointLocal();
        this.bagChuteStartLocal = anchors.bagChuteStartLocal();
        this.bagOutfeedLocal = anchors.bagOutfeedLocal();
        this.intakePathGeometry = anchors.intakePathGeometry();
    }

    public BaggingMachine getBaggingMachine() {
        return baggingMachine;
    }

    public RenderableObject getRenderable() {
        return renderable;
    }

    public PackHandoffPoint intakePoint() {
        return new PackHandoffPoint(
                MachineHandoffPointId.BAGGING_PACK_INTAKE.name().toLowerCase(),
                localPointToWorld(intakePointLocal),
                renderable.transformation.angleY);
    }

    public Vec3 bagOutfeedWorldPoint() {
        return localPointToWorld(bagOutfeedLocal);
    }

    public BagDischargePose resolveBagDischargePose(double progress, BagSpec bagSpec) {
        if (bagSpec == null) {
            throw new IllegalArgumentException("bagSpec must not be null");
        }
        float clampedProgress = (float) Math.max(0d, Math.min(1d, progress));
        Vec3 localPosition = pointAlongSegment(bagChuteStartLocal, bagOutfeedLocal, clampedProgress);
        localPosition.mutableAdd(new Vec3(0f, bagSpec.height() * 0.5f + 0.025f, 0f));
        return new BagDischargePose(
                localPointToWorld(localPosition),
                renderable.transformation.angleY,
                (float) -Math.toRadians(28d));
    }

    public float intakeTravelDistance() {
        return intakePathGeometry.pcrToIntakeBlendLength() + INTAKE_ONE_LENGTH + INTAKE_TRANSFER_GAP_X + INTAKE_TWO_LENGTH;
    }

    public float intakeTravelDistanceFor(PackDimensions dimensions) {
        if (dimensions == null) {
            throw new IllegalArgumentException("dimensions must not be null");
        }
        float intakeStartToHoldCenterDistance = intakeTravelDistance() + BODY_FACE_OFFSET_X;
        return intakeStartToHoldCenterDistance + (dimensions.length() * 0.5f);
    }

    public IntakePackPose resolveIntakePackPose(float frontDistanceFromIntakeStart, PackDimensions dimensions) {
        if (dimensions == null) {
            throw new IllegalArgumentException("dimensions must not be null");
        }
        float centerDistance = frontDistanceFromIntakeStart - (dimensions.length() * 0.5f);
        IntakeSegmentPose localPose = resolveIntakeSegmentPose(centerDistance);
        Vec3 localPosition = positionPackOnSegmentTop(
                localPose.localPosition(),
                localPose.angleZRadians(),
                localPose.topSurfaceOffset(),
                dimensions);
        return new IntakePackPose(
                localPointToWorld(localPosition),
                renderable.transformation.angleY,
                localPose.angleZRadians());
    }

    private Vec3 worldPointToLocal(Vec3 worldPoint) {
        Vec3 local = new Vec3(
                worldPoint.x - renderable.transformation.xTranslation,
                worldPoint.y - renderable.transformation.yTranslation,
                worldPoint.z - renderable.transformation.zTranslation);
        return Vec3.rotateY(local, -renderable.transformation.angleY);
    }

    private Vec3 localPointToWorld(Vec3 localPoint) {
        Vec3 rotated = Vec3.rotateY(localPoint, renderable.transformation.angleY);
        rotated.mutableAdd(new Vec3(
                renderable.transformation.xTranslation,
                renderable.transformation.yTranslation,
                renderable.transformation.zTranslation));
        return rotated;
    }

    private IntakeAndOutfeedAnchors addChildren(
            TriangleRenderer tr,
            TrackAppearance conveyorAppearance,
            ToteToBagAttachmentPose pcrOutfeedPose) {
        Vec3 pcrOutfeedLocal = worldPointToLocal(new Vec3(pcrOutfeedPose.x(), pcrOutfeedPose.y(), pcrOutfeedPose.z()));
        float pcrTopSurfaceLocalY = pcrOutfeedLocal.y + conveyorTopSurfaceOffset(PCR_ROLLER_RADIUS, PCR_BELT_THICKNESS);
        float intakeStartTopLocalY = pcrTopSurfaceLocalY - INTAKE_ONE_START_TOP_DROP;
        float intakeStartCenterlineLocalY = intakeStartTopLocalY - conveyorTopSurfaceOffset(CONVEYOR_ROLLER_RADIUS, CONVEYOR_BELT_THICKNESS);
        Vec3 intakeOneStartLocal = new Vec3(
                pcrOutfeedLocal.x + INTAKE_ONE_START_GAP_X,
                intakeStartCenterlineLocalY,
                pcrOutfeedLocal.z);
        Vec3 intakeOneLocal = centerFromStart(intakeOneStartLocal, INTAKE_ONE_LENGTH, INTAKE_ONE_ANGLE_Z);
        Vec3 intakeOneEndLocal = endFromCenter(intakeOneLocal, INTAKE_ONE_LENGTH, INTAKE_ONE_ANGLE_Z);
        Vec3 intakeTwoStartLocal = new Vec3(
                intakeOneEndLocal.x + INTAKE_TRANSFER_GAP_X,
                intakeOneEndLocal.y,
                intakeOneEndLocal.z);
        Vec3 intakeTwoLocal = centerFromStart(intakeTwoStartLocal, INTAKE_TWO_LENGTH, 0f);
        Vec3 intakeTwoEndLocal = endFromCenter(intakeTwoLocal, INTAKE_TWO_LENGTH, 0f);
        float bodyLocalX = intakeTwoEndLocal.x + BODY_FACE_OFFSET_X + (BODY_LENGTH * 0.5f);
        float conveyorTopOffset = conveyorTopSurfaceOffset(CONVEYOR_ROLLER_RADIUS, CONVEYOR_BELT_THICKNESS);
        Vec3 intakeMouthLocal = new Vec3(
                bodyLocalX - (BODY_LENGTH * 0.5f) - (INTAKE_OPENING_LENGTH * 0.5f),
                intakeTwoEndLocal.y + conveyorTopOffset + (INTAKE_OPENING_HEIGHT * 0.5f),
                intakeTwoEndLocal.z);
        Vec3 intakeHoldLocal = new Vec3(
                intakeMouthLocal.x + (INTAKE_OPENING_LENGTH * 0.5f),
                intakeTwoEndLocal.y,
                intakeTwoEndLocal.z);
        Vec3 chuteStartLocal = new Vec3(
                bodyLocalX + (BODY_LENGTH * 0.5f) + (CHUTE_LENGTH * 0.5f),
                -0.20f,
                0f);
        float chuteStartTopLocalY = chuteStartLocal.y + conveyorTopOffset;
        Vec3 bagChuteStartLocal = new Vec3(
                chuteStartLocal.x - (CHUTE_LENGTH * 0.5f),
                chuteStartLocal.y + ((float) Math.sin(Math.toRadians(28d)) * (CHUTE_LENGTH * 0.5f)),
                chuteStartLocal.z);
        Vec3 bagOutfeedLocal = new Vec3(
                chuteStartLocal.x + (CHUTE_LENGTH * 0.5f),
                chuteStartLocal.y - ((float) Math.sin(Math.toRadians(28d)) * (CHUTE_LENGTH * 0.5f)),
                chuteStartLocal.z);

        RenderableObject body = createBox(
                tr,
                "bagging_machine_body",
                BODY_LENGTH,
                BODY_HEIGHT,
                BODY_WIDTH,
                BODY_COLOUR);
        body.transformation.xTranslation = bodyLocalX;
        body.transformation.yTranslation = 0.08f;
        renderable.addChild(body);

        RenderableObject topPanel = createBox(
                tr,
                "bagging_machine_top_panel",
                0.56f,
                0.04f,
                0.60f,
                PANEL_COLOUR);
        topPanel.transformation.xTranslation = bodyLocalX - 0.06f;
        topPanel.transformation.yTranslation = 0.34f;
        topPanel.transformation.zTranslation = 0f;
        renderable.addChild(topPanel);

        RenderableObject intakeMouth = createBox(
                tr,
                "bagging_machine_intake_mouth",
                INTAKE_OPENING_LENGTH,
                INTAKE_OPENING_HEIGHT,
                INTAKE_OPENING_WIDTH,
                OPENING_COLOUR);
        intakeMouth.transformation.xTranslation = intakeMouthLocal.x;
        intakeMouth.transformation.yTranslation = intakeMouthLocal.y;
        intakeMouth.transformation.zTranslation = intakeMouthLocal.z;
        renderable.addChild(intakeMouth);

        RenderableObject intakeOne = StraightConveyorFactory.create(
                "bagging_machine_intake_one",
                tr,
                new StraightConveyorSpec(
                        INTAKE_ONE_LENGTH,
                        INTAKE_ONE_WIDTH,
                        CONVEYOR_ROLLER_RADIUS,
                        CONVEYOR_BELT_THICKNESS,
                        CONVEYOR_RETURN_DROP,
                        0.06f,
                        0.007f,
                        ConveyorVisualSpeed.fixed(0.8d)),
                new ConveyorRuntimeState(),
                conveyorAppearance);
        intakeOne.transformation.xTranslation = intakeOneLocal.x;
        intakeOne.transformation.yTranslation = intakeOneLocal.y;
        intakeOne.transformation.zTranslation = intakeOneLocal.z;
        intakeOne.transformation.angleZ = INTAKE_ONE_ANGLE_Z;
        renderable.addChild(intakeOne);

        RenderableObject intakeTwo = StraightConveyorFactory.create(
                "bagging_machine_intake_two",
                tr,
                new StraightConveyorSpec(
                        INTAKE_TWO_LENGTH,
                        INTAKE_TWO_WIDTH,
                        CONVEYOR_ROLLER_RADIUS,
                        CONVEYOR_BELT_THICKNESS,
                        CONVEYOR_RETURN_DROP,
                        0.05f,
                        0.007f,
                        ConveyorVisualSpeed.fixed(0.8d)),
                new ConveyorRuntimeState(),
                conveyorAppearance);
        intakeTwo.transformation.xTranslation = intakeTwoLocal.x;
        intakeTwo.transformation.yTranslation = intakeTwoLocal.y;
        intakeTwo.transformation.zTranslation = intakeTwoLocal.z;
        renderable.addChild(intakeTwo);

        RenderableObject outfeedMouth = createBox(
                tr,
                "bagging_machine_outfeed_mouth",
                OUTFEED_OPENING_LENGTH,
                OUTFEED_OPENING_HEIGHT,
                OUTFEED_OPENING_WIDTH,
                OPENING_COLOUR);
        outfeedMouth.transformation.xTranslation = bodyLocalX + (BODY_LENGTH * 0.5f) + (OUTFEED_OPENING_LENGTH * 0.5f);
        outfeedMouth.transformation.yTranslation = chuteStartTopLocalY + (OUTFEED_OPENING_HEIGHT * 0.5f);
        outfeedMouth.transformation.zTranslation = chuteStartLocal.z;
        renderable.addChild(outfeedMouth);

        RenderableObject chute = createBox(
                tr,
                "bagging_machine_chute",
                CHUTE_LENGTH,
                0.03f,
                0.20f,
                CHUTE_COLOUR);
        chute.transformation.xTranslation = chuteStartLocal.x;
        chute.transformation.yTranslation = chuteStartLocal.y;
        chute.transformation.zTranslation = chuteStartLocal.z;
        chute.transformation.angleZ = (float) -Math.toRadians(28d);
        renderable.addChild(chute);

        return new IntakeAndOutfeedAnchors(
                intakeOneStartLocal,
                bagChuteStartLocal,
                bagOutfeedLocal,
                new IntakePathGeometry(
                        pcrOutfeedLocal,
                        intakeOneStartLocal,
                        intakeOneEndLocal,
                        intakeTwoStartLocal,
                        intakeTwoEndLocal,
                        intakeHoldLocal,
                        pcrOutfeedLocal.y,
                        conveyorTopSurfaceOffset(PCR_ROLLER_RADIUS, PCR_BELT_THICKNESS),
                        conveyorTopSurfaceOffset(CONVEYOR_ROLLER_RADIUS, CONVEYOR_BELT_THICKNESS),
                        pcrOutfeedLocal.distanceTo(intakeOneStartLocal),
                        INTAKE_ONE_ANGLE_Z));
    }

    private record IntakeAndOutfeedAnchors(
            Vec3 intakePointLocal,
            Vec3 bagChuteStartLocal,
            Vec3 bagOutfeedLocal,
            IntakePathGeometry intakePathGeometry) {
    }

    public record IntakePackPose(
            Vec3 worldPosition,
            float yawRadians,
            float angleZRadians) {
    }

    public record BagDischargePose(
            Vec3 worldPosition,
            float yawRadians,
            float angleZRadians) {
    }

    private record IntakePathGeometry(
            Vec3 pcrOutfeedLocal,
            Vec3 intakeOneStartLocal,
            Vec3 intakeOneEndLocal,
            Vec3 intakeTwoStartLocal,
            Vec3 intakeTwoEndLocal,
            Vec3 intakeHoldLocal,
            float pcrCenterlineLocalY,
            float pcrTopSurfaceOffset,
            float intakeTopSurfaceOffset,
            float pcrToIntakeBlendLength,
            float intakeOneAngleZRadians) {
    }

    private record IntakeSegmentPose(
            Vec3 localPosition,
            float angleZRadians,
            float topSurfaceOffset) {
    }

    private float conveyorTopSurfaceOffset(float rollerRadius, float beltThickness) {
        return (rollerRadius * 2f) + (beltThickness * 0.5f);
    }

    private Vec3 centerFromStart(Vec3 startLocal, float length, float angleZ) {
        float dx = (float) Math.cos(angleZ) * (length * 0.5f);
        float dy = (float) Math.sin(angleZ) * (length * 0.5f);
        return new Vec3(startLocal.x + dx, startLocal.y + dy, startLocal.z);
    }

    private Vec3 endFromCenter(Vec3 centerLocal, float length, float angleZ) {
        float dx = (float) Math.cos(angleZ) * (length * 0.5f);
        float dy = (float) Math.sin(angleZ) * (length * 0.5f);
        return new Vec3(centerLocal.x + dx, centerLocal.y + dy, centerLocal.z);
    }

    private IntakeSegmentPose resolveIntakeSegmentPose(float centerDistance) {
        if (centerDistance <= 0f) {
            return new IntakeSegmentPose(
                    new Vec3(
                            intakePathGeometry.pcrOutfeedLocal().x + centerDistance,
                            intakePathGeometry.pcrCenterlineLocalY(),
                            intakePathGeometry.pcrOutfeedLocal().z),
                    0f,
                    intakePathGeometry.pcrTopSurfaceOffset());
        }
        if (centerDistance <= intakePathGeometry.pcrToIntakeBlendLength()) {
            float blendT = centerDistance / intakePathGeometry.pcrToIntakeBlendLength();
            return new IntakeSegmentPose(
                    pointAlongSegment(
                            intakePathGeometry.pcrOutfeedLocal(),
                            intakePathGeometry.intakeOneStartLocal(),
                            blendT),
                    intakePathGeometry.intakeOneAngleZRadians() * blendT,
                    Vec3.lerp(
                            intakePathGeometry.pcrTopSurfaceOffset(),
                            intakePathGeometry.intakeTopSurfaceOffset(),
                            blendT));
        }

        float distanceAfterBlend = centerDistance - intakePathGeometry.pcrToIntakeBlendLength();
        if (distanceAfterBlend <= INTAKE_ONE_LENGTH) {
            return new IntakeSegmentPose(
                    pointAlongSegment(
                            intakePathGeometry.intakeOneStartLocal(),
                            intakePathGeometry.intakeOneEndLocal(),
                            distanceAfterBlend / INTAKE_ONE_LENGTH),
                    intakePathGeometry.intakeOneAngleZRadians(),
                    intakePathGeometry.intakeTopSurfaceOffset());
        }

        float distanceAfterIntakeOne = distanceAfterBlend - INTAKE_ONE_LENGTH;
        if (distanceAfterIntakeOne <= INTAKE_TRANSFER_GAP_X) {
            return new IntakeSegmentPose(
                    pointAlongSegment(
                            intakePathGeometry.intakeOneEndLocal(),
                            intakePathGeometry.intakeTwoStartLocal(),
                            distanceAfterIntakeOne / INTAKE_TRANSFER_GAP_X),
                    0f,
                    intakePathGeometry.intakeTopSurfaceOffset());
        }

        float distanceAlongIntakeTwo = distanceAfterIntakeOne - INTAKE_TRANSFER_GAP_X;
        if (distanceAlongIntakeTwo <= INTAKE_TWO_LENGTH) {
            return new IntakeSegmentPose(
                    pointAlongSegment(
                            intakePathGeometry.intakeTwoStartLocal(),
                            intakePathGeometry.intakeTwoEndLocal(),
                            distanceAlongIntakeTwo / INTAKE_TWO_LENGTH),
                    0f,
                    intakePathGeometry.intakeTopSurfaceOffset());
        }

        return new IntakeSegmentPose(
                intakePathGeometry.intakeHoldLocal(),
                0f,
                intakePathGeometry.intakeTopSurfaceOffset());
    }

    private Vec3 pointAlongSegment(Vec3 start, Vec3 end, float t) {
        return Vec3.immutableLerp(start, end, Math.max(0f, Math.min(1f, t)));
    }

    private Vec3 positionPackOnSegmentTop(
            Vec3 segmentCenterlineLocal,
            float angleZRadians,
            float topSurfaceOffset,
            PackDimensions dimensions) {
        float topOffset = topSurfaceOffset + (dimensions.height() * 0.5f);
        if (Math.abs(angleZRadians) < 0.0001f) {
            return new Vec3(
                    segmentCenterlineLocal.x,
                    segmentCenterlineLocal.y + topOffset,
                    segmentCenterlineLocal.z);
        }

        Vec3 localUpFromBelt = Vec3.rotateZ(new Vec3(0f, topOffset, 0f), angleZRadians);
        return segmentCenterlineLocal.add(localUpFromBelt);
    }

    private RenderableObject createBox(
            TriangleRenderer tr,
            String id,
            float length,
            float height,
            float width,
            int colour) {
        return RenderableObject.create(
                id,
                tr,
                online.davisfamily.warehouse.rendering.model.tracks.RollerMeshFactory.createBoxRollerMesh(length, height, width),
                new Mat4.ObjectTransformation(0f, 0f, 0f, 0f, 0f, 0f, new Mat4()),
                new OneColourStrategyImpl(colour),
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
                new Mat4.ObjectTransformation(0f, 0f, 0f, 0f, 0f, 0f, new Mat4()),
                triangleIndex -> 0,
                false);
    }
}
