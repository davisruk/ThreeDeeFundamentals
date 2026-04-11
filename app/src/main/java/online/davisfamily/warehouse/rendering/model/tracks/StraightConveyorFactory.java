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
        float rollerWidth = spec.width() * 0.92f;

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
        Mesh rollerMesh = CylinderFactory.buildCylinder(spec.rollerRadius(), rollerWidth, 20, true);
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

        children.add(createRollerAssembly(
                id + "_start_roller",
                tr,
                rollerMesh,
                appearance,
                spec.rollerRadius(),
                spec.width(),
                startRollerX,
                rollerCentreY));

        children.add(createRollerAssembly(
                id + "_end_roller",
                tr,
                rollerMesh,
                appearance,
                spec.rollerRadius(),
                spec.width(),
                endRollerX,
                rollerCentreY));

        float wrapLength = (float) (Math.PI * wrapRadius);
        float loopLength = innerTopLength + wrapLength + innerTopLength + wrapLength;

        float markerSurfaceClearance = 0.0015f;

        children.add(createMarker(
                id + "_marker_a",
                tr,
                markerMesh,
                appearance.conveyorMarkerColour,
                spec,
                innerTopLength,
                wrapRadius,
                topY + (spec.beltThickness() * 0.5f) + (spec.markerThickness() * 0.5f) + markerSurfaceClearance,
                bottomY - (spec.beltThickness() * 0.5f) - (spec.markerThickness() * 0.5f) - markerSurfaceClearance,
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
                topY + (spec.beltThickness() * 0.5f) + (spec.markerThickness() * 0.5f) + markerSurfaceClearance,
                bottomY - (spec.beltThickness() * 0.5f) - (spec.markerThickness() * 0.5f) - markerSurfaceClearance,
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

    private static RenderableObject createRollerAssembly(
            String id,
            TriangleRenderer tr,
            Mesh rollerMesh,
            TrackAppearance appearance,
            float rollerRadius,
            float rollerWidth,
            float x,
            float y) {
        RenderableObject rollerRoot = RenderableObject.createWithBehaviours(
                id,
                tr,
                createInvisibleAnchorMesh(),
                new ObjectTransformation(0f, 0f, 0f, x, y, 0f, new Mat4()),
                triangleIndex -> 0,
                false,
                new SpinBehaviour(0f, 0f, -4f));

        rollerRoot.addChild(RenderableObject.create(
                id + "_body",
                tr,
                rollerMesh,
                new ObjectTransformation(0f, 0f, 0f, 0f, 0f, 0f, new Mat4()),
                appearance.rollerColour,
                false));

        addPlateMarkers(rollerRoot, id, tr, appearance.conveyorMarkerColour, rollerRadius, rollerWidth);
        return rollerRoot;
    }

    private static void addPlateMarkers(
            RenderableObject rollerRoot,
            String id,
            TriangleRenderer tr,
            ColourPickerStrategy colour,
            float rollerRadius,
            float rollerWidth) {
        float markerGap = Math.max(rollerRadius * 0.22f, 0.004f);
        float markerLength = Math.max(0.0025f, rollerRadius - (2f * markerGap));
        float markerWidth = Math.max(rollerRadius * 0.12f, 0.0025f);
        float markerThickness = Math.max(rollerRadius * 0.05f, 0.001f);
        float markerRadius = markerGap + (markerLength * 0.5f);
        float plateSurfaceClearance = 0.0015f;
        float halfRollerWidth = rollerWidth * 0.5f;
        Mesh plateMarkerMesh = RollerMeshFactory.createBoxRollerMesh(markerLength, markerWidth, markerThickness);

        for (int plate = 0; plate < 2; plate++) {
            float z = plate == 0
                    ? -(halfRollerWidth + (markerThickness * 0.5f) + plateSurfaceClearance)
                    : halfRollerWidth + (markerThickness * 0.5f) + plateSurfaceClearance;
            String plateId = plate == 0 ? "near" : "far";

            for (int i = 0; i < 4; i++) {
                float angle = i * ((float) Math.PI * 0.5f);
                float markerX = (float) Math.cos(angle) * markerRadius;
                float markerY = (float) Math.sin(angle) * markerRadius;

                rollerRoot.addChild(RenderableObject.create(
                        id + "_" + plateId + "_plate_marker_" + i,
                        tr,
                        plateMarkerMesh,
                        new ObjectTransformation(
                                0f,
                                0f,
                                angle,
                                markerX,
                                markerY,
                                z,
                                new Mat4()),
                        colour,
                        false));
            }
        }
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
