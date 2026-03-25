package online.davisfamily.threedee.rendering.track;

import java.util.ArrayList;
import java.util.List;

import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation;
import online.davisfamily.threedee.model.Mesh;
import online.davisfamily.threedee.model.tracks.RollerMeshFactory;
import online.davisfamily.threedee.model.tracks.RouteTrackLayout;
import online.davisfamily.threedee.model.tracks.TrackAppearance;
import online.davisfamily.threedee.model.tracks.TrackBuildResult;
import online.davisfamily.threedee.model.tracks.TrackBuilder;
import online.davisfamily.threedee.model.tracks.TrackInterval;
import online.davisfamily.threedee.model.tracks.TrackSpec;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.rendering.TriangleRenderer;
import online.davisfamily.threedee.rendering.appearance.ColourPickerStrategy;
import online.davisfamily.threedee.rendering.appearance.OneColourStrategyImpl;

public class RenderableTrackFactory {

	private static final ColourPickerStrategy DEFAULT_CONTAINER_COLOUR = new OneColourStrategyImpl(0x00000000);

	public static RenderableObject createRenderableTrack(TriangleRenderer tr,
			RouteTrackLayout layout,
			TrackSpec spec,
			TrackAppearance appearance) {

		ObjectTransformation identity = new Mat4.ObjectTransformation(0f,0f,0f,0f,0f,0f, new Mat4());
		List<RenderableObject> intervalObjects = new ArrayList<>();

		for (TrackInterval interval : layout.getIntervals()) {
			boolean includeGuides = spec.includeGuides
					&& !(interval.isTransfer() && spec.suppressGuidesInTransferZones);
			boolean includeRollers = spec.includeRollers
					&& !(interval.isTransfer() && spec.suppressRollersInTransferZones);

			TrackBuildResult track = TrackBuilder.build(
					layout.getGeometry(),
					spec,
					interval.getStartDistance(),
					interval.getEndDistance(),
					includeGuides,
					includeRollers);

			ColourPickerStrategy deckColour = interval.isTransfer()
					? appearance.transferDeckColour
					: appearance.deckColour;

			RenderableObject intervalRoot = RenderableObject.create(tr, track.deckMesh, identity, deckColour);
			if (track.guideMesh != null) {
				intervalRoot.addChild(RenderableObject.create(tr, track.guideMesh, identity, appearance.guideColour));
			}
			if (track.rollerTransforms != null && !track.rollerTransforms.isEmpty()) {
				createAndAddRollers(tr, spec, intervalRoot, track.rollerTransforms, appearance.rollerColour);
			}
			intervalObjects.add(intervalRoot);
		}

		if (intervalObjects.isEmpty()) {
			throw new IllegalStateException("Track layout produced no interval geometry");
		}

		RenderableObject root = RenderableObject.create(tr, intervalObjects.get(0).mesh, identity, DEFAULT_CONTAINER_COLOUR);
		root.children = new ArrayList<>();
		root.colourPicker = intervalObjects.get(0).colourPicker;
		root.addAllChildren(intervalObjects.get(0).children);
		for (int i = 1; i < intervalObjects.size(); i++) {
			root.addChild(intervalObjects.get(i));
		}
		return root;
	}
	
	private static RenderableObject createAndAddRollers(TriangleRenderer tr,
			TrackSpec spec,
			RenderableObject parent,
			List<ObjectTransformation> rollerTransforms,
			ColourPickerStrategy colour) {
		List<RenderableObject> rollers = new ArrayList<>();
		Mesh rollerMesh = RollerMeshFactory.createBoxRollerMesh(spec);
		for (ObjectTransformation t:rollerTransforms) {
			rollers.add(RenderableObject.create(tr, rollerMesh, t, colour));
		}
		parent.addAllChildren(rollers);
		return parent;
	}
}
