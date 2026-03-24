package online.davisfamily.threedee.rendering.track;

import java.util.ArrayList;
import java.util.List;

import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation;
import online.davisfamily.threedee.model.ColourPickerStrategy;
import online.davisfamily.threedee.model.Mesh;
import online.davisfamily.threedee.model.tracks.RollerMeshFactory;
import online.davisfamily.threedee.model.tracks.TrackAppearance;
import online.davisfamily.threedee.model.tracks.TrackBuildResult;
import online.davisfamily.threedee.model.tracks.TrackBuilder;
import online.davisfamily.threedee.model.tracks.TrackSpec;
import online.davisfamily.threedee.path.PathSegment3;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.rendering.TriangleRenderer;

public class RenderableTrackFactory {
	public static RenderableObject createRenderableTrack(TriangleRenderer tr, PathSegment3 geometry, TrackSpec spec, TrackAppearance appearance) {
		TrackBuildResult track = TrackBuilder.build(geometry, spec);
		ObjectTransformation transform = new ObjectTransformation(0f, 0f, 0f, 0f, 0f, 0f, new Mat4());
		RenderableObject rDeck = RenderableObject.create(tr, track.deckMesh, transform, appearance.deckColour);
		if(spec.includeGuides) {
			rDeck.addChild(RenderableObject.create(tr, track.guideMesh, transform, appearance.guideColour));
		}
		if(spec.includeRollers)
			rDeck = createAndAddRollers(tr, spec, rDeck, track.rollerTransforms, appearance.rollerColour);
		
		return rDeck;
	}
	
	private static RenderableObject createAndAddRollers(TriangleRenderer tr, TrackSpec spec, RenderableObject parent, List<ObjectTransformation> rollerTransforms, ColourPickerStrategy colour) {
		List<RenderableObject> rollers = new ArrayList<>();
		Mesh rollerMesh = RollerMeshFactory.createBoxRollerMesh(spec);
		for (ObjectTransformation t:rollerTransforms) {
			rollers.add(RenderableObject.create(tr, rollerMesh, t, colour));
		}
		parent.addAllChildren(rollers);
		return parent;
	}
}
