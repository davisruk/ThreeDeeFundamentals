package online.davisfamily.threedee.rendering.track;

import java.util.ArrayList;
import java.util.List;

import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation;
import online.davisfamily.threedee.model.Mesh;
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
                ? RollerMeshFactory.createBoxRollerMesh(spec)
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

            RenderableObject deckObject = RenderableObject.create(
                    tr,
                    built.deckMesh,
                    identity,
                    interval.getType() == TrackIntervalType.TRANSFER
                            ? appearance.transferDeckColour
                            : appearance.deckColour);

            if (spec.includeRollers && sharedRollerMesh != null && built.rollerTransforms != null) {
                addRollers(tr, deckObject, sharedRollerMesh, built.rollerTransforms, appearance.rollerColour);
            }

            parts.add(deckObject);
        }

        // Build guide sections from one-or-more allowed spans
        if (spec.includeGuides) {
            List<TrackSpan> guideSpans = RouteTrackLayoutFactory.createGuideSpans(layout, spec);
            for (TrackSpan span : guideSpans) {
                Mesh guideMesh = TrackBuilder.buildGuides(
                        layout.getGeometry(),
                        spec,
                        span.getStartDistance(),
                        span.getEndDistance());

                if (guideMesh != null) {
                    parts.add(RenderableObject.create(
                            tr,
                            guideMesh,
                            identity,
                            appearance.guideColour));
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

        List<RenderableObject> rollers = new ArrayList<>();
        for (ObjectTransformation t : rollerTransforms) {
            rollers.add(RenderableObject.create(tr, rollerMesh, t, colour));
        }
        parent.addAllChildren(rollers);
    }
}