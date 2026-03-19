package online.davisfamily.threedee.behaviour;

import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation.Axis;
import online.davisfamily.threedee.rendering.RenderableObject;

public class PingPongRotationBehaviour implements Behaviour{
    private final Axis axis;
    private final float minRadians, maxRadians;
    private float speedRadiansPerSecond;

    public PingPongRotationBehaviour(Axis axis, float minDegrees, float maxDegrees, float speedDegreesPerSecond) {
    	float min = Math.min(minDegrees, maxDegrees);
    	float max = Math.max(minDegrees, maxDegrees);
    	this.minRadians = (float)Math.toRadians(min);
    	this.maxRadians = (float)Math.toRadians(max);
    	this.axis = axis;
        this.speedRadiansPerSecond = (float)Math.toRadians(speedDegreesPerSecond);
    }

    @Override
    public void update(RenderableObject object, double dtSeconds) {
        ObjectTransformation t = object.transformation;

        float value = t.getAxisRotation(axis);
        value += speedRadiansPerSecond * dtSeconds;

        if (value < minRadians) {
            value = minRadians;
            speedRadiansPerSecond = Math.abs(speedRadiansPerSecond);
        } else if (value > maxRadians) {
            value = maxRadians;
            speedRadiansPerSecond = -Math.abs(speedRadiansPerSecond);
        }

        t.setAxisRotation(axis, value);
        
    }
}
