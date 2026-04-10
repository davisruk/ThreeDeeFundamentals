package online.davisfamily.warehouse.rendering.model.tracks;

import java.util.ArrayList;
import java.util.List;

import online.davisfamily.threedee.behaviour.Behaviour;
import online.davisfamily.threedee.behaviour.transformation.SpinBehaviour;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation;
import online.davisfamily.threedee.model.Mesh;
import online.davisfamily.threedee.model.cylinder.CylinderFactory;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.rendering.TriangleRenderer;
import online.davisfamily.threedee.rendering.appearance.ColourPickerStrategy;

public final class StraightConveyorFactory {

    public record StraightConveyorSpec(
            float length,
            float width,
            float rollerRadius,
            float beltThickness,
            float returnDrop,
            float markerLength,
            float markerThickness,
            double beltSpeedUnitsPerSecond) {
    }

    private StraightConveyorFactory() {
    }

    public static RenderableObject create(
            String id,
            TriangleRenderer tr,
            StraightConveyorSpec spec,
            TrackAppearance appearance) {

        float innerTopLength = Math.max(0.05f, spec.length() - (2f * spec.rollerRadius()));
        float rollerCentreY = spec.rollerRadius();
        float wrapRadius = spec.rollerRadius() + (spec.beltThickness() * 0.5f);
        float topY = rollerCentreY + spec.rollerRadius();
        float bottomY = rollerCentreY - spec.rollerRadius();
        float startRollerX = -innerTopLength * 0.5f;
        float endRollerX = innerTopLength * 0.5f;

        RenderableObject root = RenderableObject.create(
                id,
                tr,
                createInvisibleAnchorMesh(),
                new ObjectTransformation(0f, 0f, 0f, 0f, 0f, 0f, new Mat4()),
                triangleIndex -> 0,
                false);

        List<RenderableObject> children = new ArrayList<>();

        Mesh topBeltMesh = RollerMeshFactory.createBoxRollerMesh(innerTopLength, spec.beltThickness(), spec.width());
        Mesh bottomBeltMesh = RollerMeshFactory.createBoxRollerMesh(innerTopLength, spec.beltThickness(), spec.width());
        Mesh wrapMesh = CylinderFactory.buildCylinder(wrapRadius, spec.width(), 20, true);
        Mesh rollerMesh = CylinderFactory.buildCylinder(spec.rollerRadius(), spec.width() * 0.92f, 20, true);
        Mesh markerMesh = RollerMeshFactory.createBoxRollerMesh(spec.markerLength(), spec.markerThickness(), spec.width() * 0.96f);

        children.add(RenderableObject.create(
                id + "_top_belt",
                tr,
                topBeltMesh,
                new ObjectTransformation(0f, 0f, 0f, 0f, topY, 0f, new Mat4()),
                appearance.conveyorBeltColour,
                false));

        children.add(RenderableObject.create(
                id + "_bottom_belt",
                tr,
                bottomBeltMesh,
                new ObjectTransformation(0f, 0f, 0f, 0f, bottomY, 0f, new Mat4()),
                appearance.conveyorBeltColour,
                false));

        children.add(RenderableObject.create(
                id + "_start_wrap",
                tr,
                wrapMesh,
                new ObjectTransformation(0f, 0f, 0f, startRollerX, rollerCentreY, 0f, new Mat4()),
                appearance.conveyorBeltColour,
                false));

        children.add(RenderableObject.create(
                id + "_end_wrap",
                tr,
                wrapMesh,
                new ObjectTransformation(0f, 0f, 0f, endRollerX, rollerCentreY, 0f, new Mat4()),
                appearance.conveyorBeltColour,
                false));

        Behaviour rollerSpin = new SpinBehaviour(0f, 0f, 4f);
        children.add(RenderableObject.createWithBehaviours(
                id + "_start_roller",
                tr,
                rollerMesh,
                new ObjectTransformation(0f, 0f, 0f, startRollerX, rollerCentreY, 0f, new Mat4()),
                appearance.rollerColour,
                false,
                rollerSpin));

        children.add(RenderableObject.createWithBehaviours(
                id + "_end_roller",
                tr,
                rollerMesh,
                new ObjectTransformation(0f, 0f, 0f, endRollerX, rollerCentreY, 0f, new Mat4()),
                appearance.rollerColour,
                false,
                rollerSpin));

        float wrapLength = (float) (Math.PI * wrapRadius);
        float loopLength = innerTopLength + wrapLength + innerTopLength + wrapLength;

        children.add(createMarker(
                id + "_marker_a",
                tr,
                markerMesh,
                appearance.conveyorMarkerColour,
                spec,
                innerTopLength,
                wrapRadius,
                topY + (spec.beltThickness() * 0.5f) + (spec.markerThickness() * 0.5f) + 0.0015f,
                bottomY - (spec.beltThickness() * 0.5f) - (spec.markerThickness() * 0.5f) - 0.0015f,
                startRollerX,
                endRollerX,
                0f));

        children.add(createMarker(
                id + "_marker_b",
                tr,
                markerMesh,
                appearance.conveyorMarkerColour,
                spec,
                innerTopLength,
                wrapRadius,
                topY + (spec.beltThickness() * 0.5f) + (spec.markerThickness() * 0.5f) + 0.0015f,
                bottomY - (spec.beltThickness() * 0.5f) - (spec.markerThickness() * 0.5f) - 0.0015f,
                startRollerX,
                endRollerX,
                loopLength * 0.5f));

        root.addAllChildren(children);
        return root;
    }

    private static RenderableObject createMarker(
            String id,
            TriangleRenderer tr,
            Mesh markerMesh,
            ColourPickerStrategy colour,
            StraightConveyorSpec spec,
            float straightLength,
            float wrapRadius,
            float topY,
            float bottomY,
            float startRollerX,
            float endRollerX,
            float phaseOffset) {
        return RenderableObject.createWithBehaviours(
                id,
                tr,
                markerMesh,
                new ObjectTransformation(0f, 0f, 0f, startRollerX, topY, 0f, new Mat4()),
                colour,
                false,
                new StraightConveyorMarkerBehaviour(
                        straightLength,
                        straightLength,
                        wrapRadius,
                        topY,
                        bottomY,
                        startRollerX,
                        endRollerX,
                        spec.beltSpeedUnitsPerSecond(),
                        phaseOffset));
    }

    private static Mesh createInvisibleAnchorMesh() {
        return new Mesh(
                new online.davisfamily.threedee.matrices.Vec4[] {
                        new online.davisfamily.threedee.matrices.Vec4(0f, 0f, 0f, 1f),
                        new online.davisfamily.threedee.matrices.Vec4(0f, 0f, 0f, 1f),
                        new online.davisfamily.threedee.matrices.Vec4(0f, 0f, 0f, 1f)
                },
                new int[][] { {0, 1, 2} },
                "anchor");
    }
}
