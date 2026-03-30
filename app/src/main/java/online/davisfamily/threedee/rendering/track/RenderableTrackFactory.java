package online.davisfamily.threedee.rendering.track;

import java.util.ArrayList;
import java.util.List;

import online.davisfamily.threedee.behaviour.Behaviour;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.behaviour.transformation.SpinBehaviour;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation;
import online.davisfamily.threedee.model.Mesh;
import online.davisfamily.threedee.model.tracks.GuideSide;
import online.davisfamily.threedee.model.tracks.RollerMeshFactory;
import online.davisfamily.threedee.model.tracks.RouteTrackLayout;
import online.davisfamily.threedee.model.tracks.RouteTrackLayoutFactory;
import online.davisfamily.threedee.model.tracks.TrackAppearance;
import online.davisfamily.threedee.model.tracks.TrackBuildResult;
import online.davisfamily.threedee.model.tracks.TrackBuilder;
import online.davisfamily.threedee.model.tracks.TrackInterval;
import online.davisfamily.threedee.model.tracks.TrackIntervalType;
import online.davisfamily.threedee.model.tracks.TrackSpan;
import online.davisfamily.threedee.model.tracks.TrackSpec;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.rendering.TriangleRenderer;

public final class RenderableTrackFactory {

    private RenderableTrackFactory() {
    }

    public static RenderableObject createRenderableTrack(
            TriangleRenderer tr,
            RouteSegment routeSegment,
            TrackSpec spec,
            TrackAppearance appearance) {

        RouteTrackLayout layout = RouteTrackLayoutFactory.create(routeSegment);
        ObjectTransformation identity =
                new ObjectTransformation(0f, 0f, 0f, 0f, 0f, 0f, new Mat4());

        List<RenderableObject> parts = new ArrayList<>();

        Mesh sharedRollerMesh = spec.includeRollers
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
                    isTransfer ? "transfer" : "deck",
            		tr,
                    built.deckMesh,
                    identity,
                    isTransfer ? appearance.transferDeckColour : appearance.deckColour,
                    isTransfer);

            if (spec.includeRollers && sharedRollerMesh != null && built.rollerTransforms != null) {
                addRollers(tr, deckObject, sharedRollerMesh, built.rollerTransforms, appearance.rollerColour);
            }

            parts.add(deckObject);
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

    private static void addRollers(
            TriangleRenderer tr,
            RenderableObject parent,
            Mesh rollerMesh,
            List<ObjectTransformation> rollerTransforms,
            online.davisfamily.threedee.rendering.appearance.ColourPickerStrategy colour) {
    	Behaviour spin = new SpinBehaviour(0f,0f,1f);
        List<RenderableObject> rollers = new ArrayList<>();
        int i=0;
        for (ObjectTransformation t : rollerTransforms) {
            rollers.add(RenderableObject.createWithBehaviours("roller_" + i, tr, rollerMesh, t, colour, false, spin));
            i++;
        }
        parent.addAllChildren(rollers);
    }
}