package online.davisfamily.threedee.behaviour.routing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import online.davisfamily.threedee.model.tracks.RouteTrackLayoutFactory;
import online.davisfamily.threedee.model.tracks.TrackAppearance;
import online.davisfamily.threedee.model.tracks.TrackSpec;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.rendering.TriangleRenderer;
import online.davisfamily.threedee.rendering.track.RenderableTrackFactory;

public final class RouteTrackFactory {

    public static List<RenderableObject> createRenderableTracks(
            TriangleRenderer tr,
            Collection<RouteSegment> routeSegments,
            TrackSpec spec,
            TrackAppearance appearance) {

        List<RenderableObject> result = new ArrayList<>();
        for (RouteSegment rs : routeSegments) {
            result.add(RenderableTrackFactory.createRenderableTrack(
                    tr,
                    rs,
                    spec,
                    appearance));
        }
        return result;
    }
}
