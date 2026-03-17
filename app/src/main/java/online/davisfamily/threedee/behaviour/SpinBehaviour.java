package online.davisfamily.threedee.behaviour;

import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation;
import online.davisfamily.threedee.rendering.RenderableObject;

public class SpinBehaviour implements Behaviour {
    private final float speedX;
    private final float speedY;
    private final float speedZ;

    public SpinBehaviour(float speedX, float speedY, float speedZ) {
        this.speedX = speedX;
        this.speedY = speedY;
        this.speedZ = speedZ;
    }

    @Override
    public void update(RenderableObject object, double dtSeconds) {
        ObjectTransformation t = object.transformation;
        t.angleX += speedX * dtSeconds;
        t.angleY += speedY * dtSeconds;
        t.angleZ += speedZ * dtSeconds;
    }
}