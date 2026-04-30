package online.davisfamily.warehouse.rendering.model.tracks;

import java.util.ArrayList;
import java.util.List;

import online.davisfamily.threedee.behaviour.Behaviour;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.behaviour.transformation.SpinBehaviour;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.model.Mesh;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.rendering.TriangleRenderer;

public final class RenderableTrackFactory {

    private RenderableTrackFactory() {
    }

    public static RenderableObject createRenderableTrack(
            TriangleRenderer tr,
            RouteSegment routeSegment,
            WarehouseSegmentMetadata metadata,
            TrackSpec spec,
            TrackAppearance appearance) {
        return createRenderableTrack(tr, routeSegment, metadata, spec, appearance, null);
    }

    public static RenderableObject createRenderableTrack(
            TriangleRenderer tr,
            RouteSegment routeSegment,
            WarehouseSegmentMetadata metadata,
            TrackSpec spec,
            TrackAppearance appearance,
            ConveyorRuntimeState rollerRuntimeState) {

        RouteTrackLayout layout = RouteTrackLayoutFactory.create(routeSegment, metadata);
        ObjectTransformation identity =
                new ObjectTransformation(0f, 0f, 0f, 0f, 0f, 0f, new Mat4());

        List<RenderableObject> parts = new ArrayList<>();

        Mesh sharedRollerMesh = spec.hasRollerDrive()
                ? RollerMeshFactory.createCylinderRollerMesh(spec)//RollerMeshFactory.createBoxRollerMesh(spec)
                : null;

        // Build deck sections (normal / transfer) and attach rollers per interval
        for (TrackInterval interval : layout.getIntervals()) {
            TrackBuildResult built = TrackBuilder.buildInterval(
                    layout.getGeometry(),
                    interval,
                    spec);

            if (built.deckMesh == null) {
                continue;
            }

            boolean isTransfer = interval.getType() == TrackIntervalType.TRANSFER;
            RenderableObject deckObject = RenderableObject.create(
                    isTransfer ? interval.getTransferZone().getId() : "deck",
            		tr,
                    built.deckMesh,
                    identity,
                    isTransfer ? appearance.transferDeckColour : appearance.deckColour,
                    isTransfer);

            if (spec.hasRollerDrive() && sharedRollerMesh != null && built.rollerTransforms != null) {
                addRollers(
                        tr,
                        deckObject,
                        sharedRollerMesh,
                        built.rollerTransforms,
                        appearance.rollerColour,
                        rollerRuntimeState);
            }

            parts.add(deckObject);
        }

        if (spec.hasConveyorDrive()) {
            addConveyorVisuals(tr, routeSegment, layout, spec, appearance, parts);
        }

        // Build guide sections from one-or-more allowed spans
        if (spec.includeGuides) {
            List<TrackSpan> leftSpans = RouteTrackLayoutFactory.createGuideSpans(layout, spec, GuideSide.LEFT);
            for (TrackSpan span : leftSpans) {
                Mesh guideMesh = TrackBuilder.buildGuide(
                        layout.getGeometry(),
                        spec,
                        span.getStartDistance(),
                        span.getEndDistance(),
                        GuideSide.LEFT);

                if (guideMesh != null) {
                    parts.add(RenderableObject.create(
                            "left_guide",
                            tr,
                            guideMesh,
                            identity,
                            appearance.guideColour,
                            false));
                }
            }

            List<TrackSpan> rightSpans = RouteTrackLayoutFactory.createGuideSpans(layout, spec, GuideSide.RIGHT);
            for (TrackSpan span : rightSpans) {
                Mesh guideMesh = TrackBuilder.buildGuide(
                        layout.getGeometry(),
                        spec,
                        span.getStartDistance(),
                        span.getEndDistance(),
                        GuideSide.RIGHT);

                if (guideMesh != null) {
                    parts.add(RenderableObject.create(
                            "right_guide",
                    		tr,
                            guideMesh,
                            identity,
                            appearance.guideColour,
                    		false));
                }
            }
        }

        if (parts.isEmpty()) {
            throw new IllegalStateException("No renderable track parts were built for route segment " + routeSegment.getLabel());
        }

        RenderableObject root = parts.get(0);
        if (parts.size() > 1) {
            root.addAllChildren(parts.subList(1, parts.size()));
        }

        return root;
    }

    private static void addConveyorVisuals(
            TriangleRenderer tr,
            RouteSegment routeSegment,
            RouteTrackLayout layout,
            TrackSpec spec,
            TrackAppearance appearance,
            List<RenderableObject> parts) {

        ObjectTransformation identity =
                new ObjectTransformation(0f, 0f, 0f, 0f, 0f, 0f, new Mat4());

        Mesh beltMesh = TrackBuilder.buildConveyorBeltSurface(
                layout.getGeometry(),
                spec,
                layout.getRenderStartDistance(),
                layout.getRenderEndDistance());

        if (beltMesh != null) {
            parts.add(RenderableObject.create(
                    "conveyor_belt",
                    tr,
                    beltMesh,
                    identity,
                    appearance.conveyorBeltColour,
                    false));
        }

        Mesh endRollerMesh = ConveyorMeshFactory.createEndRollerMesh(spec);
        parts.add(createConveyorEndRoller(
                tr,
                routeSegment,
                spec,
                layout.getRenderStartDistance(),
                endRollerMesh,
                appearance.rollerColour,
                "conveyor_start_roller"));
        parts.add(createConveyorEndRoller(
                tr,
                routeSegment,
                spec,
                layout.getRenderEndDistance(),
                endRollerMesh,
                appearance.rollerColour,
                "conveyor_end_roller"));

        float segmentLength = layout.getRenderEndDistance() - layout.getRenderStartDistance();
        int markerCount = Math.max(3, (int) Math.ceil(segmentLength / spec.conveyorMarkerPitch));
        Mesh markerMesh = ConveyorMeshFactory.createMarkerMesh(spec);

        for (int i = 0; i < markerCount; i++) {
            float markerOffset = i * spec.conveyorMarkerPitch;
            ObjectTransformation markerTransform =
                    new ObjectTransformation(0f, 0f, 0f, 0f, 0f, 0f, new Mat4());
            Behaviour motion = new ConveyorMarkerBehaviour(
                    routeSegment,
                    layout.getRenderStartDistance(),
                    layout.getRenderEndDistance(),
                    spec,
                    markerOffset,
                    2.0d);
            parts.add(RenderableObject.createWithBehaviours(
                    "conveyor_marker_" + i,
                    tr,
                    markerMesh,
                    markerTransform,
                    appearance.conveyorMarkerColour,
                    false,
                    motion));
        }
    }

    private static RenderableObject createConveyorEndRoller(
            TriangleRenderer tr,
            RouteSegment routeSegment,
            TrackSpec spec,
            float distanceAlongSegment,
            Mesh rollerMesh,
            online.davisfamily.threedee.rendering.appearance.ColourPickerStrategy colour,
            String id) {

        float yaw = Vec3.yawFromDirection(
                routeSegment.getGeometry().sampleOrientationDirectionByDistance(distanceAlongSegment))
                + (float) (Math.PI / 2.0);
        Vec3 samplePoint = routeSegment.getGeometry().sampleByDistance(distanceAlongSegment);
        float rollerY = samplePoint.y + spec.deckTopY - spec.conveyorReturnDepth + ConveyorMeshFactory.getWrapRadius(spec);
        ObjectTransformation transform = new ObjectTransformation(
                0f,
                yaw,
                0f,
                samplePoint.x,
                rollerY,
                samplePoint.z,
                new Mat4());
        Behaviour spin = new SpinBehaviour(0f, 0f, 3f);
        return RenderableObject.createWithBehaviours(id, tr, rollerMesh, transform, colour, false, spin);
    }

    private static void addRollers(
            TriangleRenderer tr,
            RenderableObject parent,
            Mesh rollerMesh,
            List<ObjectTransformation> rollerTransforms,
            online.davisfamily.threedee.rendering.appearance.ColourPickerStrategy colour,
            ConveyorRuntimeState rollerRuntimeState) {
    	Behaviour spin = rollerRuntimeState == null
    			? new SpinBehaviour(0f,0f,1f)
    			: new ConveyorRollerSpinBehaviour(0f,0f,1f, rollerRuntimeState);
        List<RenderableObject> rollers = new ArrayList<>();
        int i=0;
        for (ObjectTransformation t : rollerTransforms) {
            rollers.add(RenderableObject.createWithBehaviours("roller_" + i, tr, rollerMesh, t, colour, false, spin));
            i++;
        }
        parent.addAllChildren(rollers);
    }
}
