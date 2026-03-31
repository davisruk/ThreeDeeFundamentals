package online.davisfamily.threedee.behaviour.routing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.rendering.TriangleRenderer;
import online.davisfamily.warehouse.rendering.model.tracks.RenderableTrackFactory;
import online.davisfamily.warehouse.rendering.model.tracks.RouteTrackLayoutFactory;
import online.davisfamily.warehouse.rendering.model.tracks.TrackAppearance;
import online.davisfamily.warehouse.rendering.model.tracks.TrackSpec;

public final class RouteTrackFactory {

    public static class SpecAndSegment {
    	TrackSpec spec;
    	RouteSegment segment;
    	
    	
    	public SpecAndSegment(TrackSpec spec, RouteSegment segment) {
			super();
			this.spec = spec;
			this.segment = segment;
		}


		public static SpecAndSegment createSpecAndSegment(TrackSpec ts, RouteSegment rs) {
    		return new SpecAndSegment(ts, rs);
    	}
    }
    
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
	
	public static List<RenderableObject> createRenderableTracks(
            TriangleRenderer tr,
            Collection<SpecAndSegment> segments,
            TrackAppearance appearance) {

        List<RenderableObject> result = new ArrayList<>();
        for (SpecAndSegment ss : segments) {
            result.add(RenderableTrackFactory.createRenderableTrack(
                    tr,
                    ss.segment,
                    ss.spec,
                    appearance));
        }
        return result;
    }
	
}
