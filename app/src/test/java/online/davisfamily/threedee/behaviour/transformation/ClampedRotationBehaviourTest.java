package online.davisfamily.threedee.behaviour.transformation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation.Axis;
import online.davisfamily.threedee.rendering.RenderableObject;

class ClampedRotationBehaviourTest {

    @Test
    void shouldRotateTowardPositiveTargetAndClamp() {
        RenderableObject object = renderableWithZRotation(0f);
        ClampedRotationBehaviour behaviour = new ClampedRotationBehaviour(Axis.Z, 90f, 45f);

        behaviour.update(object, 1.0d);
        assertEquals(Math.toRadians(45d), object.transformation.angleZ, 0.0001d);

        behaviour.update(object, 10.0d);
        assertEquals(Math.toRadians(90d), object.transformation.angleZ, 0.0001d);
    }

    @Test
    void shouldRotateTowardNegativeTargetAndClamp() {
        RenderableObject object = renderableWithZRotation(0f);
        ClampedRotationBehaviour behaviour = new ClampedRotationBehaviour(Axis.Z, -90f, 45f);

        behaviour.update(object, 1.0d);
        assertEquals(Math.toRadians(-45d), object.transformation.angleZ, 0.0001d);

        behaviour.update(object, 10.0d);
        assertEquals(Math.toRadians(-90d), object.transformation.angleZ, 0.0001d);
    }

    private RenderableObject renderableWithZRotation(float zRotationRadians) {
        return RenderableObject.create(
                "test",
                null,
                null,
                new ObjectTransformation(0f, 0f, zRotationRadians, 0f, 0f, 0f, new Mat4()),
                triangleIndex -> 0,
                false);
    }
}
