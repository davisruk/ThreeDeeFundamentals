package online.davisfamily.warehouse.testing;

import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperModule;
import online.davisfamily.warehouse.sim.totebag.pack.Pack;
import online.davisfamily.warehouse.sim.totebag.transfer.TippingDischargeTransfer;

public class TipperToReceiverDebugSeam {
    public Vec3 sampleWorldPosition(
            Pack pack,
            float progress,
            TipperModule tipperModule,
            Vec3 targetWorldPosition,
            Vec3 startWorldPosition,
            Vec3 containedPackLocalPosition) {
        if (pack == null || tipperModule == null || targetWorldPosition == null) {
            throw new IllegalArgumentException("Receiver seam inputs must not be null");
        }

        float clearance = (pack.getDimensions().height() * 0.5f) + 0.008f;
        Vec3 toteInteriorAnchor = tipperModule.dischargeToteInteriorWorld();
        Vec3 lidAnchor = tipperModule.dischargeLidWorld();
        Vec3 slideEntryAnchor = tipperModule.dischargeSlideEntryWorld();
        Vec3 startPoint = startWorldPosition != null
                ? Vec3.copy(startWorldPosition)
                : addClearance(containedPackLocalPosition, 0.008f);

        Vec3[] path = new Vec3[] {
                startPoint,
                addClearance(toteInteriorAnchor, clearance),
                addClearance(lidAnchor, clearance),
                addClearance(slideEntryAnchor, clearance),
                addClearance(new Vec3(
                        Vec3.lerp(slideEntryAnchor.x, targetWorldPosition.x, 0.45f),
                        Math.max(slideEntryAnchor.y, targetWorldPosition.y) + 0.10f,
                        Vec3.lerp(slideEntryAnchor.z, targetWorldPosition.z, 0.45f)), clearance),
                addClearance(targetWorldPosition, clearance)
        };
        return samplePolyline(path, progress);
    }

    private Vec3 addClearance(Vec3 point, float clearance) {
        if (point == null) {
            return new Vec3();
        }
        return new Vec3(point.x, point.y + clearance, point.z);
    }

    private Vec3 samplePolyline(Vec3[] points, float progress) {
        if (points.length == 0) {
            return new Vec3();
        }
        if (points.length == 1) {
            return Vec3.copy(points[0]);
        }

        float clamped = Math.max(0f, Math.min(1f, progress));
        float totalLength = 0f;
        float[] segmentLengths = new float[points.length - 1];
        for (int i = 0; i < points.length - 1; i++) {
            float segmentLength = points[i].distanceTo(points[i + 1]);
            segmentLengths[i] = segmentLength;
            totalLength += segmentLength;
        }

        if (totalLength <= 0.0001f) {
            return Vec3.copy(points[points.length - 1]);
        }

        float targetDistance = totalLength * clamped;
        float distanceCovered = 0f;
        for (int i = 0; i < segmentLengths.length; i++) {
            float segmentLength = segmentLengths[i];
            if (targetDistance <= distanceCovered + segmentLength || i == segmentLengths.length - 1) {
                float segmentProgress = segmentLength <= 0.0001f
                        ? 1f
                        : (targetDistance - distanceCovered) / segmentLength;
                return Vec3.immutableLerp(points[i], points[i + 1], segmentProgress);
            }
            distanceCovered += segmentLength;
        }

        return Vec3.copy(points[points.length - 1]);
    }
}
