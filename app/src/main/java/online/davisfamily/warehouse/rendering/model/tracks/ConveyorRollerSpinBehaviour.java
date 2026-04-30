package online.davisfamily.warehouse.rendering.model.tracks;

import online.davisfamily.threedee.behaviour.Behaviour;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation;
import online.davisfamily.threedee.rendering.RenderableObject;

public class ConveyorRollerSpinBehaviour implements Behaviour {
    private final float speedX;
    private final float speedY;
    private final float speedZ;
    private final ConveyorRuntimeState runtimeState;

    public ConveyorRollerSpinBehaviour(float speedX, float speedY, float speedZ, ConveyorRuntimeState runtimeState) {
        this.speedX = speedX;
        this.speedY = speedY;
        this.speedZ = speedZ;
        this.runtimeState = runtimeState;
    }

    @Override
    public void update(RenderableObject object, double dtSeconds) {
        double scale = runtimeState != null ? runtimeState.resolveSpeed(1d) : 1d;
        ObjectTransformation t = object.transformation;
        t.angleX += speedX * dtSeconds * scale;
        t.angleY += speedY * dtSeconds * scale;
        t.angleZ += speedZ * dtSeconds * scale;
    }
}
