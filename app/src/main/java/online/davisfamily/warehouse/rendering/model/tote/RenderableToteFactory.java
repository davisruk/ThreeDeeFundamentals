package online.davisfamily.warehouse.rendering.model.tote;

import java.util.List;

import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation;
import online.davisfamily.threedee.model.Mesh;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.rendering.RenderableObject.FORWARD_DIRECTION;
import online.davisfamily.threedee.rendering.TriangleRenderer;
import online.davisfamily.threedee.rendering.appearance.OneColourStrategyImpl;

public class RenderableToteFactory {
	private static final String LEFT_LID_SUFFIX = "_LeftLid";
	private static final String RIGHT_LID_SUFFIX = "_RightLid";

	public static RenderableObject createRenderableTote(String id, TriangleRenderer tr, ToteGeometry tote, boolean selectable) {
		OneColourStrategyImpl blueColour = new OneColourStrategyImpl(0xFF0000FF);
		OneColourStrategyImpl yellowColour = new OneColourStrategyImpl(0xFFFFFF00);
		OneColourStrategyImpl redColour = new OneColourStrategyImpl(0xFFFF0000);

		float openingWidth = 0.371f;   // same value passed into LidFactory
		float halfWidth = openingWidth / 2f;

		// lid meshes
		LidFactory.InterlockingLids lids = LidFactory.buildInterlockingAngularLids(
		    0.371f,  // openingWidth
		    0.547f,  // openingDepth
		    0.020f,  // thickness
		    5,       // toothCount
		    0.010f,  // seamAmplitude
		    0.24f,   // valleyFlatFraction
		    0.24f    // peakFlatFraction
		);

		// left lid
		RenderableObject rLidLeft = createRenderableLid(id, lids.leftMesh, tote.outerHeight, -halfWidth, tr, yellowColour, true);
		rLidLeft.selectable = true;
		// right lid
		RenderableObject rLidRight = createRenderableLid(id, lids.rightMesh, tote.outerHeight, +halfWidth, tr, redColour, false);
		rLidRight.selectable = true;
		// tote transformation
		ObjectTransformation tTote = new ObjectTransformation(
			0.0f,0.0f,0f, // rotation xyz
			0f,0f,-5f, // translation xyz
			new Mat4()
		);

		// tote mesh
		Mesh mTote = new Mesh(tote.v4Vertices, tote.triangles);
		
		// renderable tote
		RenderableObject rTote = RenderableObject.createWithChildren(
			id,
			tr,
			mTote, // mesh
			tTote, // transform
			blueColour,
			List.of(rLidRight, rLidLeft), // children
			FORWARD_DIRECTION.NEGATIVE_X,
			selectable
		);
		if (selectable) {
			rLidLeft.setSelectionTarget(rTote);
			rLidRight.setSelectionTarget(rTote);
		}
		return rTote;
	}
	

	private static RenderableObject createRenderableLid(String parentId, Mesh lidMesh, float yOffset, float lidWidth, TriangleRenderer tr, OneColourStrategyImpl colour, boolean isLeft) {
		ObjectTransformation tLid = new ObjectTransformation(
			    0f, 0f, 0f, // rotation xyz
			    lidWidth, yOffset, 0f, // translation xyz
			    new Mat4()
			);

		String id = isLeft ? parentId + LEFT_LID_SUFFIX : parentId + RIGHT_LID_SUFFIX;
		return RenderableObject.create(
			id,
			tr,
			lidMesh, // mesh
			tLid, // transform
			colour,
			true
		);
		
	}
}
