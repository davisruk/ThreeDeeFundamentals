package online.davisfamily.threedee.behaviour.transformation;

import online.davisfamily.threedee.behaviour.Behaviour;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation.Axis;
import online.davisfamily.threedee.rendering.RenderableObject;

public class ClampedRotationBehaviour implements Behaviour {
    private final Axis axis;
    private final float targetRadians;
    private final float speedRadiansPerSecond;

    public ClampedRotationBehaviour(Axis axis, float targetDegrees, float speedDegreesPerSecond) {
        if (axis == null) {
            throw new IllegalArgumentException("axis must not be null");
        }
        if (speedDegreesPerSecond < 0f) {
            throw new IllegalArgumentException("speedDegreesPerSecond must be >= 0");
        }
        this.axis = axis;
        this.targetRadians = (float) Math.toRadians(targetDegrees);
        this.speedRadiansPerSecond = (float) Math.toRadians(speedDegreesPerSecond);
    }

    @Override
    public void update(RenderableObject object, double dtSeconds) {
        if (object == null || dtSeconds <= 0d) {
            return;
        }

        ObjectTransformation transform = object.transformation;
        float current = transform.getAxisRotation(axis);
        if (current == targetRadians || speedRadiansPerSecond == 0f) {
            transform.setAxisRotation(axis, targetRadians);
            return;
        }

        float delta = speedRadiansPerSecond * (float) dtSeconds;
        if (current < targetRadians) {
            transform.setAxisRotation(axis, Math.min(targetRadians, current + delta));
        } else {
            transform.setAxisRotation(axis, Math.max(targetRadians, current - delta));
        }
    }
}
