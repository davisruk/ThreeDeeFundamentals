package online.davisfamily.warehouse.sim.totebag.layout;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;

public class ContainedPackLayout {
    private final float containerWidth;
    private final float containerDepth;
    private final float baseY;
    private final float gapX;
    private final float gapZ;
    private final float gapY;

    public ContainedPackLayout(
            float containerWidth,
            float containerDepth,
            float baseY,
            float gapX,
            float gapZ,
            float gapY) {
        if (containerWidth <= 0f) {
            throw new IllegalArgumentException("containerWidth must be > 0");
        }
        if (containerDepth <= 0f) {
            throw new IllegalArgumentException("containerDepth must be > 0");
        }
        if (gapX < 0f || gapZ < 0f || gapY < 0f) {
            throw new IllegalArgumentException("gaps must be >= 0");
        }
        this.containerWidth = containerWidth;
        this.containerDepth = containerDepth;
        this.baseY = baseY;
        this.gapX = gapX;
        this.gapZ = gapZ;
        this.gapY = gapY;
    }

    public Map<String, Vec3> layoutPackPlans(List<PackPlan> packPlans) {
        Map<String, Vec3> result = new LinkedHashMap<>();
        float left = -containerWidth * 0.5f;
        float back = -containerDepth * 0.5f;

        float layerBaseY = baseY;
        float layerDepthUsed = 0f;
        float layerHeight = 0f;

        float rowXCursor = 0f;
        float rowZStart = 0f;
        float rowDepth = 0f;
        float rowHeight = 0f;

        boolean rowStarted = false;

        for (PackPlan packPlan : packPlans) {
            PackDimensions dimensions = packPlan.dimensions();
            float packLength = dimensions.length();
            float packDepth = dimensions.width();
            float packHeight = dimensions.height();

            if (packLength > containerWidth) {
                throw new IllegalArgumentException("Pack " + packPlan.packId() + " length exceeds container width");
            }
            if (packDepth > containerDepth) {
                throw new IllegalArgumentException("Pack " + packPlan.packId() + " width exceeds container depth");
            }

            float requiredRowWidth = rowStarted ? rowXCursor + gapX + packLength : packLength;
            if (rowStarted && requiredRowWidth > containerWidth) {
                layerDepthUsed = Math.max(layerDepthUsed, rowZStart + rowDepth);
                layerHeight = Math.max(layerHeight, rowHeight);
                rowZStart = layerDepthUsed + gapZ;
                rowXCursor = 0f;
                rowDepth = 0f;
                rowHeight = 0f;
                rowStarted = false;
            }

            float proposedRowDepth = Math.max(rowDepth, packDepth);
            if (!rowStarted && rowZStart + proposedRowDepth > containerDepth) {
                layerBaseY += layerHeight + gapY;
                layerDepthUsed = 0f;
                layerHeight = 0f;
                rowXCursor = 0f;
                rowZStart = 0f;
                rowDepth = 0f;
                rowHeight = 0f;
                rowStarted = false;
                proposedRowDepth = packDepth;
            }

            float packMinX = rowStarted ? rowXCursor + gapX : 0f;
            float localX = left + packMinX + (packLength * 0.5f);
            float localY = layerBaseY + (packHeight * 0.5f);
            float localZ = back + rowZStart + (packDepth * 0.5f);
            result.put(packPlan.packId(), new Vec3(localX, localY, localZ));

            rowXCursor = packMinX + packLength;
            rowDepth = Math.max(rowDepth, packDepth);
            rowHeight = Math.max(rowHeight, packHeight);
            rowStarted = true;
        }

        return result;
    }
}
