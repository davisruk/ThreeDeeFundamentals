package online.davisfamily.warehouse.sim.transfer.mechanism;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation;
import online.davisfamily.threedee.matrices.Vec4;
import online.davisfamily.threedee.model.Mesh;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.warehouse.sim.transfer.TransferOutcome;

class SteeringConveyorMechanismTest {

    @Test
    void shouldAnimateWhenBranchTargetChangesWhileAlreadyReadyForBranch() {
        RenderableObject renderable = renderable("steering");
        SteeringConveyorMechanism mechanism = new SteeringConveyorMechanism(
                "mechanism",
                List.of(renderable),
                0f,
                0.5f,
                1.0f,
                0.001f,
                TransferOutcome.BRANCH);

        mechanism.setBranchYawRadians(1.5f);

        assertEquals(MechanismMotionState.MOVING_TO_BRANCH, mechanism.getMotionState());
        assertEquals(0.5f, renderable.transformation.angleY, 0.0001f);

        mechanism.update(0.25d);

        assertEquals(0.75f, renderable.transformation.angleY, 0.0001f);
    }

    private static RenderableObject renderable(String id) {
        return RenderableObject.create(
                id,
                null,
                new Mesh(
                        new Vec4[] {
                                new Vec4(0f, 0f, 0f, 1f),
                                new Vec4(0f, 0f, 0f, 1f),
                                new Vec4(0f, 0f, 0f, 1f)
                        },
                        new int[][] { {0, 1, 2} },
                        "anchor"),
                new ObjectTransformation(0f, 0f, 0f, 0f, 0f, 0f, new Mat4()),
                triangleIndex -> 0,
                false);
    }
}
