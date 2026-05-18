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

    @Test
    void shouldAllowMachineFootprintLargerThanTransferWindow() {
        SteeringTransferMachineGeometry geometry = SteeringTransferMachineGeometry.fromTransferWindow(
                new Vec3(1f, 0f, 0f),
                new Vec3(0f, 0f, 0f),
                2f);

        assertEquals(2f, geometry.baseLength(), EPSILON);
        assertEquals(2f, geometry.baseWidth(), EPSILON);
        assertEquals(1f, geometry.sourceEntryAttachmentPoint().x, EPSILON);
        assertEquals(-1f, geometry.leftTargetAttachmentPoint().z, EPSILON);
        assertEquals(1f, geometry.rightTargetAttachmentPoint().z, EPSILON);
    }

    @Test
    void shouldAttachTargetsToSideEdgesOfRectangularMachineFootprint() {
        SteeringTransferMachineGeometry geometry = SteeringTransferMachineGeometry.fromTransferWindow(
                new Vec3(2f, 0f, 0f),
                new Vec3(0f, 0f, 0f),
                2f,
                1f);

        assertEquals(2f, geometry.baseLength(), EPSILON);
        assertEquals(1f, geometry.baseWidth(), EPSILON);
        assertEquals(-0.5f, geometry.leftTargetAttachmentPoint().z, EPSILON);
        assertEquals(0.5f, geometry.rightTargetAttachmentPoint().z, EPSILON);
    }

    @Test
    void shouldAttachParallelTargetEntryOutsideMachineFootprintAtSourceEntryEnd() {
        SteeringTransferMachineGeometry geometry = SteeringTransferMachineGeometry.fromTransferWindow(
                new Vec3(2f, 0f, 0f),
                new Vec3(0f, 0f, 0f),
                2f,
                1f);

        Vec3 leftEntry = geometry.leftTargetEntryAttachmentPoint(0.4f);
        Vec3 rightEntry = geometry.rightTargetEntryAttachmentPoint(0.4f);

        assertEquals(2f, leftEntry.x, EPSILON);
        assertEquals(-0.7f, leftEntry.z, EPSILON);
        assertEquals(2f, rightEntry.x, EPSILON);
        assertEquals(0.7f, rightEntry.z, EPSILON);
    }

    @Test
    void shouldAttachParallelTargetExitOutsideMachineFootprintAtSourceExitEnd() {
        SteeringTransferMachineGeometry geometry = SteeringTransferMachineGeometry.fromTransferWindow(
                new Vec3(2f, 0f, 0f),
                new Vec3(0f, 0f, 0f),
                2f,
                1f);

        Vec3 leftExit = geometry.leftTargetExitAttachmentPoint(0.4f);
        Vec3 rightExit = geometry.rightTargetExitAttachmentPoint(0.4f);

        assertEquals(0f, leftExit.x, EPSILON);
        assertEquals(-0.7f, leftExit.z, EPSILON);
        assertEquals(0f, rightExit.x, EPSILON);
        assertEquals(0.7f, rightExit.z, EPSILON);
    }
}
