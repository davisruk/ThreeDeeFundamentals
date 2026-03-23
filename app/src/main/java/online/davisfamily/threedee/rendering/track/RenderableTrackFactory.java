package online.davisfamily.threedee.rendering.track;

import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.model.ColourPickerStrategy;
import online.davisfamily.threedee.model.Mesh;
import online.davisfamily.threedee.model.tracks.TrackMeshFactory;
import online.davisfamily.threedee.model.tracks.TrackSpec;
import online.davisfamily.threedee.path.Path3;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.rendering.TriangleRenderer;

public class RenderableTrackFactory {
	public static RenderableObject createRenderableTrack(TriangleRenderer tr, Path3 path, TrackSpec spec, ColourPickerStrategy colour) {
		Mesh mesh = TrackMeshFactory.build(path, spec);
		
		Mat4.ObjectTransformation transform = new Mat4.ObjectTransformation(0f, 0f, 0f, 0f, 0f, -5f, new Mat4());
		return RenderableObject.create(tr, mesh, transform, colour);
	}
}
