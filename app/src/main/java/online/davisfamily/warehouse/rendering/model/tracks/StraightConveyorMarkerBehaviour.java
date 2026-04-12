package online.davisfamily.warehouse.rendering.model.tracks;

import online.davisfamily.threedee.behaviour.Behaviour;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation.Axis;
import online.davisfamily.threedee.rendering.RenderableObject;

public class StraightConveyorMarkerBehaviour implements Behaviour {
    private final float topLength;
    private final float bottomLength;
    private final float wrapRadius;
    private final float topY;
    private final float bottomY;
    private final float startRollerX;
    private final float endRollerX;
    private final double speedUnitsPerSecond;
    private final float phaseOffset;
    private final ConveyorRuntimeState runtimeState;

    private double travelledDistance;

    public StraightConveyorMarkerBehaviour(
            float topLength,
            float bottomLength,
            float wrapRadius,
            float topY,
            float bottomY,
            float startRollerX,
            float endRollerX,
            double speedUnitsPerSecond,
            float phaseOffset,
            ConveyorRuntimeState runtimeState) {
        this.topLength = topLength;
        this.bottomLength = bottomLength;
        this.wrapRadius = wrapRadius;
        this.topY = topY;
        this.bottomY = bottomY;
        this.startRollerX = startRollerX;
        this.endRollerX = endRollerX;
        this.speedUnitsPerSecond = speedUnitsPerSecond;
        this.phaseOffset = phaseOffset;
        this.runtimeState = runtimeState;
    }

    @Override
    public void update(RenderableObject object, double dtSeconds) {
        float wrapLength = (float) (Math.PI * wrapRadius);
        float loopLength = topLength + wrapLength + bottomLength + wrapLength;
        double resolvedSpeed = runtimeState != null
                ? runtimeState.resolveSpeed(speedUnitsPerSecond)
                : speedUnitsPerSecond;
        travelledDistance = (travelledDistance + (resolvedSpeed * dtSeconds)) % loopLength;

        float s = (float) ((travelledDistance + phaseOffset) % loopLength);
        ObjectTransformation t = object.transformation;

        if (s < topLength) {
            t.xTranslation = startRollerX + s;
            t.yTranslation = topY;
            t.zTranslation = 0f;
            t.setAxisRotation(Axis.X, 0f);
            t.setAxisRotation(Axis.Y, 0f);
            t.setAxisRotation(Axis.Z, 0f);
            return;
        }

        s -= topLength;
        if (s < wrapLength) {
            float angle = s / wrapRadius;
            t.xTranslation = endRollerX + (float) Math.sin(angle) * wrapRadius;
            t.yTranslation = topY - (1f - (float) Math.cos(angle)) * wrapRadius;
            t.zTranslation = 0f;
            t.setAxisRotation(Axis.X, 0f);
            t.setAxisRotation(Axis.Y, 0f);
            t.setAxisRotation(Axis.Z, -angle);
            return;
        }

        s -= wrapLength;
        if (s < bottomLength) {
            t.xTranslation = endRollerX - s;
            t.yTranslation = bottomY;
            t.zTranslation = 0f;
            t.setAxisRotation(Axis.X, 0f);
            t.setAxisRotation(Axis.Y, 0f);
            t.setAxisRotation(Axis.Z, (float) Math.PI);
            return;
        }

        s -= bottomLength;
        float angle = s / wrapRadius;
        t.xTranslation = startRollerX - (float) Math.sin(angle) * wrapRadius;
        t.yTranslation = bottomY + (1f - (float) Math.cos(angle)) * wrapRadius;
        t.zTranslation = 0f;
        t.setAxisRotation(Axis.X, 0f);
        t.setAxisRotation(Axis.Y, 0f);
        t.setAxisRotation(Axis.Z, (float) Math.PI - angle);
    }
}
