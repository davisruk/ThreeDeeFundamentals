package online.davisfamily.warehouse.testing;

import online.davisfamily.threedee.matrices.Vec3;

public final class SteeringTransferMachineGeometry {
    private final Vec3 transferWindowStartPoint;
    private final Vec3 transferWindowEndPoint;
    private final Vec3 transferWindowCenterPoint;
    private final Vec3 leftTargetAttachmentPoint;
    private final Vec3 rightTargetAttachmentPoint;
    private final float conveyorLength;
    private final float conveyorWidth;
    private final float conveyorOffset;
    private final float baseLength;
    private final float baseWidth;

    private SteeringTransferMachineGeometry(
            Vec3 transferWindowStartPoint,
            Vec3 transferWindowEndPoint,
            Vec3 transferWindowCenterPoint,
            Vec3 leftTargetAttachmentPoint,
            Vec3 rightTargetAttachmentPoint,
            float conveyorLength,
            float conveyorWidth,
            float conveyorOffset,
            float baseLength,
            float baseWidth) {
        this.transferWindowStartPoint = transferWindowStartPoint;
        this.transferWindowEndPoint = transferWindowEndPoint;
        this.transferWindowCenterPoint = transferWindowCenterPoint;
        this.leftTargetAttachmentPoint = leftTargetAttachmentPoint;
        this.rightTargetAttachmentPoint = rightTargetAttachmentPoint;
        this.conveyorLength = conveyorLength;
        this.conveyorWidth = conveyorWidth;
        this.conveyorOffset = conveyorOffset;
        this.baseLength = baseLength;
        this.baseWidth = baseWidth;
    }

    public static SteeringTransferMachineGeometry fromTransferWindow(
            Vec3 transferWindowStartPoint,
            Vec3 transferWindowEndPoint) {
        if (transferWindowStartPoint == null || transferWindowEndPoint == null) {
            throw new IllegalArgumentException("transfer window points must not be null");
        }

        Vec3 transferVector = transferWindowEndPoint.subtract(transferWindowStartPoint);
        float transferLength = transferVector.length();
        if (transferLength <= 0f) {
            throw new IllegalArgumentException("transfer window length must be > 0");
        }

        Vec3 sourceDirection = transferVector.normalize();
        Vec3 rightDirection = new Vec3(sourceDirection.z, 0f, -sourceDirection.x).normalize();
        Vec3 centerPoint = Vec3.immutableLerp(transferWindowStartPoint, transferWindowEndPoint, 0.5f);

        float machineSize = transferLength;
        float mechanismWidth = Math.max(0.32f, machineSize * 0.58f);
        float conveyorLength = Math.max(0.14f, Math.min(0.18f, transferLength * 0.36f));
        float conveyorWidth = Math.min(conveyorLength * 0.4f, mechanismWidth * 0.28f);
        float gap = (mechanismWidth - (2f * conveyorWidth)) / 3f;
        float conveyorOffset = (conveyorWidth + gap) * 0.5f;
        float baseLength = machineSize;
        float baseWidth = machineSize;

        Vec3 leftAttachment = centerPoint.add(rightDirection.scale(-baseLength * 0.5f));
        Vec3 rightAttachment = centerPoint.add(rightDirection.scale(baseLength * 0.5f));

        return new SteeringTransferMachineGeometry(
                transferWindowStartPoint,
                transferWindowEndPoint,
                centerPoint,
                leftAttachment,
                rightAttachment,
                conveyorLength,
                conveyorWidth,
                conveyorOffset,
                baseLength,
                baseWidth);
    }

    public Vec3 transferWindowStartPoint() {
        return transferWindowStartPoint;
    }

    public Vec3 transferWindowEndPoint() {
        return transferWindowEndPoint;
    }

    public Vec3 transferWindowCenterPoint() {
        return transferWindowCenterPoint;
    }

    public Vec3 leftTargetAttachmentPoint() {
        return leftTargetAttachmentPoint;
    }

    public Vec3 rightTargetAttachmentPoint() {
        return rightTargetAttachmentPoint;
    }

    public float conveyorLength() {
        return conveyorLength;
    }

    public float conveyorWidth() {
        return conveyorWidth;
    }

    public float conveyorOffset() {
        return conveyorOffset;
    }

    public float baseLength() {
        return baseLength;
    }

    public float baseWidth() {
        return baseWidth;
    }
}
