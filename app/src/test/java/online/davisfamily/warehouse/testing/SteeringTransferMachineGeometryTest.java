package online.davisfamily.warehouse.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.matrices.Vec3;

class SteeringTransferMachineGeometryTest {
    private static final float EPSILON = 0.0001f;

    @Test
    void shouldUseSquareFootprintForTransferMachine() {
        SteeringTransferMachineGeometry geometry = SteeringTransferMachineGeometry.fromTransferWindow(
                new Vec3(0f, 0f, -1f),
                new Vec3(0f, 0f, 0f));

        assertEquals(1f, geometry.baseLength(), EPSILON);
        assertEquals(1f, geometry.baseWidth(), EPSILON);
    }

    @Test
    void shouldAttachTargetsToMachineSideEdgesOnCenterline() {
        SteeringTransferMachineGeometry geometry = SteeringTransferMachineGeometry.fromTransferWindow(
                new Vec3(0f, 0f, -1f),
                new Vec3(0f, 0f, 0f));

        assertEquals(-0.5f, geometry.leftTargetAttachmentPoint().x, EPSILON);
        assertEquals(-0.5f, geometry.leftTargetAttachmentPoint().z, EPSILON);
        assertEquals(0.5f, geometry.rightTargetAttachmentPoint().x, EPSILON);
        assertEquals(-0.5f, geometry.rightTargetAttachmentPoint().z, EPSILON);
    }
}
