package online.davisfamily.warehouse.sim.totebag.assembly;

import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.totebag.handoff.MachineHandoffPointId;
import online.davisfamily.warehouse.sim.totebag.handoff.PackHandoffPoint;
import online.davisfamily.warehouse.sim.totebag.machine.TippingMachine;

public class TipperModule {
    private static final float SLIDE_LENGTH = 1.20f;
    private static final float SORTER_INTAKE_CLEARANCE = 0.08f;

    private final Tote tote;
    private final TippingMachine tippingMachine;
    private final RenderableObject toteRenderable;
    private final RenderableObject assemblyRenderable;
    private final Vec3 rigOrigin;
    private final Vec3 tipperAssemblyLocalOrigin;
    private final Vec3 dischargeToteInteriorLocal;
    private final Vec3 dischargeLidLocal;
    private final Vec3 dischargeSlideEntryLocal;
    private final float tippedAngleRadians;
    private final float rigYaw;

    public TipperModule(
            Tote tote,
            TippingMachine tippingMachine,
            RenderableObject toteRenderable,
            RenderableObject assemblyRenderable,
            Vec3 rigOrigin,
            Vec3 tipperAssemblyLocalOrigin,
            Vec3 dischargeToteInteriorLocal,
            Vec3 dischargeLidLocal,
            Vec3 dischargeSlideEntryLocal,
            float tippedAngleRadians,
            float rigYaw) {
        if (tote == null
                || tippingMachine == null
                || toteRenderable == null
                || assemblyRenderable == null
                || rigOrigin == null
                || tipperAssemblyLocalOrigin == null
                || dischargeToteInteriorLocal == null
                || dischargeLidLocal == null
                || dischargeSlideEntryLocal == null) {
            throw new IllegalArgumentException("TipperModule inputs must not be null");
        }
        this.tote = tote;
        this.tippingMachine = tippingMachine;
        this.toteRenderable = toteRenderable;
        this.assemblyRenderable = assemblyRenderable;
        this.rigOrigin = Vec3.copy(rigOrigin);
        this.tipperAssemblyLocalOrigin = Vec3.copy(tipperAssemblyLocalOrigin);
        this.dischargeToteInteriorLocal = Vec3.copy(dischargeToteInteriorLocal);
        this.dischargeLidLocal = Vec3.copy(dischargeLidLocal);
        this.dischargeSlideEntryLocal = Vec3.copy(dischargeSlideEntryLocal);
        this.tippedAngleRadians = tippedAngleRadians;
        this.rigYaw = rigYaw;
    }

    public Tote getTote() {
        return tote;
    }

    public TippingMachine getTippingMachine() {
        return tippingMachine;
    }

    public RenderableObject getToteRenderable() {
        return toteRenderable;
    }

    public RenderableObject getAssemblyRenderable() {
        return assemblyRenderable;
    }

    public void syncVisuals(float visualTipProgress) {
        assemblyRenderable.transformation.angleX = tippedAngleRadians * visualTipProgress;
    }

    public float currentTipAngle() {
        return tippedAngleRadians * tippingMachine.getTipProgress();
    }

    public PackHandoffPoint dischargePoint() {
        return new PackHandoffPoint(
                MachineHandoffPointId.TIPPER_PACK_DISCHARGE.name().toLowerCase(),
                localToWorld(tipperAssemblyPointToRigLocal(
                        0f,
                        dischargeSlideEntryLocal.y - 0.05f,
                        dischargeSlideEntryLocal.z - (SLIDE_LENGTH - 0.02f),
                        tippedAngleRadians)),
                rigYaw);
    }

    public Vec3 dischargeToteInteriorWorld() {
        return localToWorld(tipperAssemblyPointToRigLocal(
                dischargeToteInteriorLocal.x,
                dischargeToteInteriorLocal.y,
                dischargeToteInteriorLocal.z,
                currentTipAngle()));
    }

    public Vec3 dischargeLidWorld() {
        return localToWorld(tipperAssemblyPointToRigLocal(
                dischargeLidLocal.x,
                dischargeLidLocal.y,
                dischargeLidLocal.z,
                currentTipAngle()));
    }

    public Vec3 dischargeSlideEntryWorld() {
        return localToWorld(tipperAssemblyPointToRigLocal(
                dischargeSlideEntryLocal.x,
                dischargeSlideEntryLocal.y,
                dischargeSlideEntryLocal.z,
                currentTipAngle()));
    }

    public Vec3 sorterIntakeMountLocalPoint() {
        return tipperAssemblyPointToRigLocal(
                0f,
                dischargeSlideEntryLocal.y - 0.05f,
                dischargeSlideEntryLocal.z - SLIDE_LENGTH - SORTER_INTAKE_CLEARANCE + 0.02f,
                tippedAngleRadians);
    }

    private Vec3 tipperAssemblyPointToRigLocal(
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

    private Vec3 localToWorld(Vec3 localPoint) {
        Vec3 rotated = Vec3.rotateY(localPoint, rigYaw);
        rotated.mutableAdd(rigOrigin);
        return rotated;
    }

    private float rotatedY(float localY, float localZ, float angleX) {
        return (float) ((localY * Math.cos(angleX)) - (localZ * Math.sin(angleX)));
    }

    private float rotatedZ(float localY, float localZ, float angleX) {
        return (float) ((localY * Math.sin(angleX)) + (localZ * Math.cos(angleX)));
    }
}
