package online.davisfamily.warehouse.sim.totebag.assembly;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import online.davisfamily.threedee.debug.SelectionInspectionRegistry;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.rendering.TriangleRenderer;
import online.davisfamily.threedee.rendering.appearance.OneColourStrategyImpl;
import online.davisfamily.warehouse.rendering.model.tracks.RollerMeshFactory;
import online.davisfamily.warehouse.sim.totebag.control.ToteTrackTipperFlowController;
import online.davisfamily.warehouse.sim.totebag.pack.Pack;
import online.davisfamily.warehouse.sim.totebag.pack.Pack.PackContainmentState;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;
import online.davisfamily.warehouse.sim.totebag.transfer.TippingDischargeTransfer;

public class TipperToSorterPackVisuals {
    private final TriangleRenderer tr;
    private final List<RenderableObject> objects;
    private final SelectionInspectionRegistry inspectionRegistry;
    private final RenderableObject toteRenderable;
    private final ToteLoadPlan toteLoadPlan;
    private final Map<String, Vec3> containedPackLayoutById;
    private final float rigYaw;
    private final TipperToSorterDischargeSeam dischargeSeam;
    private final Map<String, RenderableObject> packRenderablesById = new LinkedHashMap<>();

    public TipperToSorterPackVisuals(
            TriangleRenderer tr,
            List<RenderableObject> objects,
            SelectionInspectionRegistry inspectionRegistry,
            RenderableObject toteRenderable,
            ToteLoadPlan toteLoadPlan,
            Map<String, Vec3> containedPackLayoutById,
            float rigYaw,
            TipperToSorterDischargeSeam dischargeSeam) {
        if (tr == null
                || objects == null
                || inspectionRegistry == null
                || toteRenderable == null
                || toteLoadPlan == null
                || containedPackLayoutById == null
                || dischargeSeam == null) {
            throw new IllegalArgumentException("Pack visual inputs must not be null");
        }
        this.tr = tr;
        this.objects = objects;
        this.inspectionRegistry = inspectionRegistry;
        this.toteRenderable = toteRenderable;
        this.toteLoadPlan = toteLoadPlan;
        this.containedPackLayoutById = containedPackLayoutById;
        this.rigYaw = rigYaw;
        this.dischargeSeam = dischargeSeam;
    }

    public void sync(
            ToteTrackTipperFlowController flowController,
            TipperModule tipperModule,
            SortingModule sortingModule) {
        ensurePackRenderablesExist();
        hideDetachedPacks();
        Set<String> placedPackIds = new HashSet<>();
        positionRemainingPacksInTote(flowController, placedPackIds);
        positionActiveDischarges(flowController, tipperModule, sortingModule, placedPackIds);
        sortingModule.syncQueuedPackVisuals(
                packRenderablesById,
                placedPackIds,
                (pack, renderable) -> detachFromToteIfNeeded(pack, renderable, null));
    }

    public RenderableObject getPackRenderable(String packId) {
        return packRenderablesById.get(packId);
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

    private void positionActiveDischarges(
            ToteTrackTipperFlowController flowController,
            TipperModule tipperModule,
            SortingModule sortingModule,
            Set<String> placedPackIds) {
        for (TippingDischargeTransfer transfer : flowController.getActiveDischarges()) {
            RenderableObject renderable = packRenderablesById.get(transfer.getPack().getId());
            if (renderable == null) {
                continue;
            }
            detachFromToteIfNeeded(transfer.getPack(), renderable, transfer);
            Vec3 dischargeWorld = dischargeSeam.sampleWorldPosition(
                    transfer.getPack(),
                    (float) transfer.getProgress(),
                    tipperModule,
                    sortingModule,
                    transfer.getStartWorldPosition(),
                    containedPackLocalFor(transfer.getPack().getId()));
            renderable.transformation.xTranslation = dischargeWorld.x;
            renderable.transformation.yTranslation = dischargeWorld.y;
            renderable.transformation.zTranslation = dischargeWorld.z;
            renderable.transformation.angleX = 0f;
            renderable.transformation.angleY = rigYaw;
            renderable.transformation.angleZ = 0f;
            placedPackIds.add(transfer.getPack().getId());
        }
    }

    private void positionRemainingPacksInTote(
            ToteTrackTipperFlowController flowController,
            Set<String> placedPackIds) {
        for (PackPlan plan : toteLoadPlan.getPackPlans()) {
            if (isObserved(flowController, plan.packId())) {
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

    private boolean isObserved(ToteTrackTipperFlowController flowController, String packId) {
        for (Pack pack : flowController.getObservedPacks()) {
            if (pack.getId().equals(packId)) {
                return true;
            }
        }
        return false;
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
        renderable.transformation.angleY = rigYaw;
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

    private int colourForCorrelation(String correlationId) {
        return switch (correlationId) {
            case "bag-a" -> 0xFFE67E22;
            case "bag-b" -> 0xFF4AA3DF;
            case "bag-c" -> 0xFF7ABF66;
            default -> 0xFFBBBBBB;
        };
    }
}
