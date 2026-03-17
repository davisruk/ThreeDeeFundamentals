package online.davisfamily.threedee.behaviour;

import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation;
import online.davisfamily.threedee.rendering.RenderableObject;

public class PingPongTranslationBehaviour implements Behaviour {
    public enum Axis { X, Y, Z }

    private final Axis axis;
    private final float min;
    private final float max;
    private float speed;

    public PingPongTranslationBehaviour(Axis axis, float min, float max, float speed) {
        this.axis = axis;
        this.min = min;
        this.max = max;
        this.speed = speed;
    }

    @Override
    public void update(RenderableObject object, double dtSeconds) {
        ObjectTransformation t = object.transformation;

        float value = getAxisValue(t);
        value += speed * dtSeconds;

        if (value < min) {
            value = min;
            speed = Math.abs(speed);
        } else if (value > max) {
            value = max;
            speed = -Math.abs(speed);
        }

        setAxisValue(t, value);
    }

    private float getAxisValue(ObjectTransformation t) {
        return switch (axis) {
            case X -> t.xTranslation;
            case Y -> t.yTranslation;
            case Z -> t.zTranslation;
        };
    }

    private void setAxisValue(ObjectTransformation t, float value) {
        switch (axis) {
            case X -> t.xTranslation = value;
            case Y -> t.yTranslation = value;
            case Z -> t.zTranslation = value;
        }
    }
}