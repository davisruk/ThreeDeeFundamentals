package online.davisfamily.warehouse.rendering.model.tracks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.rendering.TriangleRenderer;

public final class RouteTrackFactory {

    public static class SpecAndSegment {
    	TrackSpec spec;
    	RouteSegment segment;
        WarehouseSegmentMetadata metadata;
    	
    	
    	public SpecAndSegment(TrackSpec spec, RouteSegment segment, WarehouseSegmentMetadata metadata) {
			super();
			this.spec = spec;
			this.segment = segment;
            this.metadata = metadata;
		}


		public static SpecAndSegment createSpecAndSegment(TrackSpec ts, RouteSegment rs, WarehouseSegmentMetadata metadata) {
    		return new SpecAndSegment(ts, rs, metadata);
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
                    new WarehouseSegmentMetadata(),
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
                    ss.metadata,
                    ss.spec,
                    appearance));
        }
        return result;
    }
	
}
