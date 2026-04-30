package online.davisfamily.threedee.behaviour.transformation;

import online.davisfamily.threedee.behaviour.Behaviour;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation.Axis;
import online.davisfamily.threedee.rendering.RenderableObject;

public class LoopTranslationBehaviour implements Behaviour {
    private final Axis axis;
    private final float min;
    private final float max;
    private final float speed;

    public LoopTranslationBehaviour(Axis axis, float min, float max, float speed) {
        if (axis == null) {
            throw new IllegalArgumentException("axis must not be null");
        }
        if (max <= min) {
            throw new IllegalArgumentException("max must be > min");
        }
        this.axis = axis;
        this.min = min;
        this.max = max;
        this.speed = speed;
    }

    @Override
    public void update(RenderableObject object, double dtSeconds) {
        ObjectTransformation t = object.transformation;
        float value = t.getAxisTranslation(axis);
        value += speed * dtSeconds;

        float span = max - min;
        while (value > max) {
            value -= span;
        }
        while (value < min) {
            value += span;
        }

        t.setAxisTranslation(axis, value);
    }
}
