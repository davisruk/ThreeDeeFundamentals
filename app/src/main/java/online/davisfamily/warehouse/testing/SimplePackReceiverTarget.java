package online.davisfamily.warehouse.testing;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import online.davisfamily.threedee.debug.SelectionInspectionRegistry;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.rendering.TriangleRenderer;
import online.davisfamily.threedee.rendering.appearance.OneColourStrategyImpl;
import online.davisfamily.warehouse.rendering.model.tracks.RollerMeshFactory;
import online.davisfamily.warehouse.sim.totebag.handoff.PackHandoffPoint;
import online.davisfamily.warehouse.sim.totebag.handoff.PackReceiveTarget;
import online.davisfamily.warehouse.sim.totebag.pack.Pack;

public class SimplePackReceiverTarget implements PackReceiveTarget {
    private static final float BODY_LENGTH = 0.72f;
    private static final float BODY_HEIGHT = 0.14f;
    private static final float BODY_WIDTH = 0.34f;
    private static final float INTAKE_FORWARD_OFFSET = -0.22f;
    private static final float PACK_SURFACE_Y = 0.10f;
    private static final float PACK_ROW_STEP = 0.14f;

    private final RenderableObject renderable;
    private final Vec3 intakeLocalPoint;
    private final List<Pack> receivedPacks = new ArrayList<>();

    public SimplePackReceiverTarget(
            TriangleRenderer tr,
            List<RenderableObject> objects,
            SelectionInspectionRegistry inspectionRegistry,
            Vec3 worldPosition,
            float yawRadians) {
        if (tr == null || objects == null || worldPosition == null) {
            throw new IllegalArgumentException("Simple receiver inputs must not be null");
        }

        renderable = RenderableObject.create(
                "simple_pack_receiver",
                tr,
                RollerMeshFactory.createBoxRollerMesh(BODY_LENGTH, BODY_HEIGHT, BODY_WIDTH),
                new Mat4.ObjectTransformation(0f, 0f, 0f, worldPosition.x, worldPosition.y, worldPosition.z, new Mat4()),
                new OneColourStrategyImpl(0xFF7B6D5A),
                true);
        renderable.transformation.angleY = yawRadians;
        objects.add(renderable);
        intakeLocalPoint = new Vec3(INTAKE_FORWARD_OFFSET, BODY_HEIGHT * 0.5f, 0f);

        if (inspectionRegistry != null) {
            inspectionRegistry.register(renderable, () -> List.of(
                    "Type: Simple pack receiver",
                    "Received packs: " + receivedPacks.size()));
        }
    }

    @Override
    public PackHandoffPoint handoffPoint() {
        return new PackHandoffPoint(
                renderable.id + "_intake",
                localPointToWorld(intakeLocalPoint),
                renderable.transformation.angleY);
    }

    @Override
    public boolean canAccept(Pack pack) {
        return pack != null;
    }

    @Override
    public void accept(Pack pack) {
        if (pack == null) {
            throw new IllegalArgumentException("pack must not be null");
        }
        if (!receivedPacks.contains(pack)) {
            receivedPacks.add(pack);
        }
        pack.setState(Pack.PackMotionState.HELD);
    }

    public void syncVisuals(Function<String, RenderableObject> packRenderableResolver) {
        for (int i = 0; i < receivedPacks.size(); i++) {
            Pack pack = receivedPacks.get(i);
            RenderableObject packRenderable = packRenderableResolver.apply(pack.getId());
            if (packRenderable == null) {
                continue;
            }
            float rowOffset = (i * PACK_ROW_STEP);
            Vec3 local = new Vec3(
                    intakeLocalPoint.x + 0.14f + rowOffset,
                    PACK_SURFACE_Y + (pack.getDimensions().height() * 0.5f),
                    0f);
            Vec3 world = localPointToWorld(local);
            packRenderable.transformation.xTranslation = world.x;
            packRenderable.transformation.yTranslation = world.y;
            packRenderable.transformation.zTranslation = world.z;
            packRenderable.transformation.angleY = renderable.transformation.angleY;
        }
    }

    private Vec3 localPointToWorld(Vec3 localPoint) {
        Vec3 rotated = Vec3.rotateY(localPoint, renderable.transformation.angleY);
        rotated.mutableAdd(new Vec3(
                renderable.transformation.xTranslation,
                renderable.transformation.yTranslation,
                renderable.transformation.zTranslation));
        return rotated;
    }
}
